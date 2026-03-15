package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.view.AutoFitTextureView
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.ServerClient

/**
 * Servidor RTSP embutido usando RtspServerCamera2 do modulo rtsp-server.
 *
 * Clientes na mesma rede assistem via:
 *   rtsp://<IP_DO_CELULAR>:8554/live
 *
 * Construtores aceitos pelo RtspServerCamera2:
 *   (context, connectChecker, port)     -- modo background
 *   (openGlView, connectChecker, port)  -- com OpenGlView
 *   (textureView, connectChecker, port) -- com TextureView (preview na tela)
 */
class RtspServer(
    private val context: Context,
    private val ctrl: Camera2Controller
) : ConnectChecker, ClientListener {

    private val tag = "RtspServer"
    val port        = 8554

    private var camera: RtspServerCamera2? = null

    var connectedClients: Int = 0
        private set

    /** Inicializa com preview visivel na TextureView. */
    fun init(view: AutoFitTextureView) {
        camera = RtspServerCamera2(view, this, port)
        camera!!.streamClient.setClientListener(this)
        Log.i(tag, "RtspServerCamera2 inicializado com TextureView")
    }

    /** Inicializa em modo background (sem preview na tela). */
    fun initBackground() {
        camera = RtspServerCamera2(context, this, port)
        camera!!.streamClient.setClientListener(this)
        Log.i(tag, "RtspServerCamera2 inicializado em modo background")
    }

    fun start() {
        val cam = camera ?: run { Log.e(tag, "init() nao chamado"); return }
        val rotation = CameraHelper.getCameraOrientation(context)
        val vOk = cam.prepareVideo(
            ctrl.currentWidth, ctrl.currentHeight,
            ctrl.currentFps, ctrl.currentBitrate * 1024, rotation
        )
        val aOk = cam.prepareAudio(128 * 1024, 44100, true)
        if (vOk && aOk) {
            cam.startStream("")
            Log.i(tag, "Servidor RTSP ativo na porta $port")
        } else {
            Log.e(tag, "Falha ao preparar encoder: vOk=$vOk aOk=$aOk")
        }
    }

    fun startPreview(facing: CameraHelper.Facing = CameraHelper.Facing.BACK) {
        val cam = camera ?: return
        try { if (!cam.isOnPreview) cam.startPreview(facing) }
        catch (e: Exception) { Log.e(tag, "Erro ao iniciar preview", e) }
    }

    fun stopPreview() {
        try { if (camera?.isOnPreview == true) camera?.stopPreview() }
        catch (e: Exception) { Log.e(tag, "Erro ao parar preview", e) }
    }

    fun stop() {
        try {
            if (camera?.isStreaming == true) camera?.stopStream()
            if (camera?.isOnPreview == true) camera?.stopPreview()
        } catch (e: Exception) { Log.e(tag, "Erro ao parar", e) }
    }

    val isStreaming: Boolean get() = camera?.isStreaming == true
    val isOnPreview: Boolean get() = camera?.isOnPreview == true

    // -- ConnectChecker -------------------------------------------------------
    override fun onConnectionStarted(url: String)  { Log.d(tag, "onConnectionStarted") }
    override fun onConnectionSuccess()              { Log.i(tag, "RTSP server pronto") }
    override fun onConnectionFailed(reason: String) { Log.e(tag, "RTSP falhou: $reason") }
    override fun onDisconnect()                     { Log.i(tag, "RTSP desconectado") }
    override fun onAuthError()                      { Log.e(tag, "auth error") }
    override fun onAuthSuccess()                    { Log.i(tag, "auth ok") }

    // -- ClientListener -------------------------------------------------------
    override fun onClientConnected(client: ServerClient) {
        connectedClients++
        Log.i(tag, "Cliente conectado: ${client.getAddress()} total=$connectedClients")
    }
    override fun onClientDisconnected(client: ServerClient) {
        connectedClients = maxOf(0, connectedClients - 1)
        Log.i(tag, "Cliente desconectado total=$connectedClients")
    }
    override fun onClientNewBitrate(bitrate: Long, client: ServerClient) {
        Log.d(tag, "Bitrate ${client.getAddress()}: ${bitrate}bps")
    }
}
