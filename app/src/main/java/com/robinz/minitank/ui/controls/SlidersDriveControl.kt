package com.robinz.minitank.ui.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private class SliderState {
    var throttle = 0f
    var steer = 0f
}

@Composable
fun SlidersDriveControl(
    onSlidersChanged: (throttleDeltaY: Float, maxThrottleTravel: Float, steeringDeltaX: Float, maxSteeringTravel: Float) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = true
) {
    val sliderState = remember { SliderState() }

    val content: @Composable (Modifier, Modifier) -> Unit = { throttleMod, steerMod ->
        // Left Vertical Slider (Throttle / Brake)
        VerticalThrottleSlider(
            modifier = throttleMod,
            onDrag = { dy, maxTravel ->
                sliderState.throttle = dy
                onSlidersChanged(dy, maxTravel, sliderState.steer, 120f)
            },
            onRelease = {
                sliderState.throttle = 0f
                onSlidersChanged(0f, 120f, sliderState.steer, 120f)
                if (sliderState.steer == 0f) onReleased()
            }
        )

        // Right Horizontal Slider (Left / Right Steering)
        HorizontalSteeringSlider(
            modifier = steerMod,
            onDrag = { dx, maxTravel ->
                sliderState.steer = dx
                onSlidersChanged(sliderState.throttle, 120f, dx, maxTravel)
            },
            onRelease = {
                sliderState.steer = 0f
                onSlidersChanged(sliderState.throttle, 120f, 0f, 120f)
                if (sliderState.throttle == 0f) onReleased()
            }
        )
    }

    if (isLandscape) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 8.dp),
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 8.dp, end = 16.dp)
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content(
                Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun VerticalThrottleSlider(
    modifier: Modifier = Modifier,
    onDrag: (deltaY: Float, maxTravel: Float) -> Unit,
    onRelease: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val thumbOffsetAnim = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceElevated)
            .border(1.5.dp, if (isDragging) CyanAccent else OutlineBorder, RoundedCornerShape(20.dp))
            .padding(12.dp)
            .testTag("vertical_throttle_slider"),
        contentAlignment = Alignment.Center
    ) {
        val totalHeight = maxHeight.value
        val maxTravel = (totalHeight * 0.42f).coerceAtLeast(60f)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Forward Label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (thumbOffsetAnim.value < -5f) CyanAccent else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ACCEL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (thumbOffsetAnim.value < -5f) CyanAccent else TextMuted
                    )
                )
            }

            // Slider Track Area with Thumb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(DarkBackground)
                    .border(1.dp, OutlineBorder, RoundedCornerShape(36.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDragEnd = {
                                isDragging = false
                                coroutineScope.launch {
                                    thumbOffsetAnim.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                                    onRelease()
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                coroutineScope.launch {
                                    thumbOffsetAnim.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                                    onRelease()
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val next = (thumbOffsetAnim.value + dragAmount.y).coerceIn(-maxTravel, maxTravel)
                                coroutineScope.launch {
                                    thumbOffsetAnim.snapTo(next)
                                }
                                onDrag(next, maxTravel)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Neutral zero line indicator
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height / 2f
                    drawLine(
                        color = OutlineBorder,
                        start = Offset(10f, midY),
                        end = Offset(size.width - 10f, midY),
                        strokeWidth = 2f
                    )
                }

                // Interactive Thumb Knob
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                        .offset { IntOffset(0, thumbOffsetAnim.value.roundToInt()) }
                        .clip(CircleShape)
                        .background(if (isDragging) CyanAccent else DarkSurface)
                        .border(2.dp, CyanAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            thumbOffsetAnim.value < -10f -> "▲"
                            thumbOffsetAnim.value > 10f -> "▼"
                            else -> "—"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDragging) DarkBackground else CyanAccent
                        )
                    )
                }
            }

            // Reverse Label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (thumbOffsetAnim.value > 5f) CyanAccent else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "REVERSE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (thumbOffsetAnim.value > 5f) CyanAccent else TextMuted
                    )
                )
            }
        }
    }
}

@Composable
fun HorizontalSteeringSlider(
    modifier: Modifier = Modifier,
    onDrag: (deltaX: Float, maxTravel: Float) -> Unit,
    onRelease: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val thumbOffsetAnim = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurfaceElevated)
            .border(1.5.dp, if (isDragging) CyanAccent else OutlineBorder, RoundedCornerShape(20.dp))
            .padding(12.dp)
            .testTag("horizontal_steering_slider"),
        contentAlignment = Alignment.Center
    ) {
        val totalWidth = maxWidth.value
        val maxTravel = (totalWidth * 0.42f).coerceAtLeast(60f)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Text(
                text = "STEERING (DISTANCE RATIO)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isDragging) CyanAccent else TextMuted
                )
            )

            // Horizontal Track Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp))
                    .background(DarkBackground)
                    .border(1.dp, OutlineBorder, RoundedCornerShape(36.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDragEnd = {
                                isDragging = false
                                coroutineScope.launch {
                                    thumbOffsetAnim.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                                    onRelease()
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                coroutineScope.launch {
                                    thumbOffsetAnim.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                                    onRelease()
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val next = (thumbOffsetAnim.value + dragAmount.x).coerceIn(-maxTravel, maxTravel)
                                coroutineScope.launch {
                                    thumbOffsetAnim.snapTo(next)
                                }
                                onDrag(next, maxTravel)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Neutral zero line indicator
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midX = size.width / 2f
                    drawLine(
                        color = OutlineBorder,
                        start = Offset(midX, 10f),
                        end = Offset(midX, size.height - 10f),
                        strokeWidth = 2f
                    )
                }

                // Interactive Thumb Knob
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center)
                        .offset { IntOffset(thumbOffsetAnim.value.roundToInt(), 0) }
                        .clip(CircleShape)
                        .background(if (isDragging) CyanAccent else DarkSurface)
                        .border(2.dp, CyanAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            thumbOffsetAnim.value < -10f -> "◀"
                            thumbOffsetAnim.value > 10f -> "▶"
                            else -> "|"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isDragging) DarkBackground else CyanAccent
                        )
                    )
                }
            }

            // Directional Indicators
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (thumbOffsetAnim.value < -5f) CyanAccent else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LEFT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (thumbOffsetAnim.value < -5f) CyanAccent else TextMuted
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RIGHT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (thumbOffsetAnim.value > 5f) CyanAccent else TextMuted
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (thumbOffsetAnim.value > 5f) CyanAccent else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
