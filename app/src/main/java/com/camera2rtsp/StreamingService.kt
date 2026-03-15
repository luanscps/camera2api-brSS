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
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Serviço em foreground que mantém o stream RTSP ativo mesmo com a tela desligada.
 *
 * O RtspServer precisa de uma AutoFitTextureView para mostrar o preview.
 * A Activity entrega essa view via attachTextureView() assim que ela estiver pronta.
 * Até lá, o serviço espera em estado "aguardando view".
 */
class StreamingService : Service() {

    private val TAG        = "StreamingService"
    private val NOTIF_ID   = 1
    private val CHANNEL_ID = "camera2rtsp_channel"

    lateinit var cameraController: Camera2Controller
        private set

    // rtspServer só é criado quando a Activity entrega a TextureView
    var rtspServer: RtspServer? = null
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
        startForeground(NOTIF_ID, buildNotification(ip, "Aguardando preview..."))

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "camera2rtsp:streaming")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        // WebControlServer já pode iniciar (não precisa da TextureView)
        try {
            httpServer = WebControlServer(8080, cameraController, applicationContext)
            httpServer.start()
            Log.i(TAG, "WebControlServer iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar WebControlServer", e)
        }

        return START_STICKY
    }

    /**
     * Chamado pela MainActivity quando a TextureView está pronta.
     * Cria o RtspServer com a view e inicia o stream.
     */
    fun attachTextureView(tv: com.pedro.library.view.AutoFitTextureView) {
        if (rtspServer != null) {
            // Já existe: apenas garante que o preview está ativo
            rtspServer!!.restartPreview()
            return
        }
        try {
            val server = RtspServer(applicationContext, cameraController, tv)
            server.init()
            server.start()
            rtspServer = server
            val ip = getLocalIpAddress()
            updateNotification(ip, "● Stream ativo")
            Log.i(TAG, "RtspServer iniciado em rtsp://$ip:${server.port}/live")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RtspServer", e)
        }
    }

    fun detachPreview() {
        rtspServer?.stopPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try {
            rtspServer?.stop()
            if (::cameraController.isInitialized) cameraController.release()
            if (::httpServer.isInitialized) httpServer.stop()
        } catch (e: Exception) { Log.e(TAG, "Erro ao parar", e) }
        wakeLock?.release()
        wakeLock = null
        Log.i(TAG, "Serviço encerrado")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Camera RTSP Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Stream RTSP ativo em segundo plano"
            setShowBadge(false)
        }
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

    fun updateNotification(ip: String, status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(ip, status))
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
