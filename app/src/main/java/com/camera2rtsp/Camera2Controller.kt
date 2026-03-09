package com.camera2rtsp

import android.content.Context
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraMetadata
import android.util.Log
import android.view.Surface

class Camera2Controller(private val context: Context) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val cameraManager = context.getSystemService(
        Context.CAMERA_SERVICE) as CameraManager
    
    // Configurações atuais
    var currentCameraId = "0" // Wide por padrão
    var iso = 400
    var exposureTime = 16666667L // 1/60s em nanosegundos
    var focusDistance = 0f
    var whiteBalance = CameraMetadata.CONTROL_AWB_MODE_AUTO
    
    fun openCamera(cameraId: String, surface: Surface) {
        currentCameraId = cameraId
        
        try {
            cameraManager.openCamera(cameraId, 
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCaptureSession(surface)
                    }
                    
                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                    }
                    
                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        Log.e("Camera2", "Camera error: $error")
                    }
                }, null)
        } catch (e: SecurityException) {
            Log.e("Camera2", "Permission denied", e)
        }
    }
    
    private fun createCaptureSession(surface: Surface) {
        cameraDevice?.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    updateCaptureRequest()
                }
                
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera2", "Session configuration failed")
                }
            }, null)
    }
    
    fun updateCaptureRequest() {
        val builder = cameraDevice?.createCaptureRequest(
            CameraDevice.TEMPLATE_RECORD) ?: return
        
        builder.apply {
            // Controle manual
            set(CaptureRequest.CONTROL_MODE,
                CameraMetadata.CONTROL_MODE_OFF)
            set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
            set(CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
            if (focusDistance > 0) {
                set(CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            }
        }
        
        try {
            captureSession?.setRepeatingRequest(
                builder.build(), null, null)
        } catch (e: Exception) {
            Log.e("Camera2", "Failed to update capture request", e)
        }
    }
    
    fun updateSettings(params: Map<String, Any>) {
        params["iso"]?.let { iso = (it as Double).toInt() }
        params["exposure"]?.let { 
            exposureTime = (it as Double).toLong() 
        }
        params["focus"]?.let { 
            focusDistance = (it as Double).toFloat() 
        }
        params["whiteBalance"]?.let {
            whiteBalance = when(it as String) {
                "daylight" -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                "cloudy" -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                "tungsten" -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                else -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            }
        }
        updateCaptureRequest()
    }
    
    fun switchCamera(cameraId: String, surface: Surface) {
        cameraDevice?.close()
        openCamera(cameraId, surface)
    }
    
    fun close() {
        captureSession?.close()
        cameraDevice?.close()
        captureSession = null
        cameraDevice = null
    }
}
