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

    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(tag, "Erro na worker", it) } }

    /** Converte qualquer Number (Int, Long, Double, Float) para Double com segurança */
    private fun Any.toSafeDouble(): Double? = (this as? Number)?.toDouble()

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(tag, "server nao inicializado"); return }

        // ── EV ───────────────────────────────────────────────────────────────
        params["exposure"]?.let {
            val ev = it.toSafeDouble()?.toInt()?.coerceIn(srv.minExposure, srv.maxExposure) ?: return@let
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(tag, "Exposure EV=$ev")
        }

        // ── ISO ── (Camera2Base não expõe setISO direto; mapeamos para EV)
        params["iso"]?.let {
            val iso = it.toSafeDouble()?.toInt() ?: return@let
            isoValue = iso
            // Mapeia ISO linearmente para o range de EV disponível
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
            val norm = it.toSafeDouble()?.toFloat()?.coerceIn(0f, 1f) ?: return@let
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
            val z = it.toSafeDouble()?.toFloat()?.coerceIn(0f, 1f) ?: return@let
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
            val br = it.toSafeDouble()?.toInt() ?: return@let
            currentBitrate = br
            srv.setVideoBitrateOnFly(br * 1024)
            Log.d(tag, "Bitrate -> ${br}kbps")
        }

        // ── FPS on-the-fly (requer restart do encoder) ────────────────────────
        params["fps"]?.let { value ->
            val fps = value.toSafeDouble()?.toInt()?.coerceIn(15, 30) ?: return@let
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
                else    -> Triple(1280, 720,  4000)  // 720p default
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
    }

    fun release() {
        workerThread.quitSafely()
    }
}
