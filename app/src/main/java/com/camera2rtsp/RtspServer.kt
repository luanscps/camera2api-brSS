package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.library.view.AutoFitTextureView
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.ServerClient

/**
 * Wrapper do RtspServerCamera2 (RTSP-Server 1.3.6 + RootEncoder 2.6.1).
 *
 * A TextureView é passada no construtor (exigência da biblioteca).
 * O preview local começa automaticamente com startPreview().
 * O stream RTSP começa com startStream().
 */
class RtspServer(
    private val context: Context,
    private val ctrl: Camera2Controller,
    private val textureView: AutoFitTextureView
) : ConnectChecker, ClientListener {

    private val TAG = "RtspServer"
    val port = 8554

    private lateinit var camera: RtspServerCamera2

    // Número de clientes RTSP conectados no momento
    var connectedClients: Int = 0
        private set

    fun init() {
        camera = RtspServerCamera2(textureView, this, port)
        camera.streamClient.setClientListener(this)
        ctrl.server = camera
    }

    // Inicia preview local + prepara encoder + sobe servidor RTSP
    fun start() {
        if (!::camera.isInitialized) init()

        // startPreview() sem argumentos — usa a TextureView do construtor
        if (!camera.isOnPreview) camera.startPreview()

        val vOk = camera.prepareVideo(
            ctrl.currentWidth, ctrl.currentHeight,
            ctrl.currentFps, ctrl.currentBitrate * 1024,
            0 /* rotation */
        )
        val aOk = camera.prepareAudio(128 * 1024, 44100, true)

        if (vOk && aOk) {
            camera.startStream()
            Log.i(TAG, "Stream RTSP iniciado na porta $port")
        } else {
            Log.e(TAG, "Falha ao preparar encoder: vOk=$vOk aOk=$aOk")
        }
    }

    fun stopPreview() {
        if (!::camera.isInitialized) return
        try { if (camera.isOnPreview) camera.stopPreview() }
        catch (e: Exception) { Log.e(TAG, "stopPreview error", e) }
    }

    fun restartPreview() {
        if (!::camera.isInitialized) return
        try { if (!camera.isOnPreview) camera.startPreview() }
        catch (e: Exception) { Log.e(TAG, "restartPreview error", e) }
    }

    val isStreaming: Boolean
        get() = ::camera.isInitialized && camera.isStreaming

    fun stop() {
        if (!::camera.isInitialized) return
        try {
            if (camera.isStreaming) camera.stopStream()
            if (camera.isOnPreview) camera.stopPreview()
        } catch (e: Exception) { Log.e(TAG, "stop error", e) }
    }

    // ── ConnectChecker ───────────────────────────────────────────
    override fun onConnectionStarted(url: String) { Log.d(TAG, "onConnectionStarted: $url") }
    override fun onConnectionSuccess()             { Log.i(TAG, "onConnectionSuccess") }
    override fun onConnectionFailed(reason: String){ Log.e(TAG, "onConnectionFailed: $reason") }
    override fun onDisconnect()                    { Log.i(TAG, "onDisconnect") }
    override fun onAuthError()                     { Log.e(TAG, "onAuthError") }
    override fun onAuthSuccess()                   { Log.i(TAG, "onAuthSuccess") }

    // ── ClientListener ───────────────────────────────────────────
    override fun onClientConnected(client: ServerClient) {
        connectedClients++
        Log.i(TAG, "Cliente conectado: ${client.getAddress()} total=$connectedClients")
    }
    override fun onClientDisconnected(client: ServerClient) {
        connectedClients = maxOf(0, connectedClients - 1)
        Log.i(TAG, "Cliente desconectado: ${client.getAddress()} total=$connectedClients")
    }
    override fun onClientNewBitrate(bitrate: Long, client: ServerClient) {}
}
