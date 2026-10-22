package com.robinz.minitank.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.controller.ControlMode
import com.robinz.minitank.ui.TankUiState
import com.robinz.minitank.ui.components.EmergencyStopButton
import com.robinz.minitank.ui.components.HeaderBar
import com.robinz.minitank.ui.components.SpeedCapControl
import com.robinz.minitank.ui.components.TelemetryHud
import com.robinz.minitank.ui.controls.ButtonsDriveControl
import com.robinz.minitank.ui.controls.GyroDriveControl
import com.robinz.minitank.ui.controls.JoystickDriveControl
import com.robinz.minitank.ui.controls.SlidersDriveControl
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary

@Composable
fun MainControllerScreen(
    uiState: TankUiState,
    onButtonsInput: (fwd: Boolean, rev: Boolean, left: Boolean, right: Boolean) -> Unit,
    onSlidersInput: (dy: Float, maxTravelY: Float, dx: Float, maxTravelX: Float) -> Unit,
    onJoystickInput: (dx: Float, dy: Float, radius: Float) -> Unit,
    onGyroEngage: (Boolean) -> Unit,
    onCalibrateGyroZero: () -> Unit,
    onDriveReleased: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSpeedCapChanged: (Int) -> Unit,
    onIncrementSpeedCap: () -> Unit,
    onDecrementSpeedCap: () -> Unit,
    onSelectControlMode: (ControlMode) -> Unit,
    onBluetoothClick: () -> Unit,
    onToggleBroadcast: () -> Unit = {},
    onLightsToggle: () -> Unit,
    onLightsBlinkToggle: () -> Unit = {},
    onSoundToggle: () -> Unit,
    onRouteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .safeDrawingPadding()
            .testTag("main_controller_screen")
    ) {
        if (isLandscape) {
            LandscapeLayout(
                uiState = uiState,
                onButtonsInput = onButtonsInput,
                onSlidersInput = onSlidersInput,
                onJoystickInput = onJoystickInput,
                onGyroEngage = onGyroEngage,
                onCalibrateGyroZero = onCalibrateGyroZero,
                onDriveReleased = onDriveReleased,
                onEmergencyStop = onEmergencyStop,
                onSpeedCapChanged = onSpeedCapChanged,
                onIncrementSpeedCap = onIncrementSpeedCap,
                onDecrementSpeedCap = onDecrementSpeedCap,
                onSelectControlMode = onSelectControlMode,
                onBluetoothClick = onBluetoothClick,
                onToggleBroadcast = onToggleBroadcast,
                onLightsToggle = onLightsToggle,
                onLightsBlinkToggle = onLightsBlinkToggle,
                onSoundToggle = onSoundToggle,
                onRouteClick = onRouteClick,
                onSettingsClick = onSettingsClick
            )
        } else {
            PortraitLayout(
                uiState = uiState,
                onButtonsInput = onButtonsInput,
                onSlidersInput = onSlidersInput,
                onJoystickInput = onJoystickInput,
                onGyroEngage = onGyroEngage,
                onCalibrateGyroZero = onCalibrateGyroZero,
                onDriveReleased = onDriveReleased,
                onEmergencyStop = onEmergencyStop,
                onSpeedCapChanged = onSpeedCapChanged,
                onIncrementSpeedCap = onIncrementSpeedCap,
                onDecrementSpeedCap = onDecrementSpeedCap,
                onSelectControlMode = onSelectControlMode,
                onBluetoothClick = onBluetoothClick,
                onToggleBroadcast = onToggleBroadcast,
                onLightsToggle = onLightsToggle,
                onLightsBlinkToggle = onLightsBlinkToggle,
                onSoundToggle = onSoundToggle,
                onRouteClick = onRouteClick,
                onSettingsClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun LandscapeLayout(
    uiState: TankUiState,
    onButtonsInput: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSlidersInput: (Float, Float, Float, Float) -> Unit,
    onJoystickInput: (Float, Float, Float) -> Unit,
    onGyroEngage: (Boolean) -> Unit,
    onCalibrateGyroZero: () -> Unit,
    onDriveReleased: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSpeedCapChanged: (Int) -> Unit,
    onIncrementSpeedCap: () -> Unit,
    onDecrementSpeedCap: () -> Unit,
    onSelectControlMode: (ControlMode) -> Unit,
    onBluetoothClick: () -> Unit,
    onToggleBroadcast: () -> Unit = {},
    onLightsToggle: () -> Unit,
    onLightsBlinkToggle: () -> Unit,
    onSoundToggle: () -> Unit,
    onRouteClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Top Header
        HeaderBar(
            connectionState = uiState.connectionState,
            connectedDeviceName = uiState.connectedDeviceName,
            lightsEnabled = uiState.lightsEnabled,
            lightsBlinkEnabled = uiState.lightsBlinkEnabled,
            soundActive = uiState.soundActive,
            isBroadcasting = uiState.isBroadcasting,
            onBluetoothClick = onBluetoothClick,
            onToggleBroadcast = onToggleBroadcast,
            onLightsToggle = onLightsToggle,
            onLightsBlinkToggle = onLightsBlinkToggle,
            onSoundToggle = onSoundToggle,
            onRouteClick = onRouteClick,
            onSettingsClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Main Cockpit Grid: Left Cluster | Center Column | Right Cluster
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Center Column with Speed Cap & STOP button (centered!)
            when (uiState.controlMode) {
                ControlMode.SLIDERS -> {
                    SlidersDriveControl(
                        onSlidersChanged = onSlidersInput,
                        onReleased = onDriveReleased,
                        isLandscape = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                ControlMode.BUTTONS -> {
                    ButtonsDriveControl(
                        onButtonsChanged = onButtonsInput,
                        onReleased = onDriveReleased,
                        isLandscape = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                ControlMode.JOYSTICK -> {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Speed Cap Vertical Placement in Joystick Mode
                        Column(
                            modifier = Modifier.width(220.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            SpeedCapControl(
                                speedCapPercent = uiState.speedCapPercent,
                                onSpeedCapChanged = onSpeedCapChanged,
                                onIncrement = onIncrementSpeedCap,
                                onDecrement = onDecrementSpeedCap
                            )
                        }

                        // Right 360 Joystick Arena
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            JoystickDriveControl(
                                onJoystickChanged = onJoystickInput,
                                onReleased = onDriveReleased
                            )
                        }
                    }
                }
                ControlMode.GYRO -> {
                    GyroDriveControl(
                        pitch = uiState.gyroPitch,
                        roll = uiState.gyroRoll,
                        calibratedPitch = uiState.gyroCalibratedPitch,
                        calibratedRoll = uiState.gyroCalibratedRoll,
                        isEngaged = uiState.gyroActive,
                        onEngageChange = onGyroEngage,
                        onCalibrateZero = onCalibrateGyroZero,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ControlModeSelectorRow(
                currentMode = uiState.controlMode,
                onSelectMode = onSelectControlMode
            )
        }

        // Bottom Dashboard Row (Prominent Centered Speed Cap + STOP Button + HUD)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Mode Selector Chips
            if (uiState.controlMode != ControlMode.JOYSTICK) {
                // Centered Speed Cap
                SpeedCapControl(
                    speedCapPercent = uiState.speedCapPercent,
                    onSpeedCapChanged = onSpeedCapChanged,
                    onIncrement = onIncrementSpeedCap,
                    onDecrement = onDecrementSpeedCap,
                    compact = true
                )
            }

            // Telemetry HUD and STOP Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryHud(
                    rawThrottle = uiState.rawThrottle,
                    rawSteering = uiState.rawSteering,
                    cappedThrottle = uiState.cappedThrottle,
                    speedCapPercent = uiState.speedCapPercent,
                    isConnected = uiState.connectionState.isConnected,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
                EmergencyStopButton(
                    onClick = onEmergencyStop,
                    compact = true
                )
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    uiState: TankUiState,
    onButtonsInput: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSlidersInput: (Float, Float, Float, Float) -> Unit,
    onJoystickInput: (Float, Float, Float) -> Unit,
    onGyroEngage: (Boolean) -> Unit,
    onCalibrateGyroZero: () -> Unit,
    onDriveReleased: () -> Unit,
    onEmergencyStop: () -> Unit,
    onSpeedCapChanged: (Int) -> Unit,
    onIncrementSpeedCap: () -> Unit,
    onDecrementSpeedCap: () -> Unit,
    onSelectControlMode: (ControlMode) -> Unit,
    onBluetoothClick: () -> Unit,
    onToggleBroadcast: () -> Unit = {},
    onLightsToggle: () -> Unit,
    onLightsBlinkToggle: () -> Unit,
    onSoundToggle: () -> Unit,
    onRouteClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        HeaderBar(
            connectionState = uiState.connectionState,
            connectedDeviceName = uiState.connectedDeviceName,
            lightsEnabled = uiState.lightsEnabled,
            lightsBlinkEnabled = uiState.lightsBlinkEnabled,
            soundActive = uiState.soundActive,
            isBroadcasting = uiState.isBroadcasting,
            onBluetoothClick = onBluetoothClick,
            onToggleBroadcast = onToggleBroadcast,
            onLightsToggle = onLightsToggle,
            onLightsBlinkToggle = onLightsBlinkToggle,
            onSoundToggle = onSoundToggle,
            onRouteClick = onRouteClick,
            onSettingsClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Prominent Centered Speed Cap Display
        SpeedCapControl(
            speedCapPercent = uiState.speedCapPercent,
            onSpeedCapChanged = onSpeedCapChanged,
            onIncrement = onIncrementSpeedCap,
            onDecrement = onDecrementSpeedCap
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Mode Selector Chips
        ControlModeSelectorRow(
            currentMode = uiState.controlMode,
            onSelectMode = onSelectControlMode
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Active Control Arena
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (uiState.controlMode) {
                ControlMode.SLIDERS -> {
                    SlidersDriveControl(
                        onSlidersChanged = onSlidersInput,
                        onReleased = onDriveReleased,
                        isLandscape = false
                    )
                }
                ControlMode.BUTTONS -> {
                    ButtonsDriveControl(
                        onButtonsChanged = onButtonsInput,
                        onReleased = onDriveReleased,
                        isLandscape = false
                    )
                }
                ControlMode.JOYSTICK -> {
                    JoystickDriveControl(
                        onJoystickChanged = onJoystickInput,
                        onReleased = onDriveReleased
                    )
                }
                ControlMode.GYRO -> {
                    GyroDriveControl(
                        pitch = uiState.gyroPitch,
                        roll = uiState.gyroRoll,
                        calibratedPitch = uiState.gyroCalibratedPitch,
                        calibratedRoll = uiState.gyroCalibratedRoll,
                        isEngaged = uiState.gyroActive,
                        onEngageChange = onGyroEngage,
                        onCalibrateZero = onCalibrateGyroZero
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Bar: Telemetry HUD & Prominent Red Emergency STOP
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelemetryHud(
                rawThrottle = uiState.rawThrottle,
                rawSteering = uiState.rawSteering,
                cappedThrottle = uiState.cappedThrottle,
                speedCapPercent = uiState.speedCapPercent,
                isConnected = uiState.connectionState.isConnected,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            EmergencyStopButton(
                onClick = onEmergencyStop,
                compact = true
            )
        }
    }
}

@Composable
private fun ControlModeSelectorRow(
    currentMode: ControlMode,
    onSelectMode: (ControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, OutlineBorder, RoundedCornerShape(10.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ControlMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CyanAccent else DarkSurfaceElevated)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("mode_chip_${mode.name}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (mode) {
                        ControlMode.SLIDERS -> "Sliders"
                        ControlMode.BUTTONS -> "Buttons"
                        ControlMode.JOYSTICK -> "Joystick"
                        ControlMode.GYRO -> "Gyro"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) DarkBackground else TextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
