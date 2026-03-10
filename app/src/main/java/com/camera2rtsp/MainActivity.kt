package com.camera2rtsp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.view.TextureView
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

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        rtspIndicator = findViewById(R.id.rtspIndicator)
        cameraPreview = findViewById(R.id.cameraPreview)

        if (checkPermissions()) {
            initializeServers()
        } else {
            requestPermissions()
        }
    }

    private fun initializeServers() {
        try {
            val ipAddress = getLocalIpAddress()

            cameraController = Camera2Controller(this)

            // Iniciar servidor RTSP real com TextureView
            rtspServer = RtspServer(this, cameraController)
            rtspServer.attachTextureView(cameraPreview)
            rtspServer.start()

            // Iniciar painel web
            httpServer = WebControlServer(8080, cameraController)
            httpServer.start()

            statusText.text = "📡 rtsp://$ipAddress:8554/live\n🌐 http://$ipAddress:8080"

            // Atualizar indicador RTSP
            rtspIndicator.text = "● RTSP :8554"
            rtspIndicator.setTextColor(0xFF10b981.toInt())

            Log.i("Camera2RTSP", "RTSP: rtsp://$ipAddress:8554/live")
            Log.i("Camera2RTSP", "Web:  http://$ipAddress:8080")

        } catch (e: Exception) {
            Log.e("Camera2RTSP", "Erro ao inicializar", e)
            statusText.text = "❌ Erro: ${e.message}"
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Camera2RTSP", "Erro ao obter IP", e)
        }
        return "127.0.0.1"
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE
            ),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeServers()
            } else {
                statusText.text = "❌ Permissões necessárias negadas"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::rtspServer.isInitialized) rtspServer.stop()
            if (::httpServer.isInitialized) httpServer.stop()
            if (::cameraController.isInitialized) cameraController.close()
        } catch (e: Exception) {
            Log.e("Camera2RTSP", "Erro ao encerrar", e)
        }
    }
}
