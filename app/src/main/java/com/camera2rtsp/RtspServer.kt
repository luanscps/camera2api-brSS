package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.rtsp.utils.ConnectCheckerRtsp

class RtspServer(
    private val context: Context,
    private val cameraController: Camera2Controller
) : ConnectCheckerRtsp {

    private var rtspServerCamera2: com.pedro.rtsp.rtsp.RtspServerCamera2? = null
    private val PORT = 8554

    fun start() {
        try {
            rtspServerCamera2 = com.pedro.rtsp.rtsp.RtspServerCamera2(
                context,
                this,
                PORT
            )

            val prepared = rtspServerCamera2?.prepareVideo(
                1280, 720,
                30,
                1200 * 1024,
                2,
                0
            ) ?: false

            if (prepared) {
                rtspServerCamera2?.startStream()
                Log.i("RtspServer", "Servidor RTSP iniciado na porta $PORT")
            } else {
                Log.e("RtspServer", "Falha ao preparar video")
            }
        } catch (e: Exception) {
            Log.e("RtspServer", "Erro ao iniciar servidor RTSP: ${e.message}")
        }
    }

    fun stop() {
        rtspServerCamera2?.stopStream()
        Log.i("RtspServer", "Servidor RTSP parado")
    }

    fun isStreaming(): Boolean = rtspServerCamera2?.isStreaming ?: false

    // ConnectCheckerRtsp callbacks
    override fun onConnectionSuccessRtsp() {
        Log.i("RtspServer", "Cliente conectado ao RTSP")
    }

    override fun onConnectionFailedRtsp(reason: String) {
        Log.e("RtspServer", "Falha de conexao RTSP: $reason")
    }

    override fun onNewBitrateRtsp(bitrate: Long) {}

    override fun onDisconnectRtsp() {
        Log.i("RtspServer", "Cliente desconectado do RTSP")
    }

    override fun onAuthErrorRtsp() {
        Log.e("RtspServer", "Erro de autenticacao RTSP")
    }

    override fun onAuthSuccessRtsp() {
        Log.i("RtspServer", "Autenticacao RTSP ok")
    }
}
