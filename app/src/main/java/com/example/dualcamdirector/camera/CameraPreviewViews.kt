package com.example.dualcamdirector.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AspectRatioCameraContainer(
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val containerW = maxWidth
        val containerH = maxHeight

        val containerRatio = containerW.value / containerH.value

        val targetW: androidx.compose.ui.unit.Dp
        val targetH: androidx.compose.ui.unit.Dp

        if (containerRatio > aspectRatio) {
            targetW = containerW
            targetH = containerW / aspectRatio
        } else {
            targetH = containerH
            targetW = containerH * aspectRatio
        }

        Box(
            modifier = Modifier
                .size(targetW, targetH)
                .align(Alignment.Center)
        ) {
            content()
        }
    }
}

private fun getDisplayRotation(view: View): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.display?.let { return it.rotation }
    }
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.let { return it.rotation }
            }
            @Suppress("DEPRECATION")
            return context.windowManager.defaultDisplay.rotation
        }
        context = context.baseContext
    }
    val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    @Suppress("DEPRECATION")
    return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
}

@Composable
fun HardwareCameraView(
    onSurfaceTextureAvailable: (SurfaceTexture) -> Unit,
    sensorOrientation: Int = 90,
    isFrontCamera: Boolean = false,
    previewWidth: Int = 1920,
    previewHeight: Int = 1080,
    rotationOffset: Int = 0,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    fun configureTransform(tv: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        val rotation = getDisplayRotation(tv)
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())

        val pWidth = maxOf(previewWidth, previewHeight).toFloat()
        val pHeight = minOf(previewWidth, previewHeight).toFloat()
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            // LANDSCAPE MODE (AOSP Standard Camera2 transform)
            val bufferRect = RectF(0f, 0f, pHeight, pWidth)
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)

            val scale = Math.max(
                viewWidth.toFloat() / pWidth,
                viewHeight.toFloat() / pHeight
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            // REVERSE PORTRAIT
            val scale = Math.max(
                viewWidth.toFloat() / pHeight,
                viewHeight.toFloat() / pWidth
            )
            val sx = (pHeight * scale) / viewWidth.toFloat()
            val sy = (pWidth * scale) / viewHeight.toFloat()
            matrix.setScale(sx, sy, centerX, centerY)
            matrix.postRotate(180f, centerX, centerY)
        } else {
            // STANDARD PORTRAIT (ROTATION_0)
            // Stream is natively delivered upright portrait by HAL.
            // Center-crop to fill view without stretching (100% square pixels):
            val scale = Math.max(
                viewWidth.toFloat() / pHeight,
                viewHeight.toFloat() / pWidth
            )
            val sx = (pHeight * scale) / viewWidth.toFloat()
            val sy = (pWidth * scale) / viewHeight.toFloat()
            matrix.setScale(sx, sy, centerX, centerY)
        }

        // Manual rotation offset if user triggers calibrate button
        if (rotationOffset != 0) {
            matrix.postRotate(rotationOffset.toFloat(), centerX, centerY)
        }

        // Horizontal flip for front-facing selfie camera
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, centerX, centerY)
        }

        tv.setTransform(matrix)
    }

    LaunchedEffect(
        sensorOrientation,
        isFrontCamera,
        previewWidth,
        previewHeight,
        rotationOffset,
        configuration.orientation
    ) {
        textureViewRef?.let { tv ->
            if (tv.width > 0 && tv.height > 0) {
                configureTransform(tv, tv.width, tv.height)
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                textureViewRef = this
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Layout change listener: triggers whenever view bounds change (e.g. rotation, resizing, inset resize)
                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val w = right - left
                    val h = bottom - top
                    if (w > 0 && h > 0 && (w != (oldRight - oldLeft) || h != (oldBottom - oldTop))) {
                        configureTransform(this, w, h)
                    }
                }

                var hasConfiguredInitialFrame = false

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        configureTransform(this@apply, w, h)
                        onSurfaceTextureAvailable(st)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        configureTransform(this@apply, w, h)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                        // Re-apply on first frame to prevent camera HAL/driver from wiping the transform matrix
                        if (!hasConfiguredInitialFrame) {
                            hasConfiguredInitialFrame = true
                            post {
                                if (width > 0 && height > 0) {
                                    configureTransform(this@apply, width, height)
                                }
                            }
                        }
                    }
                }

                if (isAvailable && surfaceTexture != null) {
                    post {
                        if (width > 0 && height > 0) {
                            configureTransform(this@apply, width, height)
                        }
                    }
                    onSurfaceTextureAvailable(surfaceTexture!!)
                }
            }
        },
        update = { tv ->
            textureViewRef = tv
            if (tv.width > 0 && tv.height > 0) {
                configureTransform(tv, tv.width, tv.height)
            }
        }
    )
}

@Composable
fun SimulatedCameraFeed(
    role: CameraRole,
    zoomRatio: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SimFeedAnim")
    
    // Head tilt / tracking motion
    val headOffset by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HeadOffset"
    )

    // Pulse for focus ring
    val focusPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FocusPulse"
    )

    // Zoom scaling calculation (0.6x to 10x)
    // 0.6x shows wide lens, 1.0x normal, >2x zooms in
    val visualScale = remember(zoomRatio) {
        when {
            zoomRatio < 1.0f -> 0.75f + (zoomRatio - 0.6f) * 0.625f
            else -> (1.0f + (zoomRatio - 1.0f) * 0.25f).coerceAtMost(3.0f)
        }
    }

    val isFront = role == CameraRole.FRONT

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(
                Brush.radialGradient(
                    colors = if (isFront) {
                        listOf(Color(0xFF2C3E50), Color(0xFF1A1A24), Color(0xFF0F0C20))
                    } else {
                        listOf(Color(0xFF23395B), Color(0xFF16192E), Color(0xFF0B0C10))
                    }
                )
            )
    ) {
        // Dynamic room background / scenery with zoom scaling
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = visualScale,
                    scaleY = visualScale
                )
        ) {
            val w = size.width
            val h = size.height

            // Studio / Room ambiance lighting
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isFront) Color(0xFFFFB07C) else Color(0xFF64B5F6)).copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.7f, h * 0.25f),
                    radius = w * 0.8f
                )
            )

            // Grid lines / bokeh elements
            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f * i),
                    center = Offset(w * (0.2f * i), h * (0.15f * i + 0.2f)),
                    radius = 30f * i
                )
            }

            // Grid pattern
            val step = w / 6f
            for (x in 0..6) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(x * step, 0f),
                    end = Offset(x * step, h),
                    strokeWidth = 1f
                )
            }
        }

        // Subject / Face Simulation (especially for Selfie/Front camera)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = visualScale,
                    scaleY = visualScale,
                    translationX = if (isFront) headOffset else 0f
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isFront) {
                // Director Selfie Face Simulation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Face Avatar / Silhouette
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFFD1B3), Color(0xFFDE9E78))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = Color(0xFF3E2723),
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.size(4.dp))

                    // Shoulders / Torso
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 45.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF334E68), Color(0xFF102A43))
                                ),
                                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                    )
                }

                // AI Face Tracking Box
                Box(
                    modifier = Modifier
                        .size((90 * focusPulse).dp)
                        .graphicsLayer(
                            translationX = headOffset * 0.5f,
                            translationY = -10f
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = size.width
                        val stroke = 2.dp.toPx()
                        val cornerLen = 14.dp.toPx()
                        val color = Color(0xFF00FFCC)

                        // Top-left corner
                        drawLine(color, Offset(0f, 0f), Offset(cornerLen, 0f), stroke)
                        drawLine(color, Offset(0f, 0f), Offset(0f, cornerLen), stroke)

                        // Top-right corner
                        drawLine(color, Offset(s, 0f), Offset(s - cornerLen, 0f), stroke)
                        drawLine(color, Offset(s, 0f), Offset(s, cornerLen), stroke)

                        // Bottom-left corner
                        drawLine(color, Offset(0f, s), Offset(cornerLen, s), stroke)
                        drawLine(color, Offset(0f, s), Offset(0f, s - cornerLen), stroke)

                        // Bottom-right corner
                        drawLine(color, Offset(s, s), Offset(s - cornerLen, s), stroke)
                        drawLine(color, Offset(s, s), Offset(s, s - cornerLen), stroke)
                    }
                }
            } else {
                // Back Scene Simulation (Landscape / Subject focus)
                Canvas(modifier = Modifier.size(80.dp)) {
                    val s = size.width
                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = 0.8f),
                        radius = s * 0.35f,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                        )
                    )
                    drawCircle(
                        color = Color(0xFFFFD700),
                        radius = 4.dp.toPx()
                    )
                }
            }
        }

        // Live Lens & Zoom Info Overlay Badge
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = if (isRecording) Color.Red else Color(0xFF00E5FF),
                modifier = Modifier.size(7.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${role.name} • ${String.format(java.util.Locale.US, "%.1fx", zoomRatio)}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
