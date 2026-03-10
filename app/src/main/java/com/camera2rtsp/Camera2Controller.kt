package com.camera2rtsp

import android.hardware.camera2.CameraMetadata
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

class Camera2Controller {

    private val TAG = "Camera2Controller"

    var server: RtspServerCamera2? = null

    // Estado atual — exposto para /api/status
    var currentCameraId  = "0"
    var exposureLevel    = 0
    var whiteBalanceMode = "auto"
    var autoFocus        = true
    var zoomLevel        = 0f
    var lanternEnabled   = false
    var oisEnabled       = false
    var currentWidth     = 1920
    var currentHeight    = 1080
    var currentBitrate   = 4000   // kbps
    var currentFps       = 30

    // Thread dedicada para operações pesadas
    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(TAG, "Erro na worker", it) } }

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(TAG, "server nao inicializado"); return }

        // ── Operações LEVES (executam na thread HTTP, retorno imediato) ──

        params["iso"]?.let {
            val iso = (it as Double).toInt()
            val minEv = srv.minExposure; val maxEv = srv.maxExposure
            val ev = (((iso - 50f) / (3200f - 50f)) * (maxEv - minEv) + minEv)
                .toInt().coerceIn(minEv, maxEv)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(TAG, "ISO $iso -> EV $ev")
        }

        params["exposure"]?.let {
            val ns = (it as Double).toLong()
            val minEv = srv.minExposure; val maxEv = srv.maxExposure
            val ratio = ((ns - 125_000L).toFloat() / (1_000_000_000L - 125_000L).toFloat()).coerceIn(0f, 1f)
            exposureLevel = (minEv + ratio * (maxEv - minEv)).toInt()
            srv.setExposure(exposureLevel)
        }

        params["focus"]?.let {
            val dist = (it as Double).toFloat()
            if (dist == 0f) { srv.enableAutoFocus(); autoFocus = true }
            else            { srv.disableAutoFocus(); autoFocus = false }
        }

        params["whiteBalance"]?.let {
            whiteBalanceMode = it as String
            val mode = when (whiteBalanceMode) {
                "daylight" -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                "cloudy"   -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                "tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                else       -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            }
            srv.enableAutoWhiteBalance(mode)
        }

        params["zoom"]?.let {
            val z = (it as Double).toFloat().coerceIn(0f, 1f)
            zoomLevel = z
            srv.setZoom(z)
        }

        params["lantern"]?.let {
            val enable = it as Boolean
            lanternEnabled = enable
            if (enable) srv.enableLantern() else srv.disableLantern()
        }

        params["ois"]?.let {
            val enable = it as Boolean
            oisEnabled = enable
            if (enable) srv.enableOpticalVideoStabilization()
        }

        // Bitrate on-the-fly — não reinicia stream, ok na thread HTTP
        params["bitrate"]?.let {
            val br = (it as Double).toInt()
            currentBitrate = br
            srv.setVideoBitrateOnFly(br * 1024)
            Log.d(TAG, "Bitrate onFly -> ${br}kbps")
        }

        // ── Operações PESADAS (worker thread, HTTP responde imediatamente) ──

        params["camera"]?.let { value ->
            val id = value as String
            currentCameraId = id
            post {
                srv.switchCamera(id)
                Log.d(TAG, "Camera -> $id")
            }
        }

        params["resolution"]?.let { value ->
            val res = value as String
            val (w, h, br) = when (res) {
                "4k"    -> Triple(3840, 2160, 20000)
                "1080p" -> Triple(1920, 1080, 8000)
                else    -> Triple(1280, 720,  4000)
            }
            currentWidth   = w
            currentHeight  = h
            currentBitrate = br

            post {
                val wasStreaming = srv.isStreaming
                if (wasStreaming) srv.stopStream()

                // stopStream() para AMBOS os encoders (vídeo + áudio).
                // Por isso é obrigatório chamar prepareAudio() novamente junto
                // com prepareVideo() — sem isso o AudioEncoder fica sem prepare
                // e startStream() lança: IllegalStateException: AudioEncoder not prepared yet
                val videoOk = srv.prepareVideo(w, h, currentFps, br * 1024, 0)
                val audioOk = srv.prepareAudio(128 * 1024, 44100, true)

                if (wasStreaming && videoOk && audioOk) {
                    srv.startStream()
                    Log.d(TAG, "Resolução aplicada -> ${w}x${h} @${br}kbps")
                } else {
                    Log.e(TAG, "Falha ao reaplicar stream: videoOk=$videoOk audioOk=$audioOk")
                }
            }
        }
    }

    fun release() {
        workerThread.quitSafely()
    }
}
