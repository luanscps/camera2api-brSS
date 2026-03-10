package com.camera2rtsp

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import com.pedro.common.ConnectChecker
import com.pedro.library.rtsp.RtspServerCamera2

class RtspServer(
    private val context: Context,
    private val cameraController: Camera2Controller
) : ConnectChecker, TextureView.SurfaceTextureListener {

    private var rtspServerCamera2: RtspServerCamera2? = null
    private val TAG = "RtspServer"
    private val PORT = 8554
    private var textureView: TextureView? = null

    fun attachTextureView(tv: TextureView) {
        textureView = tv
        tv.surfaceTextureListener = this
    }

    fun start() {
        try {
            rtspServerCamera2 = RtspServerCamera2(context, this, PORT)

            val prepared = rtspServerCamera2!!.prepareVideo(
                1280, 720,
                30,
                2500 * 1024,
                0
            ) && rtspServerCamera2!!.prepareAudio(
                128 * 1024,
                44100,
                true
            )

            if (prepared) {
                // Se o TextureView ja tem superficie pronta, inicia agora
                if (textureView?.isAvailable == true) {
                    rtspServerCamera2!!.startPreview()
                    rtspServerCamera2!!.startStream()
                    Log.i(TAG, "RTSP iniciado na porta $PORT")
                }
                // Caso contrario, onSurfaceTextureAvailable vai iniciar
            } else {
                Log.e(TAG, "Falha ao preparar encoder de video/audio")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar RtspServer", e)
        }
    }

    fun stop() {
        try {
            rtspServerCamera2?.stopStream()
            rtspServerCamera2?.stopPreview()
            rtspServerCamera2 = null
            Log.i(TAG, "RTSP parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar RTSP", e)
        }
    }

    fun isStreaming(): Boolean = rtspServerCamera2?.isStreaming ?: false

    // TextureView callbacks
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        try {
            rtspServerCamera2?.startPreview()
            if (rtspServerCamera2?.isStreaming == false) {
                rtspServerCamera2?.startStream()
                Log.i(TAG, "RTSP stream iniciado via TextureView")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar preview", e)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stop()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    // ConnectChecker callbacks
    override fun onConnectionStarted(url: String) {
        Log.i(TAG, "Cliente conectando: $url")
    }

    override fun onConnectionSuccess() {
        Log.i(TAG, "Cliente conectado")
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Falha de conexao: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {}

    override fun onDisconnect() {
        Log.i(TAG, "Cliente desconectado")
    }

    override fun onAuthError() {
        Log.e(TAG, "Erro de autenticacao")
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "Autenticacao ok")
    }
}
