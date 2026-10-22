package com.robinz.minitank.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robinz.minitank.ble.BleConstants
import com.robinz.minitank.ble.DiagnosticLog
import com.robinz.minitank.ble.DiscoveredCharacteristic
import com.robinz.minitank.ble.DiscoveredService
import com.robinz.minitank.ble.LogType
import com.robinz.minitank.ble.hexToBytes
import com.robinz.minitank.ui.theme.CyanAccent
import com.robinz.minitank.ui.theme.DarkBackground
import com.robinz.minitank.ui.theme.DarkSurface
import com.robinz.minitank.ui.theme.DarkSurfaceElevated
import com.robinz.minitank.ui.theme.DarkSurfaceVariant
import com.robinz.minitank.ui.theme.EmergencyRed
import com.robinz.minitank.ui.theme.OutlineBorder
import com.robinz.minitank.ui.theme.TextMuted
import com.robinz.minitank.ui.theme.TextPrimary
import com.robinz.minitank.ui.theme.TextSecondary
import com.robinz.minitank.ui.theme.SuccessGreen
import com.robinz.minitank.ui.theme.WarningAmber
import java.util.UUID

import androidx.compose.material.icons.filled.Sensors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    connectedName: String?,
    connectedAddress: String?,
    services: List<DiscoveredService>,
    logs: List<DiagnosticLog>,
    isBroadcasting: Boolean = false,
    lastBroadcastHex: String? = null,
    broadcastCounter: Int = 0x80,
    useExtendedAdvertising: Boolean = true,
    onBack: () -> Unit,
    onSendHex: (uuid: UUID, hex: String) -> Unit,
    onReadCharacteristic: (uuid: UUID) -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> String,
    onToggleBroadcast: () -> Unit = {},
    onUpdateBroadcastControls: (dir: Byte, intensity: Byte, axis: Byte) -> Unit = { _, _, _ -> }
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Services & Sender, 1: Live Logs
    var hasArmedMotorsInSession by remember { mutableStateOf(false) }
    var showArmConfirmationDialog by remember { mutableStateOf(false) }
    var pendingHexToSend by remember { mutableStateOf<Pair<UUID, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "GATT Diagnostics Lab",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (connectedAddress != null) {
                                "${connectedName ?: "Tank"} ($connectedAddress)"
                            } else {
                                "Disconnected (Offline)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (connectedAddress != null) CyanAccent else TextMuted
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = onExportLogs()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Mini RC Control Diagnostic Log")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Diagnostic Log"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Log",
                            tint = CyanAccent
                        )
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = TextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Wheels-Off-The-Ground Safety Warning Banner
            SafetyWarningBanner()

            // Tabs: Services / Sender vs Live Logs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = CyanAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyanAccent
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("GATT Tree", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Live Logs (${logs.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            if (isBroadcasting) "EXT-ADV (LIVE)" else "EXT-ADV Broadcast",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (isBroadcasting) CyanAccent else TextPrimary
                        )
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    ServicesAndSenderTab(
                        services = services,
                        onReadCharacteristic = onReadCharacteristic,
                        onQueueHexSend = { uuid, hex ->
                            if (!hasArmedMotorsInSession) {
                                pendingHexToSend = Pair(uuid, hex)
                                showArmConfirmationDialog = true
                            } else {
                                onSendHex(uuid, hex)
                            }
                        }
                    )
                }
                1 -> {
                    LiveLogsTab(logs = logs)
                }
                2 -> {
                    ExtendedAdvertisingTab(
                        isBroadcasting = isBroadcasting,
                        lastBroadcastHex = lastBroadcastHex,
                        broadcastCounter = broadcastCounter,
                        useExtendedAdvertising = useExtendedAdvertising,
                        onToggleBroadcast = onToggleBroadcast,
                        onUpdateBroadcastControls = onUpdateBroadcastControls
                    )
                }
            }
        }
    }

    // Safety Confirmation Dialog before first manual write of the session
    if (showArmConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                showArmConfirmationDialog = false
                pendingHexToSend = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Manual Motor Write",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Writing raw hexadecimal packets to an uncalibrated BLE peripheral can activate the drive motors or steering servos unexpectedly.\n\nVerify that the tank is raised on a stand and its tracks cannot touch the floor before proceeding.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        hasArmedMotorsInSession = true
                        showArmConfirmationDialog = false
                        pendingHexToSend?.let { (uuid, hex) ->
                            onSendHex(uuid, hex)
                        }
                        pendingHexToSend = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("I Confirmed, Send Bytes", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showArmConfirmationDialog = false
                        pendingHexToSend = null
                    }
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SafetyWarningBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EmergencyRed.copy(alpha = 0.12f))
            .border(1.dp, EmergencyRed.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = EmergencyRed,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "SAFETY WARNING: Raise tank wheels/tracks off the floor during manual packet discovery to prevent runaway collisions.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServicesAndSenderTab(
    services: List<DiscoveredService>,
    onReadCharacteristic: (uuid: UUID) -> Unit,
    onQueueHexSend: (uuid: UUID, hex: String) -> Unit
) {
    var hexInputText by remember { mutableStateOf("") }
    var selectedCharUuid by remember { mutableStateOf<UUID?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Collect all writable characteristics
    val writableChars = remember(services) {
        services.flatMap { s ->
            s.characteristics.filter { it.canWrite || it.canWriteNoResp }
        }
    }

    LaunchedEffect(writableChars) {
        if (selectedCharUuid == null && writableChars.isNotEmpty()) {
            // Default to FFF1 or FFF2
            val candidate = writableChars.find { it.uuid == BleConstants.CHAR_FFF1 }
                ?: writableChars.find { it.uuid == BleConstants.CHAR_FFF2 }
                ?: writableChars.first()
            selectedCharUuid = candidate.uuid
        }
    }

    val isValidHex = remember(hexInputText) {
        hexInputText.hexToBytes() != null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Manual Hex Sender Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, OutlineBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "MANUAL HEX PACKET SENDER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Target Characteristic Selector
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCharUuid?.let { "Char: ${it.toString().substring(4, 8).uppercase()} ($it)" } ?: "Select Characteristic",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Characteristic") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = OutlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        writableChars.forEach { char ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${char.uuid.toString().substring(4, 8).uppercase()} (${if (char.canWriteNoResp) "WRITE_NO_RESP" else "WRITE"})",
                                        color = TextPrimary
                                    )
                                },
                                onClick = {
                                    selectedCharUuid = char.uuid
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hex Input Field
                OutlinedTextField(
                    value = hexInputText,
                    onValueChange = { hexInputText = it },
                    label = { Text("Hex Bytes (e.g. FF 01 02 A4)") },
                    placeholder = { Text("Enter hex digits...") },
                    singleLine = true,
                    isError = hexInputText.isNotEmpty() && !isValidHex,
                    supportingText = {
                        if (hexInputText.isNotEmpty() && !isValidHex) {
                            Text("Invalid hex: Must have even number of valid hex chars (0-9, A-F)", color = EmergencyRed)
                        } else if (isValidHex) {
                            val byteCount = hexInputText.hexToBytes()?.size ?: 0
                            Text("$byteCount byte(s) ready to send", color = CyanAccent)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = OutlineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("hex_input_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val uuid = selectedCharUuid ?: return@Button
                        if (isValidHex) {
                            onQueueHexSend(uuid, hexInputText)
                        }
                    },
                    enabled = isValidHex && selectedCharUuid != null,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_hex_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = DarkBackground,
                        disabledContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Send Packet via GATT", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Discovered Services & Characteristics List
        item {
            Text(
                text = "DISCOVERED GATT SERVICES (${services.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            )
        }

        if (services.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No GATT services discovered yet. Connect to a tank first.",
                        color = TextMuted
                    )
                }
            }
        } else {
            items(services) { service ->
                ServiceCard(
                    service = service,
                    onReadCharacteristic = onReadCharacteristic
                )
            }
        }
    }
}

@Composable
private fun ServiceCard(
    service: DiscoveredService,
    onReadCharacteristic: (uuid: UUID) -> Unit
) {
    val isTankService = service.uuid == BleConstants.SERVICE_TANK

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(
                1.dp,
                if (isTankService) CyanAccent.copy(alpha = 0.6f) else OutlineBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isTankService) "TANK SERVICE (FFF0)" else "Service",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isTankService) CyanAccent else TextPrimary
                )
            )
        }
        Text(
            text = service.uuid.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = TextMuted,
                fontSize = 11.sp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Characteristics
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            service.characteristics.forEach { char ->
                CharacteristicRow(
                    char = char,
                    onRead = { onReadCharacteristic(char.uuid) }
                )
            }
        }
    }
}

@Composable
private fun CharacteristicRow(
    char: DiscoveredCharacteristic,
    onRead: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkBackground)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = char.uuid.toString().substring(4, 8).uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    fontFamily = FontFamily.Monospace
                )
            )

            // Property Badges
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (char.canRead) PropertyChip("READ")
                if (char.canWrite) PropertyChip("WRITE")
                if (char.canWriteNoResp) PropertyChip("NO_RESP")
                if (char.canNotify) PropertyChip("NOTIFY")
                if (char.canIndicate) PropertyChip("INDICATE")
            }
        }

        Text(
            text = char.uuid.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = TextMuted,
                fontSize = 10.sp
            )
        )

        // Last value readout and Read button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = char.lastValueHex?.let { "Val: $it" } ?: "Val: —",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (char.lastValueHex != null) CyanAccent else TextMuted,
                    fontSize = 11.sp
                )
            )

            if (char.canRead) {
                Button(
                    onClick = onRead,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read", fontSize = 10.sp, color = CyanAccent)
                }
            }
        }
    }
}

@Composable
private fun PropertyChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurfaceVariant)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun LiveLogsTab(logs: List<DiagnosticLog>) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No diagnostic events recorded yet.",
                color = TextMuted
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                val color = when (log.type) {
                    LogType.RX_NOTIFY -> CyanAccent
                    LogType.TX_WRITE -> WarningAmber
                    LogType.INFO -> TextSecondary
                    LogType.WARNING -> WarningAmber
                    LogType.ERROR -> EmergencyRed
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .border(1.dp, OutlineBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = log.formatDisplay(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = color,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtendedAdvertisingTab(
    isBroadcasting: Boolean,
    lastBroadcastHex: String?,
    broadcastCounter: Int,
    useExtendedAdvertising: Boolean,
    onToggleBroadcast: () -> Unit,
    onUpdateBroadcastControls: (dir: Byte, intensity: Byte, axis: Byte) -> Unit
) {
    var selectedDir by remember { mutableStateOf<Byte>(0x00) }
    var selectedIntensity by remember { mutableStateOf<Byte>(0x00) }
    var selectedAxis by remember { mutableStateOf<Byte>(0x04) }
    var speedCapPercent by remember { mutableIntStateOf(100) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status & Toggle Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBroadcasting) CyanAccent else OutlineBorder
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = if (isBroadcasting) CyanAccent else TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "BLE Broadcast Engine",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = if (useExtendedAdvertising) "Bluetooth 5 Extended Advertising (HCI 0x2037)" else "Legacy BLE Advertising",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = CyanAccent,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = onToggleBroadcast,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBroadcasting) EmergencyRed else CyanAccent
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isBroadcasting) "STOP" else "START",
                                fontWeight = FontWeight.Bold,
                                color = if (isBroadcasting) TextPrimary else DarkBackground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Broadcasts 23-byte AD Structure (16 FF 00 00 [19-byte payload]) roughly every 0.8s.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                    )
                }
            }
        }

        // Live Packet Stream Monitor
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Transmitted Packet (23 Bytes)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        )
                        Text(
                            text = "Counter: 0x%02X".format(broadcastCounter),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = WarningAmber,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = lastBroadcastHex ?: "Broadcast stopped. Tap START above to begin.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = if (lastBroadcastHex != null) SuccessGreen else TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Formula: 0xE9 XOR byte[0] ... XOR byte[19]. Rolling counter advances each packet.",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp)
                    )
                }
            }
        }

        // Controlled Capture & Injection Playground
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Controlled Capture Testing",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Test specific payload states while logging BTSnoop captures with Codex:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Buttons (Confirmed captures)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    selectedDir = 0x00
                                    selectedIntensity = 0x00
                                    selectedAxis = 0x04
                                    onUpdateBroadcastControls(0x00, 0x00, 0x04)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("IDLE (00 00 04)", fontSize = 11.sp, color = SuccessGreen)
                            }

                            Button(
                                onClick = {
                                    selectedDir = 0x01
                                    selectedIntensity = 0x1E // 30% confirmed
                                    selectedAxis = 0x05
                                    onUpdateBroadcastControls(0x01, 0x1E.toByte(), 0x05)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("FWD 30% (01 1E 05)", fontSize = 11.sp, color = CyanAccent)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    selectedDir = 0x02
                                    selectedIntensity = 0x1E // 30% confirmed
                                    selectedAxis = 0x04
                                    onUpdateBroadcastControls(0x02, 0x1E.toByte(), 0x04)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("REV 30% (02 1E 04)", fontSize = 11.sp, color = WarningAmber)
                            }

                            Button(
                                onClick = {
                                    selectedDir = 0x04
                                    selectedIntensity = 0x00 // Confirmed 0x00
                                    selectedAxis = 0x04
                                    onUpdateBroadcastControls(0x04, 0x00, 0x04)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("LEFT (04 00 04)", fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Current Selected Values
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "State: 0x%02X | Intensity: 0x%02X | Axis: 0x%02X".format(selectedDir, selectedIntensity, selectedAxis),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "SpeedCap: $speedCapPercent%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = CyanAccent
                            )
                        )
                    }
                }
            }
        }
    }
}
