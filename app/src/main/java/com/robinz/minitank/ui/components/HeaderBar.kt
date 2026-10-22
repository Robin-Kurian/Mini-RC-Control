package com.robinz.minitank.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ble.ConnectionState
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceVariant
import com.robinz.minitank.ui.theme.EmergencyRed
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.SuccessGreen
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary
import com.robinz.minitank.ui.theme.WarningAmber

import androidx.compose.material.icons.filled.Sensors

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HeaderBar(
    connectionState: ConnectionState,
    connectedDeviceName: String?,
    lightsEnabled: Boolean,
    lightsBlinkEnabled: Boolean = false,
    soundActive: Boolean,
    isBroadcasting: Boolean = false,
    onBluetoothClick: () -> Unit,
    onToggleBroadcast: () -> Unit = {},
    onLightsToggle: () -> Unit,
    onLightsBlinkToggle: () -> Unit,
    onSoundToggle: () -> Unit,
    onRouteClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            is ConnectionState.Connected -> SuccessGreen
            is ConnectionState.Connecting, is ConnectionState.DiscoveringServices -> WarningAmber
            is ConnectionState.Scanning, is ConnectionState.Reconnecting -> CyanAccent
            is ConnectionState.Error -> EmergencyRed
            is ConnectionState.Disconnected -> TextMuted
        },
        label = "statusColor"
    )

    val narrow = LocalConfiguration.current.screenWidthDp < 600
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, OutlineBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = if (narrow) 1 else 2
    ) {
        // App Title & Status Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onBluetoothClick() }
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .testTag("connection_pill")
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isBroadcasting) CyanAccent else statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Robin'z Mini Tank",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isBroadcasting) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyanAccent.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "READY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyanAccent
                                )
                            )
                        }
                    }
                }
                Text(
                    text = if (isBroadcasting) {
                        "ENGINE ON"
                    } else when (connectionState) {
                        is ConnectionState.Connected -> connectedDeviceName ?: "Tank Connected"
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.DiscoveringServices -> "Configuring GATT..."
                        is ConnectionState.Scanning -> "Scanning..."
                        is ConnectionState.Reconnecting -> "Reconnecting..."
                        is ConnectionState.Error -> "Offline (Tap to Scan)"
                        is ConnectionState.Disconnected -> "Tap to Connect"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isBroadcasting) CyanAccent else statusColor,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Buttons: Broadcast, BLE, Headlights, Blink 1s, Sound, Route, Settings
        Row(
            modifier = if (narrow) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (narrow) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp)
        ) {
            // BLE Extended Advertising / Broadcast Toggle
            IconButton(
                onClick = onToggleBroadcast,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBroadcasting) CyanAccent.copy(alpha = 0.22f) else DarkSurface)
                    .border(
                        1.dp,
                        if (isBroadcasting) CyanAccent else OutlineBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .testTag("broadcast_toggle_button")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Toggle BLE Broadcast Mode",
                        tint = if (isBroadcasting) CyanAccent else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isBroadcasting) "TX" else "OFF",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBroadcasting) CyanAccent else TextMuted
                        ),
                        lineHeight = 7.sp
                    )
                }
            }
            // BLE Icon button
            IconButton(
                onClick = onBluetoothClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("bluetooth_button")
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        is ConnectionState.Connected -> Icons.Default.BluetoothConnected
                        is ConnectionState.Disconnected -> Icons.Default.BluetoothDisabled
                        else -> Icons.Default.Bluetooth
                    },
                    contentDescription = "Bluetooth Status",
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Headlights Toggle (Discovered as "前灯" on car PCB)
            IconButton(
                onClick = onLightsToggle,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("lights_toggle_button")
            ) {
                Icon(
                    imageVector = if (lightsEnabled) Icons.Filled.Highlight else Icons.Outlined.Highlight,
                    contentDescription = "Headlights",
                    tint = if (lightsEnabled) CyanAccent else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Slow Blink Lights Button (1 second delay on/off)
            IconButton(
                onClick = onLightsBlinkToggle,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (lightsBlinkEnabled) WarningAmber.copy(alpha = 0.22f) else DarkSurface)
                    .border(
                        1.dp,
                        if (lightsBlinkEnabled) WarningAmber else OutlineBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .testTag("lights_blink_button")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (lightsBlinkEnabled) Icons.Filled.Highlight else Icons.Outlined.Highlight,
                        contentDescription = "Slow Blink Lights (1s delay)",
                        tint = if (lightsBlinkEnabled) WarningAmber else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "1s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (lightsBlinkEnabled) WarningAmber else TextMuted
                        ),
                        lineHeight = 7.sp
                    )
                }
            }

            // Sound / Horn Toggle (Discovered as "+SPK-" on car PCB)
            IconButton(
                onClick = onSoundToggle,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("sound_toggle_button")
            ) {
                Icon(
                    imageVector = if (soundActive) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                    contentDescription = "Horn Sound",
                    tint = if (soundActive) CyanAccent else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Route Planner
            IconButton(
                onClick = onRouteClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("route_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = "Route Mode",
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Settings
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
