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

            // Força o encoder H264 hardware Exynos — melhor qualidade/latência no Note10+
            // O OMX.Exynos.AVC.Encoder supera c2.android.avc.encoder (sw) em throughput
            // O VQApply da Samsung eleva o bitrate mínimo para ~3MB em 720p,
            // por isso usamos 4000kbps como base para deixar margem.
            val videoOk = srv.prepareVideo(
                w, h, fps,
                br * 1024,   // bitrate em bps
                0,           // rotation
                "video/avc", // MIME H264
                "OMX.Exynos.AVC.Encoder"  // força HW Exynos
            )
            val audioOk = srv.prepareAudio(128 * 1024, 44100, true)

            if (videoOk && audioOk) {
                srv.startStream()
                Log.i(TAG, "RTSP iniciado ${w}x${h} @${br}kbps encoder=OMX.Exynos.AVC.Encoder :$PORT")
            } else {
                // fallback sem forçar encoder
                Log.w(TAG, "Falha com Exynos encoder (videoOk=$videoOk audioOk=$audioOk), tentando fallback automático")
                val fallback = srv.prepareVideo(1920, 1080, 30, 4000 * 1024, 0)
                if (fallback && audioOk) srv.startStream()
                else Log.e(TAG, "Fallback também falhou")
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

    // SurfaceTextureListener — NÃO chamar stop() aqui pois onDestroy já chama
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // Não chama stop() — deixa o onDestroy da Activity gerenciar o ciclo de vida
        // para evitar o double-stop que aparecia no logcat
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
