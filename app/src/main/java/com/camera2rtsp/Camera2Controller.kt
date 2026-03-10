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

    // Thread dedicada para operações pesadas (stopStream/prepareVideo/startStream/switchCamera)
    // Mantém a thread HTTP do NanoHTTPD livre — servidor nunca para de responder
    // HandlerThread serializa os comandos: um de cada vez, sem race condition
    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    // Posta tarefa na worker thread e retorna imediatamente para a thread HTTP
    private fun post(block: () -> Unit) = worker.post { runCatching(block).onFailure { Log.e(TAG, "Erro na worker", it) } }

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(TAG, "server nao inicializado"); return }

        // ── Operações LEVES — só setam parâmetro no camera session, ok na thread HTTP ──

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
            val ev = (minEv + ratio * (maxEv - minEv)).toInt()
            exposureLevel = ev
            srv.setExposure(ev)
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

        // Bitrate on-the-fly — também leve, sem restart
        params["bitrate"]?.let {
            val br = (it as Double).toInt()
            currentBitrate = br
            srv.setVideoBitrateOnFly(br * 1024)
            Log.d(TAG, "Bitrate onFly -> ${br}kbps")
        }

        // ── Operações PESADAS — postadas na worker thread, retornam imediatamente ──
        // A thread HTTP responde {"status":"ok"} na hora; a aplicação acontece em background

        params["camera"]?.let { value ->
            val id = value as String
            currentCameraId = id
            // switchCamera pode levar 200-800ms — worker thread
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
            // stopStream + prepareVideo + startStream — bloqueia por ~500ms — worker thread
            post {
                val wasStreaming = srv.isStreaming
                if (wasStreaming) srv.stopStream()
                val ok = srv.prepareVideo(w, h, currentFps, br * 1024, 0)
                if (wasStreaming && ok) srv.startStream()
                Log.d(TAG, "Resolução -> ${w}x${h} @${br}kbps ok=$ok")
            }
        }
    }

    // Libera a worker thread quando a Activity for destruída
    fun release() {
        workerThread.quitSafely()
    }
}
