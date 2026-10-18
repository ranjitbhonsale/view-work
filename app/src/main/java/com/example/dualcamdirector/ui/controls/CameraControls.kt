package com.example.dualcamdirector.ui.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dualcamdirector.camera.CameraRole
import com.example.dualcamdirector.camera.DualCamState
import com.example.dualcamdirector.ui.frames.FancyFrameType
import java.util.Locale

@Composable
fun CameraControls(
    camState: DualCamState,
    selectedFrame: FancyFrameType,
    onFrameSelect: (FancyFrameType) -> Unit,
    onMainZoomChange: (Float) -> Unit,
    onInsetZoomChange: (Float) -> Unit,
    onSwapCameras: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleRecord: () -> Unit,
    onCaptureSnapshot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showFrameSelector by remember { mutableStateOf(false) }
    var selectedZoomTab by remember { mutableIntStateOf(0) } // 0: Main, 1: Inset

    val currentZoom = if (selectedZoomTab == 0) camState.mainZoom else camState.insetZoom
    val onCurrentZoomChange = if (selectedZoomTab == 0) onMainZoomChange else onInsetZoomChange

    if (isLandscape) {
        // LANDSCAPE MODE: Record button placed strictly on the SIDE (under right thumb!)
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // SIDE ACTION DECK (Samsung Dual Shot style: Video, Shutter Capsule, Mode)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(66.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TOP BUTTON: Video Recording Button (Circular with Video Camera Icon)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (camState.isRecording) listOf(Color(0xFFFF5252), Color(0xFFD50000))
                                else listOf(Color(0xFF555560), Color(0xFF25252B))
                            )
                        )
                        .border(
                            1.5.dp,
                            if (camState.isRecording) Color.Red else Color.White.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { onToggleRecord() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (camState.isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                        contentDescription = "Video Record",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // MIDDLE BUTTON: Shutter / Snapshot Capsule (Vertical frosted silver pill with Camera Icon)
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 82.dp)
                        .shadow(12.dp, RoundedCornerShape(27.dp))
                        .clip(RoundedCornerShape(27.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFE2E2E8),
                                    Color(0xFFB8B8C2)
                                )
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(27.dp))
                        .clickable { onCaptureSnapshot() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Capture Photo",
                        tint = Color(0xFF1E1E24),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // BOTTOM BUTTON: Mode / Fancy Frames Button (Circular button with "MODE" text)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (showFrameSelector) listOf(Color(0xFF00E5FF), Color(0xFF0091EA))
                                else listOf(Color(0xFF555560), Color(0xFF25252B))
                            )
                        )
                        .border(
                            1.5.dp,
                            if (showFrameSelector) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { showFrameSelector = !showFrameSelector },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MODE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // COMPACT FLOATING ZOOM PILL IN LANDSCAPE (Bottom-Right, keeping screen crystal clear)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 82.dp, bottom = 6.dp)
                    .width(250.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp)),
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zoom Target Tabs (Main vs Inset)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ZoomTargetTab(
                            title = "MAIN",
                            zoom = camState.mainZoom,
                            isSelected = selectedZoomTab == 0,
                            onClick = { selectedZoomTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        ZoomTargetTab(
                            title = "INSET",
                            zoom = camState.insetZoom,
                            isSelected = selectedZoomTab == 1,
                            onClick = { selectedZoomTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Preset buttons (.6x, 1x, 2x, 5x, 10x)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = listOf(0.6f, 1.0f, 2.0f, 5.0f, 10.0f)
                        for (preset in presets) {
                            val isSelected = kotlin.math.abs(currentZoom - preset) < 0.15f
                            QuickZoomPill(
                                preset = preset,
                                isSelected = isSelected,
                                onClick = { onCurrentZoomChange(preset) }
                            )
                        }
                    }
                }
            }

            // FANCY FRAMES CAROUSEL IN LANDSCAPE (Floats cleanly near the top right)
            AnimatedVisibility(
                visible = showFrameSelector,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 92.dp, top = 44.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .width(340.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp)),
                    color = Color.Black.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    FrameSelectorContent(
                        selectedFrame = selectedFrame,
                        onFrameSelect = onFrameSelect
                    )
                }
            }
        }
    } else {
        // PORTRAIT MODE: Traditional bottom deck layout
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Zoom Target Selector & Controls Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp)),
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                ZoomControlsContent(
                    camState = camState,
                    selectedZoomTab = selectedZoomTab,
                    onTabSelect = { selectedZoomTab = it },
                    currentZoom = currentZoom,
                    onZoomChange = onCurrentZoomChange,
                    isCompact = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fancy Frames Selector Carousel (Expandable)
            AnimatedVisibility(
                visible = showFrameSelector,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp)),
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                ) {
                    FrameSelectorContent(
                        selectedFrame = selectedFrame,
                        onFrameSelect = onFrameSelect
                    )
                }
            }

            // Bottom Primary Action Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash / Torch Toggle
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (camState.isTorchOn) Color(0xFFFFD700) else Color.White.copy(alpha = 0.18f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (camState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash Torch",
                        tint = if (camState.isTorchOn) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Fancy Frame Selector Toggle
                IconButton(
                    onClick = { showFrameSelector = !showFrameSelector },
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (showFrameSelector) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.18f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Fancy Frames",
                        tint = if (showFrameSelector) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Big Record Video Button (Active during recording)
                RecordButton(
                    isRecording = camState.isRecording,
                    onClick = onToggleRecord
                )

                // Snapshot Shutter Button
                IconButton(
                    onClick = onCaptureSnapshot,
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Capture Snapshot",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Camera Swap Button (Swaps Main & Inset cameras)
                IconButton(
                    onClick = onSwapCameras,
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Swap Main and Inset",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
}

@Composable
private fun ZoomControlsContent(
    camState: DualCamState,
    selectedZoomTab: Int,
    onTabSelect: (Int) -> Unit,
    currentZoom: Float,
    onZoomChange: (Float) -> Unit,
    isCompact: Boolean
) {
    Column(
        modifier = Modifier.padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = if (isCompact) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Zoom Target Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ZoomTargetTab(
                title = "MAIN (${camState.mainRole.name})",
                zoom = camState.mainZoom,
                isSelected = selectedZoomTab == 0,
                onClick = { onTabSelect(0) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            ZoomTargetTab(
                title = "INSET (${camState.insetRole.name})",
                zoom = camState.insetZoom,
                isSelected = selectedZoomTab == 1,
                onClick = { onTabSelect(1) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 6.dp))

        // Quick Zoom Ratio Buttons (.6x, 1x, 2x, 5x, 10x)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(0.6f, 1.0f, 2.0f, 5.0f, 10.0f)
            for (preset in presets) {
                val isSelected = kotlin.math.abs(currentZoom - preset) < 0.15f
                QuickZoomPill(
                    preset = preset,
                    isSelected = isSelected,
                    onClick = { onZoomChange(preset) }
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Continuous Zoom Slider (0.6x to 10.0x)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "0.6x",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isCompact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = currentZoom,
                onValueChange = { onZoomChange(it) },
                valueRange = 0.6f..10.0f,
                modifier = Modifier
                    .weight(1f)
                    .height(if (isCompact) 28.dp else 36.dp)
                    .padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Text(
                text = "10x",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isCompact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FrameSelectorContent(
    selectedFrame: FancyFrameType,
    onFrameSelect: (FancyFrameType) -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FANCY INSET FRAMES",
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Text(
                text = selectedFrame.displayName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (frame in FancyFrameType.values()) {
                FrameSelectorChip(
                    frameType = frame,
                    isSelected = frame == selectedFrame,
                    onClick = { onFrameSelect(frame) }
                )
            }
        }
    }
}

@Composable
private fun ZoomTargetTab(
    title: String,
    zoom: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.Transparent
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                shape = RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = String.format(Locale.US, "%.1fx", zoom),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun QuickZoomPill(
    preset: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val text = if (preset == 0.6f) ".6x" else "${preset.toInt()}x"
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.12f)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FrameSelectorChip(
    frameType: FancyFrameType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = frameType.displayName,
            color = if (isSelected) Color(0xFF00E5FF) else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RecordPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(if (isRecording) pulse else 1.0f)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer Ring
        Box(
            modifier = Modifier
                .size(74.dp)
                .border(
                    width = 4.dp,
                    color = if (isRecording) Color.Red else Color.White,
                    shape = CircleShape
                )
        )

        // Inner Shutter Shape
        if (isRecording) {
            // Square Stop Icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Red, RoundedCornerShape(6.dp))
            )
        } else {
            // Round Red Recording Shutter
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFFF2222), CircleShape)
            )
        }
    }
}
