package com.example.dualcamdirector.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.MediaStore
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dualcamdirector.camera.CameraRole
import com.example.dualcamdirector.camera.DualCameraCaptureHelper
import com.example.dualcamdirector.camera.DualCameraManager
import com.example.dualcamdirector.camera.DualCameraVideoRecorder
import com.example.dualcamdirector.camera.HardwareCameraView
import com.example.dualcamdirector.camera.SimulatedCameraFeed
import com.example.dualcamdirector.ui.controls.CameraControls
import com.example.dualcamdirector.ui.dialogs.CameraInfoDialog
import com.example.dualcamdirector.ui.frames.FancyFrameType
import com.example.dualcamdirector.ui.hud.CameraHud
import com.example.dualcamdirector.ui.inset.DraggableInsetView
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DualCamDirectorApp() {
    val context = LocalContext.current
    
    // Check required permissions
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (!hasCameraPermission) {
        PermissionRequiredScreen(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        )
    } else {
        CameraDirectorContent()
    }
}

@Composable
private fun CameraDirectorContent() {
    val context = LocalContext.current
    val cameraManager = remember { DualCameraManager(context) }
    val camState by cameraManager.camState.collectAsState()

    var selectedFrame by remember { mutableStateOf(FancyFrameType.POSTAGE_STAMP) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showFlashOverlay by remember { mutableStateOf(false) }
    var rotationOffset by remember { mutableIntStateOf(0) }

    // Live TextureView references for hardware-accelerated snapshot capture
    var mainTextureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var insetTextureViewRef by remember { mutableStateOf<TextureView?>(null) }

    // Saved media gallery thumbnail & URI
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var lastCapturedMimeType by remember { mutableStateOf<String?>("image/jpeg") }
    var lastCapturedThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isCapturingSnapshot by remember { mutableStateOf(false) }

    // Live video composite recorder
    val videoRecorder = remember { DualCameraVideoRecorder(context) }

    // Draggable Inset dynamic positioning in view pixels
    var insetBoundsX by remember { mutableFloatStateOf(40f) }
    var insetBoundsY by remember { mutableFloatStateOf(160f) }
    var insetBoundsW by remember { mutableFloatStateOf(300f) }
    var insetBoundsH by remember { mutableFloatStateOf(400f) }
    var parentViewW by remember { mutableFloatStateOf(1080f) }
    var parentViewH by remember { mutableFloatStateOf(2400f) }

    // Recording duration timer
    LaunchedEffect(camState.isRecording) {
        if (camState.isRecording) {
            var duration = 0L
            while (camState.isRecording) {
                delay(1000L)
                duration++
                cameraManager.updateRecordingDuration(duration)
            }
        }
    }

    // Lifecycle cleanup
    DisposableEffect(Unit) {
        onDispose {
            videoRecorder.release()
            cameraManager.stopCameras()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // MAIN CAMERA FULLSCREEN VIEW
        HardwareCameraView(
            onSurfaceTextureAvailable = { st ->
                cameraManager.setMainSurface(st)
            },
            sensorOrientation = cameraManager.mainSensorOrientation,
            isFrontCamera = cameraManager.isMainFront,
            previewWidth = cameraManager.mainPreviewSize.width,
            previewHeight = cameraManager.mainPreviewSize.height,
            rotationOffset = rotationOffset,
            onTextureViewRef = { mainTextureViewRef = it },
            modifier = Modifier.fillMaxSize()
        )

        // DRAGGABLE INSET PIP VIEW (Can be moved around even during video recording!)
        DraggableInsetView(
            frameType = selectedFrame,
            isRecording = camState.isRecording,
            onSwapClick = {
                cameraManager.swapCameras()
                Toast.makeText(context, "Swapped to Main: ${camState.insetRole.name}", Toast.LENGTH_SHORT).show()
            },
            onInsetBoundsChanged = { x, y, w, h, pw, ph ->
                insetBoundsX = x
                insetBoundsY = y
                insetBoundsW = w
                insetBoundsH = h
                parentViewW = pw
                parentViewH = ph
            },
            modifier = Modifier.fillMaxSize()
        ) {
            // SECOND CAMERA (INSET VIEW) - Real physical camera stream
            HardwareCameraView(
                onSurfaceTextureAvailable = { st ->
                    cameraManager.setInsetSurface(st)
                },
                sensorOrientation = cameraManager.insetSensorOrientation,
                isFrontCamera = cameraManager.isInsetFront,
                previewWidth = cameraManager.insetPreviewSize.width,
                previewHeight = cameraManager.insetPreviewSize.height,
                rotationOffset = rotationOffset,
                onTextureViewRef = { insetTextureViewRef = it },
                modifier = Modifier.fillMaxSize()
            )
        }

        // TOP HUD (Recording status, concurrency badge, specs dialog, orientation calibration, quick action icons)
        CameraHud(
            camState = camState,
            onInfoClick = { showInfoDialog = true },
            onRotateClick = {
                rotationOffset = (rotationOffset + 90) % 360
                Toast.makeText(context, "Calibrated: +${rotationOffset}°", Toast.LENGTH_SHORT).show()
            },
            onSwapCameras = {
                cameraManager.swapCameras()
                Toast.makeText(context, "Cameras Swapped!", Toast.LENGTH_SHORT).show()
            },
            onToggleTorch = {
                cameraManager.toggleTorch(!camState.isTorchOn)
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // BOTTOM CONTROLS (Dual zoom 0.6x-10x, fancy frames carousel, record button, swap, flash, gallery thumbnail)
        CameraControls(
            camState = camState,
            selectedFrame = selectedFrame,
            onFrameSelect = { selectedFrame = it },
            onMainZoomChange = { cameraManager.setMainZoom(it) },
            onInsetZoomChange = { cameraManager.setInsetZoom(it) },
            onSwapCameras = {
                cameraManager.swapCameras()
                Toast.makeText(context, "Cameras Swapped!", Toast.LENGTH_SHORT).show()
            },
            onToggleTorch = {
                cameraManager.toggleTorch(!camState.isTorchOn)
            },
            onToggleRecord = {
                if (camState.isRecording) {
                    cameraManager.stopRecording()
                    videoRecorder.stopRecording { uri, thumbnail ->
                        if (uri != null) {
                            lastCapturedUri = uri
                            lastCapturedMimeType = "video/mp4"
                            if (thumbnail != null) {
                                lastCapturedThumbnail = Bitmap.createScaledBitmap(thumbnail, 140, 140, true)
                            }
                            Toast.makeText(context, "Video recording saved to Gallery (DCIM/Camera)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to save video", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val mainTv = mainTextureViewRef
                    val insetTv = insetTextureViewRef
                    if (mainTv != null && insetTv != null) {
                        cameraManager.startRecording()
                        videoRecorder.startRecording(
                            mainTextureView = mainTv,
                            insetTextureView = insetTv,
                            getFrameType = { selectedFrame },
                            getInsetBounds = {
                                DualCameraVideoRecorder.InsetBounds(
                                    x = insetBoundsX,
                                    y = insetBoundsY,
                                    width = insetBoundsW,
                                    height = insetBoundsH,
                                    parentWidth = parentViewW,
                                    parentHeight = parentViewH
                                )
                            },
                            onDurationUpdate = { seconds ->
                                cameraManager.updateRecordingDuration(seconds)
                            },
                            onError = { err ->
                                Toast.makeText(context, "Recording error: $err", Toast.LENGTH_SHORT).show()
                                cameraManager.stopRecording()
                            }
                        )
                        Toast.makeText(context, "Recording started! You can drag the inset freely.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Camera preview initializing...", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCaptureSnapshot = {
                if (isCapturingSnapshot) return@CameraControls
                isCapturingSnapshot = true
                showFlashOverlay = true

                val mainTv = mainTextureViewRef
                val insetTv = insetTextureViewRef

                if (mainTv != null && insetTv != null && mainTv.isAvailable && insetTv.isAvailable) {
                    val mainBmp = DualCameraCaptureHelper.captureTransformedBitmap(mainTv)
                    val insetBmp = DualCameraCaptureHelper.captureTransformedBitmap(insetTv)

                    if (mainBmp != null && insetBmp != null) {
                        val currentFrame = selectedFrame
                        val ix = insetBoundsX
                        val iy = insetBoundsY
                        val iw = insetBoundsW
                        val ih = insetBoundsH
                        val pw = parentViewW
                        val ph = parentViewH

                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val composite = DualCameraCaptureHelper.compositeDualShot(
                                    mainBitmap = mainBmp,
                                    insetBitmap = insetBmp,
                                    frameType = currentFrame,
                                    insetX = ix,
                                    insetY = iy,
                                    insetW = iw,
                                    insetH = ih,
                                    viewW = pw,
                                    viewH = ph
                                )
                                // Free raw/intermediate bitmaps immediately
                                mainBmp.recycle()
                                insetBmp.recycle()

                                val uri = DualCameraCaptureHelper.saveBitmapToGallery(context, composite)
                                val thumb = Bitmap.createScaledBitmap(composite, 140, 140, true)
                                composite.recycle()

                                withContext(Dispatchers.Main) {
                                    if (uri != null) {
                                        lastCapturedUri = uri
                                        lastCapturedMimeType = "image/jpeg"
                                        lastCapturedThumbnail = thumb
                                        Toast.makeText(context, "Photo saved to Gallery (DCIM/Camera)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                                    }
                                    isCapturingSnapshot = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Capture error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    isCapturingSnapshot = false
                                }
                            }
                        }
                    } else {
                        mainBmp?.recycle()
                        insetBmp?.recycle()
                        isCapturingSnapshot = false
                        Toast.makeText(context, "Camera preview not ready", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    isCapturingSnapshot = false
                    Toast.makeText(context, "Camera feeds initializing...", Toast.LENGTH_SHORT).show()
                }
            },
            lastCapturedThumbnail = lastCapturedThumbnail,
            onGalleryClick = {
                if (lastCapturedUri != null) {
                    val mime = lastCapturedMimeType ?: "image/jpeg"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(lastCapturedUri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val fallback = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        try { context.startActivity(fallback) } catch (_: Exception) {}
                    }
                } else {
                    val fallback = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    try {
                        context.startActivity(fallback)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No media captured yet", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Shutter Flash Effect
        AnimatedVisibility(
            visible = showFlashOverlay,
            enter = fadeIn(tween(50)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
            LaunchedEffect(Unit) {
                delay(120)
                showFlashOverlay = false
            }
        }

        // Specs & Hardware Info Dialog
        if (showInfoDialog) {
            CameraInfoDialog(
                camState = camState,
                onDismiss = { showInfoDialog = false }
            )
        }
    }
}

@Composable
private fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF10121A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Dual-Camera Director",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "This app requires access to both front and back cameras and microphone to stream dual picture-in-picture feeds and record video.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Grant Permissions",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
