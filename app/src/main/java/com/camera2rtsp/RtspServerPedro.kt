package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.rtsp.server.RtspServer
import com.pedro.rtsp.utils.ConnectCheckerRtsp

class RtspServerPedro(
    private val context: Context,
    private val cameraController: Camera2Controller
) : ConnectCheckerRtsp {
    
    private var rtspServer: RtspServer? = null
    private val TAG = "RtspServerPedro"
    
    fun start() {
        try {
            rtspServer = RtspServer(this, 8554)
            
            // Configurar vídeo: 1280x720 @ 30fps, bitrate 2500kbps
            rtspServer?.setVideoBitrateOnFly(2500 * 1024)
            
            // Iniciar servidor
            rtspServer?.startServer()
            
            Log.i(TAG, "RTSP Server started on port 8554")
            Log.i(TAG, "Endpoint: ${rtspServer?.serverIp}:8554")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start RTSP server", e)
            throw e
        }
    }
    
    fun stop() {
        try {
            rtspServer?.stopServer()
            Log.i(TAG, "RTSP Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping RTSP server", e)
        }
    }
    
    fun getEndpoint(): String {
        return rtspServer?.serverIp ?: ""
    }
    
    // ConnectCheckerRtsp callbacks
    override fun onConnectionStartedRtsp(rtspUrl: String) {
        Log.i(TAG, "Connection started: $rtspUrl")
    }
    
    override fun onConnectionSuccessRtsp() {
        Log.i(TAG, "Connection success")
    }
    
    override fun onConnectionFailedRtsp(reason: String) {
        Log.e(TAG, "Connection failed: $reason")
    }
    
    override fun onNewBitrateRtsp(bitrate: Long) {
        Log.d(TAG, "New bitrate: $bitrate")
    }
    
    override fun onDisconnectRtsp() {
        Log.i(TAG, "Disconnected")
    }
    
    override fun onAuthErrorRtsp() {
        Log.e(TAG, "Auth error")
    }
    
    override fun onAuthSuccessRtsp() {
        Log.i(TAG, "Auth success")
    }
}