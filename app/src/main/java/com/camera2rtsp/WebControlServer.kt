package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraMetadata
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD

class WebControlServer(
    port: Int,
    private val ctrl: Camera2Controller,
    private val context: Context
) : NanoHTTPD(port) {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/"                 -> serveControlPanel()
            uri == "/status"           -> serveStatus()
            uri == "/api/status"       -> serveStatus()
            uri == "/api/capabilities" -> serveCapabilities()
            uri == "/api/control" && session.method == Method.POST -> handleControl(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun serveCapabilities(): Response {
        val caps = ctrl.discoverAllCameras(context)
        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(caps))
        resp.addHeader("Access-Control-Allow-Origin", "*")
        return resp
    }

    private fun serveStatus(): Response {
        val c         = ctrl
        val streaming = c.rtmpCamera?.isStreaming == true
        val activeCaps = CameraCapabilitiesReader.read(context, c.currentCameraId)

        val curvals = mapOf(
            "video_size"           to "${c.currentWidth}x${c.currentHeight}",
            "ffc"                  to if (c.currentCameraId == "1") "on" else "off",
            "camera_id"            to c.currentCameraId,
            "zoom"                 to String.format(java.util.Locale.US, "%.2f", c.zoomLevel),
            "focusmode"            to if (c.autoFocus) "continuous-video" else "manual",
            "focus_distance"       to String.format(java.util.Locale.US, "%.2f", c.focusDistance),
            "focal_length"         to (activeCaps?.focalLengths?.firstOrNull()?.toString() ?: "?"),
            "aperture"             to (activeCaps?.apertures?.firstOrNull()?.let {
                String.format(java.util.Locale.US, "f/%.1f", it) } ?: "?"),
            "whitebalance"         to c.whiteBalanceMode,
            "torch"                to if (c.lanternEnabled) "on" else "off",
            "iso"                  to if (c.isoValue <= 0) "AUTO" else c.isoValue.toString(),
            "shutter_speed"        to if (c.exposureNs <= 0L) "AUTO" else shutterNsToFraction(c.exposureNs),
            "bitrate_kbps"         to c.currentBitrate.toString(),
            "fps"                  to c.currentFps.toString(),
            "ois"                  to if (c.oisEnabled) "on" else "off",
            "eis"                  to if (c.eisEnabled) "on" else "off",
            "has_ois"              to (activeCaps?.hasOis == true),
            "has_flash"            to (activeCaps?.hasFlash == true),
            "hw_level"             to (activeCaps?.hardwareLevel ?: "UNKNOWN"),
            "manual_sensor"        to (activeCaps?.supportsManualSensor == true),
            "ae_lock"              to if (c.aeLocked) "on" else "off",
            "awb_lock"             to if (c.awbLocked) "on" else "off",
            "flash_mode"           to c.flashMode,
            "edge_mode"            to edgeModeStr(c.edgeMode),
            "noise_reduction_mode" to nrModeStr(c.noiseReductionMode),
            "hot_pixel_mode"       to hotPixelModeStr(c.hotPixelMode)
        )

        val zoomMax  = activeCaps?.zoomRange?.getOrNull(1) ?: 8f
        val isoMin   = activeCaps?.isoRange?.getOrNull(0) ?: 50
        val isoMax   = activeCaps?.isoRange?.getOrNull(1) ?: 3200
        val awbModes = activeCaps?.supportedAwbModes ?: listOf("auto","daylight","cloudy","incandescent","fluorescent")
        val afModes  = activeCaps?.supportedAfModes  ?: listOf("off","auto","continuous-video","continuous-picture")

        val avail = mapOf(
            "focusmode"            to afModes,
            "whitebalance"         to awbModes,
            "video_size"           to (activeCaps?.availableResolutions ?: listOf("1920x1080","1280x720","640x480")),
            "torch"                to listOf("on", "off"),
            "iso"                  to buildIsoSteps(isoMin, isoMax),
            "zoom"                 to buildZoomSteps(zoomMax),
            "shutter_speed"        to listOf("AUTO","1/8000","1/4000","1/2000","1/1000","1/500","1/250","1/125","1/60","1/30","1/15","1/8","1/4","1/2","1"),
            "camera_id"            to listOf("0", "1", "2", "3"),
            "flash_mode"           to listOf("off", "torch", "single"),
            "edge_mode"            to listOf("off", "fast", "high_quality"),
            "noise_reduction_mode" to listOf("off", "minimal", "fast", "high_quality"),
            "hot_pixel_mode"       to listOf("off", "fast", "high_quality")
        )

        val status = mapOf(
            "video_connections" to if (streaming) 1 else 0,
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
            (params["rtmpUrl"] as? String)?.let {
                StreamingService.instance?.let { svc ->
                    svc.rtmpUrl = it
                    svc.stopStream()
                    svc.startStream()
                }
            }
            ctrl.updateSettings(params)
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

    private fun serveControlPanel() =
        newFixedLengthResponse(Response.Status.OK, "text/html", WebControlHtml.build())

    private fun shutterNsToFraction(ns: Long): String {
        if (ns <= 0L) return "AUTO"
        val sec = ns / 1_000_000_000.0
        if (sec >= 1.0) return String.format(java.util.Locale.US, "%.1fs", sec)
        val den = (1.0 / sec).toInt()
        return "1/$den"
    }

    private fun buildIsoSteps(min: Int, max: Int): List<String> {
        val steps = mutableListOf("AUTO")
        var v = 50
        while (v <= max) {
            if (v >= min) steps.add(v.toString())
            v *= 2
        }
        return steps
    }

    private fun buildZoomSteps(maxZoom: Float): List<String> {
        val steps = mutableListOf("1x")
        var v = 1.5f
        while (v <= maxZoom + 0.1f) {
            steps.add(if (v == v.toLong().toFloat()) "${v.toInt()}x"
                      else "${String.format(java.util.Locale.US, "%.1f", v)}x")
            v += if (v < 4f) 0.5f else 1f
        }
        return steps
    }

    private fun edgeModeStr(v: Int) = when (v) {
        CameraMetadata.EDGE_MODE_OFF          -> "off"
        CameraMetadata.EDGE_MODE_FAST         -> "fast"
        CameraMetadata.EDGE_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }
    private fun nrModeStr(v: Int) = when (v) {
        CameraMetadata.NOISE_REDUCTION_MODE_OFF          -> "off"
        CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL      -> "minimal"
        CameraMetadata.NOISE_REDUCTION_MODE_FAST         -> "fast"
        CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }
    private fun hotPixelModeStr(v: Int) = when (v) {
        CameraMetadata.HOT_PIXEL_MODE_OFF          -> "off"
        CameraMetadata.HOT_PIXEL_MODE_FAST         -> "fast"
        CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY -> "high_quality"
        else -> "high_quality"
    }
}
