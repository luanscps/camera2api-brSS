package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.rtsp.rtsp.RtspServer as PedroRtspServer

class RtspServer(
    private val context: Context,
    private val cameraController: Camera2Controller
) : ConnectChecker {

    private var rtspServer: PedroRtspServer? = null
    private val port = 8554
    private val TAG = "RtspServer"

    fun start() {
        try {
            rtspServer = PedroRtspServer(port, this)
            rtspServer?.start()
            Log.i(TAG, "Servidor RTSP iniciado na porta $port")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RTSP", e)
        }
    }

    fun stop() {
        try {
            rtspServer?.stop()
            rtspServer = null
            Log.i(TAG, "Servidor RTSP parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar RTSP", e)
        }
    }

    fun isRunning(): Boolean = rtspServer != null

    // ConnectChecker callbacks
    override fun onConnectionStarted(url: String) {
        Log.i(TAG, "Cliente conectando: $url")
    }

    override fun onConnectionSuccess() {
        Log.i(TAG, "Cliente conectado com sucesso")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Falha na conexao: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {
        Log.d(TAG, "Novo bitrate: $bitrate")
    }

    override fun onDisconnect() {
        Log.i(TAG, "Cliente desconectado")
    }

    override fun onAuthError() {
        Log.e(TAG, "Erro de autenticacao")
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "Autenticacao bem-sucedida")
    }
}
