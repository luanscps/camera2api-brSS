package com.camera2rtsp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.AutoFitTextureView
import java.net.Inet4Address
import java.net.NetworkInterface

class StreamingService : Service(), ConnectChecker {

    private val tag       = "StreamingService"
    private val notifId   = 1
    private val channelId = "camera2rtsp_channel"

    lateinit var cameraController: Camera2Controller
        private set
    lateinit var rtmpStreamer: RtmpStreamer
        private set
    lateinit var httpServer: WebControlServer
        private set

    private var wakeLock: PowerManager.WakeLock? = null

    // URL do MediaMTX no PC -- altere o IP conforme a sua rede
    var rtmpUrl = "rtmp://192.168.1.100:1935/live/stream"

    companion object {
        const val ACTION_STOP          = "com.camera2rtsp.STOP"
        const val EXTRA_RTMP_URL       = "rtmp_url"
        var instance: StreamingService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        cameraController = Camera2Controller()
        rtmpStreamer      = RtmpStreamer(cameraController, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        intent?.getStringExtra(EXTRA_RTMP_URL)?.let { rtmpUrl = it }

        startForeground(notifId, buildNotification(getLocalIpAddress(), "Aguardando preview..."))

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "camera2rtsp:streaming")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        try {
            httpServer = WebControlServer(8080, cameraController, applicationContext)
            httpServer.start()
            Log.i(tag, "Painel web em http://${getLocalIpAddress()}:8080")
        } catch (e: Exception) {
            Log.e(tag, "Erro ao iniciar httpServer", e)
        }

        return START_STICKY
    }

    /**
     * Chamado pela Activity quando a TextureView esta disponivel.
     * Inicializa o RtmpCamera2 (precisa da view), inicia preview e stream.
     */
    fun startPreview(
        view: AutoFitTextureView,
        facing: CameraHelper.Facing = CameraHelper.Facing.BACK
    ) {
        rtmpStreamer.init(view)
        rtmpStreamer.startPreview(facing)
        // Inicia stream automaticamente se URL configurada
        if (rtmpUrl.isNotEmpty() && !rtmpStreamer.isStreaming) {
            rtmpStreamer.startStream(rtmpUrl)
        }
    }

    fun stopPreview() = rtmpStreamer.stopPreview()

    fun startStream()  = rtmpStreamer.startStream(rtmpUrl)
    fun stopStream()   = rtmpStreamer.stopStream()

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            rtmpStreamer.stop()
            if (::cameraController.isInitialized) cameraController.release()
            if (::httpServer.isInitialized) httpServer.stop()
        } catch (e: Exception) { Log.e(tag, "Erro ao parar", e) }
        wakeLock?.release()
        Log.i(tag, "Servico encerrado")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -- ConnectChecker -------------------------------------------------------
    override fun onConnectionStarted(url: String) {
        Log.i(tag, "RTMP conectando: $url")
        updateNotification(getLocalIpAddress(), "Conectando RTMP...")
    }
    override fun onConnectionSuccess() {
        Log.i(tag, "RTMP conectado")
        updateNotification(getLocalIpAddress(), "Streaming ativo")
    }
    override fun onConnectionFailed(reason: String) {
        Log.e(tag, "RTMP falhou: $reason")
        updateNotification(getLocalIpAddress(), "Erro: $reason")
    }
    override fun onDisconnect() {
        Log.i(tag, "RTMP desconectado")
        updateNotification(getLocalIpAddress(), "Desconectado")
    }
    override fun onAuthError()   { Log.e(tag, "RTMP auth error") }
    override fun onAuthSuccess() { Log.i(tag, "RTMP auth ok") }

    // -- Notificacao ----------------------------------------------------------
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId, "Camera2 RTMP Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Stream RTMP ativo"; setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(ip: String, status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Camera2 RTMP")
            .setContentText(status)
            .setSubText(rtmpUrl)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Parar", stopIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateNotification(ip: String, status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(notifId, buildNotification(ip, status))
    }

    fun getLocalIpAddress(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                val addrs  = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address)
                        return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}
