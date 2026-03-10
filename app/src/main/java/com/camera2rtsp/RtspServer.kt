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

    private var server: RtspServerCamera2? = null
    private val TAG = "RtspServer"
    private val PORT = 8554

    fun attachTextureView(tv: TextureView) {
        tv.surfaceTextureListener = this
    }

    fun start(tv: TextureView) {
        try {
            server = RtspServerCamera2(tv, this, PORT)

            val videoOk = server!!.prepareVideo(
                1280, 720,
                30,
                2500 * 1024,
                0
            )
            val audioOk = server!!.prepareAudio(
                128 * 1024,
                44100,
                true
            )

            if (videoOk && audioOk) {
                server!!.startStream()
                Log.i(TAG, "RTSP Server iniciado na porta $PORT")
            } else {
                Log.e(TAG, "Falha ao preparar video=$videoOk audio=$audioOk")
                // Tentar resolucao menor como fallback
                val fallback = server!!.prepareVideo(640, 480, 30, 1200 * 1024, 0)
                if (fallback) server!!.startStream()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    fun stop() {
        try {
            server?.stopStream()
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
    override fun onConnectionSuccess() { Log.i(TAG, "Cliente conectado") }
    override fun onConnectionFailed(reason: String) { Log.e(TAG, "Falha: $reason") }
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() { Log.i(TAG, "Cliente desconectado") }
    override fun onAuthError() { Log.e(TAG, "Erro de auth") }
    override fun onAuthSuccess() { Log.i(TAG, "Auth ok") }
}
