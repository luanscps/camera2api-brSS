package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Log
import com.pedro.library.rtmp.RtmpCamera2

class Camera2Controller {

    val tag = "Camera2Controller"

    // Referencia ao encoder RTMP ativo (atualizada pelo RtmpStreamer)
    var rtmpCamera: RtmpCamera2? = null

    // -- Configuracoes de stream --
    var currentWidth    = 1920
    var currentHeight   = 1080
    var currentFps      = 30
    var currentBitrate  = 4000   // kbps
    var currentCameraId = "0"

    // -- Controles basicos --
    var zoomLevel        = 1f
    var autoFocus        = true
    var focusDistance    = 0f
    var whiteBalanceMode = "auto"
    var lanternEnabled   = false
    var isoValue         = 100
    var oisEnabled       = false
    var eisEnabled       = false

    // -- Controles avancados de sensor (usados pelo WebControlServer) --
    var exposureNs       = 0L       // tempo de exposicao em nanosegundos
    var frameDurationNs  = 0L       // duracao de frame em nanosegundos
    var manualSensor     = false    // modo sensor manual (ISO + exposicao manual)
    var aeLocked         = false    // auto-exposure lock
    var awbLocked        = false    // auto-white-balance lock
    var flashMode        = "off"    // off | torch | single
    var edgeMode         = CameraMetadata.EDGE_MODE_HIGH_QUALITY
    var noiseReductionMode = CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
    var hotPixelMode     = CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY

    fun updateSettings(params: Map<String, Any>) {
        applyLocally(params)
        val cam = rtmpCamera ?: run {
            Log.w(tag, "rtmpCamera nulo - config guardada localmente")
            return
        }
        applyCameraParams(cam, params)
    }

    private fun applyLocally(params: Map<String, Any>) {
        (params["width"]        as? Number)?.let  { currentWidth          = it.toInt() }
        (params["height"]       as? Number)?.let  { currentHeight         = it.toInt() }
        (params["fps"]          as? Number)?.let  { currentFps            = it.toInt() }
        (params["bitrate"]      as? Number)?.let  { currentBitrate        = it.toInt() }
        (params["camera"]       as? String)?.let  { currentCameraId       = it }
        (params["zoom"]         as? Number)?.let  { zoomLevel             = it.toFloat().coerceAtLeast(1f) }
        (params["iso"]          as? Number)?.let  { isoValue              = it.toInt() }
        (params["ois"]          as? Boolean)?.let { oisEnabled            = it }
        (params["eis"]          as? Boolean)?.let { eisEnabled            = it }
        (params["lantern"]      as? Boolean)?.let { lanternEnabled        = it }
        (params["whiteBalance"] as? String)?.let  { whiteBalanceMode      = it }
        (params["exposureNs"]   as? Number)?.let  { exposureNs            = it.toLong() }
        (params["frameDuration"]as? Number)?.let  { frameDurationNs       = it.toLong() }
        (params["manualSensor"] as? Boolean)?.let { manualSensor          = it }
        (params["aeLock"]       as? Boolean)?.let { aeLocked              = it }
        (params["awbLock"]      as? Boolean)?.let { awbLocked             = it }
        (params["flashMode"]    as? String)?.let  { flashMode             = it }
        (params["edgeMode"]     as? Number)?.let  { edgeMode              = it.toInt() }
        (params["noiseReduction"]as? Number)?.let { noiseReductionMode    = it.toInt() }
        (params["hotPixel"]     as? Number)?.let  { hotPixelMode          = it.toInt() }
    }

    private fun applyCameraParams(cam: RtmpCamera2, params: Map<String, Any>) {
        try {
            (params["zoom"] as? Number)?.let {
                val z = it.toFloat().coerceAtLeast(1f)
                cam.setZoom(z); zoomLevel = z
            }

            (params["lantern"] as? Boolean)?.let { on ->
                try { if (on) cam.enableLantern() else cam.disableLantern(); lanternEnabled = on }
                catch (e: Exception) { Log.w(tag, "Lanterna nao suportada: ${e.message}") }
            }

            (params["ois"] as? Boolean)?.let { on ->
                if (on) cam.enableOpticalVideoStabilization()
                else    cam.disableOpticalVideoStabilization()
                oisEnabled = on
            }

            (params["eis"] as? Boolean)?.let { on ->
                if (on) cam.enableVideoStabilization()
                else    cam.disableVideoStabilization()
                eisEnabled = on
            }

            (params["focus"] as? Number)?.let {
                val d = it.toFloat()
                if (d <= 0f) { cam.enableAutoFocus(); autoFocus = true; focusDistance = 0f }
                else { cam.disableAutoFocus(); cam.setFocusDistance(d); autoFocus = false; focusDistance = d }
            }

            (params["whiteBalance"] as? String)?.let { wb ->
                val mode = when (wb) {
                    "daylight"                -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    "cloudy"                  -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    "incandescent","tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    "fluorescent"             -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                    else                      -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                }
                cam.enableAutoWhiteBalance(mode); whiteBalanceMode = wb
            }

            (params["camera"] as? String)?.let { id ->
                try { cam.switchCamera(id); currentCameraId = id }
                catch (e: Exception) { Log.w(tag, "switchCamera falhou: ${e.message}") }
            }

            (params["bitrate"] as? Number)?.let {
                val kbps = it.toInt()
                if (cam.isStreaming) cam.setVideoBitrateOnFly(kbps * 1024)
                currentBitrate = kbps
            }

            (params["exposure"] as? Number)?.let { cam.setExposure(it.toInt()) }

        } catch (e: Exception) {
            Log.e(tag, "Erro ao aplicar params na camera", e)
        }
    }

    fun release() { rtmpCamera = null }

    fun discoverAllCameras(context: Context): List<Map<String, Any>> {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return mgr.cameraIdList.mapNotNull { id ->
            try {
                val chars   = mgr.getCameraCharacteristics(id)
                val facing  = chars.get(CameraCharacteristics.LENS_FACING)
                val name    = when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK  -> "Traseira $id"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                    else -> "Camera $id"
                }
                val maxZoom  = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val hasOis   = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
                val evRange  = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                val resolutions = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                    ?.filter { it.width >= 640 }
                    ?.map { "${it.width}x${it.height}" } ?: emptyList()
                mapOf(
                    "camera_id"   to id,
                    "name"        to name,
                    "has_flash"   to hasFlash,
                    "has_ois"     to hasOis,
                    "zoom_range"  to listOf(1f, maxZoom),
                    "ev_range"    to listOf(evRange?.lower ?: -8, evRange?.upper ?: 8),
                    "resolutions" to resolutions
                )
            } catch (e: Exception) { Log.w(tag, "Erro camera $id: ${e.message}"); null }
        }
    }
}
