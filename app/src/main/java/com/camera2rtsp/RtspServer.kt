package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.ServerClient

/**
 * Wrapper do RtspServerCamera2 (RTSP-Server 1.3.6 + RootEncoder 2.6.1).
 *
 * Construtor correto: RtspServerCamera2(context, ConnectChecker, port)
 * Preview local:      startPreview(CameraHelper.Facing.BACK)
 * Sem preview:        stopPreview()
 */
class RtspServer(
    private val context: Context,
    private val ctrl: Camera2Controller
) : ConnectChecker, ClientListener {

    private val tag = "RtspServer"
    val port = 8554

    private lateinit var camera: RtspServerCamera2

    var connectedClients: Int = 0
        private set

    fun init() {
        camera = RtspServerCamera2(context, this, port)
        camera.streamClient.setClientListener(this)
        ctrl.server = camera
    }

    /** Prepara o encoder e inicia o servidor RTSP (sem preview na tela). */
    fun start() {
        if (!::camera.isInitialized) init()
        val vOk = camera.prepareVideo(
            ctrl.currentWidth, ctrl.currentHeight,
            ctrl.currentFps, ctrl.currentBitrate * 1024, 0
        )
        val aOk = camera.prepareAudio(128 * 1024, 44100, true)
        if (vOk && aOk) {
            camera.startStream()
            Log.i(tag, "Stream RTSP iniciado na porta $port")
        } else {
            Log.e(tag, "Falha ao preparar encoder: vOk=$vOk aOk=$aOk")
        }
    }

    /** Inicia o preview local na TextureView (CameraHelper.Facing indica qual lente). */
    fun startPreview(facing: CameraHelper.Facing = CameraHelper.Facing.BACK) {
        if (!::camera.isInitialized) return
        try {
            if (!camera.isOnPreview) camera.startPreview(facing)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao iniciar preview", e)
        }
    }

    fun stopPreview() {
        if (!::camera.isInitialized) return
        try { if (camera.isOnPreview) camera.stopPreview() }
        catch (e: Exception) { Log.e(tag, "Erro ao parar preview", e) }
    }

    fun stop() {
        if (!::camera.isInitialized) return
        try {
            if (camera.isStreaming) camera.stopStream()
            if (camera.isOnPreview)  camera.stopPreview()
        } catch (e: Exception) { Log.e(tag, "Erro ao parar", e) }
    }

    // ── ConnectChecker ─────────────────────────────────────────────
    override fun onConnectionStarted(url: String)      { Log.d(tag, "onConnectionStarted: $url") }
    override fun onConnectionSuccess()                  { Log.i(tag, "onConnectionSuccess") }
    override fun onConnectionFailed(reason: String)     { Log.e(tag, "onConnectionFailed: $reason") }
    override fun onDisconnect()                         { Log.i(tag, "onDisconnect") }
    override fun onAuthError()                          { Log.e(tag, "onAuthError") }
    override fun onAuthSuccess()                        { Log.i(tag, "onAuthSuccess") }

    // ── ClientListener ───────────────────────────────────────────
    override fun onClientConnected(client: ServerClient) {
        connectedClients++
        Log.i(tag, "Cliente conectado: ${client.getAddress()} total=$connectedClients")
    }
    override fun onClientDisconnected(client: ServerClient) {
        connectedClients = maxOf(0, connectedClients - 1)
        Log.i(tag, "Cliente desconectado total=$connectedClients")
    }
    override fun onClientNewBitrate(bitrate: Long, client: ServerClient) {}
}
