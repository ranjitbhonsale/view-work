package com.example.dualcamdirector.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dualcamdirector.camera.DualCamState
import java.util.Locale

@Composable
fun CameraHud(
    camState: DualCamState,
    onInfoClick: () -> Unit,
    onRotateClick: () -> Unit = {},
    onSwapCameras: () -> Unit = {},
    onToggleTorch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "HudRecBlink")
    val recBlink by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RecAlpha"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 14.dp,
                top = 8.dp,
                bottom = 8.dp,
                end = if (isLandscape) 96.dp else 14.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Status & In Landscape, Quick Camera Actions (Samsung Dual Shot style)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLandscape) {
                    IconButton(
                        onClick = onSwapCameras,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onToggleTorch,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (camState.isTorchOn) Color(0xFFFFD700) else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (camState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (camState.isTorchOn) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Hardware concurrency badge
                Row(
                    modifier = Modifier
                        .background(
                            if (camState.isConcurrentHardwareActive) Color(0xFF00C853).copy(alpha = 0.85f)
                            else Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (camState.isConcurrentHardwareActive) Color(0xFF00FF66) else Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (camState.isConcurrentHardwareActive) Color.White else Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (camState.isConcurrentHardwareActive) "CONCURRENT HW ACTIVE" else "DUAL DIRECTOR SIM",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Recording Status or Info button
            if (camState.isRecording) {
                Row(
                    modifier = Modifier
                        .background(Color.Red, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(recBlink)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    val minutes = camState.recordingDurationSec / 60
                    val seconds = camState.recordingDurationSec % 60
                    Text(
                        text = String.format(Locale.US, "REC %02d:%02d", minutes, seconds),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRotateClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate Feed",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Specs Info",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Active Recording Hint: "PiP Inset is draggable during recording"
        AnimatedVisibility(
            visible = camState.isRecording,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFFF9100).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = null,
                    tint = Color(0xFFFF9100),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "RECORDING ACTIVE • DRAG INSET ANYWHERE",
                    color = Color(0xFFFFB74D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
