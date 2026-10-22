package com.robinz.minitank.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.util.Log
import com.robinz.minitank.protocol.ExtendedAdvertisingTankProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Broadcast engine for controlling the Mini Tank over Bluetooth 5 Extended Advertising
 * or legacy BLE advertising.
 *
 * Transmits the 23-byte AD Structure (16 FF 00 00 [19 bytes payload]) verified from BTSnoop traces.
 */
@SuppressLint("MissingPermission")
class BleTankAdvertiser(
    private val context: Context
) {
    companion object {
        private const val TAG = "BleTankAdvertiser"
        private const val IDLE_BROADCAST_INTERVAL_MS = 800L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter
    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    // Observables
    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _lastBroadcastHex = MutableStateFlow<String?>(null)
    val lastBroadcastHex: StateFlow<String?> = _lastBroadcastHex.asStateFlow()

    private val _rollingCounter = MutableStateFlow(0x80)
    val rollingCounter: StateFlow<Int> = _rollingCounter.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _useExtendedAdvertising = MutableStateFlow(false)
    val useExtendedAdvertising: StateFlow<Boolean> = _useExtendedAdvertising.asStateFlow()

    // Current state to encode
    @Volatile private var currentDirection: Byte = ExtendedAdvertisingTankProtocol.STATE_IDLE
    @Volatile private var currentIntensity: Byte = 0x00
    @Volatile private var currentAxis: Byte = ExtendedAdvertisingTankProtocol.AXIS_IDLE
    @Volatile private var isMoving: Boolean = false

    private var broadcastLoopJob: Job? = null
    private var currentAdvertisingSet: AdvertisingSet? = null

    init {
        val hasExtAdv = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bluetoothAdapter?.isLeExtendedAdvertisingSupported == true
        } else {
            false
        }
        _useExtendedAdvertising.value = hasExtAdv
        Log.i(TAG, "BleTankAdvertiser initialized (ExtendedAdvertisingSupported=$hasExtAdv)")
    }

    /**
     * Binds confirmed drive commands:
     * - FORWARD: directionState = 0x01, intensity = power % (analog ramp), axisType = 0x05, secondaryByte = 0x64
     * - REVERSE: directionState = 0x02, intensity = power % (analog ramp), axisType = 0x04, secondaryByte = 0x64
     * - LEFT:    directionState = 0x04, intensity = 0x00 (button turn), axisType = 0x04, secondaryByte = 0x64
     * - RIGHT:   directionState = 0x08, intensity = 0x00 (button turn), axisType = 0x04, secondaryByte = 0x64
     * - IDLE:    directionState = 0x00, intensity = 0x00, axisType = 0x04, secondaryByte = 0x64
     */
    fun setDriveInput(
        throttle: Float,
        steering: Float = 0f,
        speedCapPercent: Int
    ) {
        when {
            // Forward (throttle > +0.05f)
            throttle > 0.05f -> {
                val powerPercent = (throttle * speedCapPercent).toInt().coerceIn(1, 100)
                currentDirection = ExtendedAdvertisingTankProtocol.STATE_FORWARD
                currentIntensity = powerPercent.toByte()
                currentAxis = ExtendedAdvertisingTankProtocol.AXIS_FORWARD
                isMoving = true

                if (!_isBroadcasting.value) {
                    startBroadcasting()
                }
            }
            // Reverse (throttle < -0.05f) - intensity ramps continuously with analog drag
            throttle < -0.05f -> {
                val powerPercent = (kotlin.math.abs(throttle) * speedCapPercent).toInt().coerceIn(1, 100)
                currentDirection = ExtendedAdvertisingTankProtocol.STATE_REVERSE
                currentIntensity = powerPercent.toByte()
                currentAxis = ExtendedAdvertisingTankProtocol.AXIS_REVERSE
                isMoving = true

                if (!_isBroadcasting.value) {
                    startBroadcasting()
                }
            }
            // Left (steering < -0.15f) - confirmed Left with intensity 0x00
            steering < -0.15f -> {
                currentDirection = ExtendedAdvertisingTankProtocol.STATE_LEFT
                currentIntensity = 0x00
                currentAxis = ExtendedAdvertisingTankProtocol.AXIS_LEFT
                isMoving = true

                if (!_isBroadcasting.value) {
                    startBroadcasting()
                }
            }
            // Right (steering > 0.15f) - confirmed Right with intensity 0x00
            steering > 0.15f -> {
                currentDirection = ExtendedAdvertisingTankProtocol.STATE_RIGHT
                currentIntensity = 0x00
                currentAxis = ExtendedAdvertisingTankProtocol.AXIS_RIGHT
                isMoving = true

                if (!_isBroadcasting.value) {
                    startBroadcasting()
                }
            }
            else -> {
                resetToIdle()
            }
        }
    }

    /**
     * Updates active control payload values manually (for diagnostics lab).
     */
    fun updateLiveControls(
        direction: Byte,
        intensity: Byte,
        axis: Byte
    ) {
        currentDirection = direction
        currentIntensity = intensity
        currentAxis = axis
        isMoving = direction != ExtendedAdvertisingTankProtocol.STATE_IDLE
    }

    /**
     * Resets control state to verified neutral idle.
     * Immediately transmits neutral IDLE packet if broadcasting to guarantee instant fail-safe stop.
     */
    fun resetToIdle() {
        val wasMoving = isMoving
        currentDirection = ExtendedAdvertisingTankProtocol.STATE_IDLE
        currentIntensity = 0x00
        currentAxis = ExtendedAdvertisingTankProtocol.AXIS_IDLE
        isMoving = false

        if (_isBroadcasting.value && wasMoving) {
            val counter = _rollingCounter.value
            val payload = ExtendedAdvertisingTankProtocol.buildIdleData(counter)
            _lastBroadcastHex.value = ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(payload)
            transmitPacket(payload)
            _rollingCounter.value = (counter + 1) and 0xFF
        }
    }

    /**
     * Starts continuous BLE broadcast loop.
     */
    fun startBroadcasting() {
        val adv = advertiser
        if (adv == null) {
            _lastError.value = "BLE Advertiser unavailable on this device or Bluetooth disabled"
            Log.e(TAG, "Cannot start broadcast: advertiser is null")
            return
        }

        if (_isBroadcasting.value) return
        _isBroadcasting.value = true
        _lastError.value = null
        Log.i(TAG, "Starting BLE Tank broadcast loop...")

        broadcastLoopJob?.cancel()
        broadcastLoopJob = scope.launch {
            while (isActive && _isBroadcasting.value) {
                val counter = _rollingCounter.value
                val payload = ExtendedAdvertisingTankProtocol.buildManufacturerData(
                    directionState = currentDirection,
                    intensity = currentIntensity,
                    axisType = currentAxis,
                    secondaryByte = ExtendedAdvertisingTankProtocol.CONSTANT_SECONDARY_BYTE,
                    counter = counter
                )

                _lastBroadcastHex.value = ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(payload)
                transmitPacket(payload)

                // Advance counter (0x00..0xFF)
                _rollingCounter.value = (counter + 1) and 0xFF

                // Fast 50ms pacing while actively driving, 800ms while idle
                val delayMs = if (isMoving) 50L else IDLE_BROADCAST_INTERVAL_MS
                delay(delayMs)
            }
        }
    }

    /**
     * Stops continuous BLE broadcast loop.
     */
    fun stopBroadcasting() {
        broadcastLoopJob?.cancel()
        broadcastLoopJob = null
        _isBroadcasting.value = false
        stopActiveAdvertising()
        Log.i(TAG, "Stopped BLE Tank broadcast loop")
    }

    private fun transmitPacket(manufacturerData: ByteArray) {
        val adv = advertiser ?: return

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(ExtendedAdvertisingTankProtocol.COMPANY_ID, manufacturerData)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && _useExtendedAdvertising.value) {
            transmitExtendedAdvertising(advertiseData)
        } else {
            transmitLegacyAdvertising(adv, advertiseData)
        }
    }

    private fun transmitLegacyAdvertising(
        adv: BluetoothLeAdvertiser,
        advertiseData: AdvertiseData
    ) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(600) // Transmit short burst for each counter update
            .build()

        try {
            adv.startAdvertising(settings, advertiseData, legacyCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting legacy advertising: ${e.message}")
            _lastError.value = e.message
        }
    }

    private fun transmitExtendedAdvertising(advertiseData: AdvertiseData) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val adv = advertiser ?: return

        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        try {
            if (currentAdvertisingSet == null) {
                adv.startAdvertisingSet(parameters, advertiseData, null, null, null, extendedCallback)
            } else {
                currentAdvertisingSet?.setAdvertisingData(advertiseData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extended advertising unsupported or failed, falling back to legacy: ${e.message}")
            _useExtendedAdvertising.value = false
            transmitLegacyAdvertising(adv, advertiseData)
        }
    }

    private fun stopActiveAdvertising() {
        val adv = advertiser ?: return
        try {
            adv.stopAdvertising(legacyCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping legacy advertising: ${e.message}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && currentAdvertisingSet != null) {
            try {
                adv.stopAdvertisingSet(extendedCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping extended advertising: ${e.message}")
            }
            currentAdvertisingSet = null
        }
    }

    private val legacyCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            // Success
        }

        override fun onStartFailure(errorCode: Int) {
            val msg = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Advertising unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Advertising error $errorCode"
            }
            Log.w(TAG, "Legacy advertising error: $msg")
            _lastError.value = msg
            resetToIdle()
        }
    }

    private val extendedCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                if (status == ADVERTISE_SUCCESS) {
                    currentAdvertisingSet = advertisingSet
                } else {
                    Log.w(TAG, "Extended advertising set failed with status $status, falling back to legacy")
                    _useExtendedAdvertising.value = false
                }
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                currentAdvertisingSet = null
            }
        }
    } else null
}
