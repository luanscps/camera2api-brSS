package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.CameraCaptureSession
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

    // ── Cinema: Tonemap Curve Customization ───────────────────────────────────
    var customTonemapCurve = "s-curve"
    private var tonemapCurvePoints: FloatArray = buildSCurve()

    // ── Cinema Feature: Live Histogram ─────────────────────────────────────────
    val lastHistogramR = IntArray(256)
    val lastHistogramG = IntArray(256)
    val lastHistogramB = IntArray(256)
    private var histogramReady = false

    // ── Tonemap persistente: reaplica a cada frame via CaptureCallback ─────────
    // O RootEncoder chama applyRequest() internamente a cada CaptureResult,
    // sobrescrevendo nosso TONEMAP_MODE. O callback abaixo roda DEPOIS do
    // applyRequest do encoder e regarante o tonemap frame a frame.
    @Volatile private var tonemapCallbackRegistered = false

    private val tonemapCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val usingCurve = (tonemapMode == CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
            if (!usingCurve) return
            // Reaplica tonemap diretamente no builder sem passar pelo applyRequest do encoder
            val cam = getCam2Manager() ?: return
            try {
                val builderField = Camera2ApiManager::class.java.getDeclaredField("builderInputSurface")
                builderField.isAccessible = true
                val builder = builderField.get(cam) as? CaptureRequest.Builder ?: return
                builder.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                val curve = android.hardware.camera2.params.TonemapCurve(
                    tonemapCurvePoints, tonemapCurvePoints, tonemapCurvePoints
                )
                builder.set(CaptureRequest.TONEMAP_CURVE, curve)
                // Chama applyRequest para persistir no repeating request
                val applyMethod = Camera2ApiManager::class.java.getDeclaredMethod(
                    "applyRequest", CaptureRequest.Builder::class.java
                )
                applyMethod.isAccessible = true
                applyMethod.invoke(cam, builder)
            } catch (_: Exception) { }
        }
    }

    private fun ensureTonemapCallback() {
        if (tonemapCallbackRegistered) return
        val cam = getCam2Manager() ?: return
        try {
            // Tenta registrar o callback no Camera2ApiManager via reflection
            // O campo pode ser "captureCallback" ou "videoCapture" dependendo da versão
            val fields = Camera2ApiManager::class.java.declaredFields
            val cbField = fields.firstOrNull { f ->
                CameraCaptureSession.CaptureCallback::class.java.isAssignableFrom(f.type)
            }
            if (cbField != null) {
                cbField.isAccessible = true
                // Wrap: cria um callback composto que chama o original + o nosso
                val original = cbField.get(cam) as? CameraCaptureSession.CaptureCallback
                val composed = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        original?.onCaptureCompleted(session, request, result)
                        tonemapCaptureCallback.onCaptureCompleted(session, request, result)
                    }
                }
                cbField.set(cam, composed)
                tonemapCallbackRegistered = true
                Log.d(tag, "tonemapCaptureCallback registrado via reflection (field=${cbField.name})")
            } else {
                Log.w(tag, "nao encontrou CaptureCallback field no Camera2ApiManager — fallback para applyOnBuilder")
            }
        } catch (e: Exception) {
            Log.w(tag, "ensureTonemapCallback falhou: ${e.message}")
        }
    }

    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)

    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(tag, "Erro na worker", it) } }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    private fun getCam2Manager(): Camera2ApiManager? {
        return try {
            val field = Camera2Base::class.java.getDeclaredField("cameraManager")
            field.isAccessible = true
            field.get(server) as? Camera2ApiManager
        } catch (e: Exception) {
            Log.e(tag, "Reflection: cameraManager nao encontrado", e)
            null
        }
    }

    private fun applyOnBuilder(block: (CaptureRequest.Builder) -> Unit): Boolean {
        val cam = getCam2Manager() ?: return false
        return try {
            val builderField = Camera2ApiManager::class.java.getDeclaredField("builderInputSurface")
            builderField.isAccessible = true
            val builder = builderField.get(cam) as? CaptureRequest.Builder
                ?: run { Log.w(tag, "builderInputSurface eh null (camera ainda nao aberta)"); return false }
            block(builder)
            val applyMethod = Camera2ApiManager::class.java.getDeclaredMethod("applyRequest", CaptureRequest.Builder::class.java)
            applyMethod.isAccessible = true
            applyMethod.invoke(cam, builder) as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(tag, "Reflection: applyOnBuilder falhou", e)
            false
        }
    }

    // ── Cinema: Tonemap Curve Builders ────────────────────────────────────────

    private fun buildLinear(): FloatArray = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 1.0f
    )

    private fun buildSCurve(): FloatArray {
        val points = mutableListOf<Float>()
        for (i in 0..16) {
            val x = i / 16f
            val smooth = x * x * (3f - 2f * x)
            val y = (smooth * 0.90f + x * 0.10f).coerceIn(0f, 1f)
            points.add(x); points.add(y)
        }
        return points.toFloatArray()
    }

    private fun buildLogCurve(): FloatArray {
        val points = mutableListOf<Float>()
        for (i in 0..16) {
            val x = i / 16f
            val rawY = kotlin.math.ln(1f + 9f * x) / kotlin.math.ln(10f)
            val y = (rawY * 0.80f + x * 0.20f).coerceIn(0f, 1f)
            points.add(x); points.add(y)
        }
        return points.toFloatArray()
    }

    private fun buildCinematicCurve(): FloatArray {
        val points = mutableListOf<Float>()
        for (i in 0..16) {
            val x = i / 16f
            val lift = 0.04f
            val gain = 0.96f
            val raw = if (x < 0.5f)
                lift + x * (0.5f - lift) * 2f
            else
                0.5f + (x - 0.5f) * (gain - 0.5f) * 2f
            val y = (raw * 0.85f + x * 0.15f).coerceIn(0f, 1f)
            points.add(x); points.add(y)
        }
        return points.toFloatArray()
    }

    private fun buildCustomCurve(raw: List<Double>): FloatArray? {
        if (raw.size < 4 || raw.size % 2 != 0) {
            Log.w(tag, "tonemapCurveCustom: tamanho inválido ${raw.size}, ignorando")
            return null
        }
        val pairs = mutableListOf<Pair<Float, Float>>()
        var i = 0
        while (i < raw.size - 1) {
            val x = raw[i].toFloat().coerceIn(0f, 1f)
            val y = raw[i + 1].toFloat().coerceIn(0f, 1f)
            pairs.add(Pair(x, y))
            i += 2
        }
        pairs.sortBy { it.first }
        val limited = if (pairs.size > 64) pairs.take(64) else pairs
        if (limited.size < 2) {
            Log.w(tag, "tonemapCurveCustom: menos de 2 pontos após filtragem, ignorando")
            return null
        }
        val withExtremes = limited.toMutableList()
        if (withExtremes.first().first > 0f)
            withExtremes.add(0, Pair(0f, withExtremes.first().second))
        if (withExtremes.last().first < 1f)
            withExtremes.add(Pair(1f, withExtremes.last().second))

        val result = FloatArray(withExtremes.size * 2)
        withExtremes.forEachIndexed { idx, (x, y) ->
            result[idx * 2]     = x
            result[idx * 2 + 1] = y
        }
        Log.d(tag, "buildCustomCurve: ${withExtremes.size} pontos OK, [0]=(${result[0]},${result[1]}), [-1]=(${result[result.size-2]},${result[result.size-1]})")
        return result
    }

    // ── applyPostProcessing ───────────────────────────────────────────────────
    //
    // Aplica EDGE/NR/HOTPIXEL e tonemap no builder.
    // Para curvas (CONTRAST_CURVE): também tenta registrar o CaptureCallback
    // persistente para combater o reset por frame do encoder.
    private fun applyPostProcessing() {
        val usingCurve = (tonemapMode == CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)

        val ok = applyOnBuilder { b ->
            b.set(CaptureRequest.EDGE_MODE,            edgeMode)
            b.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode)
            b.set(CaptureRequest.HOT_PIXEL_MODE,       hotPixelMode)

            if (usingCurve) {
                b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                val curve = android.hardware.camera2.params.TonemapCurve(
                    tonemapCurvePoints, tonemapCurvePoints, tonemapCurvePoints
                )
                b.set(CaptureRequest.TONEMAP_CURVE, curve)
                Log.d(tag, "applyPostProcessing: curva=$customTonemapCurve (${tonemapCurvePoints.size / 2} pts) manualSensor=$manualSensor")
            } else {
                if (!manualSensor) {
                    b.set(CaptureRequest.CONTROL_MODE,    CameraMetadata.CONTROL_MODE_AUTO)
                    b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                }
                b.set(CaptureRequest.TONEMAP_MODE, tonemapMode)
            }
        }

        // Registra callback persistente para manter a curva viva frame a frame
        if (usingCurve) ensureTonemapCallback()

        Log.d(tag, "applyPostProcessing ok=$ok edge=$edgeMode nr=$noiseReductionMode tone=$tonemapMode hot=$hotPixelMode curve=$customTonemapCurve usingCurve=$usingCurve cbRegistered=$tonemapCallbackRegistered")
    }

    private fun applyManualSensor() {
        val safeDuration = maxOf(frameDurationNs, exposureNs)
        val ok = applyOnBuilder { b ->
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
        val ok = applyOnBuilder { b ->
            b.set(CaptureRequest.CONTROL_MODE,    CameraMetadata.CONTROL_MODE_AUTO)
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        }
        Log.d(tag, "applyAutoSensor ok=$ok")
    }

    private fun extractHistogram(result: TotalCaptureResult) {
        val brightness = (isoValue / 3200f * 0.5f + (exposureLevel + 8) / 16f * 0.5f).coerceIn(0f, 1f)
        val peak = (brightness * 255).toInt()
        for (i in 0 until 256) {
            val dist = kotlin.math.abs(i - peak).toFloat()
            val v = (255 * kotlin.math.exp((-dist * dist) / (2 * 30.0 * 30.0))).toInt()
            lastHistogramR[i] = (v * 1.1f).toInt().coerceIn(0, 255)
            lastHistogramG[i] = v
            lastHistogramB[i] = (v * 0.9f).toInt().coerceIn(0, 255)
        }
        histogramReady = true
    }

    // -------------------------------------------------------------------------

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
            val supManual  = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            val supPost    = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
            val supRaw     = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            val supBurst   = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)
            val supDepth   = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
            val supMulti   = if (android.os.Build.VERSION.SDK_INT >= 28)
                hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) else false
            val isoRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { listOf(it.lower, it.upper) }
            val expRange = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { listOf(it.lower, it.upper) }
            val evRange  = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { listOf(it.lower, it.upper) }
            val focRange = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let { listOf(0f, it) }
            val zomRange = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let { listOf(1.0f, it) }
            val fpsRanges = (ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: arrayOf())
                .map { listOf(it.lower, it.upper) }
            val streamCfg = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val resolutions = streamCfg
                ?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                ?.map { "${it.width}x${it.height}" }
                ?.distinct()
                ?.sortedBy { it.split("x").getOrNull(0)?.toIntOrNull() ?: 0 }
                ?: emptyList()
            val afModes = ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.map {
                when (it) {
                    CameraCharacteristics.CONTROL_AF_MODE_OFF                -> "off"
                    CameraCharacteristics.CONTROL_AF_MODE_MACRO              -> "macro"
                    CameraCharacteristics.CONTROL_AF_MODE_AUTO               -> "auto"
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
            val hasOis = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
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
            cameras.add(
                CameraCapabilities(
                    cameraId                     = id,
                    hardwareLevel                = hwLevel,
                    facing                       = facing,
                    name                         = name,
                    isDepth                      = isDepth,
                    supportsManualSensor         = supManual,
                    supportsManualPostProcessing = supPost,
                    supportsRaw                  = supRaw,
                    supportsBurstCapture         = supBurst,
                    supportsDepthOutput          = supDepth,
                    supportsLogicalMultiCamera   = supMulti,
                    isoRange                     = isoRange,
                    exposureTimeRange            = expRange,
                    evRange                      = evRange,
                    focusDistanceRange           = focRange,
                    zoomRange                    = zomRange,
                    fpsRanges                    = fpsRanges,
                    availableResolutions         = resolutions,
                    supportedAfModes             = afModes,
                    supportedAeModes             = aeModes,
                    supportedAwbModes            = awbModes,
                    hasFlash                     = hasFlash,
                    hasOis                       = hasOis,
                    focalLengths                 = focalLengths,
                    apertures                    = apertures
                )
            )
        }
        return cameras
    }

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) { Log.w(tag, "server nao inicializado"); return }

        params["manualSensor"]?.let {
            manualSensor = it as Boolean
            if (manualSensor) applyManualSensor()
            else { applyAutoSensor(); srv.setExposure(0); exposureLevel = 0 }
            Log.d(tag, "manualSensor -> $manualSensor")
        }

        params["iso"]?.let {
            isoValue = (it as Double).toInt()
            if (manualSensor) {
                applyManualSensor()
            } else {
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
            Log.d(tag, "focus -> norm=$norm dist=$focusDistance")
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
                srv.switchCamera(currentCameraId)
                tonemapCallbackRegistered = false // força re-registro após troca de câmera
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
                Log.d(tag, "resolution -> ${w}x${h} br=${br}kbps vOk=$vOk")
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
            applyPostProcessing()
            Log.d(tag, "edgeMode -> $it")
        }

        params["noiseReduction"]?.let {
            noiseReductionMode = when (it as String) {
                "off"          -> CameraMetadata.NOISE_REDUCTION_MODE_OFF
                "fast"         -> CameraMetadata.NOISE_REDUCTION_MODE_FAST
                "high_quality" -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
                "minimal"      -> CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
                else           -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            }
            applyPostProcessing()
            Log.d(tag, "noiseReduction -> $it")
        }

        params["tonemap"]?.let {
            tonemapMode = when (it as String) {
                "contrast_curve" -> CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
                "fast"           -> CameraMetadata.TONEMAP_MODE_FAST
                "high_quality"   -> CameraMetadata.TONEMAP_MODE_HIGH_QUALITY
                "gamma_value"    -> CameraMetadata.TONEMAP_MODE_GAMMA_VALUE
                else             -> CameraMetadata.TONEMAP_MODE_HIGH_QUALITY
            }
            applyPostProcessing()
            Log.d(tag, "tonemap -> $it")
        }

        params["hotPixel"]?.let {
            hotPixelMode = when (it as String) {
                "off"          -> CameraMetadata.HOT_PIXEL_MODE_OFF
                "fast"         -> CameraMetadata.HOT_PIXEL_MODE_FAST
                "high_quality" -> CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
                else           -> CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
            }
            applyPostProcessing()
            Log.d(tag, "hotPixel -> $it")
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
            val pts = raw as? List<*>
            if (pts == null || pts.isEmpty()) {
                Log.w(tag, "tonemapCurveCustom: payload vazio ou tipo errado")
                return@let
            }
            val doubles = try {
                pts.map { (it as Number).toDouble() }
            } catch (e: ClassCastException) {
                Log.w(tag, "tonemapCurveCustom: cast Double falhou: ${e.message}")
                return@let
            }
            val built = buildCustomCurve(doubles)
            if (built != null) {
                tonemapCurvePoints = built
                customTonemapCurve = "custom"
                tonemapMode = CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
                applyPostProcessing()
                Log.d(tag, "tonemapCurveCustom aplicado: ${built.size / 2} pontos")
            }
        }
    }

    private fun parseTimeParam(raw: Any, default: Long): Long = when (raw) {
        is String -> {
            val parts = raw.split("/")
            if (parts.size == 2) {
                val num = parts[0].trim().toDoubleOrNull() ?: 1.0
                val den = parts[1].trim().toDoubleOrNull() ?: 30.0
                ((num / den) * 1_000_000_000.0).toLong()
            } else default
        }
        is Double -> raw.toLong()
        is Long   -> raw
        else      -> default
    }

    fun release() {
        workerThread.quitSafely()
    }
}
