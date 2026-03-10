package com.camera2rtsp

import android.hardware.camera2.CameraMetadata
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

class Camera2Controller {

    private val TAG = "Camera2Controller"

    var server: RtspServerCamera2? = null

    // Estado atual — exposto para /api/status
    var currentCameraId  = "0"
    var exposureLevel    = 0
    var whiteBalanceMode = "auto"
    var autoFocus        = true
    var zoomLevel        = 1.0f
    var lanternEnabled   = false
    var oisEnabled       = false

    // Configuração de vídeo
    // Defaults ajustados com base no logcat:
    //   - VQApply eleva bitrate mínimo de 2.5M para 3M no Exynos → usamos 4M para dar margem
    //   - CBR não suportado no OMX.Exynos.AVC.Encoder, fica em VBR automaticamente
    //   - 1080p é o sweet spot para H264/Exynos no Note10+ (menor latência que 4K)
    //   - 4K funciona mas gera ~32MB bruto; para RTSP/IP usar 4K com bitrate ≥ 20Mbps
    var currentWidth    = 1920
    var currentHeight   = 1080
    var currentBitrate  = 4000   // kbps — base segura acima do threshold do VQApply
    var currentFps      = 30

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) {
            Log.w(TAG, "server ainda nao inicializado, ignorando")
            return
        }

        try {
            // ISO -> EV
            params["iso"]?.let {
                val iso = (it as Double).toInt()
                val minEv = srv.minExposure
                val maxEv = srv.maxExposure
                val ev = (((iso - 50).toFloat() / (3200f - 50f)) * (maxEv - minEv) + minEv)
                    .toInt().coerceIn(minEv, maxEv)
                exposureLevel = ev
                srv.setExposure(ev)
                Log.d(TAG, "ISO $iso -> EV $ev")
            }

            // Exposição em ns -> EV
            params["exposure"]?.let {
                val ns = (it as Double).toLong()
                val minEv = srv.minExposure
                val maxEv = srv.maxExposure
                val ratio = ((ns - 125_000L).toFloat() / (1_000_000_000L - 125_000L).toFloat())
                    .coerceIn(0f, 1f)
                val ev = (minEv + ratio * (maxEv - minEv)).toInt()
                exposureLevel = ev
                srv.setExposure(ev)
            }

            // Foco
            params["focus"]?.let {
                val dist = (it as Double).toFloat()
                if (dist == 0f) { srv.enableAutoFocus(); autoFocus = true }
                else            { srv.disableAutoFocus(); autoFocus = false }
            }

            // Balanço de branco
            params["whiteBalance"]?.let {
                whiteBalanceMode = it as String
                val mode = when (whiteBalanceMode) {
                    "daylight" -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    "cloudy"   -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    "tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    else       -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                }
                srv.enableAutoWhiteBalance(mode)
            }

            // Troca de câmera
            params["camera"]?.let {
                currentCameraId = it as String
                srv.switchCamera(currentCameraId)
                Log.d(TAG, "Camera -> $currentCameraId")
            }

            // Zoom (0.0 = 1x .. 1.0 = máximo óptico)
            params["zoom"]?.let {
                val z = (it as Double).toFloat().coerceIn(0f, 1f)
                zoomLevel = z
                srv.setZoom(z)
                Log.d(TAG, "Zoom -> $z")
            }

            // Lanterna
            params["lantern"]?.let {
                val enable = it as Boolean
                lanternEnabled = enable
                if (enable) srv.enableLantern() else srv.disableLantern()
                Log.d(TAG, "Lanterna -> $enable")
            }

            // OIS
            params["ois"]?.let {
                val enable = it as Boolean
                oisEnabled = enable
                if (enable) srv.enableOpticalVideoStabilization()
                Log.d(TAG, "OIS -> $enable")
            }

            // Resolução — mapeia para resoluções reais suportadas pelo Note10+ (camera-id=0)
            // 720p  → 1280x720  @ 4000kbps  (H264/Exynos, baixa latência)
            // 1080p → 1920x1080 @ 8000kbps  (H264/Exynos, sweet spot)
            // 4k    → 3840x2160 @ 20000kbps (H264 no limite do Exynos;
            //         para streaming puro prefira MJPEG que usa bitrate bruto ~32MB
            //         e elimina artefatos de compressão temporal)
            params["resolution"]?.let {
                val res = it as String
                val (w, h, br) = when (res) {
                    "4k"    -> Triple(3840, 2160, 20000)
                    "1080p" -> Triple(1920, 1080, 8000)
                    else    -> Triple(1280, 720,  4000)  // default 720p com 4Mbps (acima do threshold VQApply)
                }
                currentWidth   = w
                currentHeight  = h
                currentBitrate = br
                val wasStreaming = srv.isStreaming
                if (wasStreaming) srv.stopStream()
                srv.prepareVideo(w, h, currentFps, br * 1024, 0)
                if (wasStreaming) srv.startStream()
                Log.d(TAG, "Resolução -> ${w}x${h} @${br}kbps")
            }

            // Bitrate manual em kbps — setVideoBitrateOnFly não requer restart do stream
            params["bitrate"]?.let {
                val br = (it as Double).toInt()
                currentBitrate = br
                srv.setVideoBitrateOnFly(br * 1024)
                Log.d(TAG, "Bitrate onFly -> ${br}kbps")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aplicar configuracao", e)
        }
    }
}
