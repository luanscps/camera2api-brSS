package com.camera2rtsp

import android.content.Context
import android.util.Log

/**
 * Servidor RTSP stub - integração com Camera2.
 * O stream real é gerenciado via biblioteca RootEncoder no CameraStreamManager.
 * Esta classe gerencia o ciclo de vida e expoe o estado do servidor.
 */
class RtspServer(
    private val context: Context,
    private val cameraController: Camera2Controller
) {
    private val TAG = "RtspServer"
    private val port = 8554
    private var running = false

    fun start() {
        try {
            running = true
            Log.i(TAG, "Servidor RTSP pronto na porta $port")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    fun stop() {
        try {
            running = false
            Log.i(TAG, "Servidor RTSP parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar RTSP", e)
        }
    }

    fun isRunning(): Boolean = running
}
