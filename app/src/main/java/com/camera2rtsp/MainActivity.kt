package com.camera2rtsp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
        // Inicializar controlador da câmera
        cameraController = Camera2Controller(this)
        
        // Inicializar servidor RTSP (porta 8554)
        rtspServer = RtspServer(cameraController)
        rtspServer.start()
        
        // Inicializar servidor HTTP (porta 8080)
        httpServer = WebControlServer(8080, cameraController)
        httpServer.start()
        
        // Exibir URLs
        val ipAddress = getLocalIpAddress()
        Log.i("Server", "RTSP URL: rtsp://$ipAddress:8554/live")
        Log.i("Server", "Web Control: http://$ipAddress:8080")
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
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
    
    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA,
                       Manifest.permission.RECORD_AUDIO),
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
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        rtspServer.stop()
        httpServer.stop()
        cameraController.close()
    }
}
