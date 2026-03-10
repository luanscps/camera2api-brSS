package com.camera2rtsp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        if (checkPermissions()) {
            initializeServers()
        } else {
            requestPermissions()
        }
    }

    private fun initializeServers() {
        try {
            cameraController = Camera2Controller(this)

            rtspServer = RtspServer(this, cameraController)
            rtspServer.start()

            httpServer = WebControlServer(8080, cameraController)
            httpServer.start()

            val ipAddress = getLocalIpAddress()
            val status = """
                ✅ Servidores Iniciados!

                📡 RTSP Stream:
                rtsp://$ipAddress:8554/live

                🌐 Painel Web:
                http://$ipAddress:8080

                💡 Abra o painel web no PC
                💡 Use VLC/OBS para ver stream
            """.trimIndent()

            statusText.text = status
            Log.i("Camera2RTSP", "RTSP: rtsp://$ipAddress:8554/live")
            Log.i("Camera2RTSP", "Web: http://$ipAddress:8080")
        } catch (e: Exception) {
            Log.e("Camera2RTSP", "Erro ao inicializar servidores", e)
            statusText.text = "❌ Erro: ${e.message}"
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
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
                statusText.text = "❌ Permissões necessárias não concedidas"
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
            Log.e("Camera2RTSP", "Erro ao parar servidores", e)
        }
    }
}
