package com.camera2rtsp

import android.hardware.camera2.CameraMetadata
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

class Camera2Controller {

    private val tag = "Camera2Controller"

    var server: RtspServerCamera2? = null

    // Estado atual (exposto para o /status)
    var currentCameraId   = "0"
    var exposureLevel     = 0
    var exposureNs        = 85000L
    var frameDurationNs   = 16665880L
    var isoValue          = 50
    var manualSensor      = false
    var whiteBalanceMode  = "auto"
    var autoFocus         = true
    var focusDistance     = 0f
    var zoomLevel         = 0f
    var lanternEnabled    = false
    var oisEnabled        = false
    var currentWidth      = 1920
    var currentHeight     = 1080
    var currentBitrate    = 4000
    var currentFps        = 30

    // ── Onda 3: Post-Processing state ────────────────────────────────────────
    var edgeMode          = -1   // -1 = não definido (usa padrão do driver)
    var noiseReductionMode = -1
    var tonemapMode       = -1
    var tonemapGamma      = 2.2f
    var hotPixelMode      = -1

    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(tag, "Erro na worker", it) } }

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(tag, "server nao inicializado"); return }

        // ── EV ───────────────────────────────────────────────────────────────
        params["exposure"]?.let {
            val ev = (it as Double).toInt().coerceIn(srv.minExposure, srv.maxExposure)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(tag, "Exposure EV=$ev")
        }

        // ── ISO ── (Camera2Base não expõe setISO direto; mapeamos para EV)
        params["iso"]?.let {
            val iso = (it as Double).toInt()
            isoValue = iso
            val minEv = srv.minExposure
            val maxEv = srv.maxExposure
            val ev = (((iso - 50f) / (3200f - 50f)) * (maxEv - minEv) + minEv)
                .toInt().coerceIn(minEv, maxEv)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(tag, "ISO $iso → EV $ev")
        }

        // ── Manual sensor toggle ──────────────────────────────────────────────
        params["manualSensor"]?.let {
            manualSensor = it as Boolean
            if (!manualSensor) {
                srv.setExposure(0)
                exposureLevel = 0
                Log.d(tag, "manualSensor OFF → EV reset")
            } else {
                Log.d(tag, "manualSensor ON")
            }
        }

        // ── Foco manual ───────────────────────────────────────────────────────
        params["focus"]?.let {
            val norm = (it as Double).toFloat().coerceIn(0f, 1f)
            if (norm == 0f) {
                autoFocus     = true
                focusDistance = 0f
                srv.enableAutoFocus()
                Log.d(tag, "Foco -> AUTO")
            } else {
                autoFocus = false
                val dist = norm * 10f
                focusDistance = dist
                srv.disableAutoFocus()
                srv.setFocusDistance(dist)
                Log.d(tag, "Foco MANUAL dist=${dist}D")
            }
        }

        // ── Modo de foco ──────────────────────────────────────────────────────
        params["focusmode"]?.let {
            when (it as String) {
                "continuous-video", "continuous-picture", "auto" -> {
                    autoFocus     = true
                    focusDistance = 0f
                    srv.enableAutoFocus()
                }
                "off" -> srv.disableAutoFocus()
            }
            Log.d(tag, "focusMode -> $it")
        }

        // ── Balanço de branco ─────────────────────────────────────────────────
        params["whiteBalance"]?.let {
            whiteBalanceMode = it as String
            val mode = when (whiteBalanceMode) {
                "daylight"                   -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                "cloudy"                     -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                "tungsten", "incandescent"   -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                "fluorescent"                -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                else                         -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            }
            srv.enableAutoWhiteBalance(mode)
            Log.d(tag, "WB -> $whiteBalanceMode")
        }

        // ── Zoom ──────────────────────────────────────────────────────────────
        params["zoom"]?.let {
            val z = (it as Double).toFloat().coerceIn(0f, 1f)
            zoomLevel = z
            val zoomRange = srv.zoomRange
            val minZ = zoomRange?.lower ?: 1f
            val maxZ = zoomRange?.upper ?: 8f
            val zoomValue = minZ + z * (maxZ - minZ)
            srv.setZoom(zoomValue)
            Log.d(tag, "Zoom -> $zoomValue (norm=$z)")
        }

        // ── Lanterna ──────────────────────────────────────────────────────────
        params["lantern"]?.let {
            val enable = it as Boolean
            lanternEnabled = enable
            if (enable) srv.enableLantern() else srv.disableLantern()
            Log.d(tag, "Lanterna -> $enable")
        }

        // ── OIS ───────────────────────────────────────────────────────────────
        params["ois"]?.let {
            val enable = it as Boolean
            oisEnabled = enable
            if (enable) srv.enableOpticalVideoStabilization()
            else        srv.disableOpticalVideoStabilization()
            Log.d(tag, "OIS -> $enable")
        }

        // ── Bitrate on-the-fly ────────────────────────────────────────────────
        params["bitrate"]?.let {
            val br = (it as Double).toInt()
            currentBitrate = br
            srv.setVideoBitrateOnFly(br * 1024)
            Log.d(tag, "Bitrate -> ${br}kbps")
        }

        // ── FPS on-the-fly (requer restart do encoder) ────────────────────────
        params["fps"]?.let { value ->
            val fps = (value as Double).toInt().coerceIn(15, 30)
            currentFps = fps
            post {
                val wasStreaming = srv.isStreaming
                if (wasStreaming) srv.stopStream()
                val videoOk = srv.prepareVideo(currentWidth, currentHeight, fps, currentBitrate * 1024, 0)
                val audioOk = srv.prepareAudio(128 * 1024, 44100, true)
                if (wasStreaming && videoOk && audioOk) srv.startStream()
                Log.d(tag, "FPS -> $fps videoOk=$videoOk")
            }
        }

        // ── Troca de câmera ───────────────────────────────────────────────────
        params["camera"]?.let { value ->
            val id = value as String
            currentCameraId = id
            post {
                srv.switchCamera(id)
                if (!autoFocus && focusDistance > 0f) {
                    srv.setFocusDistance(focusDistance)
                }
                Log.d(tag, "Camera -> $id")
            }
        }

        // ── Resolução (requer restart do encoder) ─────────────────────────────
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
                Log.d(tag, "Resolução -> ${w}x${h} @${br}kbps videoOk=$videoOk")
            }
        }

        // ── Onda 3: Edge Mode ─────────────────────────────────────────────────
        // 0=OFF  1=FAST  2=HIGH_QUALITY
        params["edgeMode"]?.let {
            val mode = (it as Double).toInt().coerceIn(0, 2)
            edgeMode = mode
            srv.setEdgeMode(mode)
            Log.d(tag, "edgeMode -> $mode")
        }

        // ── Onda 3: Noise Reduction ───────────────────────────────────────────
        // 0=OFF  1=FAST  2=HIGH_QUALITY  3=MINIMAL
        params["noiseReduction"]?.let {
            val mode = (it as Double).toInt().coerceIn(0, 3)
            noiseReductionMode = mode
            srv.setNoiseReductionMode(mode)
            Log.d(tag, "noiseReduction -> $mode")
        }

        // ── Onda 3: Tonemap ───────────────────────────────────────────────────
        // 1=CONTRAST_CURVE  2=FAST  3=HIGH_QUALITY  4=GAMMA_VALUE
        params["tonemapMode"]?.let {
            val mode = (it as Double).toInt().coerceIn(1, 4)
            tonemapMode = mode
            val gamma = (params["tonemapGamma"] as? Double)?.toFloat() ?: tonemapGamma
            tonemapGamma = gamma
            if (mode == 4) {
                srv.setTonemapMode(mode, gamma)
            } else {
                srv.setTonemapMode(mode)
            }
            Log.d(tag, "tonemapMode -> $mode gamma=$gamma")
        }

        // ── Onda 3: Hot Pixel ─────────────────────────────────────────────────
        // 0=OFF  1=FAST  2=HIGH_QUALITY
        params["hotPixel"]?.let {
            val mode = (it as Double).toInt().coerceIn(0, 2)
            hotPixelMode = mode
            srv.setHotPixelMode(mode)
            Log.d(tag, "hotPixel -> $mode")
        }
    }

    fun release() {
        workerThread.quitSafely()
    }
}
