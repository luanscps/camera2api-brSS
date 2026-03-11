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

    // Estado atual (exposto para o /status)
    var currentCameraId   = "0"
    var exposureLevel     = 0       // EV compensation
    var exposureNs        = 85000L  // nanoseconds (lido do sensor)
    var frameDurationNs   = 16665880L
    var isoValue          = 50
    var manualSensor      = false
    var whiteBalanceMode  = "auto"
    var autoFocus         = true
    var focusDistance     = 0f      // diopters; 0 = auto/infinito
    var zoomLevel         = 0f      // 0..1
    var lanternEnabled    = false
    var oisEnabled        = false
    var currentWidth      = 1920
    var currentHeight     = 1080
    var currentBitrate    = 4000
    var currentFps        = 30

    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(TAG, "Erro na worker", it) } }

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(TAG, "server nao inicializado"); return }

        // ── EV (exposição direta) ─────────────────────────────────────────────
        params["exposure"]?.let {
            val ev = (it as Double).toInt().coerceIn(srv.minExposure, srv.maxExposure)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(TAG, "Exposure EV=$ev (range ${srv.minExposure}..${srv.maxExposure})")
        }

        // ── ISO ───────────────────────────────────────────────────────────────
        // O painel envia o valor real (ex: 400). Mapeamos para EV como fallback
        // pois RootEncoder não expõe ISO direto (precisa manual_sensor).
        params["iso"]?.let {
            val iso = (it as Double).toInt()
            isoValue = iso
            // Tenta setar via setExposure se manualSensor estiver off
            if (!manualSensor) {
                val minEv = srv.minExposure
                val maxEv = srv.maxExposure
                val ev = (((iso - 50f) / (3200f - 50f)) * (maxEv - minEv) + minEv)
                    .toInt().coerceIn(minEv, maxEv)
                exposureLevel = ev
                srv.setExposure(ev)
                Log.d(TAG, "ISO $iso (modo auto-EV) → EV $ev")
            } else {
                // Com manual_sensor: usa cameraManager para setar ISO direto
                val cm = srv.cameraManager
                cm.setISO(iso)
                Log.d(TAG, "ISO $iso (modo manual_sensor)")
            }
        }

        // ── Manual sensor on/off ──────────────────────────────────────────────
        params["manualSensor"]?.let {
            manualSensor = it as Boolean
            if (!manualSensor) {
                srv.setAutoExposure()
                Log.d(TAG, "manualSensor OFF -> auto exposure")
            } else {
                Log.d(TAG, "manualSensor ON")
            }
        }

        // ── Foco manual (0..1 normalizado, 0=auto) ────────────────────────────
        // O painel envia 0..1 (normalizado de 0..10 diopters)
        params["focus"]?.let {
            val norm = (it as Double).toFloat().coerceIn(0f, 1f)
            if (norm == 0f) {
                autoFocus     = true
                focusDistance = 0f
                srv.enableAutoFocus()
                Log.d(TAG, "Foco -> AUTO")
            } else {
                autoFocus = false
                val cm = srv.cameraManager
                val maxDist = cm.maxFocus   // diopters máximos do sensor
                val dist    = norm * maxDist
                focusDistance = dist
                srv.disableAutoFocus()
                cm.setFocusDistance(dist)
                Log.d(TAG, "Foco MANUAL dist=${dist}D (norm=$norm, max=${maxDist}D)")
            }
        }

        // ── Modo de foco (continuous, off, auto) ──────────────────────────────
        params["focusmode"]?.let {
            when (it as String) {
                "continuous-video", "continuous-picture" -> {
                    autoFocus = true
                    focusDistance = 0f
                    srv.enableAutoFocus()
                }
                "off" -> {
                    srv.disableAutoFocus()
                }
                "auto" -> {
                    srv.enableAutoFocus()
                }
            }
            Log.d(TAG, "focusmode -> $it")
        }

        // ── Balanço de branco ─────────────────────────────────────────────────
        params["whiteBalance"]?.let {
            whiteBalanceMode = it as String
            val mode = when (whiteBalanceMode) {
                "daylight"    -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                "cloudy"      -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                "tungsten",
                "incandescent"-> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                "fluorescent" -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                else          -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            }
            srv.enableAutoWhiteBalance(mode)
            Log.d(TAG, "WB -> $whiteBalanceMode (mode=$mode)")
        }

        // ── Zoom ──────────────────────────────────────────────────────────────
        params["zoom"]?.let {
            val z = (it as Double).toFloat().coerceIn(0f, 1f)
            zoomLevel = z
            srv.setZoom(z)
            Log.d(TAG, "Zoom -> $z")
        }

        // ── Lanterna ──────────────────────────────────────────────────────────
        params["lantern"]?.let {
            val enable = it as Boolean
            lanternEnabled = enable
            if (enable) srv.enableLantern() else srv.disableLantern()
            Log.d(TAG, "Lanterna -> $enable")
        }

        // ── OIS ───────────────────────────────────────────────────────────────
        params["ois"]?.let {
            val enable = it as Boolean
            oisEnabled = enable
            if (enable) srv.enableOpticalVideoStabilization()
            else        srv.disableOpticalVideoStabilization()
            Log.d(TAG, "OIS -> $enable")
        }

        // ── Bitrate on-the-fly ────────────────────────────────────────────────
        params["bitrate"]?.let {
            val br = (it as Double).toInt()
            currentBitrate = br
            srv.setVideoBitrateOnFly(br * 1024)
            Log.d(TAG, "Bitrate -> ${br}kbps")
        }

        // ── Troca de câmera ───────────────────────────────────────────────────
        params["camera"]?.let { value ->
            val id = value as String
            currentCameraId = id
            post {
                srv.switchCamera(id)
                if (!autoFocus && focusDistance > 0f) {
                    srv.cameraManager.setFocusDistance(focusDistance)
                }
                Log.d(TAG, "Camera -> $id")
            }
        }

        // ── Resolução ─────────────────────────────────────────────────────────
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
                Log.d(TAG, "Resolução -> ${w}x${h} @${br}kbps videoOk=$videoOk")
            }
        }
    }

    fun release() {
        workerThread.quitSafely()
    }
}
