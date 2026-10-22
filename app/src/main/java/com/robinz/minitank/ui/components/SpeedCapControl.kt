package com.robinz.minitank.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary
import kotlin.math.roundToInt

@Composable
fun SpeedCapControl(
    speedCapPercent: Int,
    onSpeedCapChanged: (Int) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .widthIn(min = if (compact) 180.dp else 220.dp, max = 340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, OutlineBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("speed_cap_control"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label
        if (!compact) Text(
            text = "MAX SPEED CAP",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                color = TextMuted
            )
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Prominent Centered Display & Stepper Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Decrement Button (-)
            IconButton(
                onClick = onDecrement,
                enabled = speedCapPercent > 20,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DarkBackground)
                    .border(1.dp, OutlineBorder, CircleShape)
                    .testTag("speed_decrement_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease Max Speed",
                    tint = if (speedCapPercent > 20) CyanAccent else TextMuted
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Prominent Centered Value Display
            Text(
                text = "$speedCapPercent%",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = CyanAccent
                ),
                modifier = Modifier.testTag("speed_cap_display")
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Increment Button (+)
            IconButton(
                onClick = onIncrement,
                enabled = speedCapPercent < 100,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DarkBackground)
                    .border(1.dp, OutlineBorder, CircleShape)
                    .testTag("speed_increment_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase Max Speed",
                    tint = if (speedCapPercent < 100) CyanAccent else TextMuted
                )
            }
        }

        // Stepped Slider: 20% to 100% in 8 steps (10% increments)
        if (!compact) Slider(
            value = speedCapPercent.toFloat(),
            onValueChange = { newValue ->
                val step = (newValue / 10f).roundToInt() * 10
                onSpeedCapChanged(step.coerceIn(20, 100))
            },
            valueRange = 20f..100f,
            steps = 7, // 30, 40, 50, 60, 70, 80, 90
            colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                inactiveTrackColor = DarkBackground,
                activeTickColor = DarkBackground,
                inactiveTickColor = TextMuted
            ),
            modifier = Modifier
                .height(28.dp)
                .testTag("speed_cap_slider")
        )
    }
}
