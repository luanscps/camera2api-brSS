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

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkPermissions()) {
            initializeServers()
        }
    }

    private fun initializeServers() {
        try {
            // Inicializar controlador da câmera
            cameraController = Camera2Controller(this)

            // Inicializar servidor RTSP com referência ao controller
            rtspServer = RtspServer(this, cameraController)
            rtspServer.start()

            // Inicializar servidor HTTP
            httpServer = WebControlServer(8080, cameraController)
            httpServer.start()

            // Obter IPs e mostrar URLs
            val ip = getLocalIpAddress()
            val rtspUrl = rtspServer.getEndpoint() ?: "rtsp://$ip:8554/live"
            val webUrl = "http://$ip:8080"

            Log.i("MainActivity", "=== SERVIDORES INICIADOS ===")
            Log.i("MainActivity", "RTSP: $rtspUrl")
            Log.i("MainActivity", "Web Control: $webUrl")
            Log.i("MainActivity", "=============================")

            // Atualizar UI se TextViews existirem
            runOnUiThread {
                try {
                    findViewById<TextView>(R.id.tv_status)?.text = "✅ Servidores Ativos"
                    findViewById<TextView>(R.id.tv_rtsp_url)?.text = "RTSP: $rtspUrl"
                    findViewById<TextView>(R.id.tv_web_url)?.text = "Web: $webUrl"
                } catch (e: Exception) {
                    Log.w("MainActivity", "UI update failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao inicializar servidores", e)
            e.printStackTrace()
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao obter IP", e)
        }
        return "127.0.0.1"
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        val deniedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (deniedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                deniedPermissions.toTypedArray(),
                CAMERA_PERMISSION_CODE
            )
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i("MainActivity", "Permissões concedidas")
                initializeServers()
            } else {
                Log.e("MainActivity", "Permissões negadas")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            rtspServer.stop()
            httpServer.stop()
            cameraController.close()
            Log.i("MainActivity", "Servidores finalizados")
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao finalizar", e)
        }
    }
}
