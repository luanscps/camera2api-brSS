package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Log
import com.pedro.library.rtmp.RtmpCamera2

class Camera2Controller {

    val tag = "Camera2Ctrl"

    // Referencia ao encoder RTMP ativo
    var rtmpCamera: RtmpCamera2? = null

    // Cache das capabilities da camara ativa (preenchido em open/switchCamera)
    private var caps: CameraCapabilities? = null

    // Contexto necessario para ler CameraManager (setado pelo Service)
    var appContext: Context? = null

    // -- Config de stream --
    var currentWidth    = 1920
    var currentHeight   = 1080
    var currentFps      = 30
    var currentBitrate  = 4000
    var currentCameraId = "0"

    // -- Estado dos controles --
    var zoomLevel        = 1f
    var autoFocus        = true
    var focusDistance    = 0f
    var whiteBalanceMode = "auto"
    var lanternEnabled   = false
    var isoValue         = 0        // 0 = AUTO
    var shutterNs        = 0L       // 0 = AUTO
    var oisEnabled       = false
    var eisEnabled       = false
    var exposureNs       = 0L
    var frameDurationNs  = 0L
    var manualSensor     = false
    var aeLocked         = false
    var awbLocked        = false
    var flashMode        = "off"
    var edgeMode         = CameraMetadata.EDGE_MODE_HIGH_QUALITY
    var noiseReductionMode = CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
    var hotPixelMode     = CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY

    // -- Cache de capabilities da camera ativa --------------------------------

    /** Chama isso sempre que a camera muda (switchCamera / attach). */
    fun loadCapabilities(cameraId: String) {
        currentCameraId = cameraId
        val ctx = appContext ?: return
        caps = CameraCapabilitiesReader.read(ctx, cameraId)
        Log.i(tag, "caps carregadas para id=$cameraId iso=${caps?.isoRange} zoom=${caps?.zoomRange}")
    }

    // -- Entry point chamado pela UI e WebGui ---------------------------------

    fun updateSettings(params: Map<String, Any>) {
        applyLocally(params)
        val cam = rtmpCamera ?: run {
            Log.w(tag, "rtmpCamera nulo — config guardada localmente")
            return
        }
        applyCameraParams(cam, params)
    }

    // -- Guarda estado local --------------------------------------------------

    private fun applyLocally(params: Map<String, Any>) {
        (params["width"]         as? Number)?.let { currentWidth       = it.toInt() }
        (params["height"]        as? Number)?.let { currentHeight      = it.toInt() }
        (params["fps"]           as? Number)?.let { currentFps         = it.toInt() }
        (params["bitrate"]       as? Number)?.let { currentBitrate     = it.toInt() }
        (params["camera"]        as? String)?.let { loadCapabilities(it) }
        params["zoom"]?.let           { zoomLevel        = parseZoom(it) }
        params["iso"]?.let            { isoValue         = parseIso(it) }
        params["shutterSpeed"]?.let   { shutterNs        = parseShutter(it) }
        (params["ois"]           as? Boolean)?.let { oisEnabled        = it }
        (params["eis"]           as? Boolean)?.let { eisEnabled        = it }
        (params["lantern"]       as? Boolean)?.let { lanternEnabled    = it }
        (params["whiteBalance"]  as? String)?.let  { whiteBalanceMode  = it }
        (params["exposureNs"]    as? Number)?.let  { exposureNs        = it.toLong() }
        (params["frameDuration"] as? Number)?.let  { frameDurationNs   = it.toLong() }
        (params["manualSensor"]  as? Boolean)?.let { manualSensor      = it }
        (params["aeLock"]        as? Boolean)?.let { aeLocked          = it }
        (params["awbLock"]       as? Boolean)?.let { awbLocked         = it }
        (params["flashMode"]     as? String)?.let  { flashMode         = it }
        (params["edgeMode"]      as? Number)?.let  { edgeMode          = it.toInt() }
        (params["noiseReduction"]as? Number)?.let  { noiseReductionMode = it.toInt() }
        (params["hotPixel"]      as? Number)?.let  { hotPixelMode      = it.toInt() }
    }

    // -- Aplica na camera -----------------------------------------------------

    private fun applyCameraParams(cam: RtmpCamera2, params: Map<String, Any>) {
        try {

            // ---- ZOOM -------------------------------------------------------
            params["zoom"]?.let {
                val z = parseZoom(it).coerceAtLeast(1f)
                cam.setZoom(z)
                zoomLevel = z
                Log.d(tag, "zoom -> $z")
            }

            // ---- ISO --------------------------------------------------------
            // Aceita: Number (100), String ("100"), String ("AUTO" / "0")
            params["iso"]?.let {
                val iso = parseIso(it)
                if (iso <= 0) {
                    // AUTO: desliga manual sensor se estava ligado via ISO
                    cam.setExposure(0)
                    manualSensor = false
                    Log.d(tag, "iso -> AUTO")
                } else {
                    // Usa EV compensation como proxy quando sensor manual
                    // nao e suportado; quando suportado aplica diretamente.
                    if (caps?.supportsManualSensor == true) {
                        cam.setExposure(isoToEv(iso))
                    } else {
                        cam.setExposure(isoToEv(iso))
                    }
                    isoValue = iso
                    Log.d(tag, "iso -> $iso (ev=${isoToEv(iso)})")
                }
            }

            // ---- SHUTTER SPEED ----------------------------------------------
            // Aceita: "1/1000", "1/500", "AUTO", ou Long (nanosegundos)
            params["shutterSpeed"]?.let {
                val ns = parseShutter(it)
                if (ns <= 0L) {
                    // AUTO — reabilita AE automatico
                    cam.setExposure(0)
                    manualSensor = false
                    Log.d(tag, "shutterSpeed -> AUTO")
                } else {
                    // Converte para EV aproximado para cameras sem MANUAL_SENSOR
                    // (cameras com suporte real usarao CaptureRequest direto)
                    val evCompensation = shutterNsToEv(ns)
                    cam.setExposure(evCompensation)
                    shutterNs = ns
                    Log.d(tag, "shutterSpeed -> ${ns}ns (ev=$evCompensation)")
                }
            }

            // ---- LANTERNA ---------------------------------------------------
            (params["lantern"] as? Boolean)?.let { on ->
                try {
                    if (on) cam.enableLantern() else cam.disableLantern()
                    lanternEnabled = on
                    Log.d(tag, "lantern -> $on")
                } catch (e: Exception) { Log.w(tag, "lanterna nao suportada: ${e.message}") }
            }

            // ---- OIS --------------------------------------------------------
            (params["ois"] as? Boolean)?.let { on ->
                try {
                    if (on) cam.enableOpticalVideoStabilization()
                    else    cam.disableOpticalVideoStabilization()
                    oisEnabled = on
                    Log.d(tag, "OIS -> $on")
                } catch (e: Exception) { Log.w(tag, "OIS nao suportado: ${e.message}") }
            }

            // ---- EIS --------------------------------------------------------
            (params["eis"] as? Boolean)?.let { on ->
                try {
                    if (on) cam.enableVideoStabilization()
                    else    cam.disableVideoStabilization()
                    eisEnabled = on
                    Log.d(tag, "EIS -> $on")
                } catch (e: Exception) { Log.w(tag, "EIS nao suportado: ${e.message}") }
            }

            // ---- FOCO -------------------------------------------------------
            // Aceita: "AUTO", Number (0.0–1.0), String numerica ("0.5")
            params["focus"]?.let {
                val focusStr = it.toString().trim()
                if (focusStr.equals("AUTO", ignoreCase = true) || focusStr == "0" || focusStr == "0.0") {
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

            // ---- WHITE BALANCE ----------------------------------------------
            // Aceita os valores do dial ("2700K","3200K","4200K","5600K",
            // "6500K","7500K","AUTO") e os da WebGui ("daylight","cloudy", etc.)
            (params["whiteBalance"] as? String)?.let { wb ->
                val mode = wbStringToMode(wb)
                cam.enableAutoWhiteBalance(mode)
                whiteBalanceMode = wb
                Log.d(tag, "WB -> $wb (mode=$mode)")
            }

            // ---- CAMERA SWITCH ----------------------------------------------
            (params["camera"] as? String)?.let { id ->
                try {
                    cam.switchCamera(id)
                    loadCapabilities(id)
                    Log.d(tag, "switchCamera -> $id")
                } catch (e: Exception) { Log.w(tag, "switchCamera falhou: ${e.message}") }
            }

            // ---- BITRATE ----------------------------------------------------
            (params["bitrate"] as? Number)?.let {
                val kbps = it.toInt()
                if (cam.isStreaming) cam.setVideoBitrateOnFly(kbps * 1024)
                currentBitrate = kbps
                Log.d(tag, "bitrate -> ${kbps}kbps")
            }

            // ---- EXPOSURE (EV compensation direto) --------------------------
            (params["exposure"] as? Number)?.let {
                cam.setExposure(it.toInt())
                Log.d(tag, "exposure EV -> ${it.toInt()}")
            }

        } catch (e: Exception) {
            Log.e(tag, "Erro ao aplicar params", e)
        }
    }

    // -- Parsers auxiliares ---------------------------------------------------

    /**
     * Converte zoom para Float.
     * Aceita: Number, "1x", "1.5x", "2x", "150" (percent-style da WebGui)
     */
    private fun parseZoom(v: Any): Float {
        if (v is Number) return v.toFloat().coerceAtLeast(1f)
        val s = v.toString().trim()
        // "1.5x" ou "1.5X"
        if (s.endsWith("x", ignoreCase = true))
            return s.dropLast(1).toFloatOrNull()?.coerceAtLeast(1f) ?: 1f
        // Percent-style: "150" -> 1.5x  (WebGui manda 100-800)
        val n = s.toFloatOrNull() ?: return 1f
        return if (n >= 100f) (n / 100f).coerceAtLeast(1f) else n.coerceAtLeast(1f)
    }

    /**
     * Converte ISO para Int.
     * Aceita: Number, "100", "AUTO" -> 0
     */
    private fun parseIso(v: Any): Int {
        if (v is Number) return v.toInt()
        val s = v.toString().trim()
        if (s.equals("AUTO", ignoreCase = true)) return 0
        return s.toIntOrNull() ?: 0
    }

    /**
     * Converte shutter speed para nanosegundos.
     * Aceita: Long (ns), "1/1000", "1/500", "AUTO" -> 0
     */
    private fun parseShutter(v: Any): Long {
        if (v is Number) return v.toLong()
        val s = v.toString().trim()
        if (s.equals("AUTO", ignoreCase = true)) return 0L
        // Fracao: "1/1000"
        if (s.contains("/")) {
            val parts = s.split("/")
            val num = parts[0].toDoubleOrNull() ?: return 0L
            val den = parts[1].toDoubleOrNull() ?: return 0L
            if (den == 0.0) return 0L
            return ((num / den) * 1_000_000_000.0).toLong()
        }
        return s.toLongOrNull() ?: 0L
    }

    /**
     * Mapeia string de WB para CameraMetadata.CONTROL_AWB_MODE_*
     * Aceita nomes do dial ("2700K","3200K","4200K","5600K","6500K","7500K")
     * e nomes descritivos ("daylight","cloudy","incandescent","fluorescent").
     */
    private fun wbStringToMode(wb: String): Int = when (wb.uppercase().trim()) {
        "AUTO"                   -> CameraMetadata.CONTROL_AWB_MODE_AUTO
        "2700K", "INCANDESCENT",
        "TUNGSTEN", "WARM"       -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
        "3200K", "FLUORESCENT"   -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
        "4200K", "COOL_WHITE",
        "COOL-WHITE"             -> CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT
        "5600K", "DAYLIGHT",
        "SUN", "SUNNY"           -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
        "6500K", "CLOUDY",
        "CLOUDY_DAYLIGHT",
        "CLOUDY-DAYLIGHT"        -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
        "7500K", "SHADE"         -> CameraMetadata.CONTROL_AWB_MODE_SHADE
        "TWILIGHT"               -> CameraMetadata.CONTROL_AWB_MODE_TWILIGHT
        else                     -> CameraMetadata.CONTROL_AWB_MODE_AUTO
    }

    /**
     * Converte ISO para EV compensation aproximado.
     * ISO 100 = EV 0, cada dobro de ISO = +1 EV.
     */
    private fun isoToEv(iso: Int): Int {
        if (iso <= 0) return 0
        return Math.log(iso / 100.0).div(Math.log(2.0)).toInt().coerceIn(-8, 8)
    }

    /**
     * Converte tempo de exposicao (ns) para EV compensation aproximado.
     * Referencia: 1/60s = 16.7ms = 16_700_000ns = EV 0 para video 60fps.
     */
    private fun shutterNsToEv(ns: Long): Int {
        if (ns <= 0L) return 0
        val refNs = 16_666_667L // 1/60s
        return Math.log(ns.toDouble() / refNs).div(Math.log(2.0)).toInt().coerceIn(-8, 8)
    }

    // -- Lifecycle ------------------------------------------------------------

    fun release() { rtmpCamera = null; caps = null }

    // -- Discovery para WebGui ------------------------------------------------

    fun discoverAllCameras(context: Context): List<Map<String, Any>> {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return mgr.cameraIdList.mapNotNull { id ->
            try {
                val chars   = mgr.getCameraCharacteristics(id)
                val facing  = chars.get(CameraCharacteristics.LENS_FACING)
                val caps    = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return@mapNotNull null

                // Ignora depth-only
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) &&
                    !caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE))
                    return@mapNotNull null

                val focalLen = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 4f
                val name = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Frontal"
                    CameraCharacteristics.LENS_FACING_BACK  -> when {
                        focalLen < 2.5f -> "Ultra Wide"
                        focalLen > 6f   -> "Tele"
                        else            -> "Wide"
                    }
                    else -> "Camera $id"
                }

                val maxZoom  = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val hasOis   = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
                val evRange  = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                    ?.map { awbModeToString(it) } ?: listOf("auto")
                val afModes  = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    ?.map { afModeToString(it) } ?: listOf("auto")
                val resolutions = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                    ?.filter { it.width >= 640 }
                    ?.sortedByDescending { it.width * it.height }
                    ?.map { "${it.width}x${it.height}" } ?: emptyList()

                mapOf(
                    "camera_id"    to id,
                    "name"         to name,
                    "facing"       to (if (facing == CameraCharacteristics.LENS_FACING_FRONT) "front" else "back"),
                    "has_flash"    to hasFlash,
                    "has_ois"      to hasOis,
                    "zoom_range"   to listOf(1f, maxZoom),
                    "ev_range"     to listOf(evRange?.lower ?: -8, evRange?.upper ?: 8),
                    "iso_range"    to if (isoRange != null) listOf(isoRange.lower, isoRange.upper) else null,
                    "exposure_range_ns" to if (expRange != null) listOf(expRange.lower, expRange.upper) else null,
                    "awb_modes"    to awbModes,
                    "af_modes"     to afModes,
                    "focal_length" to focalLen,
                    "resolutions"  to resolutions
                )
            } catch (e: Exception) { Log.w(tag, "discoverAllCameras erro id=$id: ${e.message}"); null }
        }
    }

    private fun awbModeToString(mode: Int) = when (mode) {
        CameraMetadata.CONTROL_AWB_MODE_AUTO             -> "auto"
        CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT     -> "incandescent"
        CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT      -> "fluorescent"
        CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "warm_fluorescent"
        CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT         -> "daylight"
        CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT  -> "cloudy"
        CameraMetadata.CONTROL_AWB_MODE_TWILIGHT         -> "twilight"
        CameraMetadata.CONTROL_AWB_MODE_SHADE            -> "shade"
        else -> "auto"
    }

    private fun afModeToString(mode: Int) = when (mode) {
        CameraMetadata.CONTROL_AF_MODE_OFF               -> "off"
        CameraMetadata.CONTROL_AF_MODE_AUTO              -> "auto"
        CameraMetadata.CONTROL_AF_MODE_MACRO             -> "macro"
        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO  -> "continuous-video"
        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE-> "continuous-picture"
        else -> "auto"
    }
}
