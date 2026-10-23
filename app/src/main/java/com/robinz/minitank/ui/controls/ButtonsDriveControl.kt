package com.robinz.minitank.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary

@Composable
fun ButtonsDriveControl(
    onButtonsChanged: (forward: Boolean, reverse: Boolean, left: Boolean, right: Boolean) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = true,
    controlsSwapped: Boolean = false,
    centerContent: (@Composable () -> Unit)? = null
) {
    var forwardPressed by remember { mutableStateOf(false) }
    var reversePressed by remember { mutableStateOf(false) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    fun updateState(fwd: Boolean, rev: Boolean, l: Boolean, r: Boolean) {
        forwardPressed = fwd
        reversePressed = rev
        leftPressed = l
        rightPressed = r
        if (!fwd && !rev && !l && !r) {
            onReleased()
        } else {
            onButtonsChanged(fwd, rev, l, r)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val buttonHeight = if (isLandscape) {
            (maxHeight * 0.43f).coerceIn(88.dp, 116.dp)
        } else {
            (maxHeight * 0.30f).coerceIn(100.dp, 136.dp)
        }
        val buttonWidth = if (isLandscape) {
            (buttonHeight * 1.15f).coerceAtLeast(100.dp)
        } else {
            (maxWidth * 0.30f).coerceIn(100.dp, 124.dp)
        }
        val throttleButtonWidth = if (isLandscape) buttonWidth + 24.dp else buttonWidth
        val stackSpacing = if (isLandscape) 18.dp else 14.dp
        val steeringButtonHeight = buttonHeight * 2 + stackSpacing

        if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = controlSideArrangement(controlsSwapped, Arrangement.SpaceBetween),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Forward & Reverse
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TactileControlButton(
                    label = "FORWARD",
                    icon = Icons.Default.ArrowUpward,
                    isPressed = forwardPressed,
                    testTag = "button_forward",
                    btnWidth = throttleButtonWidth,
                    btnHeight = buttonHeight,
                    onPressChange = { pressed ->
                        updateState(pressed, reversePressed, leftPressed, rightPressed)
                    }
                )
                TactileControlButton(
                    label = "REVERSE",
                    icon = Icons.Default.ArrowDownward,
                    isPressed = reversePressed,
                    testTag = "button_reverse",
                    btnWidth = throttleButtonWidth,
                    btnHeight = buttonHeight,
                    onPressChange = { pressed ->
                        updateState(forwardPressed, pressed, leftPressed, rightPressed)
                    }
                )
            }

            centerContent?.invoke()

            // Right Group: Left & Right
            Row(
                horizontalArrangement = Arrangement.spacedBy(stackSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileControlButton(
                    label = "LEFT",
                    icon = Icons.Default.ArrowBack,
                    isPressed = leftPressed,
                    testTag = "button_left",
                    btnWidth = buttonWidth,
                    btnHeight = steeringButtonHeight,
                    onPressChange = { pressed ->
                        updateState(forwardPressed, reversePressed, pressed, rightPressed)
                    }
                )
                TactileControlButton(
                    label = "RIGHT",
                    icon = Icons.Default.ArrowForward,
                    isPressed = rightPressed,
                    testTag = "button_right",
                    btnWidth = buttonWidth,
                    btnHeight = steeringButtonHeight,
                    onPressChange = { pressed ->
                        updateState(forwardPressed, reversePressed, leftPressed, pressed)
                    }
                )
            }
        }
    } else {
        // Portrait Mode: Ergonomic layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = controlSideArrangement(controlsSwapped, Arrangement.SpaceBetween),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Thumb: Throttle Stack
            Column(
                verticalArrangement = Arrangement.spacedBy(stackSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TactileControlButton(
                    label = "FWD",
                    icon = Icons.Default.ArrowUpward,
                    isPressed = forwardPressed,
                    testTag = "button_forward",
                    btnWidth = buttonWidth,
                    btnHeight = buttonHeight,
                    onPressChange = { pressed ->
                        updateState(pressed, reversePressed, leftPressed, rightPressed)
                    }
                )
                TactileControlButton(
                    label = "REV",
                    icon = Icons.Default.ArrowDownward,
                    isPressed = reversePressed,
                    testTag = "button_reverse",
                    btnWidth = buttonWidth,
                    btnHeight = buttonHeight,
                    onPressChange = { pressed ->
                        updateState(forwardPressed, pressed, leftPressed, rightPressed)
                    }
                )
            }

            // Right Thumb: Steering Stack
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TactileControlButton(
                    label = "LEFT",
                    icon = Icons.Default.ArrowBack,
                    isPressed = leftPressed,
                    testTag = "button_left",
                    btnWidth = buttonWidth,
                    btnHeight = steeringButtonHeight,
                    onPressChange = { pressed ->
                        updateState(forwardPressed, reversePressed, pressed, rightPressed)
                    }
                )
                TactileControlButton(
                    label = "RIGHT",
                    icon = Icons.Default.ArrowForward,
                    isPressed = rightPressed,
                    testTag = "button_right",
                    btnWidth = buttonWidth,
                    btnHeight = steeringButtonHeight,
                    onPressChange = { pressed ->
                        updateState(forwardPressed, reversePressed, leftPressed, pressed)
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun TactileControlButton(
    label: String,
    icon: ImageVector,
    isPressed: Boolean,
    testTag: String,
    onPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    btnWidth: Dp = 88.dp,
    btnHeight: Dp = 76.dp
) {
    val iconSize = (btnHeight * 0.34f).coerceIn(30.dp, 42.dp)
    val labelSize = if (btnHeight >= 112.dp) 14.sp else 13.sp
    Box(
        modifier = modifier
            .size(width = btnWidth, height = btnHeight)
            .shadow(if (isPressed) 12.dp else 5.dp, RoundedCornerShape(20.dp), spotColor = CyanAccent)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isPressed) CyanAccent else DarkSurfaceElevated)
            .border(
                width = 2.dp,
                color = if (isPressed) Color.White else OutlineBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onPressChange(true)
                    do {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                    } while (anyPressed)
                    onPressChange(false)
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPressed) DarkBackground else CyanAccent,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize,
                    color = if (isPressed) DarkBackground else TextPrimary
                )
            )
        }
    }
}
