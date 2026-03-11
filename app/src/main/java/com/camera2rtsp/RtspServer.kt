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

    private var stopped = false

    var connectedClients = 0
        private set

    // ── Início normal (com TextureView visível) ────────────────────────────────
    fun start(tv: TextureView) {
        stopped = false
        try {
            val srv = RtspServerCamera2(context, this, PORT)
            server = srv
            cameraController.server = srv
            prepareAndStart(srv)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    // ── Início offscreen (segundo plano / tela desligada) ──────────────────────
    // Usa SurfaceTexture diretamente, sem precisar de TextureView na tela
    fun startWithOffscreenSurface(surfaceTexture: SurfaceTexture) {
        stopped = false
        try {
            val srv = RtspServerCamera2(context, this, PORT)
            server = srv
            cameraController.server = srv
            prepareAndStart(srv)
            // Abre a câmera passando a SurfaceTexture offscreen
            srv.startPreview(surfaceTexture)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP offscreen", e)
        }
    }

    private fun prepareAndStart(srv: RtspServerCamera2) {
        val w   = cameraController.currentWidth
        val h   = cameraController.currentHeight
        val br  = cameraController.currentBitrate
        val fps = cameraController.currentFps

        val videoOk = srv.prepareVideo(w, h, fps, br * 1024, 0)
        val audioOk = srv.prepareAudio(128 * 1024, 44100, true)

        if (videoOk && audioOk) {
            srv.startStream()
            Log.i(TAG, "RTSP iniciado ${w}x${h} @${br}kbps :$PORT")
        } else {
            Log.w(TAG, "Falha prepareVideo=$videoOk prepareAudio=$audioOk, tentando fallback 1080p")
            val fallback = srv.prepareVideo(1920, 1080, 30, 4000 * 1024, 0)
            if (fallback && audioOk) srv.startStream()
            else Log.e(TAG, "Fallback também falhou")
        }
    }

    fun attachTextureView(tv: TextureView) {
        tv.surfaceTextureListener = this
        // Se o servidor já está rodando, vincula a surface ao preview
        server?.let { srv ->
            if (tv.isAvailable) {
                srv.startPreview(tv.surfaceTexture!!)
            }
        }
    }

    fun stop() {
        if (stopped) { Log.d(TAG, "stop() já chamado, ignorando"); return }
        stopped = true
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

    // SurfaceTextureListener
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
        server?.startPreview(surface)
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    // ConnectChecker
    override fun onConnectionStarted(url: String)  { connectedClients++; Log.i(TAG, "Cliente conectando: $url") }
    override fun onConnectionSuccess()              { Log.i(TAG, "Cliente conectado") }
    override fun onConnectionFailed(reason: String) { connectedClients = maxOf(0, connectedClients - 1); Log.e(TAG, "Falha: $reason") }
    override fun onNewBitrate(bitrate: Long)         { cameraController.currentBitrate = (bitrate / 1024).toInt() }
    override fun onDisconnect()                      { connectedClients = maxOf(0, connectedClients - 1); Log.i(TAG, "Desconectado") }
    override fun onAuthError()                       { Log.e(TAG, "Auth error") }
    override fun onAuthSuccess()                     { Log.i(TAG, "Auth ok") }
}
