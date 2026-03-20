package com.camera2rtsp

import android.content.Context
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView

/**
 * Wrapper do RtmpCamera2 (RootEncoder 2.6.1).
 */
class RtmpStreamer(
    private val ctrl: Camera2Controller,
    private val checker: ConnectChecker
) {
    private val tag = "RtmpStreamer"

    var camera: RtmpCamera2? = null
        private set

    val isStreaming: Boolean get() = camera?.isStreaming == true
    val isOnPreview: Boolean get() = camera?.isOnPreview == true

    // -- Preview MJPEG para WebGUI ------------------------------------------
    @Volatile private var lastCaptureMs = 0L
    private var webPreviewEnabled = false

    companion object {
        /** Último frame JPEG capturado — servido pelo WebControlServer em /api/preview */
        @Volatile var lastFrameJpeg: ByteArray? = null
    }

    /**
     * Habilita captura de frames JPEG para o /api/preview da WebGUI (throttle 1fps).
     * Usa Camera2ApiManager.addImageListener — Surface auxiliar independente do RTMP.
     * API real (2.6.1): addImageListener(width, height, format, maxImages, autoClose, ImageCallback)
     */
    fun enableWebPreview() {
        val cam = camera ?: return
        if (webPreviewEnabled) return
        try {
            val w = ctrl.currentWidth
            val h = ctrl.currentHeight
            cam.addImageListener(
                w, h,
                ImageFormat.JPEG,
                2,       // maxImages
                true,    // autoClose — a lib fecha o Image após o callback
                object : Camera2ApiManager.ImageCallback {
                    override fun onImageAvailable(image: Image) {
                        try {
                            val now = System.currentTimeMillis()
                            if (now - lastCaptureMs >= 1000L) { // throttle 1fps
                                lastCaptureMs = now
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                lastFrameJpeg = bytes
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "enableWebPreview frame error: ${e.message}")
                        }
                        // Não chamamos image.close() pois autoClose=true
                    }
                }
            )
            webPreviewEnabled = true
            Log.i(tag, "Web preview habilitado (1fps JPEG ${w}x${h})")
        } catch (e: Exception) {
            Log.w(tag, "enableWebPreview: addImageListener falhou — ${e.message}")
        }
    }

    /** Desabilita captura e limpa o buffer. */
    fun disableWebPreview() {
        try {
            camera?.removeImageListener()
        } catch (e: Exception) {
            Log.w(tag, "disableWebPreview: ${e.message}")
        }
        webPreviewEnabled = false
        lastFrameJpeg = null
        Log.i(tag, "Web preview desabilitado")
    }

    // -- Inicialização --------------------------------------------------------

    fun initWithView(view: OpenGlView, context: Context) {
        releaseCamera()
        camera = RtmpCamera2(view, checker)
        ctrl.rtmpCamera = camera
        Log.i(tag, "RtmpCamera2 inicializado com OpenGlView")
    }

    fun initBackground(context: Context) {
        if (camera != null && !isOnPreview) return
        releaseCamera()
        camera = RtmpCamera2(context, checker)
        ctrl.rtmpCamera = camera
        Log.i(tag, "RtmpCamera2 inicializado (modo background)")
    }

    // -- Preview --------------------------------------------------------------

    fun startPreview(facing: CameraHelper.Facing = CameraHelper.Facing.BACK) {
        val cam = camera ?: return
        try {
            if (!cam.isOnPreview) cam.startPreview(facing)
            enableWebPreview()
        } catch (e: Exception) {
            Log.e(tag, "Erro ao iniciar preview", e)
        }
    }

    fun stopPreview() {
        try {
            disableWebPreview()
            if (camera?.isOnPreview == true) camera?.stopPreview()
        } catch (e: Exception) {
            Log.e(tag, "Erro ao parar preview", e)
        }
    }

    fun switchCamera(facing: CameraHelper.Facing) {
        val cam = camera ?: return
        try {
            disableWebPreview()
            if (cam.isOnPreview) cam.stopPreview()
            cam.startPreview(facing)
            enableWebPreview()
            Log.i(tag, "Camera trocada para $facing")
        } catch (e: Exception) {
            Log.e(tag, "Erro ao trocar camera", e)
        }
    }

    fun switchCameraById(cameraId: String, facing: CameraHelper.Facing) {
        val cam = camera ?: return
        try {
            disableWebPreview()
            if (cam.isOnPreview) cam.stopPreview()
            cam.startPreview(facing)
            ctrl.currentCameraId = cameraId
            enableWebPreview()
            Log.i(tag, "Camera trocada para ID=$cameraId facing=$facing")
        } catch (e: Exception) {
            Log.e(tag, "Erro ao trocar camera por ID", e)
        }
    }

    // -- Stream ---------------------------------------------------------------

    fun startStream(context: Context, url: String) {
        val cam = camera ?: run { Log.e(tag, "init não chamado"); return }
        if (cam.isStreaming) { Log.d(tag, "já streaming"); return }
        val rotation = CameraHelper.getCameraOrientation(context)
        val vOk = cam.prepareVideo(
            ctrl.currentWidth, ctrl.currentHeight,
            ctrl.currentFps, ctrl.currentBitrate * 1024, rotation
        )
        val aOk = cam.prepareAudio(128 * 1024, 44100, true)
        if (vOk && aOk) {
            cam.startStream(url)
            Log.i(tag, "RTMP iniciado: $url")
        } else {
            Log.e(tag, "Falha ao preparar encoder: vOk=$vOk aOk=$aOk")
        }
    }

    fun stopStream() {
        try { if (camera?.isStreaming == true) camera?.stopStream() }
        catch (e: Exception) { Log.e(tag, "Erro ao parar stream", e) }
    }

    fun stop() {
        disableWebPreview()
        stopStream()
        stopPreview()
        releaseCamera()
    }

    private fun releaseCamera() {
        disableWebPreview()
        try {
            camera?.let {
                if (it.isStreaming) it.stopStream()
                if (it.isOnPreview) it.stopPreview()
            }
        } catch (e: Exception) {
            Log.w(tag, "releaseCamera: ${e.message}")
        }
        camera = null
        ctrl.rtmpCamera = null
    }
}
