package com.example.dualcamdirector.camera

import android.util.Size

enum class CameraRole {
    BACK,
    FRONT
}

data class CameraLensInfo(
    val id: String,
    val role: CameraRole,
    val minZoom: Float = 0.6f,
    val maxZoom: Float = 10.0f,
    val isConcurrentSupported: Boolean = false,
    val hasFlash: Boolean = false
)

data class DualCamState(
    val mainRole: CameraRole = CameraRole.BACK,
    val insetRole: CameraRole = CameraRole.FRONT,
    val mainZoom: Float = 1.0f,
    val insetZoom: Float = 1.0f,
    val isRecording: Boolean = false,
    val recordingDurationSec: Long = 0L,
    val isTorchOn: Boolean = false,
    val isConcurrentHardwareActive: Boolean = false,
    val hardwareConcurrencyMessage: String = "",
    val availableBackCameras: List<String> = emptyList(),
    val availableFrontCameras: List<String> = emptyList(),
    val mainPreviewSize: Size = Size(1920, 1080),
    val insetPreviewSize: Size = Size(1280, 720),
    val mainSensorOrientation: Int = 90,
    val insetSensorOrientation: Int = 270
)
