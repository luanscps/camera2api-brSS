package com.camera2rtsp

data class CameraCapabilities(
    val cameraId: String,
    val hardwareLevel: String,  // LEGACY, LIMITED, FULL, LEVEL_3
    val facing: String,         // BACK, FRONT, EXTERNAL
    val name: String,           // Wide, Ultra Wide, Telephoto, Frontal
    
    // Capabilities flags
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val supportsRaw: Boolean,
    val supportsBurstCapture: Boolean,
    val supportsDepthOutput: Boolean,
    val supportsLogicalMultiCamera: Boolean,
    
    // Ranges disponíveis
    val isoRange: Pair<Int, Int>?,           // min, max
    val exposureTimeRange: Pair<Long, Long>?, // ns min, ns max
    val evRange: Pair<Int, Int>?,            // min, max
    val focusDistanceRange: Pair<Float, Float>?,
    val zoomRange: Pair<Float, Float>?,
    val fpsRanges: List<Pair<Int, Int>>,    // [(min, max), ...]
    
    // Formatos e resoluções
    val availableResolutions: List<String>,  // ["3840x2160", "1920x1080", ...]
    val supportedAFModes: List<String>,
    val supportedAEModes: List<String>,
    val supportedAWBModes: List<String>,
    
    // Hardware físico
    val hasFlash: Boolean,
    val hasOIS: Boolean,
    val focalLengths: List<Float>,
    val apertures: List<Float>
)
