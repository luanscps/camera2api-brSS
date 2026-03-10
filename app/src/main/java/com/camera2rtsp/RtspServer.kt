package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtsp.RtspServerCamera2

class RtspServer(
    private val context: Context,
    private val cameraController: Camera2Controller
) : ConnectChecker {

    private var rtspServer: RtspServerCamera2? = null
    private val port = 8554

    fun start() {
        try {
            // Criar servidor RTSP usando Camera2 API
            rtspServer = RtspServerCamera2(context, true, this, port)

            // Configurar callback da câmera para integração
            rtspServer?.getCameraFacing()?.let { facing ->
                Log.i("RtspServer", "Camera facing: $facing")
            }

            // Preparar encoder de vídeo com configurações seguras para Exynos
            val prepared = rtspServer?.prepareVideo(
                1280,           // width
                720,            // height  
                30,             // fps
                2500 * 1024,    // bitrate (2.5 Mbps - seguro para Exynos)
                0,              // rotação
                CameraHelper.getCameraOrientation(context)
            ) ?: false

            if (!prepared) {
                Log.e("RtspServer", "Falha ao preparar video encoder")
                // Tentar com bitrate menor
                rtspServer?.prepareVideo(
                    640, 480, 30, 1500 * 1024, 0,
                    CameraHelper.getCameraOrientation(context)
                )
            }

            // Preparar áudio
            rtspServer?.prepareAudio(
                64000,  // bitrate
                32000,  // sample rate
                true,   // stereo
                false,  // echo canceler
                false   // noise suppressor
            )

            // Iniciar servidor RTSP
            rtspServer?.startStream()
            
            Log.i("RtspServer", "Servidor RTSP iniciado na porta $port")
            Log.i("RtspServer", "Endpoint: ${rtspServer?.getEndPointConnection()}")
            
        } catch (e: Exception) {
            Log.e("RtspServer", "Erro ao iniciar RTSP", e)
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            if (rtspServer?.isStreaming == true) {
                rtspServer?.stopStream()
            }
            rtspServer = null
            Log.i("RtspServer", "Servidor RTSP parado")
        } catch (e: Exception) {
            Log.e("RtspServer", "Erro ao parar RTSP", e)
        }
    }

    fun getEndpoint(): String? {
        return rtspServer?.getEndPointConnection()
    }

    fun isStreaming(): Boolean {
        return rtspServer?.isStreaming ?: false
    }

    // ConnectChecker callbacks
    override fun onConnectionStarted(url: String) {
        Log.i("RtspServer", "Cliente conectando: $url")
    }

    override fun onConnectionSuccess() {
        Log.i("RtspServer", "Cliente conectado com sucesso")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e("RtspServer", "Falha na conexão: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {
        Log.d("RtspServer", "Novo bitrate: $bitrate")
    }

    override fun onDisconnect() {
        Log.i("RtspServer", "Cliente desconectado")
    }

    override fun onAuthError() {
        Log.e("RtspServer", "Erro de autenticação")
    }

    override fun onAuthSuccess() {
        Log.i("RtspServer", "Autenticação bem-sucedida")
    }
}
