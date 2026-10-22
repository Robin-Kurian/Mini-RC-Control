package com.robinz.minitank.ui.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun JoystickDriveControl(
    onJoystickChanged: (deltaX: Float, deltaY: Float, radius: Float) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val knobOffsetX = remember { Animatable(0f) }
    val knobOffsetY = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("joystick_control_arena"),
        contentAlignment = Alignment.Center
    ) {
        val arenaSize = minOf(maxWidth, maxHeight)
        val maxRadius = (arenaSize.value * 0.38f).coerceIn(60f, 130f)
        val knobSize = (arenaSize.value * 0.28f).coerceIn(52f, 76f).dp

        Box(
            modifier = Modifier
                .size(arenaSize)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(DarkSurfaceElevated)
                .border(2.dp, if (isDragging) CyanAccent else OutlineBorder, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            coroutineScope.launch {
                                knobOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                            }
                            coroutineScope.launch {
                                knobOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                            }
                            onReleased()
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                knobOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                            }
                            coroutineScope.launch {
                                knobOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
                            }
                            onReleased()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val rawTargetX = knobOffsetX.value + dragAmount.x
                            val rawTargetY = knobOffsetY.value + dragAmount.y
                            val distance = hypot(rawTargetX, rawTargetY)
                            val clampedX: Float
                            val clampedY: Float
                            if (distance > maxRadius) {
                                val angle = atan2(rawTargetY, rawTargetX)
                                clampedX = maxRadius * cos(angle)
                                clampedY = maxRadius * sin(angle)
                            } else {
                                clampedX = rawTargetX
                                clampedY = rawTargetY
                            }

                            coroutineScope.launch {
                                knobOffsetX.snapTo(clampedX)
                                knobOffsetY.snapTo(clampedY)
                            }
                            onJoystickChanged(clampedX, clampedY, maxRadius)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Canvas: Crosshairs and Deadzone indicator ring
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radiusPx = size.width / 2f

                // Outer border guide
                drawCircle(
                    color = OutlineBorder,
                    radius = radiusPx * 0.95f,
                    style = Stroke(width = 1.5f)
                )

                // Dead-zone ring (0.08)
                drawCircle(
                    color = CyanAccent.copy(alpha = 0.25f),
                    radius = radiusPx * 0.16f,
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                )

                // Tactical Crosshair Lines
                drawLine(
                    color = OutlineBorder.copy(alpha = 0.5f),
                    start = Offset(center.x, center.y - radiusPx * 0.85f),
                    end = Offset(center.x, center.y + radiusPx * 0.85f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = OutlineBorder.copy(alpha = 0.5f),
                    start = Offset(center.x - radiusPx * 0.85f, center.y),
                    end = Offset(center.x + radiusPx * 0.85f, center.y),
                    strokeWidth = 1f
                )
            }

            // Draggable Thumb Knob
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            knobOffsetX.value.roundToInt(),
                            knobOffsetY.value.roundToInt()
                        )
                    }
                    .size(knobSize)
                    .shadow(10.dp, CircleShape, spotColor = CyanAccent)
                    .clip(CircleShape)
                    .background(if (isDragging) CyanAccent else DarkBackground)
                    .border(2.5.dp, CyanAccent, CircleShape)
                    .testTag("joystick_knob"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isDragging) "●" else "○",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = if (isDragging) DarkBackground else CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }
        }
    }
}
