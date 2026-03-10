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

    private val TAG  = "RtspServer"
    private val PORT = 8554

    // Mantém quantos clientes estão conectados para o painel
    var connectedClients = 0
        private set

    fun start(tv: TextureView) {
        try {
            val srv = RtspServerCamera2(context, this, PORT)
            server = srv
            cameraController.server = srv

            val w  = cameraController.currentWidth
            val h  = cameraController.currentHeight
            val br = cameraController.currentBitrate
            val fps = cameraController.currentFps

            val videoOk = srv.prepareVideo(w, h, fps, br * 1024, 0)
            val audioOk = srv.prepareAudio(128 * 1024, 44100, true)

            if (videoOk && audioOk) {
                srv.startStream()
                Log.i(TAG, "RTSP iniciado ${w}x${h} @${br}kbps :$PORT")
            } else {
                Log.e(TAG, "Falha prepareVideo=$videoOk prepareAudio=$audioOk — fallback 640x480")
                if (srv.prepareVideo(640, 480, 30, 1200 * 1024, 0)) srv.startStream()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    fun attachTextureView(tv: TextureView) { tv.surfaceTextureListener = this }

    fun stop() {
        try {
            server?.stopStream()
            cameraController.server = null
            server = null
            Log.i(TAG, "RTSP parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar", e)
        }
    }

    fun isStreaming(): Boolean = server?.isStreaming ?: false

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { stop(); return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onConnectionStarted(url: String) { connectedClients++; Log.i(TAG, "Cliente conectando: $url") }
    override fun onConnectionSuccess()             { Log.i(TAG, "Cliente conectado") }
    override fun onConnectionFailed(reason: String){ connectedClients = maxOf(0, connectedClients - 1); Log.e(TAG, "Falha: $reason") }
    override fun onNewBitrate(bitrate: Long)        { cameraController.currentBitrate = (bitrate / 1024).toInt() }
    override fun onDisconnect()                     { connectedClients = maxOf(0, connectedClients - 1); Log.i(TAG, "Desconectado") }
    override fun onAuthError()                      { Log.e(TAG, "Auth error") }
    override fun onAuthSuccess()                    { Log.i(TAG, "Auth ok") }
}
