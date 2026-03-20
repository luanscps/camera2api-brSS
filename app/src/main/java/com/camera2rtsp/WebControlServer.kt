package com.camera2rtsp

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD

class WebControlServer(
    port: Int,
    private val cameraController: Camera2Controller,
    private val context: Context
) : NanoHTTPD(port) {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            // CORS preflight
            session.method == Method.OPTIONS -> {
                val resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
                resp.addHeader("Access-Control-Allow-Origin", "*")
                resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                resp.addHeader("Access-Control-Allow-Headers", "Content-Type")
                resp
            }
            uri == "/"                  -> serveControlPanel()
            uri == "/status"            -> serveStatus()
            uri == "/api/status"        -> serveStatus()
            uri == "/api/capabilities"  -> serveCapabilities()
            uri == "/api/preview"       -> servePreview()
            uri == "/api/control" && session.method == Method.POST -> handleControl(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    // ── Painel de controle (UI v5) ──────────────────────────────────
    private fun serveControlPanel(): Response {
        val html = WebControlHtml.build()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    // ── Preview MJPEG (JPEG estático, atualizado pelo streamer a ~1fps) ─────
    private fun servePreview(): Response {
        val jpegBytes = RtmpStreamer.lastFrameJpeg
        return if (jpegBytes != null && jpegBytes.isNotEmpty()) {
            val resp = newFixedLengthResponse(
                Response.Status.OK, "image/jpeg",
                java.io.ByteArrayInputStream(jpegBytes), jpegBytes.size.toLong()
            )
            resp.addHeader("Cache-Control", "no-store")
            resp.addHeader("Access-Control-Allow-Origin", "*")
            resp
        } else {
            // Câmera não iniciada ou frame ainda não capturado
            newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        }
    }

    // ── Capabilities ─────────────────────────────────────────────────
    private fun serveCapabilities(): Response {
        val capabilities = cameraController.discoverAllCameras(context)
        val json = gson.toJson(capabilities)
        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    // ── Helpers de modo ───────────────────────────────────────────────
    private fun edgeModeStr(v: Int) = when (v) {
        android.hardware.camera2.CameraMetadata.EDGE_MODE_OFF          -> "off"
        android.hardware.camera2.CameraMetadata.EDGE_MODE_FAST         -> "fast"
        android.hardware.camera2.CameraMetadata.EDGE_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }

    private fun nrModeStr(v: Int) = when (v) {
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF          -> "off"
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL      -> "minimal"
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_FAST         -> "fast"
        android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }

    private fun hotPixelModeStr(v: Int) = when (v) {
        android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_OFF          -> "off"
        android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_FAST         -> "fast"
        android.hardware.camera2.CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }

    // ── Status JSON ───────────────────────────────────────────────────
    private fun serveStatus(): Response {
        val c = cameraController
        val streaming = c.rtmpCamera?.isStreaming == true
        val focusMode = if (c.autoFocus) "continuous-video" else "off"
        val focusDist = String.format(java.util.Locale.US, "%.2f", c.focusDistance)
        val numClients = if (streaming) 1 else 0

        val curvals = mapOf(
            "video_size"           to "${c.currentWidth}x${c.currentHeight}",
            "ffc"                  to if (c.currentCameraId == "1") "on" else "off",
            "camera_id"            to c.currentCameraId,
            "zoom"                 to "${(c.zoomLevel * 100).toInt() + 100}",
            "focusmode"            to focusMode,
            "focus_distance"       to focusDist,
            "focal_length"         to "4.30",
            "aperture"             to "1.5 (fixo)",
            "whitebalance"         to c.whiteBalanceMode,
            "torch"                to if (c.lanternEnabled) "on" else "off",
            "iso"                  to c.isoValue.toString(),
            "exposure_ns"          to c.exposureNs.toString(),
            "frame_duration"       to c.frameDurationNs.toString(),
            "manual_sensor"        to if (c.manualSensor) "on" else "off",
            "bitrate_kbps"         to c.currentBitrate.toString(),
            "fps"                  to c.currentFps.toString(),
            "ois"                  to if (c.oisEnabled) "on" else "off",
            "eis"                  to if (c.eisEnabled) "on" else "off",
            "ae_lock"              to if (c.aeLocked) "on" else "off",
            "awb_lock"             to if (c.awbLocked) "on" else "off",
            "flash_mode"           to c.flashMode,
            "edge_mode"            to edgeModeStr(c.edgeMode),
            "noise_reduction_mode" to nrModeStr(c.noiseReductionMode),
            "tonemap_mode"         to "high_quality",
            "hot_pixel_mode"       to hotPixelModeStr(c.hotPixelMode),
            // ✅ FIX: expõe rotação atual para o poll da WebGUI sincronizar o OSD
            "rotation"             to c.previewRotation.toString()
        )

        val avail = mapOf(
            "focusmode"      to listOf("off", "auto", "continuous-video", "continuous-picture"),
            "whitebalance"   to listOf("auto", "incandescent", "fluorescent", "daylight", "cloudy"),
            "video_size"     to listOf("3840x2160", "1920x1080", "1280x720", "960x540"),
            "torch"          to listOf("on", "off"),
            "manual_sensor"  to listOf("on", "off"),
            "focus_distance" to (0..100).map { String.format(java.util.Locale.US, "%.2f", it * 0.1) },
            "iso"            to listOf("50", "100", "200", "400", "800", "1600", "3200"),
            "zoom"           to (100..800 step 7).map { it.toString() },
            "camera_id"      to listOf("0", "1", "2", "3"),
            "flash_mode"     to listOf("off", "torch", "single"),
            "edge_mode"      to listOf("off", "fast", "high_quality"),
            "noise_reduction_mode" to listOf("off", "minimal", "fast", "high_quality"),
            "tonemap_mode"   to listOf("high_quality"),
            "hot_pixel_mode" to listOf("off", "fast", "high_quality")
        )

        val status = mapOf(
            "video_connections" to numClients,
            "audio_connections" to 0,
            "streaming"         to streaming,
            "rtmp_url"          to (StreamingService.instance?.rtmpUrl ?: ""),
            "curvals"           to curvals,
            "avail"             to avail
        )

        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(status))
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    // ── Controle via POST /api/control ────────────────────────────────
    private fun handleControl(session: IHTTPSession): Response {
        val map = mutableMapOf<String, String>()
        return try {
            session.parseBody(map)
            val json = map["postData"] ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "text/plain", "No data"
            )
            val params = com.google.gson.Gson().fromJson<Map<String, Any>>(
                json, object : TypeToken<Map<String, Any>>() {}.type
            )

            // Troca de URL RTMP em tempo real
            (params["rtmpUrl"] as? String)?.let { newUrl ->
                StreamingService.instance?.let { svc ->
                    svc.rtmpUrl = newUrl
                    svc.stopStream()
                    svc.startStream()
                }
            }

            // Controle de stream (botões Iniciar / Parar da UI v5)
            (params["streamAction"] as? String)?.let { action ->
                when (action) {
                    "start" -> StreamingService.instance?.startStream()
                    "stop"  -> StreamingService.instance?.stopStream()
                }
            }

            cameraController.updateSettings(params)
            val resp = newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
            resp.addHeader("Access-Control-Allow-Origin", "*")
            resp
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "application/json",
                """{"status":"error","message":"${e.message}"}"""
            )
        }
    }
}
