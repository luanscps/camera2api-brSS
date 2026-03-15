package com.camera2rtsp

import android.content.Context
import android.view.Surface
import android.util.Log
import com.pedro.rtspserver.RtspServerCamera2

/**
 * Wrapper do RtspServerCamera2 da biblioteca rtsp-rtmp-stream.
 *
 * startBackground() — inicia o encoder + servidor RTSP sem preview local.
 * startPreview(surface) — conecta um Surface externo para ver na tela.
 * stopPreview() — remove o Surface sem interromper o stream.
 * attachTextureView() — compat para chamadas antigas (delega a startPreview).
 */
class RtspServer(private val context: Context,
                 private val ctrl: Camera2Controller) {

    private val TAG  = "RtspServer"
    val port         = 8554
    private lateinit var camera: RtspServerCamera2

    val connectedClients: Int
        get() = if (::camera.isInitialized) camera.sctpClient else 0

    // Prepara e inicia o servidor sem Surface (sem preview na tela)
    fun startBackground() {
        camera = RtspServerCamera2(context, true, port)
        ctrl.server = camera

        val vOk = camera.prepareVideo(
            ctrl.currentWidth, ctrl.currentHeight,
            ctrl.currentFps, ctrl.currentBitrate * 1024,
            0 /* rotation */
        )
        val aOk = camera.prepareAudio(128 * 1024, 44100, true)

        if (vOk && aOk) {
            camera.startStream()
            Log.i(TAG, "Stream RTSP iniciado na porta $port")
        } else {
            Log.e(TAG, "Falha ao preparar: vOk=$vOk aOk=$aOk")
        }
    }

    // Conecta um Surface para exibir preview local
    fun startPreview(surface: Surface) {
        if (!::camera.isInitialized) return
        try {
            camera.startPreview(surface)
            Log.d(TAG, "Preview local iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar preview", e)
        }
    }

    // Remove o preview da tela sem parar o stream
    fun stopPreview() {
        if (!::camera.isInitialized) return
        try {
            camera.stopPreview()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar preview", e)
        }
    }

    // Compatibilidade com chamadas legadas
    fun attachTextureView(tv: android.view.TextureView) {
        tv.surfaceTexture?.let { startPreview(Surface(it)) }
    }

    val isStreaming: Boolean get() = if (::camera.isInitialized) camera.isStreaming else false

    fun stop() {
        if (!::camera.isInitialized) return
        try {
            if (camera.isStreaming) camera.stopStream()
            camera.stopPreview()
        } catch (e: Exception) { Log.e(TAG, "Erro ao parar", e) }
    }
}
