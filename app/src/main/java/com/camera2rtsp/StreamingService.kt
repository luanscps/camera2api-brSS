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
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.AutoFitTextureView
import java.net.Inet4Address
import java.net.NetworkInterface

class StreamingService : Service() {

    private val tag       = "StreamingService"
    private val notifId   = 1
    private val channelId = "camera2rtsp_channel"

    lateinit var cameraController: Camera2Controller
        private set
    lateinit var rtspServer: RtspServer
        private set
    lateinit var httpServer: WebControlServer
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
        cameraController = Camera2Controller()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        val ip = getLocalIpAddress()
        startForeground(notifId, buildNotification(ip, "Aguardando preview..."))

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "camera2rtsp:streaming")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        // RtspServer e HttpServer sao iniciados em startPreview(),
        // quando a TextureView ja esta disponivel.
        rtspServer = RtspServer(applicationContext, cameraController)

        try {
            httpServer = WebControlServer(8080, cameraController, applicationContext)
            httpServer.start()
        } catch (e: Exception) {
            Log.e(tag, "Erro ao iniciar httpServer", e)
        }

        return START_STICKY
    }

    /**
     * Chamado pela Activity quando a TextureView esta disponivel.
     * Inicializa o RtspServerCamera2 (que precisa da view) e inicia
     * o encoder + stream + preview.
     */
    fun startPreview(
        view: AutoFitTextureView,
        facing: CameraHelper.Facing = CameraHelper.Facing.BACK
    ) {
        if (!::rtspServer.isInitialized) return
        // Re-init se a view mudou ou ainda nao foi inicializado
        if (cameraController.server == null) {
            rtspServer.init(view)
            rtspServer.start()
            updateNotification(getLocalIpAddress(), "\u25cf Stream ativo")
            Log.i(tag, "Servico ativo apos preview init")
        }
        rtspServer.startPreview(facing)
    }

    fun stopPreview() {
        if (::rtspServer.isInitialized) rtspServer.stopPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            if (::rtspServer.isInitialized)       rtspServer.stop()
            if (::cameraController.isInitialized) cameraController.release()
            if (::httpServer.isInitialized)        httpServer.stop()
        } catch (e: Exception) { Log.e(tag, "Erro ao parar", e) }
        wakeLock?.release()
        Log.i(tag, "Servico encerrado")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId, "Camera2 RTSP Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Stream RTSP ativo em segundo plano"; setShowBadge(false) }
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

    fun updateNotification(ip: String, status: String) {
        getSystemService(NotificationManager::class.java).notify(notifId, buildNotification(ip, status))
    }

    private fun getLocalIpAddress(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                val addrs = iface.inetAddresses
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
