package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

class Camera2Controller {

    private val tag = "Camera2Controller"

    var server: RtspServerCamera2? = null

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
    var eisEnabled        = false
    var aeLocked          = false
    var awbLocked         = false
    var flashMode         = "off"  // off, torch, single
    var currentWidth      = 1920
    var currentHeight     = 1080
    var currentBitrate    = 4000
    var currentFps        = 30

    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(tag, "Erro na worker", it) } }

    fun discoverAllCameras(context: Context): List<CameraCapabilities> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameras = mutableListOf<CameraCapabilities>()

        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)

            val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY  -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL    -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3       -> "LEVEL_3"
                else -> "UNKNOWN"
            }

            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            fun hasCap(value: Int) = caps.contains(value)

            val manualSensor  = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val manualPost    = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
            val raw           = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            val burst         = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)
            val depth         = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
            val multiCam      = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)

            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let {
                listOf(it.lower, it.upper)
            }
            val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let {
                listOf(it.lower, it.upper)
            }
            val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let {
                listOf(it.lower, it.upper)
            }
            val focusRange = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let {
                listOf(0f, it)
            }
            val zoomRange = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let {
                listOf(1.0f, it)
            }

            val fpsRangeArray = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: arrayOf()
            val fpsRanges = fpsRangeArray.map { listOf(it.lower, it.upper) }

            val streamConfig = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val resolutions = streamConfig?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                ?.map { "${it.width}x${it.height}" }
                ?.distinct()
                ?.sortedBy { it.split("x").getOrNull(0)?.toIntOrNull() ?: 0 }
                ?: emptyList()

            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.map {
                when (it) {
                    CameraCharacteristics.CONTROL_AF_MODE_OFF                -> "off"
                    CameraCharacteristics.CONTROL_AF_MODE_AUTO               -> "auto"
                    CameraCharacteristics.CONTROL_AF_MODE_MACRO              -> "macro"
                    CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO   -> "continuous-video"
                    CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "continuous-picture"
                    CameraCharacteristics.CONTROL_AF_MODE_EDOF               -> "edof"
                    else -> "unknown"
                }
            } ?: emptyList()

            val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.map {
                when (it) {
                    CameraCharacteristics.CONTROL_AE_MODE_OFF                  -> "off"
                    CameraCharacteristics.CONTROL_AE_MODE_ON                   -> "on"
                    CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH        -> "on-auto-flash"
                    CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH      -> "on-always-flash"
                    CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "on-auto-flash-redeye"
                    else -> "unknown"
                }
            } ?: emptyList()

            val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.map {
                when (it) {
                    CameraCharacteristics.CONTROL_AWB_MODE_OFF              -> "off"
                    CameraCharacteristics.CONTROL_AWB_MODE_AUTO             -> "auto"
                    CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT     -> "incandescent"
                    CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT      -> "fluorescent"
                    CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "warm-fluorescent"
                    CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT         -> "daylight"
                    CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT  -> "cloudy"
                    CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT         -> "twilight"
                    CameraCharacteristics.CONTROL_AWB_MODE_SHADE            -> "shade"
                    else -> "unknown"
                }
            } ?: emptyList()

            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            val hasOIS   = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                ?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) ?: false

            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()
            val apertures    = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList()

            val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK     -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT    -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }

            val isDepth = depth && resolutions.isEmpty()

            val name = when {
                isDepth                        -> "Depth/ToF"
                id == "0" && facing == "BACK"  -> "Wide"
                id == "1" && facing == "FRONT" -> "Frontal"
                id == "2" && facing == "BACK"  -> "Ultra Wide"
                id == "3" && facing == "BACK"  -> "Telephoto"
                facing == "FRONT"              -> "Frontal $id"
                else                           -> "Cam $id"
            }

            cameras.add(
                CameraCapabilities(
                    cameraId                  = id,
                    hardwareLevel             = hwLevel,
                    facing                    = facing,
                    name                      = name,
                    isDepth                   = isDepth,
                    supportsManualSensor      = manualSensor,
                    supportsManualPostProcessing = manualPost,
                    supportsRaw               = raw,
                    supportsBurstCapture      = burst,
                    supportsDepthOutput       = depth,
                    supportsLogicalMultiCamera = multiCam,
                    isoRange                  = isoRange,
                    exposureTimeRange         = expRange,
                    evRange                   = evRange,
                    focusDistanceRange        = focusRange,
                    zoomRange                 = zoomRange,
                    fpsRanges                 = fpsRanges,
                    availableResolutions      = resolutions,
                    supportedAFModes          = afModes,
                    supportedAEModes          = aeModes,
                    supportedAWBModes         = awbModes,
                    hasFlash                  = hasFlash,
                    hasOIS                    = hasOIS,
                    focalLengths              = focalLengths,
                    apertures                 = apertures
                )
            )
        }

        return cameras
    }

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(tag, "server nao inicializado"); return }

        params["exposure"]?.let {
            val ev = (it as Double).toInt().coerceIn(srv.minExposure, srv.maxExposure)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(tag, "Exposure EV=$ev")
        }

        params["iso"]?.let {
            val iso = (it as Double).toInt()
            isoValue = iso
            val minEv = srv.minExposure
            val maxEv = srv.maxExposure
            val ev = (((iso - 50f) / (3200f - 50f)) * (maxEv - minEv) + minEv)
                .toInt().coerceIn(minEv, maxEv)
            exposureLevel = ev
            srv.setExposure(ev)
            Log.d(tag, "ISO $iso -> EV $ev")
        }

        params["manualSensor"]?.let {
            manualSensor = it as Boolean
            if (!manualSensor) {
                srv.setExposure(0)
                exposureLevel = 0
                Log.d(tag, "manualSensor OFF -> EV reset")
            } else {
                Log.d(tag, "manualSensor ON")
            }
        }

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

        params["afTrigger"]?.let {
            // Reativa AF continuo como equivalente ao trigger no contexto de streaming
            srv.enableAutoFocus()
            Log.d(tag, "AF Trigger acionado via enableAutoFocus")
        }

        params["whiteBalance"]?.let {
            whiteBalanceMode = it as String
            val mode = when (whiteBalanceMode) {
                "daylight"                 -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                "cloudy"                   -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                "tungsten", "incandescent" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                "fluorescent"              -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                else                       -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            }
            srv.enableAutoWhiteBalance(mode)
            Log.d(tag, "WB -> $whiteBalanceMode")
        }

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

        params["lantern"]?.let {
            val enable = it as Boolean
            lanternEnabled = enable
            if (enable) srv.enableLantern() else srv.disableLantern()
            Log.d(tag, "Lanterna -> $enable")
        }

        params["ois"]?.let {
            val enable = it as Boolean
            oisEnabled = enable
            if (enable) srv.enableOpticalVideoStabilization()
            else        srv.disableOpticalVideoStabilization()
            Log.d(tag, "OIS -> $enable")
        }

        params["eis"]?.let {
            val enable = it as Boolean
            eisEnabled = enable
            if (enable) srv.enableVideoStabilization()
            else        srv.disableVideoStabilization()
            Log.d(tag, "EIS -> $enable")
        }

        params["aeLock"]?.let {
            val lock = it as Boolean
            aeLocked = lock
            // AE Lock: desliga/liga o AutoExposure como equivalente
            if (lock) srv.disableAutoExposure()
            else      srv.enableAutoExposure()
            Log.d(tag, "AE Lock -> $lock")
        }

        params["awbLock"]?.let {
            val lock = it as Boolean
            awbLocked = lock
            // AWB Lock: desliga/liga o AutoWhiteBalance como equivalente
            if (lock) srv.disableAutoWhiteBalance()
            else      srv.enableAutoWhiteBalance(CameraMetadata.CONTROL_AWB_MODE_AUTO)
            Log.d(tag, "AWB Lock -> $lock")
        }

        params["flashMode"]?.let {
            val mode = it as String
            flashMode = mode
            when (mode) {
                "off" -> {
                    srv.disableLantern()
                    lanternEnabled = false
                }
                "torch" -> {
                    srv.enableLantern()
                    lanternEnabled = true
                }
                "single" -> {
                    srv.disableLantern()
                    lanternEnabled = false
                }
            }
            Log.d(tag, "Flash Mode -> $mode")
        }

        params["bitrate"]?.let {
            val br = (it as Double).toInt()
            currentBitrate = br
            srv.setVideoBitrateOnFly(br * 1024)
            Log.d(tag, "Bitrate -> ${br}kbps")
        }

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

        params["camera"]?.let { value ->
            val id = value as String
            currentCameraId = id
            post {
                srv.switchCamera(id)
                if (!autoFocus && focusDistance > 0f) srv.setFocusDistance(focusDistance)
                Log.d(tag, "Camera -> $id")
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
            post {
                val wasStreaming = srv.isStreaming
                if (wasStreaming) srv.stopStream()
                val videoOk = srv.prepareVideo(w, h, currentFps, br * 1024, 0)
                val audioOk = srv.prepareAudio(128 * 1024, 44100, true)
                if (wasStreaming && videoOk && audioOk) srv.startStream()
                Log.d(tag, "Resolucao -> ${w}x${h} @${br}kbps videoOk=$videoOk")
            }
        }
    }

    fun release() {
        workerThread.quitSafely()
    }
}
