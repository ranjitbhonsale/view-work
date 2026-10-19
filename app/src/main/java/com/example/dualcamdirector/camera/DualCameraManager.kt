package com.example.dualcamdirector.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "DualCameraManager"

class DualCameraManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    var backCameraId: String? = null
        private set
    var frontCameraId: String? = null
        private set

    var backSensorOrientation: Int = 90
        private set
    var frontSensorOrientation: Int = 270
        private set

    var mainPreviewSize: Size = Size(1920, 1080)
        private set
    var insetPreviewSize: Size = Size(1280, 720)
        private set

    val mainSensorOrientation: Int
        get() = if (_camState.value.mainRole == CameraRole.BACK) backSensorOrientation else frontSensorOrientation

    val insetSensorOrientation: Int
        get() = if (_camState.value.insetRole == CameraRole.BACK) backSensorOrientation else frontSensorOrientation

    val isMainFront: Boolean
        get() = _camState.value.mainRole == CameraRole.FRONT

    val isInsetFront: Boolean
        get() = _camState.value.insetRole == CameraRole.FRONT

    private var primaryCameraDevice: CameraDevice? = null
    private var secondaryCameraDevice: CameraDevice? = null

    private var primaryCaptureSession: CameraCaptureSession? = null
    private var secondaryCaptureSession: CameraCaptureSession? = null

    private var primaryRequestBuilder: CaptureRequest.Builder? = null
    private var secondaryRequestBuilder: CaptureRequest.Builder? = null

    private var primarySurface: Surface? = null
    private var secondarySurface: Surface? = null

    private var mainSurfaceTexture: SurfaceTexture? = null
    private var insetSurfaceTexture: SurfaceTexture? = null

    private val _camState = MutableStateFlow(DualCamState())
    val camState: StateFlow<DualCamState> = _camState.asStateFlow()

    init {
        detectCameraCapabilities()
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("Camera2Background").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun detectCameraCapabilities() {
        try {
            val backList = mutableListOf<String>()
            val frontList = mutableListOf<String>()

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    backList.add(id)
                    if (backCameraId == null) {
                        backCameraId = id
                        backSensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                    }
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontList.add(id)
                    if (frontCameraId == null) {
                        frontCameraId = id
                        frontSensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
                    }
                }
            }

            // Fallbacks if IDs not assigned
            if (backCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                backCameraId = cameraManager.cameraIdList[0]
            }
            if (frontCameraId == null && cameraManager.cameraIdList.size > 1) {
                frontCameraId = cameraManager.cameraIdList[1]
            }

            _camState.update { current ->
                current.copy(
                    isConcurrentHardwareActive = true,
                    hardwareConcurrencyMessage = "Dual Camera Stream Active",
                    availableBackCameras = backList,
                    availableFrontCameras = frontList,
                    mainSensorOrientation = backSensorOrientation,
                    insetSensorOrientation = frontSensorOrientation
                )
            }

            Log.i(TAG, "Detected Cameras -> Back: $backCameraId ($backSensorOrientation°), Front: $frontCameraId ($frontSensorOrientation°)")
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting camera capabilities", e)
        }
    }

    private fun getOptimalPreviewSize(cameraId: String, preferredWidth: Int, preferredHeight: Int): Size {
        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java)
            if (!sizes.isNullOrEmpty()) {
                val targetRatio = preferredWidth.toFloat() / preferredHeight.toFloat()

                // 1. Prioritize sizes matching preferred aspect ratio (e.g. 16:9 within 3% tolerance)
                val matchingRatioSizes = sizes.filter {
                    val r = it.width.toFloat() / it.height.toFloat()
                    kotlin.math.abs(r - targetRatio) < 0.05f
                }
                if (matchingRatioSizes.isNotEmpty()) {
                    val match = matchingRatioSizes.minByOrNull {
                        kotlin.math.abs(it.width - preferredWidth) + kotlin.math.abs(it.height - preferredHeight)
                    }
                    if (match != null) {
                        Log.i(TAG, "getOptimalPreviewSize for $cameraId: matched target aspect ratio $targetRatio -> ${match.width}x${match.height}")
                        return match
                    }
                }

                // 2. Fallback to 4:3 if preferred is not available
                val fourThreeSizes = sizes.filter {
                    val r = it.width.toFloat() / it.height.toFloat()
                    kotlin.math.abs(r - (4f / 3f)) < 0.05f
                }
                if (fourThreeSizes.isNotEmpty()) {
                    val match = fourThreeSizes.minByOrNull {
                        kotlin.math.abs(it.width - preferredWidth) + kotlin.math.abs(it.height - preferredHeight)
                    }
                    if (match != null) {
                        Log.i(TAG, "getOptimalPreviewSize for $cameraId: fallback to 4:3 ratio -> ${match.width}x${match.height}")
                        return match
                    }
                }

                // 3. Fallback to closest overall size
                val match = sizes.minByOrNull {
                    kotlin.math.abs(it.width - preferredWidth) + kotlin.math.abs(it.height - preferredHeight)
                }
                if (match != null) return match
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting optimal preview size for $cameraId", e)
        }
        return Size(preferredWidth, preferredHeight)
    }

    @Volatile
    private var isPaused = false

    @Volatile
    private var isOpeningPrimary = false

    @Volatile
    private var isOpeningSecondary = false

    fun setMainSurface(st: SurfaceTexture) {
        mainSurfaceTexture = st
        startBackgroundThread()
        if (!isPaused) {
            openPrimaryCamera(st)
        }
    }

    fun setInsetSurface(st: SurfaceTexture) {
        insetSurfaceTexture = st
        startBackgroundThread()
        if (!isPaused) {
            openSecondaryCamera(st)
        }
    }

    fun onMainSurfaceDestroyed() {
        Log.i(TAG, "onMainSurfaceDestroyed()")
        closePrimaryCamera()
        mainSurfaceTexture = null
    }

    fun onInsetSurfaceDestroyed() {
        Log.i(TAG, "onInsetSurfaceDestroyed()")
        closeSecondaryCamera()
        insetSurfaceTexture = null
    }

    fun pauseCameras() {
        Log.i(TAG, "pauseCameras()")
        isPaused = true
        isOpeningPrimary = false
        isOpeningSecondary = false
        closePrimaryCamera()
        closeSecondaryCamera()
    }

    fun resumeCameras(mainSurface: SurfaceTexture? = null, insetSurface: SurfaceTexture? = null) {
        Log.i(TAG, "resumeCameras()")
        isPaused = false
        startBackgroundThread()

        if (mainSurface != null && isSurfaceTextureValid(mainSurface)) {
            mainSurfaceTexture = mainSurface
        }
        if (insetSurface != null && isSurfaceTextureValid(insetSurface)) {
            insetSurfaceTexture = insetSurface
        }

        mainSurfaceTexture?.let { st ->
            if (isSurfaceTextureValid(st) && primaryCameraDevice == null && !isOpeningPrimary) {
                openPrimaryCamera(st)
            }
        }

        insetSurfaceTexture?.let { st ->
            if (isSurfaceTextureValid(st) && secondaryCameraDevice == null && !isOpeningSecondary) {
                openSecondaryCamera(st)
            }
        }
    }

    fun stopCameras() {
        Log.i(TAG, "stopCameras()")
        pauseCameras()
        stopBackgroundThread()
    }

    private fun isSurfaceTextureValid(st: SurfaceTexture?): Boolean {
        if (st == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return !st.isReleased
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun openPrimaryCamera(surfaceTexture: SurfaceTexture?) {
        if (isPaused) {
            Log.i(TAG, "openPrimaryCamera skipped: app is paused")
            return
        }
        if (surfaceTexture == null || !isSurfaceTextureValid(surfaceTexture)) {
            Log.w(TAG, "openPrimaryCamera: surfaceTexture is null or released")
            return
        }
        if (primaryCameraDevice != null || isOpeningPrimary) {
            Log.i(TAG, "Primary camera already open or opening")
            return
        }

        val role = _camState.value.mainRole
        val targetCameraId = if (role == CameraRole.BACK) backCameraId else frontCameraId
        if (targetCameraId == null) {
            Log.w(TAG, "No camera ID found for main role $role")
            return
        }

        isOpeningPrimary = true

        try {
            closePrimaryCamera()
            val previewSize = getOptimalPreviewSize(targetCameraId, 1920, 1080)
            mainPreviewSize = previewSize
            _camState.update { it.copy(mainPreviewSize = previewSize) }
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            primarySurface = Surface(surfaceTexture)

            Log.i(TAG, "Opening Primary Camera (ID: $targetCameraId, Role: $role, Size: ${previewSize.width}x${previewSize.height})")

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "Primary Camera onOpened: ${camera.id}")
                    isOpeningPrimary = false
                    primaryCameraDevice = camera
                    createPrimaryCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Primary Camera onDisconnected: ${camera.id}")
                    isOpeningPrimary = false
                    camera.close()
                    primaryCameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Primary Camera onError: ${camera.id}, code: $error")
                    isOpeningPrimary = false
                    camera.close()
                    primaryCameraDevice = null

                    if (!isPaused && (error == ERROR_CAMERA_IN_USE || error == ERROR_MAX_CAMERAS_IN_USE || error == ERROR_CAMERA_DEVICE)) {
                        Log.i(TAG, "Primary camera HAL busy/error $error. Retrying in 350ms...")
                        backgroundHandler?.postDelayed({
                            if (!isPaused && primaryCameraDevice == null && isSurfaceTextureValid(mainSurfaceTexture)) {
                                openPrimaryCamera(mainSurfaceTexture)
                            }
                        }, 350L)
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open primary camera", e)
            isOpeningPrimary = false
        }
    }

    private fun createPrimaryCaptureSession() {
        val device = primaryCameraDevice ?: return
        val surface = primarySurface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            applyZoom(builder, _camState.value.mainRole, _camState.value.mainZoom)

            primaryRequestBuilder = builder

            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    primaryCaptureSession = session
                    try {
                        session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        Log.i(TAG, "Primary camera repeating request active")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting primary repeating request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Primary CaptureSession configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating primary capture session", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSecondaryCamera(surfaceTexture: SurfaceTexture?) {
        if (isPaused) {
            Log.i(TAG, "openSecondaryCamera skipped: app is paused")
            return
        }
        if (surfaceTexture == null || !isSurfaceTextureValid(surfaceTexture)) {
            Log.w(TAG, "openSecondaryCamera: surfaceTexture is null or released")
            return
        }
        if (secondaryCameraDevice != null || isOpeningSecondary) {
            Log.i(TAG, "Secondary camera already open or opening")
            return
        }

        val role = _camState.value.insetRole
        val targetCameraId = if (role == CameraRole.BACK) backCameraId else frontCameraId
        if (targetCameraId == null) {
            Log.w(TAG, "No camera ID found for inset role $role")
            return
        }

        isOpeningSecondary = true

        try {
            closeSecondaryCamera()
            val previewSize = getOptimalPreviewSize(targetCameraId, 1280, 720)
            insetPreviewSize = previewSize
            _camState.update { it.copy(insetPreviewSize = previewSize) }
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            secondarySurface = Surface(surfaceTexture)

            Log.i(TAG, "Opening Secondary Inset Camera (ID: $targetCameraId, Role: $role, Size: ${previewSize.width}x${previewSize.height})")

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "Secondary Inset Camera onOpened: ${camera.id}")
                    isOpeningSecondary = false
                    secondaryCameraDevice = camera
                    createSecondaryCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Secondary Inset Camera onDisconnected: ${camera.id}")
                    isOpeningSecondary = false
                    camera.close()
                    secondaryCameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Secondary Inset Camera onError: ${camera.id}, code: $error")
                    isOpeningSecondary = false
                    camera.close()
                    secondaryCameraDevice = null

                    if (!isPaused && (error == ERROR_CAMERA_IN_USE || error == ERROR_MAX_CAMERAS_IN_USE || error == ERROR_CAMERA_DEVICE)) {
                        Log.i(TAG, "Secondary camera HAL busy/error $error. Retrying in 350ms...")
                        backgroundHandler?.postDelayed({
                            if (!isPaused && secondaryCameraDevice == null && isSurfaceTextureValid(insetSurfaceTexture)) {
                                openSecondaryCamera(insetSurfaceTexture)
                            }
                        }, 350L)
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open secondary camera", e)
            isOpeningSecondary = false
        }
    }

    private fun createSecondaryCaptureSession() {
        val device = secondaryCameraDevice ?: return
        val surface = secondarySurface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            applyZoom(builder, _camState.value.insetRole, _camState.value.insetZoom)

            secondaryRequestBuilder = builder

            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    secondaryCaptureSession = session
                    try {
                        session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        Log.i(TAG, "Secondary inset camera repeating request active")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting secondary repeating request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Secondary CaptureSession configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating secondary capture session", e)
        }
    }

    private fun closePrimaryCamera() {
        try {
            primaryCaptureSession?.close()
            primaryCaptureSession = null
            primaryCameraDevice?.close()
            primaryCameraDevice = null
            primarySurface?.release()
            primarySurface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing primary camera", e)
        }
    }

    private fun closeSecondaryCamera() {
        try {
            secondaryCaptureSession?.close()
            secondaryCaptureSession = null
            secondaryCameraDevice?.close()
            secondaryCameraDevice = null
            secondarySurface?.release()
            secondarySurface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing secondary camera", e)
        }
    }

    private fun applyZoom(builder: CaptureRequest.Builder, role: CameraRole, zoomRatio: Float) {
        val camId = if (role == CameraRole.BACK) backCameraId else frontCameraId
        if (camId == null) return

        try {
            val chars = cameraManager.getCameraCharacteristics(camId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (range != null) {
                    val clamped = zoomRatio.coerceIn(range.lower, range.upper)
                    builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clamped)
                    return
                }
            }

            // Fallback digital crop
            val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            if (activeArray != null && zoomRatio >= 1.0f) {
                val cropWidth = (activeArray.width() / zoomRatio).toInt()
                val cropHeight = (activeArray.height() / zoomRatio).toInt()
                val cropLeft = (activeArray.width() - cropWidth) / 2
                val cropTop = (activeArray.height() - cropHeight) / 2
                val cropRect = Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
                builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying zoom", e)
        }
    }

    fun setMainZoom(zoom: Float) {
        val clamped = zoom.coerceIn(0.6f, 10.0f)
        _camState.update { it.copy(mainZoom = clamped) }

        val builder = primaryRequestBuilder ?: return
        val session = primaryCaptureSession ?: return
        applyZoom(builder, _camState.value.mainRole, clamped)
        try {
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating main zoom", e)
        }
    }

    fun setInsetZoom(zoom: Float) {
        val clamped = zoom.coerceIn(0.6f, 10.0f)
        _camState.update { it.copy(insetZoom = clamped) }

        val builder = secondaryRequestBuilder ?: return
        val session = secondaryCaptureSession ?: return
        applyZoom(builder, _camState.value.insetRole, clamped)
        try {
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating inset zoom", e)
        }
    }

    fun swapCameras() {
        val oldMain = _camState.value.mainRole
        val oldInset = _camState.value.insetRole
        val oldMainZoom = _camState.value.mainZoom
        val oldInsetZoom = _camState.value.insetZoom
        val oldMainOrient = _camState.value.mainSensorOrientation
        val oldInsetOrient = _camState.value.insetSensorOrientation

        _camState.update {
            it.copy(
                mainRole = oldInset,
                insetRole = oldMain,
                mainZoom = oldInsetZoom,
                insetZoom = oldMainZoom,
                mainSensorOrientation = oldInsetOrient,
                insetSensorOrientation = oldMainOrient
            )
        }

        // Restart both cameras on their respective surfaces with swapped IDs
        closePrimaryCamera()
        closeSecondaryCamera()

        mainSurfaceTexture?.let { if (isSurfaceTextureValid(it)) openPrimaryCamera(it) }
        insetSurfaceTexture?.let { if (isSurfaceTextureValid(it)) openSecondaryCamera(it) }
    }

    fun toggleTorch(enable: Boolean) {
        _camState.update { it.copy(isTorchOn = enable) }
        val builder = primaryRequestBuilder ?: return
        val session = primaryCaptureSession ?: return

        if (_camState.value.mainRole == CameraRole.BACK) {
            builder.set(
                CaptureRequest.FLASH_MODE,
                if (enable) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
            )
            try {
                session.setRepeatingRequest(builder.build(), null, backgroundHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling torch", e)
            }
        }
    }

    fun startRecording() {
        _camState.update { it.copy(isRecording = true, recordingDurationSec = 0L) }
    }

    fun updateRecordingDuration(seconds: Long) {
        _camState.update { it.copy(recordingDurationSec = seconds) }
    }

    fun stopRecording() {
        _camState.update { it.copy(isRecording = false) }
    }
}
