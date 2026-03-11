package com.camera2rtsp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class StreamingService : Service() {

    private val TAG        = "StreamingService"
    private val NOTIF_ID   = 1
    private val CHANNEL_ID = "camera2rtsp_channel"

    lateinit var rtspServer: RtspServer
        private set
    lateinit var httpServer: WebControlServer
        private set
    lateinit var cameraController: Camera2Controller
        private set

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_STOP = "com.camera2rtsp.STOP"
        var instance: StreamingService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startStreaming()
        return START_STICKY
    }

    private fun startStreaming() {
        val ip = getLocalIpAddress()
        startForeground(NOTIF_ID, buildNotification(ip, "Iniciando…"))

        // WakeLock PARTIAL: mantém CPU ativa com tela desligada
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "camera2rtsp:streaming")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        try {
            cameraController = Camera2Controller()
            rtspServer       = RtspServer(this, cameraController)

            // startBackground() — Camera2Base(Context) ativa modo isBackground=true.
            // A câmera é aberta internamente pelo startStream() sem precisar de View.
            rtspServer.startBackground()

            httpServer = WebControlServer(8080, cameraController)
            httpServer.start()

            updateNotification(ip, "● Streaming ativo")
            Log.i(TAG, "Serviço ativo — rtsp://$ip:8554/live | http://$ip:8080")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar streaming", e)
            updateNotification(ip, "❌ ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            if (::cameraController.isInitialized) cameraController.release()
            if (::rtspServer.isInitialized)       rtspServer.stop()
            if (::httpServer.isInitialized)       httpServer.stop()
        } catch (e: Exception) { Log.e(TAG, "Erro ao parar", e) }
        wakeLock?.release()
        wakeLock = null
        Log.i(TAG, "Serviço encerrado")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notificação ────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Camera RTSP Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Stream RTSP ativo em segundo plano"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(ip: String, status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera2 RTSP")
            .setContentText(status)
            .setSubText("rtsp://$ip:8554/live")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Parar", stopIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(ip: String, status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(ip, status))
    }

    private fun getLocalIpAddress(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.let { ifaces ->
                while (ifaces.hasMoreElements()) {
                    val iface = ifaces.nextElement()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address)
                            return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}
