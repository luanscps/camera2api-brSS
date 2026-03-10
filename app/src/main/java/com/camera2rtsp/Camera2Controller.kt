package com.camera2rtsp

import android.hardware.camera2.CameraMetadata
import android.util.Log
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.rtspserver.RtspServerCamera2

/**
 * Ponte entre o WebControlServer e a Camera2Source interna do RtspServerCamera2.
 * Todos os controles são aplicados diretamente via videoSource da biblioteca.
 */
class Camera2Controller {

    private val TAG = "Camera2Controller"

    // Referência injetada pelo RtspServer após server.start()
    var server: RtspServerCamera2? = null

    // Estado atual (para /api/status)
    var currentCameraId = "0"
    var exposureLevel = 0
    var whiteBalanceMode = "auto"
    var autoFocus = true

    private fun cam(): Camera2Source? {
        val src = server?.videoSource
        if (src !is Camera2Source) {
            Log.w(TAG, "videoSource nao e Camera2Source, ignorando")
            return null
        }
        return src
    }

    fun updateSettings(params: Map<String, Any>) {
        val camera = cam() ?: return

        try {
            // ISO 50-3200 convertido para faixa EV -3..+3
            params["iso"]?.let {
                val iso = (it as Double).toInt()
                val ev = ((iso - 400) / 400.0).coerceIn(-3.0, 3.0).toInt()
                exposureLevel = ev
                camera.setExposure(ev)
                Log.d(TAG, "ISO $iso -> EV $ev")
            }

            // Exposicao em ns -> EV aproximado via log
            params["exposure"]?.let {
                val ns = (it as Double).toLong()
                val ev = Math.log(ns / 16_666_667.0).toInt().coerceIn(-3, 3)
                exposureLevel = ev
                camera.setExposure(ev)
                Log.d(TAG, "Exposure ${ns}ns -> EV $ev")
            }

            // Foco: 0 = auto, >0 = desabilitar AF
            params["focus"]?.let {
                val dist = (it as Double).toFloat()
                if (dist == 0f) {
                    camera.enableAutoFocus()
                    autoFocus = true
                    Log.d(TAG, "Foco -> Auto")
                } else {
                    camera.disableAutoFocus()
                    autoFocus = false
                    Log.d(TAG, "Foco -> Manual (AF desabilitado)")
                }
            }

            // Balanco de branco
            params["whiteBalance"]?.let {
                whiteBalanceMode = it as String
                val mode = when (whiteBalanceMode) {
                    "daylight" -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    "cloudy"   -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    "tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    else       -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                }
                camera.enableAutoWhiteBalance(mode)
                Log.d(TAG, "WB -> $whiteBalanceMode (mode=$mode)")
            }

            // Troca de camera por ID
            params["camera"]?.let {
                currentCameraId = it as String
                camera.openCameraId(currentCameraId)
                Log.d(TAG, "Camera -> $currentCameraId")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aplicar configuracao", e)
        }
    }
}
