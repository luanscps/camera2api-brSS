package com.camera2rtsp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var rtspServer: RtspServer
    private lateinit var httpServer: WebControlServer
    private lateinit var cameraController: Camera2Controller
    private lateinit var statusText: TextView
    private lateinit var rtspIndicator: TextView
    private lateinit var cameraPreview: TextureView

    private val PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        rtspIndicator = findViewById(R.id.rtspIndicator)
        cameraPreview = findViewById(R.id.cameraPreview)

        if (checkPermissions()) initializeServers()
        else requestPermissions()
    }

    private fun initializeServers() {
        try {
            val ip = getLocalIpAddress()

            cameraController = Camera2Controller(this)

            // Iniciar servidor RTSP passando o TextureView diretamente
            rtspServer = RtspServer(this, cameraController)
            rtspServer.attachTextureView(cameraPreview)
            rtspServer.start(cameraPreview)

            // Painel web
            httpServer = WebControlServer(8080, cameraController)
            httpServer.start()

            statusText.text = "📡 rtsp://$ip:8554/live\n🌐 http://$ip:8080"
            rtspIndicator.text = "● RTSP :8554"
            rtspIndicator.setTextColor(0xFF10b981.toInt())

            Log.i("MainActivity", "RTSP: rtsp://$ip:8554/live")
            Log.i("MainActivity", "Web:  http://$ip:8080")
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro", e)
            statusText.text = "❌ ${e.message}"
        }
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
        } catch (e: Exception) { Log.e("MainActivity", "IP error", e) }
        return "127.0.0.1"
    }

    private fun checkPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        ), PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
            initializeServers()
        else statusText.text = "❌ Permissões negadas"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::rtspServer.isInitialized) rtspServer.stop()
            if (::httpServer.isInitialized) httpServer.stop()
            if (::cameraController.isInitialized) cameraController.close()
        } catch (e: Exception) { Log.e("MainActivity", "Erro ao encerrar", e) }
    }
}
