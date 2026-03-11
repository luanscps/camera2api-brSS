package com.camera2rtsp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.TextureView
import androidx.core.app.NotificationCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class StreamingService : Service() {

    private val TAG = "StreamingService"
    private val NOTIF_ID = 1
    private val CHANNEL_ID = "camera2rtsp_channel"

    lateinit var rtspServer: RtspServer
        private set
    lateinit var httpServer: WebControlServer
        private set
    lateinit var cameraController: Camera2Controller
        private set

    // WakeLock: mantém CPU ativa com tela desligada
    private var wakeLock: PowerManager.WakeLock? = null

    // TextureView virtual: necessário porque RtspServerCamera2 precisa de uma Surface
    // mesmo com tela desligada. Criamos uma SurfaceTexture fora de tela (1x1).
    private var offscreenTexture: SurfaceTexture? = null
    private var offscreenView: TextureView? = null

    companion object {
        const val ACTION_START = "com.camera2rtsp.START"
        const val ACTION_STOP  = "com.camera2rtsp.STOP"
        var instance: StreamingService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startStreaming()
        }
        return START_STICKY
    }

    private fun startStreaming() {
        val ip = getLocalIpAddress()

        // Inicia como foreground imediatamente (obrigatório no Android 9+)
        startForeground(NOTIF_ID, buildNotification(ip, "Iniciando…"))

        // WakeLock — mantém CPU viva quando a tela apaga
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "camera2rtsp:streaming"
        ).apply { acquire(12 * 60 * 60 * 1000L) } // máx 12h

        try {
            cameraController = Camera2Controller()
            rtspServer = RtspServer(this, cameraController)

            // Surface offscreen: o encoder precisa de uma Surface para receber frames
            // da câmera mesmo sem TextureView visível na tela
            offscreenTexture = SurfaceTexture(0).also { it.setDefaultBufferSize(1920, 1080) }
            offscreenView = TextureView(this)

            rtspServer.startWithOffscreenSurface(offscreenTexture!!)

            httpServer = WebControlServer(8080, cameraController)
            httpServer.start()

            updateNotification(ip, "● Streaming ativo")
            Log.i(TAG, "Serviço iniciado — RTSP rtsp://$ip:8554/live  Web http://$ip:8080")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar streaming", e)
            updateNotification(ip, "❌ Erro: ${e.message}")
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
        offscreenTexture?.release()
        offscreenTexture = null
        wakeLock?.release()
        wakeLock = null
        Log.i(TAG, "Serviço destruído")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notificação ────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera RTSP Streaming",
                NotificationManager.IMPORTANCE_LOW  // sem som, não intrusivo
            ).apply {
                description = "Stream RTSP ativo em segundo plano"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(ip, status))
    }

    private fun getLocalIpAddress(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.let { interfaces ->
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
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
