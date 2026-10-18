package com.example.dualcamdirector.ui.frames

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FancyFrameType(
    val displayName: String,
    val iconDescription: String,
    val isCircular: Boolean = false
) {
    POSTAGE_STAMP("Postage Stamp", "Vintage perforated postage stamp with cancellation mark"),
    MINIMAL("Minimal", "Clean rounded frame"),
    CYBER_NEON("Cyberpunk Neon", "Animated glowing neon with HUD"),
    POLAROID("Retro Polaroid", "Classic instant film frame"),
    HOLO_RING("Holo Ring", "Circular rotating rainbow ring", isCircular = true),
    GOLD_LUXURY("Gold Luxury", "Metallic gold bevel frame"),
    STREAMER_HUD("Streamer HUD", "Live streaming broadcast frame"),
    COMIC_POP("Comic Pop", "Comic book ink style"),
    HEART_BEAT("Heart Glow", "Vibrant heart vlog frame")
}

@Composable
fun FancyFrameContainer(
    frameType: FancyFrameType,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FrameAnimation")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    when (frameType) {
        FancyFrameType.POSTAGE_STAMP -> PostageStampFrame(modifier, content)
        FancyFrameType.MINIMAL -> MinimalFrame(modifier, content)
        FancyFrameType.CYBER_NEON -> CyberNeonFrame(modifier, pulse, isRecording, content)
        FancyFrameType.POLAROID -> PolaroidFrame(modifier, content)
        FancyFrameType.HOLO_RING -> HoloRingFrame(modifier, rotation, pulse, content)
        FancyFrameType.GOLD_LUXURY -> GoldLuxuryFrame(modifier, content)
        FancyFrameType.STREAMER_HUD -> StreamerHudFrame(modifier, isRecording, pulse, content)
        FancyFrameType.COMIC_POP -> ComicPopFrame(modifier, content)
        FancyFrameType.HEART_BEAT -> HeartBeatFrame(modifier, pulse, isRecording, content)
    }
}

@Composable
private fun MinimalFrame(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(12.dp, shape)
            .border(
                width = 2.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.8f),
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.5f)
                    )
                ),
                shape = shape
            )
            .background(Color.Black, shape)
            .clip(shape)
    ) {
        content()
        // Subtle corner indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(8.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        )
    }
}

@Composable
private fun CyberNeonFrame(
    modifier: Modifier,
    pulse: Float,
    isRecording: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    val neonCyan = Color(0xFF00F0FF)
    val neonMagenta = Color(0xFFFF007F)
    val neonGreen = Color(0xFF00FF66)

    val activeColor1 = if (isRecording) Color.Red else neonCyan
    val activeColor2 = if (isRecording) neonMagenta else neonGreen

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                elevation = (14 * pulse).dp,
                shape = shape,
                ambientColor = activeColor1,
                spotColor = activeColor2
            )
            .border(
                width = (3 * pulse).dp,
                brush = Brush.sweepGradient(
                    listOf(activeColor1, activeColor2, activeColor1)
                ),
                shape = shape
            )
            .background(Color.Black, shape)
            .clip(shape)
    ) {
        content()

        // Cyberpunk HUD Overlay elements
        // Top Left Tech Bracket
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "CAM_02 [LIVE]",
                color = activeColor1,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Bottom right targeting reticle
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(16.dp)
                .drawWithContent {
                    drawContent()
                    val s = size.width
                    // Mini target reticle
                    drawLine(activeColor2, Offset(0f, s / 2), Offset(s, s / 2), strokeWidth = 2f)
                    drawLine(activeColor2, Offset(s / 2, 0f), Offset(s / 2, s), strokeWidth = 2f)
                    drawCircle(activeColor1, radius = s / 3, style = Stroke(width = 1.5f))
                }
        )
    }
}

@Composable
private fun PolaroidFrame(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val innerShape = RoundedCornerShape(3.dp)
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    Column(
        modifier = modifier
            .fillMaxSize()
            .shadow(16.dp, shape)
            .background(Color(0xFFF8F7F2), shape)
            .border(1.dp, Color(0xFFE2DEC9), shape)
            .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black, innerShape)
                .clip(innerShape)
        ) {
            content()
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF4A4A4A),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "DUAL•CAM",
                    color = Color(0xFF2C2C2C),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Text(
                text = dateFormat,
                color = Color(0xFF7A7A7A),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HoloRingFrame(
    modifier: Modifier,
    rotation: Float,
    pulse: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = CircleShape
    val rainbowColors = listOf(
        Color(0xFFFF0000),
        Color(0xFFFF7F00),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF0000FF),
        Color(0xFF4B0082),
        Color(0xFF9400D3),
        Color(0xFFFF0000)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow((12 * pulse).dp, shape, spotColor = Color(0xFF00F0FF))
            .border(
                width = 3.5.dp,
                brush = Brush.sweepGradient(rainbowColors),
                shape = shape
            )
            .background(Color.Black, shape)
            .clip(shape)
    ) {
        content()

        // Glowing orbiting badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "360° ORB",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GoldLuxuryFrame(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val goldGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFBF953F),
            Color(0xFFFCF6BA),
            Color(0xFFB38728),
            Color(0xFFFBF5B7),
            Color(0xFFAA771C)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(16.dp, shape, spotColor = Color(0xFFBF953F))
            .border(width = 3.5.dp, brush = goldGradient, shape = shape)
            .background(Color(0xFF141414), shape)
            .clip(shape)
    ) {
        content()

        // Luxury Crest Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF22201A).copy(alpha = 0.9f), Color(0xFF141414).copy(alpha = 0.8f))
                    ),
                    RoundedCornerShape(6.dp)
                )
                .border(1.dp, Color(0xFFBF953F), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFCF6BA),
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "DIRECTOR",
                color = Color(0xFFFCF6BA),
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun StreamerHudFrame(
    modifier: Modifier,
    isRecording: Boolean,
    pulse: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val borderCol = if (isRecording) Color(0xFFFF2222) else Color(0xFF9146FF) // Twitch purple

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(14.dp, shape, ambientColor = borderCol, spotColor = borderCol)
            .border(2.5.dp, borderCol, shape)
            .background(Color.Black, shape)
            .clip(shape)
    ) {
        content()

        // Live ON AIR Indicator
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(
                    if (isRecording) Color.Red else Color(0xFF18181B).copy(alpha = 0.9f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = if (isRecording) Color.White else Color(0xFF00FF66),
                modifier = Modifier.size(8.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isRecording) "REC" else "ON AIR",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Live Audio VU Bars Visualizer Simulation at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color(0xFF00FF66),
                modifier = Modifier.size(11.dp)
            )
            val bar1 = (8 * pulse).dp
            val bar2 = (14 / pulse).dp
            val bar3 = (10 * pulse).dp
            val bar4 = (6 * pulse).dp

            Box(Modifier.width(3.dp).height(bar1).background(Color(0xFF00FF66), RoundedCornerShape(1.dp)))
            Box(Modifier.width(3.dp).height(bar2).background(Color(0xFF00FF66), RoundedCornerShape(1.dp)))
            Box(Modifier.width(3.dp).height(bar3).background(Color(0xFFFFDD00), RoundedCornerShape(1.dp)))
            Box(Modifier.width(3.dp).height(bar4).background(Color(0xFFFF3333), RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun ComicPopFrame(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val inkColor = Color(0xFF111111)
    val yellowPop = Color(0xFFFFE600)
    val cyanPop = Color(0xFF00E5FF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(inkColor, shape)
            .padding(end = 4.dp, bottom = 4.dp)
            .border(3.5.dp, inkColor, shape)
            .background(yellowPop, shape)
            .clip(shape)
    ) {
        content()

        // Comic burst tag
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(cyanPop, CutCornerShape(4.dp))
                .border(2.dp, inkColor, CutCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "POW! PIP",
                color = inkColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Cursive
            )
        }
    }
}

@Composable
private fun HeartBeatFrame(
    modifier: Modifier,
    pulse: Float,
    isRecording: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val pinkNeon = Color(0xFFFF2D75)
    val violetNeon = Color(0xFF9900EF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                elevation = (12 * pulse).dp,
                shape = shape,
                ambientColor = pinkNeon,
                spotColor = violetNeon
            )
            .border(
                width = 3.dp,
                brush = Brush.linearGradient(listOf(pinkNeon, violetNeon, pinkNeon)),
                shape = shape
            )
            .background(Color.Black, shape)
            .clip(shape)
    ) {
        content()

        // Heart Icon Tag
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .background(pinkNeon.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun PostageStampFrame(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(16.dp, RoundedCornerShape(2.dp))
            .background(Color(0xFFFBF8F0)) // Classic ivory postal paper
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height

                // Postal stamp postmark cancellation wavy ink lines (Samsung Dual Shot style)
                val inkColor = Color(0x66333333)
                val wavePath = androidx.compose.ui.graphics.Path()

                for (lineIdx in 0..2) {
                    val yBase = 20f + lineIdx * 10f
                    wavePath.reset()
                    wavePath.moveTo(0f, yBase)
                    var x = 0f
                    var waveUp = true
                    while (x < w * 0.42f) {
                        val nextX = x + 12f
                        val nextY = if (waveUp) yBase - 3.5f else yBase + 3.5f
                        wavePath.quadraticBezierTo(x + 6f, nextY, nextX, yBase)
                        x = nextX
                        waveUp = !waveUp
                    }
                    drawPath(
                        path = wavePath,
                        color = inkColor,
                        style = Stroke(width = 1.5f)
                    )
                }

                // Circular postmark stamp
                drawCircle(
                    color = inkColor,
                    radius = 20f,
                    center = Offset(w * 0.22f, 30f),
                    style = Stroke(width = 1.2f)
                )

                // Postage stamp serrated / perforated teeth along all 4 edges
                val perfRadius = 4.dp.toPx()
                val spacing = 12.dp.toPx()

                // Top & Bottom edges
                var px = spacing
                while (px < w - spacing / 2) {
                    drawCircle(color = Color.Black.copy(alpha = 0.9f), radius = perfRadius, center = Offset(px, 0f))
                    drawCircle(color = Color.Black.copy(alpha = 0.9f), radius = perfRadius, center = Offset(px, h))
                    px += spacing
                }

                // Left & Right edges
                var py = spacing
                while (py < h - spacing / 2) {
                    drawCircle(color = Color.Black.copy(alpha = 0.9f), radius = perfRadius, center = Offset(0f, py))
                    drawCircle(color = Color.Black.copy(alpha = 0.9f), radius = perfRadius, center = Offset(w, py))
                    py += spacing
                }
            }
            .padding(10.dp)
    ) {
        // Inner photo preview window
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color(0xFFDCD6CA))
                .clip(RoundedCornerShape(2.dp))
        ) {
            content()
        }
    }
}
