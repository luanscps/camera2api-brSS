package com.camera2rtsp

import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.AutoFitTextureView

/**
 * Wrapper do RtmpCamera2 (RootEncoder 2.6.1).
 *
 * Fluxo:
 *   1. init(view)          -- cria RtmpCamera2 passando a TextureView
 *   2. startPreview()      -- inicia preview local na view
 *   3. startStream(url)    -- push RTMP para MediaMTX
 *   4. stopStream()        -- para apenas o push, preview continua
 *   5. stopPreview()       -- para o preview
 */
class RtmpStreamer(
    private val ctrl: Camera2Controller,
    private val checker: ConnectChecker
) {
    private val tag = "RtmpStreamer"

    private var camera: RtmpCamera2? = null

    val isStreaming: Boolean get() = camera?.isStreaming == true
    val isOnPreview: Boolean get() = camera?.isOnPreview == true

    /** Inicializa com a TextureView -- deve ser chamado quando a surface esta disponivel. */
    fun init(view: AutoFitTextureView) {
        if (camera != null) return          // ja inicializado
        camera = RtmpCamera2(view, checker)
        ctrl.rtmpCamera = camera
        Log.i(tag, "RtmpCamera2 inicializado")
    }

    /** Reinicializa forcando uma nova view (ex: volta de onPause). */
    fun reinit(view: AutoFitTextureView) {
        camera = null
        ctrl.rtmpCamera = null
        init(view)
    }

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

    /**
     * Prepara encoder e inicia push RTMP.
     * url ex: "rtmp://192.168.1.100:1935/live/stream"
     */
    fun startStream(url: String) {
        val cam = camera ?: run { Log.e(tag, "init() nao foi chamado antes de startStream()"); return }
        if (cam.isStreaming) { Log.d(tag, "ja streaming"); return }
        val vOk = cam.prepareVideo(
            ctrl.currentWidth, ctrl.currentHeight,
            ctrl.currentFps, ctrl.currentBitrate * 1024
        )
        val aOk = cam.prepareAudio(128 * 1024, 44100, true)
        if (vOk && aOk) {
            cam.startStream(url)
            Log.i(tag, "RTMP stream iniciado: $url")
        } else {
            Log.e(tag, "Falha ao preparar encoder: vOk=$vOk aOk=$aOk")
        }
    }

    fun stopStream() {
        try { if (camera?.isStreaming == true) camera?.stopStream() }
        catch (e: Exception) { Log.e(tag, "Erro ao parar stream", e) }
    }

    fun stop() {
        stopStream()
        stopPreview()
    }
}
