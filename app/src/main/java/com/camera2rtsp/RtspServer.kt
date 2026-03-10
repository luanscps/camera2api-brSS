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

            // Usar resolucao menor e configuracoes seguras para Exynos Note10+
            // CBR nao e suportado - usar prepareVideo sem bitrate customizado
            val prepared = rtspStream?.prepareVideo(
                1280,    // width
                720,     // height
                30,      // fps
                4000 * 1024, // bitrate alto para o Samsung ajustar internamente
                2,       // iFrame interval
                0        // rotacao
            ) ?: false

            if (prepared) {
                rtspStream?.startStream("rtsp://0.0.0.0:$port/live")
                Log.i("RtspServer", "RTSP iniciado na porta $port")
            } else {
                // Tentar com resolucao menor se falhar
                val prepared2 = rtspStream?.prepareVideo(
                    640, 480, 30, 2000 * 1024, 2, 0
                ) ?: false

                if (prepared2) {
                    rtspStream?.startStream("rtsp://0.0.0.0:$port/live")
                    Log.i("RtspServer", "RTSP iniciado em 640x480")
                } else {
                    Log.e("RtspServer", "Falha ao preparar video")
                }
            }
        } catch (e: Exception) {
            Log.e("RtspServer", "Erro ao iniciar RTSP: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            rtspStream?.stopStream()
        } catch (e: Exception) {
            Log.e("RtspServer", "Erro ao parar: ${e.message}")
        }
        Log.i("RtspServer", "RTSP parado")
    }

    override fun onConnectionStarted(url: String) {
        Log.i("RtspServer", "Conectando: $url")
    }

    override fun onConnectionSuccess() {
        Log.i("RtspServer", "Cliente conectado ao RTSP")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e("RtspServer", "Falha de conexao: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {}

    override fun onDisconnect() {
        Log.i("RtspServer", "Cliente desconectado")
    }

    override fun onAuthError() {
        Log.e("RtspServer", "Erro de autenticacao RTSP")
    }

    override fun onAuthSuccess() {
        Log.i("RtspServer", "Autenticacao RTSP ok")
    }
}
