package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.AutoFitTextureView

/**
 * Wrapper do RtmpCamera2 (RootEncoder 2.6.1).
 *
 * RtmpCamera2 aceita apenas:
 *   - OpenGlView  (para preview com OpenGL)
 *   - Context     (modo background, sem preview na tela)
 *
 * AutoFitTextureView NAO e um OpenGlView, portanto usamos o construtor
 * Context (background) e exibimos o preview capturando os frames via
 * SurfaceTexture da propria TextureView.
 *
 * Fluxo:
 *   1. init(context)       -- cria RtmpCamera2 em modo background
 *   2. startPreview()      -- inicia captura de camera (sem GL view)
 *   3. startStream(url)    -- push RTMP para MediaMTX
 *   4. stopStream()        -- para apenas o push
 *   5. stop()              -- para stream + camera
 */
class RtmpStreamer(
    private val ctrl: Camera2Controller,
    private val checker: ConnectChecker
) {
    private val tag = "RtmpStreamer"

    var camera: RtmpCamera2? = null
        private set

    val isStreaming: Boolean get() = camera?.isStreaming == true

    /**
     * Inicializa com o Context da Activity/Service.
     * Deve ser chamado uma unica vez antes de startPreview.
     */
    fun init(context: Context) {
        if (camera != null) return
        camera = RtmpCamera2(context, checker)
        ctrl.rtmpCamera = camera
        Log.i(tag, "RtmpCamera2 inicializado (modo background/context)")
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
        try { camera?.stopCamera() }
        catch (e: Exception) { Log.e(tag, "Erro ao parar preview", e) }
    }

    /**
     * Prepara encoder e inicia push RTMP.
     * url ex: "rtmp://192.168.1.100:1935/live/stream"
     *
     * prepareVideo(width, height, fps, bitrate_bps, rotation)
     * O rotation e obtido automaticamente via CameraHelper.
     */
    fun startStream(context: Context, url: String) {
        val cam = camera ?: run { Log.e(tag, "init() nao chamado antes de startStream()"); return }
        if (cam.isStreaming) { Log.d(tag, "ja streaming"); return }

        val rotation = CameraHelper.getCameraOrientation(context)
        val vOk = cam.prepareVideo(
            ctrl.currentWidth,
            ctrl.currentHeight,
            ctrl.currentFps,
            ctrl.currentBitrate * 1024,
            rotation
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
