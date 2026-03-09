package com.camera2rtsp

import android.content.Context
import com.pedro.rtsp.rtsp.RtspServerCamera2

class RtspServer(
    private val cameraController: Camera2Controller
) {
    private var rtspServerCamera2: RtspServerCamera2? = null
    private var context: Context? = null
    
    fun setContext(ctx: Context) {
        context = ctx
    }
    
    fun start() {
        context?.let { ctx ->
            rtspServerCamera2 = RtspServerCamera2(
                ctx,
                true, // usar conexão
                8554  // porta RTSP
            )
            
            rtspServerCamera2?.setLogs(true)
            
            // Preparar vídeo
            rtspServerCamera2?.prepareVideo(
                1280, 720, // resolução
                30,        // fps
                1200 * 1024, // bitrate
                0          // rotação
            )
            
            // Iniciar servidor
            rtspServerCamera2?.startStream("")
        }
    }
    
    fun stop() {
        rtspServerCamera2?.stopStream()
    }
    
    fun getEndpoint(): String {
        return rtspServerCamera2?.getEndPointConnection() ?: ""
    }
}
