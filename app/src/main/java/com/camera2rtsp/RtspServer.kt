package com.camera2rtsp

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
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

    // ── Início normal com TextureView (primeiro plano) ──────────────────────────
    fun start(tv: TextureView) {
        stopped = false
        try {
            val srv = RtspServerCamera2(context, this, PORT)
            server = srv
            cameraController.server = srv
            prepareAndStart(srv)
            // Inicia o preview no TextureView se já estiver disponível
            if (tv.isAvailable) {
                srv.startPreview(CameraHelper.Facing.BACK, tv.width, tv.height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    // ── Início em segundo plano (sem View na tela) ────────────────────────────
    // Quando Camera2Base é instanciado com Context (não com OpenGlView),
    // o flag isBackground=true é ativado internamente. Nesse modo a câmera
    // abre automaticamente dentro de startStream(), sem precisar de startPreview().
    fun startBackground() {
        stopped = false
        try {
            val srv = RtspServerCamera2(context, this, PORT)
            server = srv
            cameraController.server = srv
            prepareAndStart(srv)
            // Não chamamos startPreview() — Camera2Base no modo isBackground abre
            // a câmera internamente quando startStream() é chamado.
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP em background", e)
        }
    }

    private fun prepareAndStart(srv: RtspServerCamera2) {
        val w   = cameraController.currentWidth
        val h   = cameraController.currentHeight
        val fps = cameraController.currentFps
        val br  = cameraController.currentBitrate  // kbps → bps
        val rot = CameraHelper.getCameraOrientation(context)

        // Camera2Base 2.6.1: prepareVideo(width, height, fps, bitrate_bps, rotation)
        val videoOk = srv.prepareVideo(w, h, fps, br * 1024, rot)
        // prepareAudio(bitrate_bps, sampleRate, isStereo)
        val audioOk = srv.prepareAudio(128 * 1024, 44100, true)

        if (videoOk && audioOk) {
            srv.startStream()
            Log.i(TAG, "RTSP iniciado ${w}x${h} @${fps}fps ${br}kbps :$PORT")
        } else {
            Log.w(TAG, "Falha prepareVideo=$videoOk prepareAudio=$audioOk, tentando fallback 1080p")
            val fallback = srv.prepareVideo(1920, 1080, 30, 4000 * 1024, rot)
            if (fallback && audioOk) srv.startStream()
            else Log.e(TAG, "Fallback também falhou")
        }
    }

    // Reconecta o TextureView ao preview quando o app volta ao primeiro plano
    fun attachTextureView(tv: TextureView) {
        tv.surfaceTextureListener = this
        val srv = server ?: return
        if (tv.isAvailable && !srv.isOnPreview) {
            srv.startPreview(CameraHelper.Facing.BACK, tv.width, tv.height)
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

    // SurfaceTextureListener — usado só quando há TextureView na tela
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
        val srv = server ?: return
        if (!srv.isOnPreview) srv.startPreview(CameraHelper.Facing.BACK, w, h)
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (server?.isStreaming == false) server?.stopCamera()
        return true
    }
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
