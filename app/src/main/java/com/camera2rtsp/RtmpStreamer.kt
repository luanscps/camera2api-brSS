package com.camera2rtsp

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2

/**
 * Wrapper do RtmpCamera2 (RootEncoder library:2.4.5).
 *
 * RtmpCamera2 aceita:
 *   - OpenGlView  -> preview com OpenGL
 *   - Context     -> modo background (sem preview na tela)
 *
 * Usamos Context (background) porque a TextureView de preview
 * ja e gerenciada pelo RtspServer que usa RtspServerCamera2.
 * As duas instancias de camera nao podem compartilhar a mesma View.
 */
class RtmpStreamer(
    private val ctrl: Camera2Controller,
    private val checker: ConnectChecker
) {
    private val tag = "RtmpStreamer"

    var camera: RtmpCamera2? = null
        private set

    val isStreaming: Boolean get() = camera?.isStreaming == true

    fun init(context: Context) {
        if (camera != null) return
        camera = RtmpCamera2(context, checker)
        ctrl.rtmpCamera = camera
        Log.i(tag, "RtmpCamera2 inicializado (modo background)")
    }

    fun startPreview(facing: CameraHelper.Facing = CameraHelper.Facing.BACK) {
        val cam = camera ?: return
        try { if (!cam.isOnPreview) cam.startPreview(facing) }
        catch (e: Exception) { Log.e(tag, "Erro ao iniciar preview", e) }
    }

    fun stopPreview() {
        try { camera?.stopCamera() }
        catch (e: Exception) { Log.e(tag, "Erro ao parar preview", e) }
    }

    fun startStream(context: Context, url: String) {
        val cam = camera ?: run { Log.e(tag, "init() nao chamado"); return }
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
