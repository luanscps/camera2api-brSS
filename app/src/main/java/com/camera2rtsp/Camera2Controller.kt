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
    var zoomLevel        = 0f
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
        val cam = rtmpCamera ?: run {
            Log.w(tag, "rtmpCamera nulo, guardando configuracao apenas")
            applyLocally(params)
            return
        }
        applyLocally(params)
        applyCameraParams(cam, params)
    }

    private fun applyLocally(params: Map<String, Any>) {
        (params["width"]   as? Number)?.let { currentWidth   = it.toInt() }
        (params["height"]  as? Number)?.let { currentHeight  = it.toInt() }
        (params["fps"]     as? Number)?.let { currentFps     = it.toInt() }
        (params["bitrate"] as? Number)?.let { currentBitrate = it.toInt() }
        (params["camera"]  as? String)?.let { currentCameraId = it }
        (params["zoom"]    as? Number)?.let { zoomLevel      = it.toFloat() }
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
            (params["zoom"] as? Number)?.let {
                val z = it.toFloat().coerceIn(0f, 1f)
                cam.setZoom(z)
                zoomLevel = z
            }
            (params["lantern"] as? Boolean)?.let {
                cam.enableLantern()
                if (!it) cam.disableLantern()
                lanternEnabled = it
            }
            (params["ois"] as? Boolean)?.let {
                if (it) cam.enableOis() else cam.disableOis()
                oisEnabled = it
            }
            (params["focus"] as? Number)?.let {
                val d = it.toFloat()
                if (d == 0f) {
                    cam.enableAutoFocus()
                    autoFocus = true
                } else {
                    cam.tapToFocus(d, d)
                    autoFocus = false
                    focusDistance = d
                }
            }
            (params["whiteBalance"] as? String)?.let { wb ->
                val mode = when (wb) {
                    "daylight"      -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    "cloudy"        -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    "incandescent"  -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    "fluorescent"   -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                    "tungsten"      -> CameraMetadata.CONTROL_AWB_MODE_TUNGSTEN_LIGHT
                    else            -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                }
                cam.setWhiteBalance(mode)
                whiteBalanceMode = wb
            }
            (params["camera"] as? String)?.let { id ->
                cam.changeVideoSourceCamera(
                    if (id == "1") com.pedro.encoder.input.video.CameraHelper.Facing.FRONT
                    else           com.pedro.encoder.input.video.CameraHelper.Facing.BACK
                )
                currentCameraId = id
            }
            (params["bitrate"] as? Number)?.let {
                val kbps = it.toInt()
                if (cam.isStreaming) cam.setVideoBitrateOnFly(kbps * 1024)
                currentBitrate = kbps
            }
        } catch (e: Exception) {
            Log.e(tag, "Erro ao aplicar params", e)
        }
    }

    fun release() {
        rtmpCamera = null
    }

    // -- Descobre cameras disponiveis --
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
                val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                val focusRange = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val hasOis = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
                val supportsManual = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                val supportsPostProc = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
                val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    ?.map { m -> when(m) { 0->"off"; 1->"auto"; 3->"continuous-video"; 4->"continuous-picture"; else->"mode$m" } }
                    ?: listOf("off")
                val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                    ?.map { m -> when(m) { 0->"off"; 1->"auto"; 2->"incandescent"; 3->"fluorescent"; 4->"warm-fluorescent"; 5->"daylight"; 6->"cloudy"; 7->"twilight"; 8->"shade"; else->"mode$m" } }
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
                    "zoom_range"  to listOf(zoomRange?.lower ?: 1f, zoomRange?.upper ?: 1f),
                    "focus_distance_range" to listOf(0f, focusRange ?: 0f),
                    "supports_manual_sensor" to supportsManual,
                    "supports_manual_post_processing" to supportsPostProc,
                    "supported_af_modes" to afModes,
                    "supported_awb_modes" to awbModes,
                    "ev_range"    to listOf(evRange?.lower ?: -8, evRange?.upper ?: 8),
                    "available_resolutions" to resolutions
                )
            } catch (_: Exception) { null }
        }
    }
}
