package com.camera2rtsp

data class CameraCapabilities(
    val cameraId: String,
    val hardwareLevel: String,  // LEGACY, LIMITED, FULL, LEVEL_3
    val facing: String,         // BACK, FRONT, EXTERNAL
    val name: String,           // Wide, Ultra Wide, Telephoto, Frontal
    val isDepth: Boolean,       // true = sensor Depth/ToF (ocultar da UI)

    // Capabilities flags
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val supportsRaw: Boolean,
    val supportsBurstCapture: Boolean,
    val supportsDepthOutput: Boolean,
    val supportsLogicalMultiCamera: Boolean,

    // Ranges como List<Number> para serializar como array JSON [min, max]
    val isoRange: List<Int>?,            // [min, max]
    val exposureTimeRange: List<Long>?,  // [min_ns, max_ns]
    val evRange: List<Int>?,             // [min, max]
    val focusDistanceRange: List<Float>?,
    val zoomRange: List<Float>?,
    val fpsRanges: List<List<Int>>,      // [[min,max], ...]

    // Formatos e resoluções
    val availableResolutions: List<String>,
    val supportedAFModes: List<String>,
    val supportedAEModes: List<String>,
    val supportedAWBModes: List<String>,

    // Hardware físico
    val hasFlash: Boolean,
    val hasOIS: Boolean,
    val focalLengths: List<Float>,
    val apertures: List<Float>
)
