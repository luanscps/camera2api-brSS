package com.camera2rtsp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
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
        
        // Solicitar permissões
        if (checkCameraPermission()) {
            initializeServers()
        }
    }
    
    private fun initializeServers() {
        try {
            // Inicializar controlador da câmera
            cameraController = Camera2Controller(this)
            
            // Inicializar servidor RTSP (porta 8554)
            rtspServer = RtspServer(this, cameraController)
            rtspServer.start()
            
            // Inicializar servidor HTTP (porta 8080)
            httpServer = WebControlServer(8080, cameraController)
            httpServer.start()
            
            // Exibir URLs na tela e no log
            val ipAddress = getLocalIpAddress()
            val rtspUrl = "rtsp://$ipAddress:8554/live"
            val webUrl = "http://$ipAddress:8080"
            
            findViewById<TextView>(R.id.rtspUrlText)?.text = rtspUrl
            findViewById<TextView>(R.id.webUrlText)?.text = webUrl
            findViewById<TextView>(R.id.statusText)?.text = "✅ Servidores Ativos"
            
            Log.i("Server", "RTSP URL: $rtspUrl")
            Log.i("Server", "Web Control: $webUrl")
            
            Toast.makeText(this, "Servidores iniciados com sucesso!", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao inicializar servidores", e)
            findViewById<TextView>(R.id.statusText)?.text = "❌ Erro: ${e.message}"
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
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
            Log.e("MainActivity", "Erro ao obter IP", e)
        }
        return "127.0.0.1"
    }
    
    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.INTERNET
                ),
                CAMERA_PERMISSION_CODE)
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
            if (grantResults.isNotEmpty() && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeServers()
            } else {
                Toast.makeText(this, 
                    "Permissões de câmera necessárias!", 
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            rtspServer.stop()
            httpServer.stop()
            cameraController.close()
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao parar servidores", e)
        }
    }
}