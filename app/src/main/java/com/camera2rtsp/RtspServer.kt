package com.camera2rtsp

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import com.pedro.common.ConnectChecker
import com.pedro.encoder.video.VideoEncoder
import com.pedro.rtspserver.RtspServerCamera2

class RtspServer(
    private val context: Context,
    private val cameraController: Camera2Controller
) : ConnectChecker, TextureView.SurfaceTextureListener {

    var server: RtspServerCamera2? = null
        private set

    private val TAG  = "RtspServer"
    private val PORT = 8554

    // Guard contra double-stop (causa GL already released + BufferQueue abandoned)
    private var stopped = false

    var connectedClients = 0
        private set

    fun start(tv: TextureView) {
        stopped = false
        try {
            val srv = RtspServerCamera2(context, this, PORT)
            server = srv
            cameraController.server = srv

            val w   = cameraController.currentWidth
            val h   = cameraController.currentHeight
            val br  = cameraController.currentBitrate  // kbps
            val fps = cameraController.currentFps

            // prepareVideo(width, height, fps, bitrate_bps, rotation)
            // RootEncoder 2.6.1 seleciona automaticamente o encoder HW (OMX.Exynos.AVC.Encoder)
            // quando disponivel — o SW encoder (c2.android.avc.encoder) so e usado como fallback.
            // O VQApply da Samsung eleva bitrate minimo de 2.5M para 3M em 720p no Exynos,
            // por isso usamos 4Mbps como base para nao ser sobrescrito silenciosamente.
            val videoOk = srv.prepareVideo(
                w, h, fps,
                br * 1024,  // bitrate em bps
                0           // rotation
            )
            val audioOk = srv.prepareAudio(128 * 1024, 44100, true)

            if (videoOk && audioOk) {
                srv.startStream()
                Log.i(TAG, "RTSP iniciado ${w}x${h} @${br}kbps :$PORT")
            } else {
                Log.w(TAG, "Falha prepareVideo=$videoOk prepareAudio=$audioOk, tentando fallback 1080p")
                val fallback = srv.prepareVideo(1920, 1080, 30, 4000 * 1024, 0)
                if (fallback && audioOk) srv.startStream()
                else Log.e(TAG, "Fallback tambem falhou")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    fun attachTextureView(tv: TextureView) { tv.surfaceTextureListener = this }

    fun stop() {
        // Evita double-stop que causava:
        //   SurfaceManager: GL already released
        //   BufferQueueProducer: cancelBuffer BufferQueue has been abandoned
        if (stopped) {
            Log.d(TAG, "stop() chamado mais de uma vez, ignorando")
            return
        }
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

    // SurfaceTextureListener — NAO chama stop() aqui pois onDestroy ja chama
    // Isso evitava o double-stop que aparecia no logcat
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {}
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
