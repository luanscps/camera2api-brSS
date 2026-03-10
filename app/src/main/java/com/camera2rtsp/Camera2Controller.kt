package com.camera2rtsp

import android.hardware.camera2.CameraMetadata
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

/**
 * Ponte entre o WebControlServer e o RtspServerCamera2.
 * RtspServerCamera2 herda Camera2Base que ja expoe todos os metodos
 * de controle da camara diretamente - nao precisa de videoSource.
 */
class Camera2Controller {

    private val TAG = "Camera2Controller"

    // Referencia injetada pelo RtspServer apos server.start()
    var server: RtspServerCamera2? = null

    // Estado atual (para /api/status)
    var currentCameraId = "0"
    var exposureLevel = 0
    var whiteBalanceMode = "auto"
    var autoFocus = true

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) {
            Log.w(TAG, "server ainda nao inicializado, ignorando")
            return
        }

        try {
            // ISO 50-3200 -> EV usando faixa real do device
            params["iso"]?.let {
                val iso = (it as Double).toInt()
                val minEv = srv.minExposure
                val maxEv = srv.maxExposure
                // Normaliza ISO 50..3200 para minEv..maxEv
                val ev = (((iso - 50).toFloat() / (3200f - 50f)) * (maxEv - minEv) + minEv)
                    .toInt().coerceIn(minEv, maxEv)
                exposureLevel = ev
                srv.setExposure(ev)
                Log.d(TAG, "ISO $iso -> EV $ev (range $minEv..$maxEv)")
            }

            // Exposicao em ns -> EV proporcional
            params["exposure"]?.let {
                val ns = (it as Double).toLong()
                val minEv = srv.minExposure
                val maxEv = srv.maxExposure
                // 1s = muito lento (EV max), 1/8000s = rapido (EV min)
                // ns range: 125_000 (1/8000s) .. 1_000_000_000 (1s)
                val ratio = ((ns - 125_000L).toFloat() / (1_000_000_000L - 125_000L).toFloat())
                    .coerceIn(0f, 1f)
                val ev = (minEv + ratio * (maxEv - minEv)).toInt()
                exposureLevel = ev
                srv.setExposure(ev)
                Log.d(TAG, "Exposure ${ns}ns -> EV $ev")
            }

            // Foco: 0 = auto, qualquer outro = desabilita AF
            params["focus"]?.let {
                val dist = (it as Double).toFloat()
                if (dist == 0f) {
                    srv.enableAutoFocus()
                    autoFocus = true
                    Log.d(TAG, "Foco -> Auto")
                } else {
                    srv.disableAutoFocus()
                    autoFocus = false
                    Log.d(TAG, "Foco -> Manual (AF desabilitado, dist=$dist)")
                }
            }

            // Balanco de branco - usa enableAutoWhiteBalance diretamente no Camera2Base
            params["whiteBalance"]?.let {
                whiteBalanceMode = it as String
                val mode = when (whiteBalanceMode) {
                    "daylight" -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    "cloudy"   -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    "tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    else       -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                }
                srv.enableAutoWhiteBalance(mode)
                Log.d(TAG, "WB -> $whiteBalanceMode (mode=$mode)")
            }

            // Troca de camera por ID - usa switchCamera(String) do Camera2Base
            params["camera"]?.let {
                currentCameraId = it as String
                srv.switchCamera(currentCameraId)
                Log.d(TAG, "Camera -> $currentCameraId")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aplicar configuracao", e)
        }
    }
}
