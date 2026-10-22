package com.robinz.minitank.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robinz.minitank.ble.BleTankAdvertiser
import com.robinz.minitank.ble.BleTankClient
import com.robinz.minitank.ble.ConnectionState
import com.robinz.minitank.ble.DiagnosticLog
import com.robinz.minitank.ble.DiscoveredDevice
import com.robinz.minitank.ble.DiscoveredService
import com.robinz.minitank.controller.ControlMode
import com.robinz.minitank.controller.DriveController
import com.robinz.minitank.data.TankPreferencesRepository
import com.robinz.minitank.protocol.UnknownTankProtocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class TankUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val speedCapPercent: Int = 20,
    val controlMode: ControlMode = ControlMode.SLIDERS,
    val hapticFeedbackEnabled: Boolean = true,
    val autoReconnectEnabled: Boolean = true,
    val lightsEnabled: Boolean = false,
    val lightsBlinkEnabled: Boolean = false,
    val soundActive: Boolean = false,
    val engineStarted: Boolean = false,
    val rawThrottle: Float = 0f,
    val rawSteering: Float = 0f,
    val cappedThrottle: Float = 0f,
    val scannedDevices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val showScanDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showDiagnosticsScreen: Boolean = false,
    val showRouteScreen: Boolean = false,
    val discoveredServices: List<DiscoveredService> = emptyList(),
    val diagnosticsLogs: List<DiagnosticLog> = emptyList(),
    val gyroPitch: Float = 0f,
    val gyroRoll: Float = 0f,
    val gyroActive: Boolean = false,
    val gyroCalibratedPitch: Float = 0f,
    val gyroCalibratedRoll: Float = 0f,
    val initialSpeedBoostEnabled: Boolean = false,
    val isBroadcasting: Boolean = false,
    val lastBroadcastHex: String? = null,
    val broadcastCounter: Int = 0x80,
    val broadcastError: String? = null,
    val useExtendedAdvertising: Boolean = true
)

class TankViewModel(
    application: Application
) : AndroidViewModel(application), SensorEventListener {

    companion object {
        private const val TAG = "TankViewModel"
    }

    private val preferencesRepository = TankPreferencesRepository(application)
    private val protocol = UnknownTankProtocol()
    val bleClient = BleTankClient(application, protocol)
    val bleAdvertiser = BleTankAdvertiser(application)
    private val driveController = DriveController(initialSpeedCapPercent = 20)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _uiState = MutableStateFlow(TankUiState())
    val uiState: StateFlow<TankUiState> = _uiState.asStateFlow()

    private var hasAttemptedAutoReconnect = false
    private var lightsBlinkJob: Job? = null

    init {
        // Wire BLE fail-safe stop trigger
        bleClient.onEmergencyStopTriggered = {
            stopLightsSlowBlink()
            driveController.reset()
            syncDriveState()
        }

        // Collect preferences
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collectLatest { prefs ->
                driveController.speedCapPercent = prefs.speedCapPercent
                _uiState.update { current ->
                    current.copy(
                        speedCapPercent = prefs.speedCapPercent,
                        controlMode = prefs.controlMode,
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        autoReconnectEnabled = prefs.autoReconnectEnabled,
                        initialSpeedBoostEnabled = prefs.initialSpeedBoostEnabled
                    )
                }
                syncDriveState()

                // Handle auto-reconnect once preferences are loaded
                if (prefs.autoReconnectEnabled && !prefs.lastDeviceAddress.isNullOrEmpty() && !hasAttemptedAutoReconnect) {
                    hasAttemptedAutoReconnect = true
                    val state = bleClient.connectionState.value
                    if (state is ConnectionState.Disconnected) {
                        Log.i(TAG, "Triggering auto-reconnect to saved tank: ${prefs.lastDeviceAddress}")
                        bleClient.connect(prefs.lastDeviceAddress, prefs.lastDeviceName)
                    }
                }
            }
        }

        // Collect BLE connection states
        viewModelScope.launch {
            bleClient.connectionState.collectLatest { state ->
                _uiState.update { current ->
                    val name = if (state is ConnectionState.Connected) state.deviceName else current.connectedDeviceName
                    val addr = if (state is ConnectionState.Connected) state.deviceAddress else current.connectedDeviceAddress
                    current.copy(
                        connectionState = state,
                        connectedDeviceName = name,
                        connectedDeviceAddress = addr
                    )
                }

                if (state is ConnectionState.Connected) {
                    // Save to preferences
                    preferencesRepository.saveLastConnectedDevice(state.deviceAddress, state.deviceName)
                    _uiState.update { it.copy(showScanDialog = false) }
                } else if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                    stopLightsSlowBlink()
                    driveController.reset()
                    syncDriveState()
                }
            }
        }

        // Collect Scanned devices
        viewModelScope.launch {
            bleClient.scannedDevices.collectLatest { list ->
                _uiState.update { it.copy(scannedDevices = list) }
            }
        }

        // Collect Scanning state
        viewModelScope.launch {
            bleClient.isScanning.collectLatest { isScan ->
                _uiState.update { it.copy(isScanning = isScan) }
            }
        }

        // Collect Discovered Services
        viewModelScope.launch {
            bleClient.discoveredServices.collectLatest { services ->
                _uiState.update { it.copy(discoveredServices = services) }
            }
        }

        // Collect Diagnostics Logs
        viewModelScope.launch {
            bleClient.diagnosticsLogs.collectLatest { logs ->
                _uiState.update { it.copy(diagnosticsLogs = logs) }
            }
        }

        // Collect BLE Advertiser State
        viewModelScope.launch {
            bleAdvertiser.isBroadcasting.collectLatest { broadcasting ->
                _uiState.update { it.copy(isBroadcasting = broadcasting) }
            }
        }
        viewModelScope.launch {
            bleAdvertiser.lastBroadcastHex.collectLatest { hex ->
                _uiState.update { it.copy(lastBroadcastHex = hex) }
            }
        }
        viewModelScope.launch {
            bleAdvertiser.rollingCounter.collectLatest { counter ->
                _uiState.update { it.copy(broadcastCounter = counter) }
            }
        }
        viewModelScope.launch {
            bleAdvertiser.lastError.collectLatest { err ->
                _uiState.update { it.copy(broadcastError = err) }
            }
        }
        viewModelScope.launch {
            bleAdvertiser.useExtendedAdvertising.collectLatest { ext ->
                _uiState.update { it.copy(useExtendedAdvertising = ext) }
            }
        }
    }

    // ---------------------------------------------------------
    // BLE Broadcaster Control
    // ---------------------------------------------------------
    fun toggleBroadcasting() {
        if (bleAdvertiser.isBroadcasting.value) {
            bleAdvertiser.stopBroadcasting()
        } else {
            bleAdvertiser.resetToIdle()
            bleAdvertiser.startBroadcasting()
        }
    }

    fun startBroadcasting() {
        bleAdvertiser.resetToIdle()
        bleAdvertiser.startBroadcasting()
    }

    fun stopBroadcasting() {
        bleAdvertiser.stopBroadcasting()
    }

    // ---------------------------------------------------------
    // Driving Inputs
    // ---------------------------------------------------------
    fun onButtonsInput(forward: Boolean, reverse: Boolean, left: Boolean, right: Boolean) {
        val state = driveController.updateButtons(forward, reverse, left, right)
        syncDriveState()
        dispatchDriveToBle(state)
        vibrateSubtle()
    }

    fun onSlidersInput(
        throttleDeltaY: Float,
        maxThrottleTravel: Float,
        steeringDeltaX: Float,
        maxSteeringTravel: Float
    ) {
        val state = driveController.updateSliders(
            throttleDeltaY = throttleDeltaY,
            maxThrottleTravel = maxThrottleTravel,
            steeringDeltaX = steeringDeltaX,
            maxSteeringTravel = maxSteeringTravel
        )
        syncDriveState()
        dispatchDriveToBle(state)
    }

    fun onJoystickInput(deltaX: Float, deltaY: Float, radius: Float) {
        val state = driveController.updateJoystick(deltaX, deltaY, radius)
        syncDriveState()
        dispatchDriveToBle(state)
    }

    fun onGyroEngage(active: Boolean) {
        _uiState.update { it.copy(gyroActive = active) }
        if (active) {
            startGyroSensor()
            vibrateSubtle()
        } else {
            stopGyroSensor()
            onDriveReleased()
        }
    }

    fun calibrateGyroZero() {
        val currentPitch = _uiState.value.gyroPitch
        val currentRoll = _uiState.value.gyroRoll
        _uiState.update {
            it.copy(
                gyroCalibratedPitch = currentPitch,
                gyroCalibratedRoll = currentRoll
            )
        }
        vibrateSubtle()
    }

    /**
     * Called on finger release, touch cancellation, or leaving controls.
     * Resets both axes and sends priority stop.
     */
    fun onDriveReleased() {
        driveController.reset()
        syncDriveState()
        bleClient.sendStopCommand()
        bleAdvertiser.resetToIdle()
    }

    fun onEmergencyStop() {
        stopLightsSlowBlink()
        driveController.reset()
        syncDriveState()
        bleClient.triggerFailSafeStop()
        bleAdvertiser.resetToIdle()
        vibrateEmergency()
    }

    private fun dispatchDriveToBle(driveState: com.robinz.minitank.controller.DriveState) {
        // BLE Extended Advertising / Broadcast mode (Confirmed Forward binding)
        bleAdvertiser.setDriveInput(
            throttle = driveState.rawThrottle,
            speedCapPercent = driveState.speedCapPercent
        )

        // Bilateral GATT mode (if connected to GATT UART tank)
        if (_uiState.value.connectionState.isConnected) {
            if (driveState.isZero) {
                bleClient.sendStopCommand()
            } else {
                bleClient.sendDriveCommand(
                    throttle = driveState.rawThrottle,
                    steering = driveState.rawSteering,
                    speedCapPercent = driveState.speedCapPercent
                )
            }
        }
    }

    private fun syncDriveState() {
        val state = driveController.getDriveState()
        _uiState.update {
            it.copy(
                rawThrottle = state.rawThrottle,
                rawSteering = state.rawSteering,
                cappedThrottle = state.cappedThrottle
            )
        }
    }

    // ---------------------------------------------------------
    // Speed Cap & Control Mode
    // ---------------------------------------------------------
    fun setSpeedCapPercent(percent: Int) {
        val clamped = percent.coerceIn(20, 100)
        driveController.speedCapPercent = clamped
        viewModelScope.launch {
            preferencesRepository.updateSpeedCap(clamped)
        }
        syncDriveState()
        vibrateSubtle()
    }

    fun incrementSpeedCap() {
        val current = _uiState.value.speedCapPercent
        if (current < 100) {
            setSpeedCapPercent(current + 10)
        }
    }

    fun decrementSpeedCap() {
        val current = _uiState.value.speedCapPercent
        if (current > 20) {
            setSpeedCapPercent(current - 10)
        }
    }

    fun setControlMode(mode: ControlMode) {
        viewModelScope.launch {
            preferencesRepository.updateControlMode(mode)
        }
        onDriveReleased()
        vibrateSubtle()
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHapticFeedback(enabled)
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAutoReconnect(enabled)
        }
    }

    // ---------------------------------------------------------
    // Secondary Hardware Controls
    // ---------------------------------------------------------
    fun toggleLights() {
        if (_uiState.value.lightsBlinkEnabled) {
            stopLightsSlowBlink()
        }
        val next = !_uiState.value.lightsEnabled
        _uiState.update { it.copy(lightsEnabled = next) }
        bleClient.setLights(next)
        vibrateSubtle()
    }

    /**
     * Toggles slow blinking mode where lights alternate on/off with 1 second delay.
     */
    fun toggleLightsSlowBlink() {
        val shouldEnable = !_uiState.value.lightsBlinkEnabled
        if (shouldEnable) {
            startLightsSlowBlink()
        } else {
            stopLightsSlowBlink()
        }
        vibrateSubtle()
    }

    fun startLightsSlowBlink() {
        lightsBlinkJob?.cancel()
        _uiState.update { it.copy(lightsBlinkEnabled = true) }
        lightsBlinkJob = viewModelScope.launch {
            try {
                while (isActive) {
                    // Turn Lights ON
                    _uiState.update { it.copy(lightsEnabled = true) }
                    bleClient.setLights(true)
                    delay(1000L) // 1 second delay

                    // Turn Lights OFF
                    _uiState.update { it.copy(lightsEnabled = false) }
                    bleClient.setLights(false)
                    delay(1000L) // 1 second delay
                }
            } catch (e: Exception) {
                // Ignore cancellation gracefully
            }
        }
    }

    fun stopLightsSlowBlink() {
        lightsBlinkJob?.cancel()
        lightsBlinkJob = null
        _uiState.update { it.copy(lightsBlinkEnabled = false, lightsEnabled = false) }
        bleClient.setLights(false)
    }

    fun toggleSound() {
        val next = !_uiState.value.soundActive
        _uiState.update { it.copy(soundActive = next) }
        bleClient.setSound(next)
        vibrateSubtle()
    }

    fun triggerEngineStart() {
        val next = !_uiState.value.engineStarted
        _uiState.update { it.copy(engineStarted = next) }
        bleClient.triggerEngineStart()
        vibrateSubtle()
    }

    // ---------------------------------------------------------
    // Dialog & Screen Visibility
    // ---------------------------------------------------------
    fun setShowScanDialog(show: Boolean) {
        _uiState.update { it.copy(showScanDialog = show) }
        if (show) {
            bleClient.startScan()
        } else {
            bleClient.stopScan()
        }
    }

    fun setShowSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun setShowDiagnosticsScreen(show: Boolean) {
        _uiState.update { it.copy(showDiagnosticsScreen = show) }
    }

    fun setShowRouteScreen(show: Boolean) {
        _uiState.update { it.copy(showRouteScreen = show) }
    }

    // ---------------------------------------------------------
    // Diagnostics Actions
    // ---------------------------------------------------------
    fun sendManualHex(uuid: UUID, hex: String) {
        viewModelScope.launch {
            bleClient.writeCustomHex(uuid, hex)
        }
    }

    fun readCharacteristic(uuid: UUID) {
        viewModelScope.launch {
            bleClient.readCharacteristic(uuid)
        }
    }

    fun exportDiagnosticsText(): String = bleClient.exportLogsText()

    fun clearDiagnosticsLogs() {
        bleClient.clearLogs()
    }

    // ---------------------------------------------------------
    // Sensor Handling (Gyro / Gravity Tilt)
    // ---------------------------------------------------------
    private fun startGyroSensor() {
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopGyroSensor() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (!_uiState.value.gyroActive) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // Convert to degrees: orientation[1] is pitch, orientation[2] is roll
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

            _uiState.update { it.copy(gyroPitch = pitch, gyroRoll = roll) }

            val driveState = driveController.updateGyro(
                pitchDegrees = pitch,
                rollDegrees = roll,
                centerPitchDegrees = _uiState.value.gyroCalibratedPitch,
                centerRollDegrees = _uiState.value.gyroCalibratedRoll
            )
            syncDriveState()
            dispatchDriveToBle(driveState)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Fallback for devices without rotation vector
            val x = event.values[0]
            val y = event.values[1]
            val roll = (x * 9f).coerceIn(-90f, 90f)
            val pitch = (y * 9f).coerceIn(-90f, 90f)

            _uiState.update { it.copy(gyroPitch = pitch, gyroRoll = roll) }

            val driveState = driveController.updateGyro(
                pitchDegrees = pitch,
                rollDegrees = roll,
                centerPitchDegrees = _uiState.value.gyroCalibratedPitch,
                centerRollDegrees = _uiState.value.gyroCalibratedRoll
            )
            syncDriveState()
            dispatchDriveToBle(driveState)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------------------------------------------------------
    // Haptics
    // ---------------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun vibrateSubtle() {
        if (!_uiState.value.hapticFeedbackEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        } catch (e: Exception) {
            // Ignore haptic failures gracefully
        }
    }

    @SuppressLint("MissingPermission")
    private fun vibrateEmergency() {
        if (!_uiState.value.hapticFeedbackEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (e: Exception) {
            // Ignore haptic failures gracefully
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLightsSlowBlink()
        stopGyroSensor()
        bleClient.disconnect(cleanShutdown = true)
    }
}
