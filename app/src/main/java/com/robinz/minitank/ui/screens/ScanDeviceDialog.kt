package com.robinz.minitank.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.robinz.minitank.ble.ConnectionState
import com.robinz.minitank.ble.DiscoveredDevice
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.DarkSurfaceVariant
import com.robinz.minitank.ui.theme.EmergencyRed
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.SuccessGreen
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary
import com.robinz.minitank.ui.theme.TextSecondary
import com.robinz.minitank.ui.theme.WarningAmber

import androidx.compose.material.icons.filled.Sensors

enum class ScanFilterMode(val label: String) {
    TANKS_ONLY("Tanks Only"),
    NAMED_ONLY("Named"),
    ALL("All Devices")
}

@Composable
fun ScanDeviceDialog(
    scannedDevices: List<DiscoveredDevice>,
    isScanning: Boolean,
    connectionState: ConnectionState = ConnectionState.Disconnected,
    isBroadcasting: Boolean = false,
    onStartScan: (filterFff0: Boolean) -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (address: String, name: String?) -> Unit,
    onToggleBroadcast: () -> Unit = {},
    onCancelConnect: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var filterMode by remember { mutableStateOf(ScanFilterMode.TANKS_ONLY) }
    var showHelpBanner by remember { mutableStateOf(false) }

    // Auto-dismiss dialog once device is successfully connected
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            onDismiss()
        }
    }

    val connectingAddress = when (connectionState) {
        is ConnectionState.Connecting -> connectionState.deviceAddress
        is ConnectionState.DiscoveringServices -> connectionState.deviceAddress
        else -> null
    }

    val filteredList = remember(scannedDevices, filterMode) {
        when (filterMode) {
            ScanFilterMode.TANKS_ONLY -> scannedDevices.filter {
                it.isLikelyTank || (it.isNamed && !it.isPeripheralOrTag)
            }
            ScanFilterMode.NAMED_ONLY -> scannedDevices.filter { it.isNamed }
            ScanFilterMode.ALL -> scannedDevices
        }
    }

    val hiddenCount = scannedDevices.size - filteredList.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceElevated,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .border(1.dp, OutlineBorder, RoundedCornerShape(20.dp))
                .testTag("scan_device_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mini Tank Control Signal",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showHelpBanner = !showHelpBanner },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Why unknown devices appear?",
                                tint = if (showHelpBanner) CyanAccent else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted
                            )
                        }
                    }
                }

                // Educational / Helpful Banner regarding Unknown Peripherals / Mice
                AnimatedVisibility(visible = showHelpBanner) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBackground)
                            .border(1.dp, OutlineBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Why do unknown devices & mice appear?",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bluetooth Low Energy devices (wireless mice, smartwatches, keyboards, TVs, and beacons) broadcast continuously. Many don't include their name in the primary beacon packet, so Android displays them as 'Unknown Peripheral'.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tip: Keep 'Tanks Only' active to hide irrelevant ambient accessories.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SuccessGreen,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Filter & Scan Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ScanFilterMode.values().forEach { mode ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = { filterMode = mode },
                                label = {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        color = if (filterMode == mode) CyanAccent else TextSecondary
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DarkBackground,
                                    containerColor = DarkSurface
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyanAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scanning",
                                style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontSize = 11.sp)
                            )
                        } else {
                            IconButton(
                                onClick = { onStartScan(false) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rescan",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Device count / ambient hidden notice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = if (scannedDevices.isEmpty()) {
                            "Scanning 2.4 GHz BLE band..."
                        } else {
                            "Found ${scannedDevices.size} nearby" +
                                if (hiddenCount > 0 && filterMode != ScanFilterMode.ALL) " ($hiddenCount ambient hidden)" else ""
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )

                    if (hiddenCount > 0 && filterMode == ScanFilterMode.TANKS_ONLY) {
                        Text(
                            text = "Show All",
                            maxLines = 1,
                            modifier = Modifier
                                .clickable { filterMode = ScanFilterMode.ALL }
                                .padding(4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // BLE Broadcast Mode (Extended Advertising) Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkBackground,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isBroadcasting) CyanAccent else OutlineBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = if (isBroadcasting) CyanAccent else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "One-way control signal",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBroadcasting) CyanAccent else TextPrimary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBroadcasting) {
                                    "Car control active • one-way BLE signal"
                                } else {
                                    "Turn the car on, then start the control signal"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            )
                        }

                        Button(
                            onClick = onToggleBroadcast,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBroadcasting) CyanAccent.copy(alpha = 0.25f) else DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (isBroadcasting) "Stop Signal" else "Start Signal",
                                color = if (isBroadcasting) CyanAccent else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Active Connection Progress or Error Banner
                AnimatedVisibility(visible = connectingAddress != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = CyanAccent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (connectionState is ConnectionState.DiscoveringServices) "Discovering Tank Services..." else "Connecting to Tank...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = connectingAddress.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                            TextButton(
                                onClick = onCancelConnect,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Cancel", color = EmergencyRed, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (connectionState is ConnectionState.Error) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.8f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = EmergencyRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Connection Failed",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = EmergencyRed,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                                TextButton(
                                    onClick = { onStartScan(false) },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Retry Scan", color = CyanAccent, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = connectionState.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }
                }

                // Devices List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = if (filterMode == ScanFilterMode.TANKS_ONLY && scannedDevices.isNotEmpty()) {
                                    "The car does not need to appear in this list"
                                } else if (isScanning) {
                                    "Searching for nearby Bluetooth tanks..."
                                } else {
                                    "No BLE devices detected"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (filterMode == ScanFilterMode.TANKS_ONLY && hiddenCount > 0) {
                                    "This tank listens for a one-way signal; scanning cannot confirm reception."
                                } else {
                                    "Ensure your mini tank is powered ON with battery connected."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (filterMode != ScanFilterMode.ALL && hiddenCount > 0) {
                                    Button(
                                        onClick = { filterMode = ScanFilterMode.ALL },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Show All ($hiddenCount)", color = CyanAccent, fontSize = 12.sp)
                                    }
                                }
                                Button(
                                    onClick = { onStartScan(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Scan Again", color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_device_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredList.forEach { device ->
                            val isConnectingThis = connectingAddress.equals(device.address, ignoreCase = true)
                            DeviceRowItem(
                                device = device,
                                isConnectingThis = isConnectingThis,
                                onClick = {
                                    if (connectingAddress == null) {
                                        onStopScan()
                                        onSelectDevice(device.address, device.name)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

}

@Composable
private fun DeviceRowItem(
    device: DiscoveredDevice,
    isConnectingThis: Boolean = false,
    onClick: () -> Unit
) {
    val isPrimaryMatch = device.hasFff0Service || device.isLikelyTank
    val isPeripheral = device.isPeripheralOrTag && !device.hasFff0Service

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBackground)
            .border(
                1.dp,
                if (device.hasFff0Service) CyanAccent.copy(alpha = 0.6f)
                else if (device.isLikelyTank) CyanAccent.copy(alpha = 0.3f)
                else OutlineBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("device_item_${device.address}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name ?: "Unknown Peripheral",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isPrimaryMatch) FontWeight.Bold else FontWeight.Medium,
                        color = if (isPrimaryMatch) TextPrimary else TextSecondary,
                        fontSize = 14.sp
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Badge
                if (device.hasFff0Service) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SuccessGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "TANK (FFF0)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SuccessGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else if (device.isLikelyTank) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "TANK CANDIDATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CyanAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else if (isPeripheral && device.isNamed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(WarningAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACCESSORY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WarningAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                } else if (!device.isNamed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TextMuted.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "UNNAMED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = device.address,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            )
        }

        // RSSI Badge or Connecting Indicator
        if (isConnectingThis) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CyanAccent.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = CyanAccent
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Connecting",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val signalColor = when {
                    device.rssi > -65 -> SuccessGreen
                    device.rssi > -80 -> CyanAccent
                    else -> TextMuted
                }

                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = signalColor,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(signalColor)
                )
            }
        }
    }
}
