package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.library.base.Camera2Base
import com.pedro.library.rtmp.RtmpCamera2
import kotlin.math.ln

class Camera2Controller {

    val tag = "Camera2Ctrl"

    // Referencia ao encoder RTMP ativo (setada pelo RtmpStreamer)
    var rtmpCamera: RtmpCamera2? = null
    var appContext: Context? = null

    // Config de stream
    var currentWidth    = 1920
    var currentHeight   = 1080
    var currentFps      = 30
    var currentBitrate  = 4000
    var currentCameraId = "0"

    // Estado dos controles
    var zoomLevel        = 1f
    var autoFocus        = true
    var focusDistance    = 0f
    var whiteBalanceMode = "auto"
    var lanternEnabled   = false
    var isoValue         = 0        // 0 = AUTO
    var shutterNs        = 0L       // 0 = AUTO
    var oisEnabled       = false
    var eisEnabled       = false
    var exposureNs       = 33_333_333L
    var frameDurationNs  = 33_333_333L
    var manualSensor     = false
    var aeLocked         = false
    var awbLocked        = false
    var flashMode        = "off"
    var edgeMode           = CameraMetadata.EDGE_MODE_HIGH_QUALITY
    var noiseReductionMode = CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
    var hotPixelMode       = CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
    var exposureLevel      = 0

    // Worker thread para operacoes de camera que nao podem ser no main thread
    private val workerThread = HandlerThread("CameraWorker").also { it.start() }
    private val worker = Handler(workerThread.looper)
    private fun post(block: () -> Unit) =
        worker.post { runCatching(block).onFailure { Log.e(tag, "worker err", it) } }

    // =========================================================================
    // REFLECTION — acesso direto ao CaptureRequest.Builder do RtmpCamera2
    // Mesma tecnica usada na v3 com RtspServerCamera2, funciona pois ambos
    // herdam de Camera2Base e usam Camera2ApiManager internamente.
    // =========================================================================

    private fun getCam2Manager(): Camera2ApiManager? = try {
        val f = Camera2Base::class.java.getDeclaredField("cameraManager")
        f.isAccessible = true
        f.get(rtmpCamera) as? Camera2ApiManager
    } catch (e: Exception) {
        Log.e(tag, "getCam2Manager falhou: ${e.message}")
        null
    }

    /**
     * Aplica um bloco de configuracoes diretamente no CaptureRequest.Builder
     * ativo do Camera2ApiManager e re-submete a repeating request.
     * Retorna true se aplicado com sucesso.
     */
    private fun applyOnBuilder(block: (CaptureRequest.Builder) -> Unit): Boolean {
        val cam = getCam2Manager() ?: return false
        return try {
            // builderInputSurface e o builder da repeating request de preview/stream
            val bf = Camera2ApiManager::class.java.getDeclaredField("builderInputSurface")
            bf.isAccessible = true
            val builder = bf.get(cam) as? CaptureRequest.Builder
                ?: run { Log.w(tag, "builderInputSurface nulo"); return false }
            block(builder)
            // applyRequest re-submete a repeating request com os novos parametros
            val am = Camera2ApiManager::class.java.getDeclaredMethod(
                "applyRequest", CaptureRequest.Builder::class.java
            )
            am.isAccessible = true
            am.invoke(cam, builder) as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(tag, "applyOnBuilder falhou: ${e.message}")
            false
        }
    }

    // =========================================================================
    // Blocos de aplicacao de sensor
    // =========================================================================

    /** Modo manual: seta ISO + tempo de exposicao diretamente no sensor */
    private fun applyManualSensor() {
        val safeDuration = maxOf(frameDurationNs, exposureNs)
        val ok = applyOnBuilder { b ->
            b.set(CaptureRequest.CONTROL_MODE,          CameraMetadata.CONTROL_MODE_OFF)
            b.set(CaptureRequest.CONTROL_AE_MODE,       CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY,    isoValue)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME,  exposureNs)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, safeDuration)
        }
        val fps = if (safeDuration > 0) (1_000_000_000.0 / safeDuration).toInt() else 0
        Log.d(tag, "manualSensor ok=$ok ISO=$isoValue exp=${exposureNs}ns dur=${safeDuration}ns fpsMax=$fps")
        applyPostProcessing()
    }

    /** Volta ao AE automatico */
    private fun applyAutoSensor() {
        val ok = applyOnBuilder { b ->
            b.set(CaptureRequest.CONTROL_MODE,    CameraMetadata.CONTROL_MODE_AUTO)
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        }
        Log.d(tag, "autoSensor ok=$ok")
    }

    /** Aplica Edge / NR / HotPixel / Tonemap (independente do modo sensor) */
    private fun applyPostProcessing() {
        val ok = applyOnBuilder { b ->
            b.set(CaptureRequest.EDGE_MODE,            edgeMode)
            b.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode)
            b.set(CaptureRequest.HOT_PIXEL_MODE,       hotPixelMode)
            b.set(CaptureRequest.TONEMAP_MODE,         CameraMetadata.TONEMAP_MODE_HIGH_QUALITY)
        }
        Log.d(tag, "postProcessing ok=$ok edge=$edgeMode nr=$noiseReductionMode hot=$hotPixelMode")
    }

    /** Aplica White Balance via CaptureRequest diretamente */
    private fun applyWhiteBalance(mode: Int) {
        val ok = applyOnBuilder { b ->
            b.set(CaptureRequest.CONTROL_AWB_MODE, mode)
        }
        Log.d(tag, "WB applyOnBuilder ok=$ok mode=$mode")
    }

    // =========================================================================
    // Entry point principal
    // =========================================================================

    fun updateSettings(params: Map<String, Any>) {
        val cam = rtmpCamera ?: run {
            Log.w(tag, "rtmpCamera nulo - guardando estado localmente")
            applyLocally(params)
            return
        }
        applyLocally(params)
        applyCameraParams(cam, params)
    }

    // Guarda estado local (usado para restaurar apos troca de camera)
    private fun applyLocally(params: Map<String, Any>) {
        (params["width"]         as? Number)?.let { currentWidth        = it.toInt() }
        (params["height"]        as? Number)?.let { currentHeight       = it.toInt() }
        (params["fps"]           as? Number)?.let { currentFps          = it.toInt() }
        (params["bitrate"]       as? Number)?.let { currentBitrate      = it.toInt() }
        (params["camera"]        as? String)?.let { currentCameraId     = it }
        params["zoom"]?.let          { zoomLevel        = parseZoom(it) }
        params["iso"]?.let           { isoValue         = parseIso(it) }
        params["shutterSpeed"]?.let  { shutterNs        = parseShutter(it) }
        (params["ois"]          as? Boolean)?.let { oisEnabled       = it }
        (params["eis"]          as? Boolean)?.let { eisEnabled       = it }
        (params["lantern"]      as? Boolean)?.let { lanternEnabled   = it }
        (params["whiteBalance"] as? String)?.let  { whiteBalanceMode = it }
        (params["manualSensor"] as? Boolean)?.let { manualSensor     = it }
        (params["aeLock"]       as? Boolean)?.let { aeLocked         = it }
        (params["awbLock"]      as? Boolean)?.let { awbLocked        = it }
        (params["flashMode"]    as? String)?.let  { flashMode        = it }
    }

    // Aplica cada parametro no hardware da camera
    private fun applyCameraParams(cam: RtmpCamera2, params: Map<String, Any>) {

        // ---- MODO SENSOR MANUAL / AUTO --------------------------------------
        // Deve ser processado ANTES de ISO e shutterSpeed
        (params["manualSensor"] as? Boolean)?.let { manual ->
            manualSensor = manual
            if (manual) applyManualSensor()
            else {
                applyAutoSensor()
                cam.setExposure(0)
                exposureLevel = 0
            }
            Log.d(tag, "manualSensor -> $manual")
        }

        // ---- ISO ------------------------------------------------------------
        // Se manualSensor=true: seta SENSOR_SENSITIVITY direto via reflection
        // Se manualSensor=false: usa EV compensation como proxy
        params["iso"]?.let {
            val iso = parseIso(it)
            isoValue = if (iso <= 0) 100 else iso
            if (manualSensor) {
                applyManualSensor()
            } else {
                // Mapeia ISO para EV no range real da camera
                val minEv = cam.minExposure
                val maxEv = cam.maxExposure
                val ev = (((isoValue - 50f) / (3200f - 50f)) * (maxEv - minEv) + minEv)
                    .toInt().coerceIn(minEv, maxEv)
                exposureLevel = ev
                cam.setExposure(ev)
            }
            Log.d(tag, "ISO -> $isoValue manual=$manualSensor")
        }

        // ---- SHUTTER SPEED --------------------------------------------------
        params["shutterSpeed"]?.let {
            val ns = parseShutter(it)
            if (ns > 0L) {
                exposureNs = ns
                if (manualSensor) applyManualSensor()
            }
            Log.d(tag, "shutterSpeed -> ${if (ns <= 0L) "AUTO" else "${ns}ns"}")
        }

        // ---- FRAME DURATION -------------------------------------------------
        (params["frameDuration"] as? Number)?.let {
            frameDurationNs = it.toLong()
            if (manualSensor) applyManualSensor()
        }

        // ---- EV DIRETO (apenas no modo auto) --------------------------------
        (params["exposure"] as? Number)?.let {
            if (!manualSensor) {
                val ev = it.toInt().coerceIn(cam.minExposure, cam.maxExposure)
                exposureLevel = ev
                cam.setExposure(ev)
                Log.d(tag, "exposure EV -> $ev")
            }
        }

        // ---- ZOOM -----------------------------------------------------------
        // zoomRange da camera (Camera2 API: lower=1f, upper=maxDigitalZoom)
        params["zoom"]?.let {
            val z = parseZoom(it)
            val zr = cam.zoomRange
            val realZoom = z.coerceIn(zr.lower, zr.upper)
            cam.setZoom(realZoom)
            zoomLevel = realZoom
            Log.d(tag, "zoom -> $realZoom (range ${zr.lower}..${zr.upper})")
        }

        // ---- FOCO -----------------------------------------------------------
        params["focus"]?.let {
            val focusStr = it.toString().trim()
            if (focusStr.equals("AUTO", ignoreCase = true) ||
                focusStr == "0" || focusStr == "0.0") {
                cam.enableAutoFocus()
                autoFocus = true
                focusDistance = 0f
                Log.d(tag, "focus -> AUTO")
            } else {
                val d = focusStr.toFloatOrNull() ?: 0f
                if (d <= 0f) {
                    cam.enableAutoFocus()
                    autoFocus = true
                    focusDistance = 0f
                } else {
                    cam.disableAutoFocus()
                    cam.setFocusDistance(d)
                    autoFocus = false
                    focusDistance = d
                    Log.d(tag, "focus -> $d")
                }
            }
        }

        // ---- FOCUSMODE (para compatibilidade com WebGui) --------------------
        (params["focusmode"] as? String)?.let {
            when (it) {
                "continuous-video", "continuous-picture", "auto" -> {
                    cam.enableAutoFocus()
                    autoFocus = true
                    focusDistance = 0f
                }
                "off" -> cam.disableAutoFocus()
            }
            Log.d(tag, "focusMode -> $it")
        }

        // ---- WHITE BALANCE --------------------------------------------------
        // Usa applyOnBuilder para setar CONTROL_AWB_MODE diretamente
        (params["whiteBalance"] as? String)?.let { wb ->
            val mode = wbStringToMode(wb)
            applyWhiteBalance(mode)
            // Fallback: tambem chama o metodo publico da lib
            try { cam.enableAutoWhiteBalance(mode) } catch (_: Exception) {}
            whiteBalanceMode = wb
            Log.d(tag, "WB -> $wb mode=$mode")
        }

        // ---- LANTERNA -------------------------------------------------------
        (params["lantern"] as? Boolean)?.let { on ->
            try {
                if (on) cam.enableLantern() else cam.disableLantern()
                lanternEnabled = on
                Log.d(tag, "lantern -> $on")
            } catch (ex: Exception) { Log.w(tag, "lantern: ${ex.message}") }
        }

        // ---- OIS ------------------------------------------------------------
        (params["ois"] as? Boolean)?.let { on ->
            try {
                if (on) cam.enableOpticalVideoStabilization()
                else    cam.disableOpticalVideoStabilization()
                oisEnabled = on
                // Aplica tambem via CaptureRequest para garantir
                val oisMode = if (on)
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                else
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                applyOnBuilder { b ->
                    b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, oisMode)
                }
                Log.d(tag, "OIS -> $on")
            } catch (ex: Exception) { Log.w(tag, "OIS: ${ex.message}") }
        }

        // ---- EIS ------------------------------------------------------------
        (params["eis"] as? Boolean)?.let { on ->
            try {
                if (on) cam.enableVideoStabilization()
                else    cam.disableVideoStabilization()
                eisEnabled = on
                // Aplica tambem via CaptureRequest para garantir
                val eisMode = if (on)
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                applyOnBuilder { b ->
                    b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, eisMode)
                }
                Log.d(tag, "EIS -> $on")
            } catch (ex: Exception) { Log.w(tag, "EIS: ${ex.message}") }
        }

        // ---- AE LOCK --------------------------------------------------------
        (params["aeLock"] as? Boolean)?.let { lock ->
            aeLocked = lock
            try {
                if (lock) cam.disableAutoExposure() else cam.enableAutoExposure()
            } catch (_: Exception) {
                applyOnBuilder { b ->
                    b.set(CaptureRequest.CONTROL_AE_LOCK, lock)
                }
            }
            Log.d(tag, "aeLock -> $lock")
        }

        // ---- AWB LOCK -------------------------------------------------------
        (params["awbLock"] as? Boolean)?.let { lock ->
            awbLocked = lock
            try {
                if (lock) cam.disableAutoWhiteBalance()
                else cam.enableAutoWhiteBalance(CameraMetadata.CONTROL_AWB_MODE_AUTO)
            } catch (_: Exception) {
                applyOnBuilder { b ->
                    b.set(CaptureRequest.CONTROL_AWB_LOCK, lock)
                }
            }
            Log.d(tag, "awbLock -> $lock")
        }

        // ---- FLASH MODE -----------------------------------------------------
        (params["flashMode"] as? String)?.let { fm ->
            flashMode = fm
            when (fm) {
                "torch" -> try { cam.enableLantern(); lanternEnabled = true } catch (_: Exception) {}
                "single" -> applyOnBuilder { b ->
                    b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE)
                }
                else -> try { cam.disableLantern(); lanternEnabled = false } catch (_: Exception) {}
            }
            Log.d(tag, "flashMode -> $fm")
        }

        // ---- BITRATE --------------------------------------------------------
        (params["bitrate"] as? Number)?.let {
            val kbps = it.toInt()
            if (cam.isStreaming) cam.setVideoBitrateOnFly(kbps * 1024)
            currentBitrate = kbps
            Log.d(tag, "bitrate -> ${kbps}kbps")
        }

        // ---- FPS (requer reiniciar encoder) ---------------------------------
        (params["fps"] as? Number)?.let { value ->
            val fps = value.toInt().coerceIn(15, 60)
            currentFps = fps
            if (!manualSensor) frameDurationNs = 1_000_000_000L / fps
            post {
                val wasStreaming = cam.isStreaming
                val url = StreamingService.instance?.rtmpUrl ?: ""
                if (wasStreaming) cam.stopStream()
                val vOk = cam.prepareVideo(
                    currentWidth, currentHeight, fps, currentBitrate * 1024, 0
                )
                val aOk = cam.prepareAudio(128 * 1024, 44100, true)
                if (wasStreaming && vOk && aOk) cam.startStream(url)
                Log.d(tag, "fps -> $fps vOk=$vOk")
            }
        }

        // ---- RESOLUCAO ------------------------------------------------------
        (params["resolution"] as? String)?.let { value ->
            val (w, h, br) = when (value) {
                "4k", "3840x2160" -> Triple(3840, 2160, 20000)
                "1080p", "1920x1080" -> Triple(1920, 1080, 8000)
                "720p", "1280x720" -> Triple(1280, 720, 4000)
                else -> {
                    val parts = value.split("x")
                    val pw = parts.getOrNull(0)?.toIntOrNull() ?: 1920
                    val ph = parts.getOrNull(1)?.toIntOrNull() ?: 1080
                    Triple(pw, ph, currentBitrate)
                }
            }
            currentWidth = w; currentHeight = h; currentBitrate = br
            post {
                val wasStreaming = cam.isStreaming
                val url = StreamingService.instance?.rtmpUrl ?: ""
                if (wasStreaming) cam.stopStream()
                val vOk = cam.prepareVideo(w, h, currentFps, br * 1024, 0)
                val aOk = cam.prepareAudio(128 * 1024, 44100, true)
                if (wasStreaming && vOk && aOk) cam.startStream(url)
                Log.d(tag, "resolution -> ${w}x${h} vOk=$vOk")
            }
        }

        // ---- TROCA DE CAMERA ------------------------------------------------
        (params["camera"] as? String)?.let { id ->
            post {
                try {
                    cam.switchCamera(id)
                    currentCameraId = id
                    // Restaura estado apos troca
                    if (manualSensor) applyManualSensor()
                    else if (!autoFocus && focusDistance > 0f) cam.setFocusDistance(focusDistance)
                    Log.d(tag, "switchCamera -> $id")
                } catch (ex: Exception) { Log.w(tag, "switchCamera: ${ex.message}") }
            }
        }

        // ---- EDGE MODE ------------------------------------------------------
        (params["edgeMode"] as? String)?.let {
            edgeMode = when (it) {
                "off"  -> CameraMetadata.EDGE_MODE_OFF
                "fast" -> CameraMetadata.EDGE_MODE_FAST
                else   -> CameraMetadata.EDGE_MODE_HIGH_QUALITY
            }
            applyPostProcessing()
            Log.d(tag, "edgeMode -> $it")
        }

        // ---- NOISE REDUCTION ------------------------------------------------
        (params["noiseReduction"] as? String)?.let {
            noiseReductionMode = when (it) {
                "off"     -> CameraMetadata.NOISE_REDUCTION_MODE_OFF
                "fast"    -> CameraMetadata.NOISE_REDUCTION_MODE_FAST
                "minimal" -> CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
                else      -> CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            }
            applyPostProcessing()
            Log.d(tag, "noiseReduction -> $it")
        }

        // ---- HOT PIXEL ------------------------------------------------------
        (params["hotPixel"] as? String)?.let {
            hotPixelMode = when (it) {
                "off"  -> CameraMetadata.HOT_PIXEL_MODE_OFF
                "fast" -> CameraMetadata.HOT_PIXEL_MODE_FAST
                else   -> CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY
            }
            applyPostProcessing()
            Log.d(tag, "hotPixel -> $it")
        }
    }

    // =========================================================================
    // Parsers
    // =========================================================================

    /** Aceita: Number, "1.5x", "1.5X", "150" (percent-style WebGui 100-800) */
    private fun parseZoom(v: Any): Float {
        if (v is Number) return v.toFloat().coerceAtLeast(1f)
        val s = v.toString().trim()
        if (s.endsWith("x", ignoreCase = true))
            return s.dropLast(1).toFloatOrNull()?.coerceAtLeast(1f) ?: 1f
        val n = s.toFloatOrNull() ?: return 1f
        return if (n >= 100f) (n / 100f).coerceAtLeast(1f) else n.coerceAtLeast(1f)
    }

    /** Aceita: Number, "100", "AUTO" -> 0 */
    private fun parseIso(v: Any): Int {
        if (v is Number) return v.toInt()
        val s = v.toString().trim()
        if (s.equals("AUTO", ignoreCase = true)) return 0
        return s.toIntOrNull() ?: 0
    }

    /** Aceita: Long (ns), "1/1000", "1/500", "AUTO" -> 0 */
    private fun parseShutter(v: Any): Long {
        if (v is Number) return v.toLong()
        val s = v.toString().trim()
        if (s.equals("AUTO", ignoreCase = true)) return 0L
        if (s.contains("/")) {
            val parts = s.split("/")
            val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return 0L
            val den = parts.getOrNull(1)?.toDoubleOrNull() ?: return 0L
            if (den == 0.0) return 0L
            return ((num / den) * 1_000_000_000.0).toLong()
        }
        return s.toLongOrNull() ?: 0L
    }

    /**
     * Mapeia string de WB para CONTROL_AWB_MODE_*
     * Aceita nomes do dial Kelvin ("2700K"…"7500K") e nomes descritivos.
     */
    private fun wbStringToMode(wb: String): Int = when (wb.uppercase().trim()) {
        "AUTO"                               -> CameraMetadata.CONTROL_AWB_MODE_AUTO
        "2700K", "INCANDESCENT",
        "TUNGSTEN", "WARM"                   -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
        "3200K", "FLUORESCENT"               -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
        "4200K", "WARM_FLUORESCENT",
        "COOL_WHITE", "COOL-WHITE",
        "WARM-FLUORESCENT"                   -> CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT
        "5600K", "DAYLIGHT",
        "SUN", "SUNNY"                       -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
        "6500K", "CLOUDY",
        "CLOUDY_DAYLIGHT", "CLOUDY-DAYLIGHT" -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
        "7500K", "SHADE"                     -> CameraMetadata.CONTROL_AWB_MODE_SHADE
        "TWILIGHT"                           -> CameraMetadata.CONTROL_AWB_MODE_TWILIGHT
        else                                 -> CameraMetadata.CONTROL_AWB_MODE_AUTO
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    fun release() {
        workerThread.quitSafely()
        rtmpCamera = null
    }

    // =========================================================================
    // Discovery de cameras (para WebGui /api/capabilities)
    // =========================================================================

    fun discoverAllCameras(context: Context): List<CameraCapabilities> {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameras = mutableListOf<CameraCapabilities>()
        for (id in mgr.cameraIdList) {
            val entry = readCameraCapabilities(mgr, id) ?: continue
            cameras.add(entry)
        }
        return cameras
    }

    private fun readCameraCapabilities(mgr: CameraManager, id: String): CameraCapabilities? {
        return try {
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
            val supMulti   = hasCap(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
            val isoRange   = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { listOf(it.lower, it.upper) }
            val expRange   = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { listOf(it.lower, it.upper) }
            val evRange    = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let { listOf(it.lower, it.upper) }
            val focRange   = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let { listOf(0f, it) }
            val zomRange   = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let { listOf(1.0f, it) }
            val fpsRanges  = (ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: arrayOf())
                .map { listOf(it.lower, it.upper) }
            val streamCfg  = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val resolutions = streamCfg?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                ?.map { "${it.width}x${it.height}" }?.distinct()
                ?.sortedByDescending { it.split("x").getOrNull(0)?.toIntOrNull() ?: 0 } ?: emptyList()
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
            CameraCapabilities(
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
            )
        } catch (e: Exception) {
            Log.w(tag, "readCameraCapabilities id=$id: ${e.message}")
            null
        }
    }
}
