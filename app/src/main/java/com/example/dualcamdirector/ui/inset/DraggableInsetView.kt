package com.example.dualcamdirector.ui.inset

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dualcamdirector.ui.frames.FancyFrameContainer
import com.example.dualcamdirector.ui.frames.FancyFrameType
import kotlin.math.roundToInt

enum class InsetSize(val label: String, val widthDp: Int, val heightDp: Int) {
    COMPACT("Small", 130, 175),
    STANDARD("Medium", 165, 220),
    EXPANDED("Large", 200, 265)
}

@Composable
fun DraggableInsetView(
    frameType: FancyFrameType,
    isRecording: Boolean,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var insetSize by remember { mutableStateOf(InsetSize.STANDARD) }
    
    // Position offset state in pixels
    var offsetX by remember { mutableFloatStateOf(40f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parentWidthPx = with(density) { maxWidth.toPx() }
        val parentHeightPx = with(density) { maxHeight.toPx() }

        val targetWidthDp = if (frameType.isCircular) insetSize.widthDp.dp else insetSize.widthDp.dp
        val targetHeightDp = if (frameType.isCircular) insetSize.widthDp.dp else insetSize.heightDp.dp

        val insetWidthPx = with(density) { targetWidthDp.toPx() }
        val insetHeightPx = with(density) { targetHeightDp.toPx() }

        // Animate width & height changes smoothly
        val animatedWidth by animateDpAsState(targetValue = targetWidthDp, animationSpec = spring(), label = "InsetW")
        val animatedHeight by animateDpAsState(targetValue = targetHeightDp, animationSpec = spring(), label = "InsetH")

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val rightMarginPx = with(density) { if (isLandscape) 95.dp.toPx() else 20.dp.toPx() }
        val bottomMarginPx = with(density) { if (isLandscape) 20.dp.toPx() else 180.dp.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        offsetX.roundToInt().coerceIn(20, (parentWidthPx - insetWidthPx - rightMarginPx).toInt().coerceAtLeast(20)),
                        offsetY.roundToInt().coerceIn(50, (parentHeightPx - insetHeightPx - bottomMarginPx).toInt().coerceAtLeast(50))
                    )
                }
                .size(width = animatedWidth, height = animatedHeight)
                // Gesture detector: drag gestures work anytime, even during video recording!
                .pointerInput(parentWidthPx, parentHeightPx, insetWidthPx, insetHeightPx, isLandscape) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val maxX = (parentWidthPx - insetWidthPx - rightMarginPx).coerceAtLeast(20f)
                        val maxY = (parentHeightPx - insetHeightPx - bottomMarginPx).coerceAtLeast(50f)
                        offsetX = (offsetX + dragAmount.x).coerceIn(20f, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(50f, maxY)
                    }
                }
                // Double tap on inset to swap cameras
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onSwapClick()
                        }
                    )
                }
        ) {
            // Fancy Frame Container
            FancyFrameContainer(
                frameType = frameType,
                isRecording = isRecording,
                modifier = Modifier.fillMaxSize()
            ) {
                content()

                // Overlay Controls on the inset (Size toggle & quick swap button)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Swap Icon
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Swap Camera",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onSwapClick() }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Size Toggle
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Toggle Size",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                insetSize = when (insetSize) {
                                    InsetSize.COMPACT -> InsetSize.STANDARD
                                    InsetSize.STANDARD -> InsetSize.EXPANDED
                                    InsetSize.EXPANDED -> InsetSize.COMPACT
                                }
                            }
                    )
                }

                // Drag indicator affordance at top center
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .size(width = 24.dp, height = 3.dp)
                        .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
