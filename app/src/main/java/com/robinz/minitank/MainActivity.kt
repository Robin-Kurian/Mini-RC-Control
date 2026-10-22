package com.robinz.minitank

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robinz.minitank.ui.TankViewModel
import com.robinz.minitank.ui.screens.DiagnosticsScreen
import com.robinz.minitank.ui.screens.MainControllerScreen
import com.robinz.minitank.ui.screens.RouteScreen
import com.robinz.minitank.ui.screens.ScanDeviceDialog
import com.robinz.minitank.ui.screens.SettingsDialog
import com.robinz.minitank.ui.theme.RobinMiniTankTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TankViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RobinMiniTankTheme {
                TankApp(viewModel)
            }
        }
    }
}

@Composable
fun TankApp(viewModel: TankViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Request BLE permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        permissionLauncher.launch(permissionsToRequest)
    }

    // Fail-safe: stop tank on app pause, backgrounding, or loss of focus
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onDriveReleased()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onDriveReleased()
        }
    }

    // Main Navigation
    when {
        uiState.showDiagnosticsScreen -> {
            DiagnosticsScreen(
                connectedName = uiState.connectedDeviceName,
                connectedAddress = uiState.connectedDeviceAddress,
                services = uiState.discoveredServices,
                logs = uiState.diagnosticsLogs,
                isBroadcasting = uiState.isBroadcasting,
                lastBroadcastHex = uiState.lastBroadcastHex,
                broadcastCounter = uiState.broadcastCounter,
                useExtendedAdvertising = uiState.useExtendedAdvertising,
                onBack = { viewModel.setShowDiagnosticsScreen(false) },
                onSendHex = { uuid, hex -> viewModel.sendManualHex(uuid, hex) },
                onReadCharacteristic = { uuid -> viewModel.readCharacteristic(uuid) },
                onClearLogs = { viewModel.clearDiagnosticsLogs() },
                onExportLogs = { viewModel.exportDiagnosticsText() },
                onToggleBroadcast = { viewModel.toggleBroadcasting() },
                onUpdateBroadcastControls = { dir, intensity, axis ->
                    viewModel.bleAdvertiser.updateLiveControls(dir, intensity, axis)
                }
            )
        }
        uiState.showRouteScreen -> {
            RouteScreen(
                currentSpeedCap = uiState.speedCapPercent,
                onSpeedCapChanged = { viewModel.setSpeedCapPercent(it) },
                onIncrementSpeed = { viewModel.incrementSpeedCap() },
                onDecrementSpeed = { viewModel.decrementSpeedCap() },
                onBack = { viewModel.setShowRouteScreen(false) }
            )
        }
        else -> {
            MainControllerScreen(
                uiState = uiState,
                onButtonsInput = { fwd, rev, left, right ->
                    viewModel.onButtonsInput(fwd, rev, left, right)
                },
                onSlidersInput = { dy, maxTravelY, dx, maxTravelX ->
                    viewModel.onSlidersInput(dy, maxTravelY, dx, maxTravelX)
                },
                onJoystickInput = { dx, dy, radius ->
                    viewModel.onJoystickInput(dx, dy, radius)
                },
                onGyroEngage = { active ->
                    viewModel.onGyroEngage(active)
                },
                onCalibrateGyroZero = {
                    viewModel.calibrateGyroZero()
                },
                onDriveReleased = {
                    viewModel.onDriveReleased()
                },
                onEmergencyStop = {
                    viewModel.onEmergencyStop()
                },
                onSpeedCapChanged = {
                    viewModel.setSpeedCapPercent(it)
                },
                onIncrementSpeedCap = {
                    viewModel.incrementSpeedCap()
                },
                onDecrementSpeedCap = {
                    viewModel.decrementSpeedCap()
                },
                onSelectControlMode = {
                    viewModel.setControlMode(it)
                },
                onBluetoothClick = {
                    if (uiState.connectionState.isConnected) {
                        viewModel.bleClient.disconnect(cleanShutdown = true)
                    } else {
                        viewModel.setShowScanDialog(true)
                    }
                },
                onToggleBroadcast = {
                    viewModel.toggleBroadcasting()
                },
                onLightsToggle = {
                    viewModel.toggleLights()
                },
                onLightsBlinkToggle = {
                    viewModel.toggleLightsSlowBlink()
                },
                onSoundToggle = {
                    viewModel.toggleSound()
                },
                onRouteClick = {
                    viewModel.setShowRouteScreen(true)
                },
                onSettingsClick = {
                    viewModel.setShowSettingsDialog(true)
                }
            )
        }
    }

    // Modal Dialogs
    if (uiState.showScanDialog) {
        ScanDeviceDialog(
            scannedDevices = uiState.scannedDevices,
            isScanning = uiState.isScanning,
            connectionState = uiState.connectionState,
            isBroadcasting = uiState.isBroadcasting,
            onStartScan = { filterFff0 ->
                viewModel.bleClient.startScan(filterFff0)
            },
            onStopScan = {
                viewModel.bleClient.stopScan()
            },
            onSelectDevice = { address, name ->
                viewModel.bleClient.connect(address, name)
            },
            onToggleBroadcast = {
                viewModel.toggleBroadcasting()
            },
            onCancelConnect = {
                viewModel.bleClient.disconnect(cleanShutdown = true)
            },
            onDismiss = {
                viewModel.setShowScanDialog(false)
            }
        )
    }

    if (uiState.showSettingsDialog) {
        SettingsDialog(
            currentMode = uiState.controlMode,
            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
            autoReconnectEnabled = uiState.autoReconnectEnabled,
            lightsBlinkEnabled = uiState.lightsBlinkEnabled,
            onSelectMode = { viewModel.setControlMode(it) },
            onHapticChange = { viewModel.setHapticFeedback(it) },
            onAutoReconnectChange = { viewModel.setAutoReconnect(it) },
            onLightsBlinkToggle = { viewModel.toggleLightsSlowBlink() },
            onTriggerEngineStart = { viewModel.triggerEngineStart() },
            onOpenDiagnostics = { viewModel.setShowDiagnosticsScreen(true) },
            onDismiss = { viewModel.setShowSettingsDialog(false) }
        )
    }
}
