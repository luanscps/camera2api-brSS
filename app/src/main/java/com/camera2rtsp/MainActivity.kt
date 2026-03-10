package com.camera2rtsp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.library.view.OpenGlView
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var rtspServer: RtspServer
    private lateinit var httpServer: WebControlServer
    private lateinit var cameraController: Camera2Controller
    private lateinit var glView: OpenGlView

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glView = OpenGlView(this)

        if (checkCameraPermission()) {
            initializeServers()
        }
    }

    private fun initializeServers() {
        cameraController = Camera2Controller(this)

        // RTSP recebe context e cameraController
        rtspServer = RtspServer(this, cameraController)
        rtspServer.start(glView)

        // HTTP na porta 8080
        httpServer = WebControlServer(8080, cameraController)
        httpServer.start()

        val ip = getLocalIpAddress()
        val rtspUrl = "rtsp://$ip:8554/live"
        val webUrl = "http://$ip:8080"

        Log.i("Server", "RTSP: $rtspUrl")
        Log.i("Server", "Web: $webUrl")

        findViewById<TextView>(R.id.tv_status).text = "✅ Servidores ativos!"
        findViewById<TextView>(R.id.tv_rtsp_url).text = "RTSP: $rtspUrl"
        findViewById<TextView>(R.id.tv_web_url).text = "Web: $webUrl"
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address)
                        return addr.hostAddress ?: ""
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return "127.0.0.1"
    }

    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                CAMERA_PERMISSION_CODE
            )
            false
        } else true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeServers()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rtspServer.stop()
        httpServer.stop()
        cameraController.close()
    }
}
