package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Log
import com.pedro.library.rtmp.RtmpCamera2

class Camera2Controller {

    val tag = "Camera2Controller"

    // Referencia ao encoder ativo (usada pelo WebControlServer para status)
    var rtmpCamera: RtmpCamera2? = null

    // -- Configuracoes de stream --
    var currentWidth    = 1920
    var currentHeight   = 1080
    var currentFps      = 30
    var currentBitrate  = 4000   // kbps
    var currentCameraId = "0"

    // -- Controles de lente / sensor --
    var zoomLevel        = 1f    // nivel real (>= 1.0)
    var autoFocus        = true
    var focusDistance    = 0f
    var whiteBalanceMode = "auto"
    var lanternEnabled   = false
    var isoValue         = 100
    var exposureNs       = 0L
    var frameDurationNs  = 0L
    var manualSensor     = false
    var oisEnabled       = false
    var eisEnabled       = false
    var aeLocked         = false
    var awbLocked        = false
    var flashMode        = "off"
    var edgeMode         = CameraMetadata.EDGE_MODE_HIGH_QUALITY
    var noiseReductionMode = CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
    var hotPixelMode     = CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY

    fun updateSettings(params: Map<String, Any>) {
        applyLocally(params)
        val cam = rtmpCamera ?: run {
            Log.w(tag, "rtmpCamera nulo - configuracao guardada localmente")
            return
        }
        applyCameraParams(cam, params)
    }

    private fun applyLocally(params: Map<String, Any>) {
        (params["width"]   as? Number)?.let { currentWidth   = it.toInt() }
        (params["height"]  as? Number)?.let { currentHeight  = it.toInt() }
        (params["fps"]     as? Number)?.let { currentFps     = it.toInt() }
        (params["bitrate"] as? Number)?.let { currentBitrate = it.toInt() }
        (params["camera"]  as? String)?.let { currentCameraId = it }
        (params["zoom"]    as? Number)?.let { zoomLevel      = it.toFloat().coerceAtLeast(1f) }
        (params["iso"]     as? Number)?.let { isoValue       = it.toInt() }
        (params["ois"]     as? Boolean)?.let { oisEnabled    = it }
        (params["eis"]     as? Boolean)?.let { eisEnabled    = it }
        (params["lantern"] as? Boolean)?.let { lanternEnabled = it }
        (params["manualSensor"] as? Boolean)?.let { manualSensor = it }
        (params["aeLock"]  as? Boolean)?.let { aeLocked      = it }
        (params["awbLock"] as? Boolean)?.let { awbLocked     = it }
        (params["whiteBalance"] as? String)?.let { whiteBalanceMode = it }
        (params["flashMode"]    as? String)?.let { flashMode  = it }
    }

    private fun applyCameraParams(cam: RtmpCamera2, params: Map<String, Any>) {
        try {
            // ---- Zoom (nivel real: 1.0 = sem zoom) ----
            (params["zoom"] as? Number)?.let {
                val z = it.toFloat().coerceAtLeast(1f)
                cam.setZoom(z)
                zoomLevel = z
            }

            // ---- Lanterna ----
            (params["lantern"] as? Boolean)?.let { on ->
                try {
                    if (on) cam.enableLantern() else cam.disableLantern()
                    lanternEnabled = on
                } catch (e: Exception) {
                    Log.w(tag, "Lanterna nao suportada: ${e.message}")
                }
            }

            // ---- OIS (estabilizacao optica) ----
            // API real: enableOpticalVideoStabilization / disableOpticalVideoStabilization
            (params["ois"] as? Boolean)?.let { on ->
                if (on) cam.enableOpticalVideoStabilization()
                else    cam.disableOpticalVideoStabilization()
                oisEnabled = on
            }

            // ---- EIS (estabilizacao digital) ----
            (params["eis"] as? Boolean)?.let { on ->
                if (on) cam.enableVideoStabilization()
                else    cam.disableVideoStabilization()
                eisEnabled = on
            }

            // ---- Foco ----
            // API real: setFocusDistance(float) para foco manual, enableAutoFocus() para auto
            (params["focus"] as? Number)?.let {
                val d = it.toFloat()
                if (d <= 0f) {
                    cam.enableAutoFocus()
                    autoFocus = true
                    focusDistance = 0f
                } else {
                    cam.disableAutoFocus()
                    cam.setFocusDistance(d)
                    autoFocus = false
                    focusDistance = d
                }
            }

            // ---- Balanco de branco ----
            // API real: enableAutoWhiteBalance(int mode)
            (params["whiteBalance"] as? String)?.let { wb ->
                val mode = when (wb) {
                    "daylight"     -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    "cloudy"       -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    "incandescent" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    "fluorescent"  -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                    // "tungsten" nao existe na Camera2 API, mapear para incandescent
                    "tungsten"     -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    else           -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                }
                cam.enableAutoWhiteBalance(mode)
                whiteBalanceMode = wb
            }

            // ---- Troca de camera ----
            // API real: switchCamera(String cameraId) ou switchCamera() para front/back
            (params["camera"] as? String)?.let { id ->
                try {
                    cam.switchCamera(id)
                    currentCameraId = id
                } catch (e: Exception) {
                    Log.w(tag, "switchCamera falhou para id=$id: ${e.message}")
                }
            }

            // ---- Bitrate em tempo real ----
            (params["bitrate"] as? Number)?.let {
                val kbps = it.toInt()
                if (cam.isStreaming) cam.setVideoBitrateOnFly(kbps * 1024)
                currentBitrate = kbps
            }

            // ---- Exposicao (EV compensation) ----
            (params["exposure"] as? Number)?.let {
                cam.setExposure(it.toInt())
            }

        } catch (e: Exception) {
            Log.e(tag, "Erro ao aplicar params na camera", e)
        }
    }

    fun release() {
        rtmpCamera = null
    }

    // -- Descobre cameras disponiveis (usando apenas API 26+) --
    fun discoverAllCameras(context: Context): List<Map<String, Any>> {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return mgr.cameraIdList.mapNotNull { id ->
            try {
                val chars = mgr.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                val isDepth = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) == true
                val name = when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK  -> "Traseira $id"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                    else -> "Camera $id"
                }
                // Zoom: usa getZoomRange() do RtmpCamera2 quando disponivel,
                // ou SCALER_AVAILABLE_MAX_DIGITAL_ZOOM (API 21+) como fallback
                val maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                val focusRange = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val hasOis = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                val supportsManual     = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                val supportsPostProc   = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
                val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    ?.map { m -> when (m) { 0->"off"; 1->"auto"; 3->"continuous-video"; 4->"continuous-picture"; else->"mode$m" } }
                    ?: listOf("off")
                val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                    ?.map { m -> when (m) { 0->"off"; 1->"auto"; 2->"incandescent"; 3->"fluorescent"; 4->"warm-fluorescent"; 5->"daylight"; 6->"cloudy"; 7->"twilight"; 8->"shade"; else->"mode$m" } }
                    ?: listOf("auto")
                val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                val resolutions = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                    ?.filter { it.width >= 640 }
                    ?.map { "${it.width}x${it.height}" }
                    ?.distinct() ?: emptyList()

                mapOf(
                    "camera_id"   to id,
                    "name"        to name,
                    "is_depth"    to isDepth,
                    "has_flash"   to hasFlash,
                    "has_ois"     to hasOis,
                    "zoom_range"  to listOf(1f, maxZoom),
                    "focus_distance_range" to listOf(0f, focusRange),
                    "supports_manual_sensor" to supportsManual,
                    "supports_manual_post_processing" to supportsPostProc,
                    "supported_af_modes" to afModes,
                    "supported_awb_modes" to awbModes,
                    "ev_range"    to listOf(evRange?.lower ?: -8, evRange?.upper ?: 8),
                    "available_resolutions" to resolutions
                )
            } catch (e: Exception) {
                Log.w(tag, "Erro ao ler camera $id: ${e.message}")
                null
            }
        }
    }
}
