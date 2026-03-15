package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.AutoFitTextureView
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.ServerClient

class RtspServer(
    private val context: Context,
    private val ctrl: Camera2Controller
) : ConnectChecker, ClientListener {

    private val tag = "RtspServer"
    val port = 8554

    private var camera: RtspServerCamera2? = null

    var connectedClients: Int = 0
        private set

    /**
     * Inicializa o RtspServerCamera2 com a TextureView.
     * A view DEVE ser passada aqui — a biblioteca renderiza
     * o preview atraves da surface interna da view.
     */
    fun init(view: AutoFitTextureView) {
        camera = RtspServerCamera2(view, this, port)
        camera!!.streamClient.setClientListener(this)
        ctrl.server = camera
    }

    /** Prepara o encoder e inicia o servidor RTSP. */
    fun start() {
        val cam = camera ?: return
        val vOk = cam.prepareVideo(
            ctrl.currentWidth, ctrl.currentHeight,
            ctrl.currentFps, ctrl.currentBitrate * 1024, 0
        )
        val aOk = cam.prepareAudio(128 * 1024, 44100, true)
        if (vOk && aOk) {
            cam.startStream()
            Log.i(tag, "Stream RTSP iniciado na porta $port")
        } else {
            Log.e(tag, "Falha ao preparar encoder: vOk=$vOk aOk=$aOk")
        }
    }

    /** Inicia o preview local (a view ja foi passada no construtor). */
    fun startPreview(facing: CameraHelper.Facing = CameraHelper.Facing.BACK) {
        val cam = camera ?: return
        try {
            if (!cam.isOnPreview) cam.startPreview(facing)
        } catch (e: Exception) {
            Log.e(tag, "Erro ao iniciar preview", e)
        }
    }

    fun stopPreview() {
        try { if (camera?.isOnPreview == true) camera?.stopPreview() }
        catch (e: Exception) { Log.e(tag, "Erro ao parar preview", e) }
    }

    fun stop() {
        try {
            if (camera?.isStreaming == true) camera?.stopStream()
            if (camera?.isOnPreview  == true) camera?.stopPreview()
        } catch (e: Exception) { Log.e(tag, "Erro ao parar", e) }
    }

    // -- ConnectChecker -------------------------------------------------------
    override fun onConnectionStarted(url: String)      { Log.d(tag, "onConnectionStarted: $url") }
    override fun onConnectionSuccess()                  { Log.i(tag, "onConnectionSuccess") }
    override fun onConnectionFailed(reason: String)     { Log.e(tag, "onConnectionFailed: $reason") }
    override fun onDisconnect()                         { Log.i(tag, "onDisconnect") }
    override fun onAuthError()                          { Log.e(tag, "onAuthError") }
    override fun onAuthSuccess()                        { Log.i(tag, "onAuthSuccess") }

    // -- ClientListener -------------------------------------------------------
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
