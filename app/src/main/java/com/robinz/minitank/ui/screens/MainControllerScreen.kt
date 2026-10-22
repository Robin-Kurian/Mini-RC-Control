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
    if (uiState.controlMode == ControlMode.JOYSTICK) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HeaderBar(
                    connectionState = uiState.connectionState,
                    connectedDeviceName = uiState.connectedDeviceName,
                    lightsEnabled = uiState.lightsEnabled,
                    lightsBlinkEnabled = uiState.lightsBlinkEnabled,
                    soundActive = uiState.soundActive,
                    isBroadcasting = uiState.isBroadcasting,
                    onBluetoothClick = onBluetoothClick,
                    onEmergencyStop = onEmergencyStop,
                    onToggleBroadcast = onToggleBroadcast,
                    onLightsToggle = onLightsToggle,
                    onLightsBlinkToggle = onLightsBlinkToggle,
                    onSoundToggle = onSoundToggle,
                    onRouteClick = onRouteClick,
                    onSettingsClick = onSettingsClick,
                    forceNarrow = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                LandscapeDashboard(
                    uiState = uiState,
                    onSpeedCapChanged = onSpeedCapChanged,
                    onIncrementSpeedCap = onIncrementSpeedCap,
                    onDecrementSpeedCap = onDecrementSpeedCap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ControlModeSelectorRow(
                        currentMode = uiState.controlMode,
                        onSelectMode = onSelectControlMode
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                JoystickDriveControl(
                    onJoystickChanged = onJoystickInput,
                    onReleased = onDriveReleased
                )
            }
        }
    } else {
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
            onEmergencyStop = onEmergencyStop,
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
            if (uiState.controlMode == ControlMode.JOYSTICK) {
                LandscapeDashboard(
                    uiState = uiState,
                    onSpeedCapChanged = onSpeedCapChanged,
                    onIncrementSpeedCap = onIncrementSpeedCap,
                    onDecrementSpeedCap = onDecrementSpeedCap,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    JoystickDriveControl(
                        onJoystickChanged = onJoystickInput,
                        onReleased = onDriveReleased
                    )
                }
            } else {
                when (uiState.controlMode) {
                    ControlMode.SLIDERS -> {
                        SlidersDriveControl(
                            onSlidersChanged = onSlidersInput,
                            onReleased = onDriveReleased,
                            isLandscape = true,
                            modifier = Modifier.weight(1f),
                            centerContent = {
                                LandscapeDashboard(
                                    uiState = uiState,
                                    onSpeedCapChanged = onSpeedCapChanged,
                                    onIncrementSpeedCap = onIncrementSpeedCap,
                                    onDecrementSpeedCap = onDecrementSpeedCap,
                                    modifier = Modifier.fillMaxHeight(),
                                    showControlModeSelector = true,
                                    currentMode = uiState.controlMode,
                                    onSelectControlMode = onSelectControlMode
                                )
                            }
                        )
                    }
                    ControlMode.BUTTONS -> {
                        ButtonsDriveControl(
                            onButtonsChanged = onButtonsInput,
                            onReleased = onDriveReleased,
                            isLandscape = true,
                            modifier = Modifier.weight(1f),
                            centerContent = {
                                LandscapeDashboard(
                                    uiState = uiState,
                                    onSpeedCapChanged = onSpeedCapChanged,
                                    onIncrementSpeedCap = onIncrementSpeedCap,
                                    onDecrementSpeedCap = onDecrementSpeedCap,
                                    modifier = Modifier.width(240.dp).fillMaxHeight()
                                )
                            }
                        )
                    }
                    ControlMode.JOYSTICK -> Unit
                    ControlMode.GYRO -> {
                        GyroDriveControl(
                            pitch = uiState.gyroPitch,
                            roll = uiState.gyroRoll,
                            calibratedPitch = uiState.gyroCalibratedPitch,
                            calibratedRoll = uiState.gyroCalibratedRoll,
                            isEngaged = uiState.gyroActive,
                            onEngageChange = onGyroEngage,
                            onCalibrateZero = onCalibrateGyroZero,
                            modifier = Modifier.weight(1f),
                            centerContent = {
                                LandscapeDashboard(
                                    uiState = uiState,
                                    onSpeedCapChanged = onSpeedCapChanged,
                                    onIncrementSpeedCap = onIncrementSpeedCap,
                                    onDecrementSpeedCap = onDecrementSpeedCap,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                        )
                    }
                }

                if (uiState.controlMode != ControlMode.BUTTONS &&
                    uiState.controlMode != ControlMode.SLIDERS &&
                    uiState.controlMode != ControlMode.GYRO
                ) {
                    LandscapeDashboard(
                        uiState = uiState,
                        onSpeedCapChanged = onSpeedCapChanged,
                        onIncrementSpeedCap = onIncrementSpeedCap,
                        onDecrementSpeedCap = onDecrementSpeedCap,
                        modifier = Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                            .padding(start = 12.dp)
                    )
                }
            }
        }

        if (uiState.controlMode != ControlMode.SLIDERS) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ControlModeSelectorRow(
                    currentMode = uiState.controlMode,
                    onSelectMode = onSelectControlMode
                )
            }
        }

    }
    }
}

@Composable
private fun LandscapeDashboard(
    uiState: TankUiState,
    onSpeedCapChanged: (Int) -> Unit,
    onIncrementSpeedCap: () -> Unit,
    onDecrementSpeedCap: () -> Unit,
    modifier: Modifier = Modifier,
    showControlModeSelector: Boolean = false,
    currentMode: ControlMode = ControlMode.SLIDERS,
    onSelectControlMode: (ControlMode) -> Unit = {}
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        if (showControlModeSelector) {
            ControlModeSelectorRow(
                currentMode = currentMode,
                onSelectMode = onSelectControlMode
            )
        }
        SpeedCapControl(
            speedCapPercent = uiState.speedCapPercent,
            onSpeedCapChanged = onSpeedCapChanged,
            onIncrement = onIncrementSpeedCap,
            onDecrement = onDecrementSpeedCap,
            compact = true
        )
        TelemetryHud(
            rawThrottle = uiState.rawThrottle,
            rawSteering = uiState.rawSteering,
            cappedThrottle = uiState.cappedThrottle,
            speedCapPercent = uiState.speedCapPercent,
            isConnected = uiState.connectionState.isConnected,
            modifier = Modifier.widthIn(max = 200.dp)
        )
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
            .padding(horizontal = 12.dp, vertical = 16.dp),
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
            onEmergencyStop = onEmergencyStop,
            onToggleBroadcast = onToggleBroadcast,
            onLightsToggle = onLightsToggle,
            onLightsBlinkToggle = onLightsBlinkToggle,
            onSoundToggle = onSoundToggle,
            onRouteClick = onRouteClick,
            onSettingsClick = onSettingsClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        SpeedCapControl(
            speedCapPercent = uiState.speedCapPercent,
            onSpeedCapChanged = onSpeedCapChanged,
            onIncrement = onIncrementSpeedCap,
            onDecrement = onDecrementSpeedCap
        )

        Spacer(modifier = Modifier.height(14.dp))

        ControlModeSelectorRow(
            currentMode = uiState.controlMode,
            onSelectMode = onSelectControlMode
        )

        Spacer(modifier = Modifier.height(14.dp))

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

        Spacer(modifier = Modifier.height(14.dp))

        TelemetryHud(
            rawThrottle = uiState.rawThrottle,
            rawSteering = uiState.rawSteering,
            cappedThrottle = uiState.cappedThrottle,
            speedCapPercent = uiState.speedCapPercent,
            isConnected = uiState.connectionState.isConnected,
            modifier = Modifier.fillMaxWidth()
        )
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
