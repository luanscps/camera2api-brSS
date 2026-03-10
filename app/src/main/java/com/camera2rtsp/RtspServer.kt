package com.camera2rtsp

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import com.pedro.common.ConnectChecker
import com.pedro.rtspserver.RtspServerCamera2

class RtspServer(
    private val context: Context,
    private val cameraController: Camera2Controller
) : ConnectChecker, TextureView.SurfaceTextureListener {

    var server: RtspServerCamera2? = null
        private set

    private val TAG = "RtspServer"
    private val PORT = 8554

    fun start(tv: TextureView) {
        try {
            val srv = RtspServerCamera2(context, this, PORT)
            server = srv

            // Injeta a referência no controlador para que os controles web funcionem
            cameraController.server = srv

            val videoOk = srv.prepareVideo(
                1280, 720,
                30,
                2500 * 1024,
                0
            )
            val audioOk = srv.prepareAudio(
                128 * 1024,
                44100,
                true
            )

            if (videoOk && audioOk) {
                srv.startStream()
                Log.i(TAG, "RTSP Server iniciado na porta $PORT")
            } else {
                Log.e(TAG, "Falha prepareVideo=$videoOk prepareAudio=$audioOk - tentando fallback 640x480")
                val fallback = srv.prepareVideo(640, 480, 30, 1200 * 1024, 0)
                if (fallback) srv.startStream()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    fun attachTextureView(tv: TextureView) {
        tv.surfaceTextureListener = this
    }

    fun stop() {
        try {
            server?.stopStream()
            cameraController.server = null
            server = null
            Log.i(TAG, "RTSP parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar RTSP", e)
        }
    }

    fun isStreaming(): Boolean = server?.isStreaming ?: false

    // TextureView callbacks
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stop()
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    // ConnectChecker callbacks
    override fun onConnectionStarted(url: String) { Log.i(TAG, "Cliente conectando: $url") }
    override fun onConnectionSuccess() { Log.i(TAG, "Cliente conectado com sucesso") }
    override fun onConnectionFailed(reason: String) { Log.e(TAG, "Falha de conexão: $reason") }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() { Log.i(TAG, "Cliente desconectado") }
    override fun onAuthError() { Log.e(TAG, "Erro de autenticação") }
    override fun onAuthSuccess() { Log.i(TAG, "Autenticação ok") }
}
