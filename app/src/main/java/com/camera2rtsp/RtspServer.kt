package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.library.rtsp.RtspStream

class RtspServer(
    private val context: Context
) : ConnectChecker {

    private var rtspStream: RtspStream? = null
    private val port = 8554

    fun start() {
        try {
            rtspStream = RtspStream(context, this)

            val prepared = rtspStream?.prepareVideo(
                1280, 720,
                30,
                1200 * 1024,
                2,
                0
            ) ?: false

            if (prepared) {
                rtspStream?.startStream("rtsp://0.0.0.0:$port/live")
                Log.i("RtspServer", "RTSP iniciado na porta $port")
            } else {
                Log.e("RtspServer", "Falha ao preparar video RTSP")
            }
        } catch (e: Exception) {
            Log.e("RtspServer", "Erro: ${e.message}")
        }
    }

    fun stop() {
        rtspStream?.stopStream()
        Log.i("RtspServer", "RTSP parado")
    }

    override fun onConnectionStarted(url: String) {
        Log.i("RtspServer", "Conectando: $url")
    }

    override fun onConnectionSuccess() {
        Log.i("RtspServer", "Cliente conectado")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e("RtspServer", "Falha: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {}

    override fun onDisconnect() {
        Log.i("RtspServer", "Cliente desconectado")
    }

    override fun onAuthError() {
        Log.e("RtspServer", "Erro de autenticacao")
    }

    override fun onAuthSuccess() {
        Log.i("RtspServer", "Auth ok")
    }
}
