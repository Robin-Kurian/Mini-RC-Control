package com.robinz.minitank.ui.controls

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary
import com.robinz.minitank.ui.theme.WarningAmber

@Composable
fun GyroDriveControl(
    pitch: Float,
    roll: Float,
    calibratedPitch: Float,
    calibratedRoll: Float,
    isEngaged: Boolean,
    onEngageChange: (Boolean) -> Unit,
    onCalibrateZero: () -> Unit,
    modifier: Modifier = Modifier,
    centerContent: (@Composable () -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val relPitch = (pitch - calibratedPitch).coerceIn(-30f, 30f)
    val relRoll = (roll - calibratedRoll).coerceIn(-30f, 30f)

    if (isLandscape && centerContent != null) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizonBubbleBox(
                relPitch = relPitch,
                relRoll = relRoll,
                isEngaged = isEngaged,
                boxSize = 250.dp
            )

            Box(
                modifier = Modifier.width(240.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                centerContent()
            }

            Column(
                modifier = Modifier.width(220.dp).fillMaxHeight().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PITCH: %+.1f°".format(relPitch),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "ROLL: %+.1f°".format(relRoll),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Button(
                    onClick = onCalibrateZero,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurface,
                        contentColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .border(1.dp, OutlineBorder, RoundedCornerShape(12.dp))
                        .testTag("calibrate_zero_button")
                ) {
                    Icon(imageVector = Icons.Default.Adjust, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "ZERO LEVEL", fontSize = 12.sp)
                }

                DeadmanTouchPad(
                    isEngaged = isEngaged,
                    onEngageChange = onEngageChange,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
            }
        }
    } else if (isLandscape) {
        // Landscape Responsive Layout: Artificial Horizon on Left, Readouts & Controls on Right
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Artificial Horizon Bubble Level
            HorizonBubbleBox(
                relPitch = relPitch,
                relRoll = relRoll,
                isEngaged = isEngaged,
                boxSize = 145.dp
            )

            // Right Panel: Telemetry, Zero Level Button, Hold-to-tilt Pad
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tilt Readout metrics
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PITCH: %+.1f°".format(relPitch),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "ROLL: %+.1f°".format(relRoll),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                // Controls: Calibrate Zero & Dead-man Hold-to-Drive Pad
                Row(
                    modifier = Modifier.width(360.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCalibrateZero,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = CyanAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .border(1.dp, OutlineBorder, RoundedCornerShape(12.dp))
                            .testTag("calibrate_zero_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "ZERO LEVEL", fontSize = 12.sp)
                    }

                    DeadmanTouchPad(
                        isEngaged = isEngaged,
                        onEngageChange = onEngageChange,
                        modifier = Modifier
                            .height(52.dp)
                            .weight(1f)
                    )
                }
            }
        }
    } else {
        // Portrait Layout: Stacked vertically with comfortable spacing
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Visual Artificial Horizon Bubble Level
            HorizonBubbleBox(
                relPitch = relPitch,
                relRoll = relRoll,
                isEngaged = isEngaged,
                boxSize = 165.dp
            )

            // Tilt Readout metrics
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PITCH: %+.1f°".format(relPitch),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "ROLL: %+.1f°".format(relRoll),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                )
            }

            // Controls: Calibrate Zero & Dead-man Hold-to-Drive Pad
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calibrate Button
                Button(
                    onClick = onCalibrateZero,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurface,
                        contentColor = CyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .border(1.dp, OutlineBorder, RoundedCornerShape(12.dp))
                        .testTag("calibrate_zero_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Adjust,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "ZERO LEVEL", fontSize = 12.sp)
                }

                // Touch Dead-Man Safety Pad
                DeadmanTouchPad(
                    isEngaged = isEngaged,
                    onEngageChange = onEngageChange,
                    modifier = Modifier
                        .height(54.dp)
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HorizonBubbleBox(
    relPitch: Float,
    relRoll: Float,
    isEngaged: Boolean,
    boxSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(CircleShape)
            .background(DarkSurfaceElevated)
            .border(2.dp, if (isEngaged) CyanAccent else OutlineBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f

            // Level rings
            drawCircle(color = OutlineBorder, radius = maxRadius * 0.9f, style = Stroke(1.5f))
            drawCircle(color = OutlineBorder.copy(alpha = 0.5f), radius = maxRadius * 0.5f, style = Stroke(1f))
            drawCircle(color = CyanAccent.copy(alpha = 0.3f), radius = maxRadius * 0.2f, style = Stroke(1f))

            // Center crosshair
            drawLine(
                color = OutlineBorder,
                start = Offset(center.x - 12f, center.y),
                end = Offset(center.x + 12f, center.y),
                strokeWidth = 2f
            )
            drawLine(
                color = OutlineBorder,
                start = Offset(center.x, center.y - 12f),
                end = Offset(center.x, center.y + 12f),
                strokeWidth = 2f
            )

            // Tilt bubble offset
            val bubbleX = center.x + (relRoll / 30f) * (maxRadius * 0.7f)
            val bubbleY = center.y + (relPitch / 30f) * (maxRadius * 0.7f)

            drawCircle(
                color = if (isEngaged) CyanAccent else WarningAmber,
                radius = 12f,
                center = Offset(bubbleX, bubbleY)
            )
        }

        Text(
            text = if (isEngaged) "GYRO ACTIVE" else "HOLD PAD TO TILT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = if (isEngaged) CyanAccent else TextMuted
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun DeadmanTouchPad(
    isEngaged: Boolean,
    onEngageChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isEngaged) CyanAccent else DarkSurfaceElevated)
            .border(
                2.dp,
                if (isEngaged) Color.White else CyanAccent,
                RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onEngageChange(true)
                    do {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                    } while (anyPressed)
                    onEngageChange(false)
                }
            }
            .testTag("gyro_engage_pad"),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                tint = if (isEngaged) DarkBackground else CyanAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isEngaged) "TRANSMITTING TILT" else "HOLD TO DRIVE",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isEngaged) DarkBackground else TextPrimary
                )
            )
        }
    }
}
