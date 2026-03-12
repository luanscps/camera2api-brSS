package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.library.base.Camera2Base
import com.pedro.rtspserver.RtspServerCamera2
import kotlin.math.pow

class Camera2Controller {

    private val tag = "Camera2Controller"

    var server: RtspServerCamera2? = null

    var currentCameraId  = "0"
    var exposureLevel    = 0
    var exposureNs       = 33_333_333L
    var frameDurationNs  = 33_333_333L
    var isoValue         = 100
    var manualSensor     = false
    var whiteBalanceMode = "auto"
    var autoFocus        = true
    var focusDistance    = 0f
    var zoomLevel        = 0f
    var lanternEnabled   = false
    var oisEnabled       = false
    var eisEnabled       = false
    var aeLocked         = false
    var awbLocked        = false
    var flashMode        = "off"
    var currentWidth     = 1920
    var currentHeight    = 1080
    var currentBitrate   = 4000
    var currentFps       = 30

    // ── Onda 3: Post-processing state ─────────────────────────────────────────
    var edgeMode           = CameraMetadata.EDGE_MODE_HIGH_QUALITY
    var noiseReductionMode = CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
    var tonemapMode        = CameraMetadata.TONEMAP_MODE_HIGH_QUALITY
    var hotPixelMode       = CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY

    // ── Tonemap Curve ──────────────────────────────────────────────────────
    var customTonemapCurve = "s-curve"
    private var tonemapCurvePoints: FloatArray = buildSCurve()

    // ── Histogram (simulado) ─────────────────────────────────────────────────
    val lastHistogramR = IntArray(256)
    val lastHistogramG = IntArray(256)
    val lastHistogramB = IntArray(256)

    // ── Worker thread ────────────────────────────────────────────────────────
    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)
    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(tag, "worker error", it) } }

    // =========================================================================
    // Acesso ao Camera2ApiManager via reflection (unico ponto de reflection)
    // =========================================================================

    private fun getCam2Manager(): Camera2ApiManager? = try {
        val f = Camera2Base::class.java.getDeclaredField("cameraManager")
        f.isAccessible = true
        f.get(server) as? Camera2ApiManager
    } catch (e: Exception) {
        Log.e(tag, "getCam2Manager falhou", e); null
    }

    // =========================================================================
    // setCustomRequest — API publica do RootEncoder
    // Equivale a: builder.set(...) + setRepeatingRequest(builder.build())
    // Nao precisa de reflection alem do getCam2Manager acima.
    // =========================================================================

    private fun customRequest(block: (CaptureRequest.Builder) -> Unit): Boolean {
        val cam = getCam2Manager() ?: return false
        return try {
            cam.setCustomRequest(block)
        } catch (e: Exception) {
            Log.e(tag, "setCustomRequest falhou", e); false
        }
    }

    // =========================================================================
    // Tonemap persistente via setCustomOnCaptureCompletedCallback
    //
    // PROBLEMA REAL: o driver Samsung (HAL3) silenciosamente ignora
    // TONEMAP_MODE_CONTRAST_CURVE em muitas configuracoes de stream.
    // Para confirmar se o tonemap esta sendo ACEITO, lemos TONEMAP_MODE
    // de volta do CaptureResult a cada frame.
    //
    // Estrategia:
    //   - Registramos o callback via setCustomOnCaptureCompletedCallback
    //   - A cada frame lemos result.get(CaptureResult.TONEMAP_MODE)
    //   - Se o driver retornar algo diferente de CONTRAST_CURVE, logamos
    //     como WARN ("HAL ignorou TONEMAP") e tentamos reaplicar
    //   - Isso tambem serve de diagnostico: se o log aparecer sempre,
    //     o Note10+ simplesmente nao suporta TONEMAP em stream de video
    // =========================================================================

    @Volatile private var tonemapCallbackActive = false

    private fun activateTonemapCallback() {
        if (tonemapCallbackActive) return
        val cam = getCam2Manager() ?: return
        cam.setCustomOnCaptureCompletedCallback { _, _, result ->
            if (tonemapMode != CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE) return@setCustomOnCaptureCompletedCallback
            val actual = result.get(CaptureResult.TONEMAP_MODE)
            if (actual != CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE) {
                Log.w(tag, "HAL nao aceitou TONEMAP_CONTRAST_CURVE (retornou $actual) — tentando reaplicar")
                // Tenta reaplicar via setCustomRequest
                customRequest { b ->
                    b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                    b.set(CaptureRequest.TONEMAP_CURVE, android.hardware.camera2.params.TonemapCurve(
                        tonemapCurvePoints, tonemapCurvePoints, tonemapCurvePoints
                    ))
                }
            }
        }
        tonemapCallbackActive = true
        Log.d(tag, "tonemapCallback ativado via setCustomOnCaptureCompletedCallback")
    }

    private fun deactivateTonemapCallback() {
        if (!tonemapCallbackActive) return
        getCam2Manager()?.setCustomOnCaptureCompletedCallback(null)
        tonemapCallbackActive = false
        Log.d(tag, "tonemapCallback desativado")
    }

    // =========================================================================
    // Post-processing
    // =========================================================================

    private fun applyPostProcessing() {
        val usingCurve = (tonemapMode == CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)

        val ok = customRequest { b ->
            b.set(CaptureRequest.EDGE_MODE,            edgeMode)
            b.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode)
            b.set(CaptureRequest.HOT_PIXEL_MODE,       hotPixelMode)

            if (usingCurve) {
                b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                b.set(CaptureRequest.TONEMAP_CURVE, android.hardware.camera2.params.TonemapCurve(
                    tonemapCurvePoints, tonemapCurvePoints, tonemapCurvePoints
                ))
                Log.d(tag, "applyPostProcessing: CONTRAST_CURVE curva=$customTonemapCurve pts=${tonemapCurvePoints.size / 2}")
            } else {
                if (!manualSensor) {
                    b.set(CaptureRequest.CONTROL_MODE,    CameraMetadata.CONTROL_MODE_AUTO)
                    b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                }
                b.set(CaptureRequest.TONEMAP_MODE, tonemapMode)
            }
        }

        if (usingCurve) activateTonemapCallback()
        else deactivateTonemapCallback()

        Log.d(tag, "applyPostProcessing ok=$ok edge=$edgeMode nr=$noiseReductionMode tone=$tonemapMode hot=$hotPixelMode")
    }

    // =========================================================================
    // Sensor manual / auto
    // =========================================================================

    private fun applyManualSensor() {
        val safeDuration = maxOf(frameDurationNs, exposureNs)
        val ok = customRequest { b ->
            b.set(CaptureRequest.CONTROL_MODE,          CameraMetadata.CONTROL_MODE_OFF)
            b.set(CaptureRequest.CONTROL_AE_MODE,       CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY,    isoValue)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME,  exposureNs)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, safeDuration)
        }
        val fpsMax = if (safeDuration > 0) (1_000_000_000.0 / safeDuration).toInt() else 0
        Log.d(tag, "applyManualSensor ok=$ok ISO=$isoValue exp=${exposureNs}ns dur=${safeDuration}ns fpsMax=$fpsMax")
        applyPostProcessing()
    }

    private fun applyAutoSensor() {
        val ok = customRequest { b ->
            b.set(CaptureRequest.CONTROL_MODE,    CameraMetadata.CONTROL_MODE_AUTO)
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        }
        Log.d(tag, "applyAutoSensor ok=$ok")
    }

    // =========================================================================
    // Tonemap curve builders
    // =========================================================================

    private fun buildLinear(): FloatArray = floatArrayOf(0f, 0f, 1f, 1f)

    private fun buildSCurve(): FloatArray {
        val pts = mutableListOf<Float>()
        for (i in 0..16) {
            val x = i / 16f
            val s = x * x * (3f - 2f * x)
            pts.add(x); pts.add((s * 0.90f + x * 0.10f).coerceIn(0f, 1f))
        }
        return pts.toFloatArray()
    }

    private fun buildLogCurve(): FloatArray {
        val pts = mutableListOf<Float>()
        for (i in 0..16) {
            val x = i / 16f
            val y = kotlin.math.ln(1f + 9f * x) / kotlin.math.ln(10f)
            pts.add(x); pts.add((y * 0.80f + x * 0.20f).coerceIn(0f, 1f))
        }
        return pts.toFloatArray()
    }

    private fun buildCinematicCurve(): FloatArray {
        val pts = mutableListOf<Float>()
        for (i in 0..16) {
            val x = i / 16f; val lift = 0.04f; val gain = 0.96f
            val raw = if (x < 0.5f) lift + x * (0.5f - lift) * 2f
                      else 0.5f + (x - 0.5f) * (gain - 0.5f) * 2f
            pts.add(x); pts.add((raw * 0.85f + x * 0.15f).coerceIn(0f, 1f))
        }
        return pts.toFloatArray()
    }

    private fun buildCustomCurve(raw: List<Double>): FloatArray? {
        if (raw.size < 4 || raw.size % 2 != 0) return null
        val pairs = mutableListOf<Pair<Float, Float>>()
        var i = 0
        while (i < raw.size - 1) {
            pairs.add(raw[i].toFloat().coerceIn(0f,1f) to raw[i+1].toFloat().coerceIn(0f,1f))
            i += 2
        }
        val sorted = pairs.sortedBy { it.first }.take(64).toMutableList()
        if (sorted.size < 2) return null
        if (sorted.first().first > 0f) sorted.add(0, 0f to sorted.first().second)
        if (sorted.last().first  < 1f) sorted.add(1f to sorted.last().second)
        val result = FloatArray(sorted.size * 2)
        sorted.forEachIndexed { idx, (x, y) -> result[idx*2] = x; result[idx*2+1] = y }
        return result
    }

    // =========================================================================
    // discoverAllCameras
    // =========================================================================

    fun discoverAllCameras(context: Context): List<CameraCapabilities> {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameras = mutableListOf<CameraCapabilities>()
        for (id in mgr.cameraIdList) {
            val ch = mgr.getCameraCharacteristics(id)
            val hwLevel = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY  -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL    -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3       -> "LEVEL_3"
                else -> "UNKNOWN"
            }
            val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            fun hasCap(v: Int) = caps.contains(v)
            val supManual = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val supPost   = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
            val supRaw    = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            val supBurst  = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)
            val supDepth  = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
            val supMulti  = if (android.os.Build.VERSION.SDK_INT >= 28)
                hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) else false
            val isoRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { listOf(it.lower, it.upper) }
            val expRange = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { listOf(it.lower, it.upper) }
            val evRange  = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { listOf(it.lower, it.upper) }
            val focRange = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let { listOf(0f, it) }
            val zomRange = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let { listOf(1.0f, it) }
            val fpsRanges = (ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: arrayOf()).map { listOf(it.lower, it.upper) }
            val streamCfg = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val resolutions = streamCfg?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                ?.map { "${it.width}x${it.height}" }?.distinct()
                ?.sortedBy { it.split("x").getOrNull(0)?.toIntOrNull() ?: 0 } ?: emptyList()
            val afModes = ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.map {
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
            val aeModes = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.map {
                when (it) {
                    CameraCharacteristics.CONTROL_AE_MODE_OFF                  -> "off"
                    CameraCharacteristics.CONTROL_AE_MODE_ON                   -> "on"
                    CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH        -> "on-auto-flash"
                    CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH      -> "on-always-flash"
                    CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "on-auto-flash-redeye"
                    else -> "unknown"
                }
            } ?: emptyList()
            val awbModes = ch.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.map {
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
            val hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            val hasOis   = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                ?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) ?: false
            val focalLengths = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()
            val apertures    = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList()
            val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK     -> "BACK"
                CameraCharacteristics.LENS_FACING_FRONT    -> "FRONT"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }
            val isDepth = supDepth && resolutions.isEmpty()
            val name = when {
                isDepth                        -> "Depth/ToF"
                id == "0" && facing == "BACK"  -> "Wide"
                id == "1" && facing == "FRONT" -> "Frontal"
                id == "2" && facing == "BACK"  -> "Ultra Wide"
                id == "3" && facing == "BACK"  -> "Telephoto"
                facing == "FRONT"              -> "Frontal $id"
                else                           -> "Cam $id"
            }
            cameras.add(CameraCapabilities(
                cameraId = id, hardwareLevel = hwLevel, facing = facing, name = name,
                isDepth = isDepth, supportsManualSensor = supManual,
                supportsManualPostProcessing = supPost, supportsRaw = supRaw,
                supportsBurstCapture = supBurst, supportsDepthOutput = supDepth,
                supportsLogicalMultiCamera = supMulti, isoRange = isoRange,
                exposureTimeRange = expRange, evRange = evRange,
                focusDistanceRange = focRange, zoomRange = zomRange,
                fpsRanges = fpsRanges, availableResolutions = resolutions,
                supportedAfModes = afModes, supportedAeModes = aeModes,
                supportedAwbModes = awbModes, hasFlash = hasFlash, hasOis = hasOis,
                focalLengths = focalLengths, apertures = apertures
            ))
        }
        return cameras
    }

    // =========================================================================
    // updateSettings
    // =========================================================================

    fun updateSettings(params: Map<String, Any>) {
        val srv = server ?: run { Log.w(tag, "server nao inicializado"); return }

        params["manualSensor"]?.let {
            manualSensor = it as Boolean
            if (manualSensor) applyManualSensor()
            else { applyAutoSensor(); srv.setExposure(0); exposureLevel = 0 }
            Log.d(tag, "manualSensor -> $manualSensor")
        }

        params["iso"]?.let {
            isoValue = (it as Double).toInt()
            if (manualSensor) applyManualSensor()
            else {
                val minEv = srv.minExposure; val maxEv = srv.maxExposure
                val ev = (((isoValue - 50f) / (3200f - 50f)) * (maxEv - minEv) + minEv)
                    .toInt().coerceIn(minEv, maxEv)
                exposureLevel = ev; srv.setExposure(ev)
            }
            Log.d(tag, "ISO -> $isoValue manual=$manualSensor")
        }

        params["shutterSpeed"]?.let { raw ->
            exposureNs = parseTimeParam(raw, 33_333_333L)
            if (manualSensor) applyManualSensor()
            Log.d(tag, "shutterSpeed -> ${exposureNs}ns")
        }

        params["frameDuration"]?.let { raw ->
            frameDurationNs = parseTimeParam(raw, 33_333_333L)
            if (manualSensor) applyManualSensor()
            Log.d(tag, "frameDuration -> ${frameDurationNs}ns")
        }

        params["exposure"]?.let {
            if (!manualSensor) {
                val ev = (it as Double).toInt().coerceIn(srv.minExposure, srv.maxExposure)
                exposureLevel = ev; srv.setExposure(ev)
                Log.d(tag, "EV -> $ev")
            }
        }

        params["focus"]?.let {
            val norm = (it as Double).toFloat().coerceIn(0f, 1f)
            if (norm == 0f) { autoFocus = true; focusDistance = 0f; srv.enableAutoFocus() }
            else { autoFocus = false; focusDistance = norm * 10f; srv.disableAutoFocus(); srv.setFocusDistance(focusDistance) }
            Log.d(tag, "focus norm=$norm dist=$focusDistance")
        }

        params["focusmode"]?.let {
            when (it as String) {
                "continuous-video", "continuous-picture", "auto" -> { autoFocus = true; focusDistance = 0f; srv.enableAutoFocus() }
                "off" -> srv.disableAutoFocus()
            }
            Log.d(tag, "focusMode -> $it")
        }

        params["afTrigger"]?.let { srv.enableAutoFocus(); Log.d(tag, "afTrigger") }

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
            val z = (it as Double).toFloat().coerceIn(0f, 1f); zoomLevel = z
            val zr = srv.zoomRange; srv.setZoom(zr.lower + z * (zr.upper - zr.lower))
            Log.d(tag, "zoom -> $z")
        }

        params["lantern"]?.let {
            lanternEnabled = it as Boolean
            if (lanternEnabled) srv.enableLantern() else srv.disableLantern()
            Log.d(tag, "lantern -> $lanternEnabled")
        }

        params["ois"]?.let {
            oisEnabled = it as Boolean
            if (oisEnabled) srv.enableOpticalVideoStabilization() else srv.disableOpticalVideoStabilization()
            Log.d(tag, "ois -> $oisEnabled")
        }

        params["eis"]?.let {
            eisEnabled = it as Boolean
            if (eisEnabled) srv.enableVideoStabilization() else srv.disableVideoStabilization()
            Log.d(tag, "eis -> $eisEnabled")
        }

        params["aeLock"]?.let {
            aeLocked = it as Boolean
            if (aeLocked) srv.disableAutoExposure() else srv.enableAutoExposure()
            Log.d(tag, "aeLock -> $aeLocked")
        }

        params["awbLock"]?.let {
            awbLocked = it as Boolean
            if (awbLocked) srv.disableAutoWhiteBalance()
            else srv.enableAutoWhiteBalance(CameraMetadata.CONTROL_AWB_MODE_AUTO)
            Log.d(tag, "awbLock -> $awbLocked")
        }

        params["flashMode"]?.let {
            flashMode = it as String
            when (flashMode) {
                "torch" -> { srv.enableLantern(); lanternEnabled = true }
                else    -> { srv.disableLantern(); lanternEnabled = false }
            }
            Log.d(tag, "flashMode -> $flashMode")
        }

        params["bitrate"]?.let {
            currentBitrate = (it as Double).toInt()
            srv.setVideoBitrateOnFly(currentBitrate * 1024)
            Log.d(tag, "bitrate -> ${currentBitrate}kbps")
        }

        params["fps"]?.let { value ->
            val fps = (value as Double).toInt().coerceIn(15, 30)
            currentFps = fps
            if (!manualSensor) frameDurationNs = 1_000_000_000L / fps
            post {
                val wasStreaming = srv.isStreaming
                if (wasStreaming) srv.stopStream()
                val vOk = srv.prepareVideo(currentWidth, currentHeight, fps, currentBitrate * 1024, 0)
                val aOk = srv.prepareAudio(128 * 1024, 44100, true)
                if (wasStreaming && vOk && aOk) srv.startStream()
                Log.d(tag, "fps -> $fps vOk=$vOk")
            }
        }

        params["camera"]?.let { value ->
            currentCameraId = value as String
            post {
                // Ao trocar de camera a sessao e recriada, desativa callback
                // para ser re-registrado na nova sessao
                deactivateTonemapCallback()
                srv.switchCamera(currentCameraId)
                if (manualSensor) applyManualSensor()
                else if (!autoFocus && focusDistance > 0f) srv.setFocusDistance(focusDistance)
                Log.d(tag, "camera -> $currentCameraId")
            }
        }

        params["resolution"]?.let { value ->
            val (w, h, br) = when (value as String) {
                "4k"    -> Triple(3840, 2160, 20000)
                "1080p" -> Triple(1920, 1080, 8000)
                else    -> Triple(1280, 720,  4000)
            }
            currentWidth = w; currentHeight = h; currentBitrate = br
            post {
                val wasStreaming = srv.isStreaming
                if (wasStreaming) srv.stopStream()
                val vOk = srv.prepareVideo(w, h, currentFps, br * 1024, 0)
                val aOk = srv.prepareAudio(128 * 1024, 44100, true)
                if (wasStreaming && vOk && aOk) srv.startStream()
                Log.d(tag, "resolution -> ${w}x${h} vOk=$vOk")
            }
        }

        // ── Onda 3: Post-processing ───────────────────────────────────────────
        params["edgeMode"]?.let {
            edgeMode = when (it as String) {
                "off"          -> CameraMetadata.EDGE_MODE_OFF
                "fast"         -> CameraMetadata.EDGE_MODE_FAST
                "high_quality" -> CameraMetadata.EDGE_MODE_HIGH_QUALITY
                else           -> CameraMetadata.EDGE_MODE_HIGH_QUALITY
            }
            applyPostProcessing(); Log.d(tag, "edgeMode -> $it")
        }

        params["noiseReduction"]?.let {
            noiseReductionMode = when (it as String) {
                "off"          -> CameraMetadata.NOISE_REDUCTION_MODE_OFF
                "fast"         -> CameraMetadata.NOISE_REDUCTION_MODE_FAST
                "high_quality" -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
                "minimal"      -> CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
                else           -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            }
            applyPostProcessing(); Log.d(tag, "noiseReduction -> $it")
        }

        params["tonemap"]?.let {
            tonemapMode = when (it as String) {
                "contrast_curve" -> CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
                "fast"           -> CameraMetadata.TONEMAP_MODE_FAST
                "high_quality"   -> CameraMetadata.TONEMAP_MODE_HIGH_QUALITY
                "gamma_value"    -> CameraMetadata.TONEMAP_MODE_GAMMA_VALUE
                else             -> CameraMetadata.TONEMAP_MODE_HIGH_QUALITY
            }
            applyPostProcessing(); Log.d(tag, "tonemap -> $it")
        }

        params["hotPixel"]?.let {
            hotPixelMode = when (it as String) {
                "off"          -> CameraMetadata.HOT_PIXEL_MODE_OFF
                "fast"         -> CameraMetadata.HOT_PIXEL_MODE_FAST
                "high_quality" -> CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
                else           -> CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
            }
            applyPostProcessing(); Log.d(tag, "hotPixel -> $it")
        }

        params["tonemapCurve"]?.let {
            customTonemapCurve = it as String
            tonemapCurvePoints = when (customTonemapCurve) {
                "linear"    -> buildLinear()
                "s-curve"   -> buildSCurve()
                "log"       -> buildLogCurve()
                "cinematic" -> buildCinematicCurve()
                else        -> buildSCurve()
            }
            tonemapMode = CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
            applyPostProcessing()
            Log.d(tag, "tonemapCurve -> $customTonemapCurve (${tonemapCurvePoints.size / 2} pts)")
        }

        @Suppress("UNCHECKED_CAST")
        params["tonemapCurveCustom"]?.let { raw ->
            val pts = raw as? List<*> ?: run { Log.w(tag, "tonemapCurveCustom: tipo errado"); return@let }
            val doubles = try { pts.map { (it as Number).toDouble() } }
            catch (e: ClassCastException) { Log.w(tag, "tonemapCurveCustom cast falhou"); return@let }
            val built = buildCustomCurve(doubles) ?: return@let
            tonemapCurvePoints = built
            customTonemapCurve = "custom"
            tonemapMode = CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
            applyPostProcessing()
            Log.d(tag, "tonemapCurveCustom: ${built.size / 2} pontos")
        }
    }

    private fun parseTimeParam(raw: Any, default: Long): Long = when (raw) {
        is String -> {
            val p = raw.split("/")
            if (p.size == 2) {
                val n = p[0].trim().toDoubleOrNull() ?: 1.0
                val d = p[1].trim().toDoubleOrNull() ?: 30.0
                ((n / d) * 1_000_000_000.0).toLong()
            } else default
        }
        is Double -> raw.toLong()
        is Long   -> raw
        else      -> default
    }

    fun release() { workerThread.quitSafely() }
}
