package com.camera2rtsp

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

class Camera2Controller {

    private val TAG = "Camera2Controller"

    var server: RtspServerCamera2? = null

    var currentCameraId  = "0"
    var exposureLevel    = 0
    var whiteBalanceMode = "auto"
    var autoFocus        = true
    var focusDistance    = 0f       // 0 = auto, >0 = manual (diopters)
    var zoomLevel        = 0f
    var lanternEnabled   = false
    var oisEnabled       = false
    var currentWidth     = 1920
    var currentHeight    = 1080
    var currentBitrate   = 4000
    var currentFps       = 30

    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(TAG, "Erro na worker", it) } }

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(TAG, "server nao inicializado"); return }

        // ── ISO → EV compensation ──────────────────────────────────────────
        params["iso"]?.let {
            val iso = (it as Double).toInt()
            val minEv = srv.minExposure
            val maxEv = srv.maxExposure
            val ev = (((iso - 50f) / (3200f - 50f)) * (maxEv - minEv) + minEv)
                .toInt().coerceIn(minEv, maxEv)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(TAG, "ISO $iso → EV $ev  (range $minEv..$maxEv)")
        }

        // ── Exposição direta em EV (-n..+n) ───────────────────────────────
        // O painel envia "exposure" como valor EV direto (inteiro, ex: -3..+3)
        params["exposure"]?.let {
            val ev = (it as Double).toInt().coerceIn(srv.minExposure, srv.maxExposure)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(TAG, "Exposure EV $ev  (range ${srv.minExposure}..${srv.maxExposure})")
        }

        // ── Foco ──────────────────────────────────────────────────────────
        // O painel envia "focus" como Float:
        //   0.0          → autofoco
        //   0.1 .. 1.0  → foco manual mapeado para 0..maxFocusDistance diopters
        //                  (1/distância em metros; infinito = 0 diopters)
        params["focus"]?.let {
            val value = (it as Double).toFloat().coerceIn(0f, 1f)
            if (value == 0f) {
                // Volta ao autofoco
                autoFocus     = true
                focusDistance = 0f
                srv.enableAutoFocus()
                Log.d(TAG, "Foco → AUTO")
            } else {
                // Foco manual: desativa AF e define distância via CaptureRequest
                autoFocus = false
                val camera2Manager = srv.cameraManager  // Camera2ApiManager exposto pelo RootEncoder
                val maxDist = camera2Manager.maxFocus   // diopters máximos do sensor
                val dist    = value * maxDist           // 0 = infinito, maxDist = macro
                focusDistance = dist
                srv.disableAutoFocus()
                camera2Manager.setFocusDistance(dist)
                Log.d(TAG, "Foco → MANUAL dist=${dist}D  (max=${maxDist}D, slider=$value)")
            }
        }

        // ── Balanço de branco ─────────────────────────────────────────────
        params["whiteBalance"]?.let {
            whiteBalanceMode = it as String
            val mode = when (whiteBalanceMode) {
                "daylight" -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                "cloudy"   -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                "tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                "fluorescent" -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                else       -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            }
            srv.enableAutoWhiteBalance(mode)
            Log.d(TAG, "WB → $whiteBalanceMode (mode=$mode)")
        }

        // ── Zoom ──────────────────────────────────────────────────────────
        params["zoom"]?.let {
            val z = (it as Double).toFloat().coerceIn(0f, 1f)
            zoomLevel = z
            srv.setZoom(z)
            Log.d(TAG, "Zoom → $z")
        }

        // ── Lanterna ──────────────────────────────────────────────────────
        params["lantern"]?.let {
            val enable = it as Boolean
            lanternEnabled = enable
            if (enable) srv.enableLantern() else srv.disableLantern()
            Log.d(TAG, "Lanterna → $enable")
        }

        // ── OIS ───────────────────────────────────────────────────────────
        params["ois"]?.let {
            val enable = it as Boolean
            oisEnabled = enable
            if (enable) srv.enableOpticalVideoStabilization()
            else        srv.disableOpticalVideoStabilization()
            Log.d(TAG, "OIS → $enable")
        }

        // ── Bitrate on-the-fly ────────────────────────────────────────────
        params["bitrate"]?.let {
            val br = (it as Double).toInt()
            currentBitrate = br
            srv.setVideoBitrateOnFly(br * 1024)
            Log.d(TAG, "Bitrate onFly → ${br}kbps")
        }

        // ── Troca de câmera (PESADA → worker) ─────────────────────────────
        params["camera"]?.let { value ->
            val id = value as String
            currentCameraId = id
            post {
                srv.switchCamera(id)
                // Reaplicar foco manual após troca de câmera
                if (!autoFocus && focusDistance > 0f) {
                    srv.cameraManager.setFocusDistance(focusDistance)
                }
                Log.d(TAG, "Camera → $id")
            }
        }

        // ── Resolução (PESADA → worker) ────────────────────────────────────
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
                val videoOk = srv.prepareVideo(w, h, currentFps, br * 1024, 0)
                val audioOk = srv.prepareAudio(128 * 1024, 44100, true)
                if (wasStreaming && videoOk && audioOk) srv.startStream()
                Log.d(TAG, "Resolução → ${w}x${h} @${br}kbps  video=$videoOk audio=$audioOk")
            }
        }
    }

    fun release() {
        workerThread.quitSafely()
    }
}
