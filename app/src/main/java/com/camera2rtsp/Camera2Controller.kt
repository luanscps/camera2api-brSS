package com.camera2rtsp

import android.hardware.camera2.CameraMetadata
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

/**
 * Ponte entre o WebControlServer e o RtspServerCamera2.
 * Todos os controles (ISO, exposição, foco, WB, câmera) são
 * aplicados diretamente na instância da biblioteca.
 */
class Camera2Controller {

    private val TAG = "Camera2Controller"

    // Referência injetada pelo MainActivity após o server ser criado
    var server: RtspServerCamera2? = null

    // Estado atual (para /api/status)
    var currentCameraId = "0"
    var iso = 400
    var exposureTime = 16666667L  // 1/60s em ns
    var focusDistance = 0f
    var whiteBalanceMode = "auto"

    fun updateSettings(params: Map<String, Any>) {
        val srv = server
        if (srv == null) {
            Log.w(TAG, "server ainda nao inicializado, ignorando comando")
            return
        }

        try {
            params["iso"]?.let {
                iso = (it as Double).toInt()
                srv.setExposure(iso)          // ISO via setExposure(isoValue)
                Log.d(TAG, "ISO -> $iso")
            }

            params["exposure"]?.let {
                exposureTime = (it as Double).toLong()
                // A biblioteca aceita exposure em nanossegundos via setExposure(ns)
                // O segundo parâmetro é o tempo de exposição
                srv.setExposure(iso, exposureTime)
                Log.d(TAG, "Exposure -> $exposureTime ns")
            }

            params["focus"]?.let {
                focusDistance = (it as Double).toFloat()
                if (focusDistance == 0f) {
                    srv.enableAutoFocus()
                    Log.d(TAG, "Foco -> Auto")
                } else {
                    srv.setFocusDistance(focusDistance)
                    Log.d(TAG, "Foco -> $focusDistance")
                }
            }

            params["whiteBalance"]?.let {
                whiteBalanceMode = it as String
                val mode = when (whiteBalanceMode) {
                    "daylight"  -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    "cloudy"    -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    "tungsten"  -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    else        -> CameraMetadata.CONTROL_AWB_MODE_AUTO
                }
                srv.setWhiteBalance(mode)
                Log.d(TAG, "WB -> $whiteBalanceMode")
            }

            params["camera"]?.let {
                currentCameraId = it as String
                srv.changeVideoSourceCamera(currentCameraId.toInt())
                Log.d(TAG, "Camera -> $currentCameraId")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao aplicar configuração", e)
        }
    }
}
