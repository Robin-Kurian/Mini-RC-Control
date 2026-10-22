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
import android.os.SystemClock
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
 * Broadcast engine for controlling the Mini Tank over legacy BLE advertising.
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
        private const val ACTIVE_BROADCAST_INTERVAL_MS = 200L
        private const val MIN_ACTIVE_COMMAND_MS = 200L
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
    @Volatile private var movementStartedAtMs: Long = 0L

    private var broadcastLoopJob: Job? = null
    private var pendingIdleJob: Job? = null
    private var currentAdvertisingSet: AdvertisingSet? = null
    @Volatile private var extendedStartPending = false
    @Volatile private var legacyAdvertisingActive = false

    init {
        // The original app uses legacy/scannable advertising PDUs (HCI event
        // properties 0x0012). The tank's observer does not react to Bluetooth 5
        // extended advertising PDUs even when their manufacturer payload matches.
        _useExtendedAdvertising.value = false
        Log.i(TAG, "BleTankAdvertiser initialized (forced legacy advertising for tank compatibility)")
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
        val throttleDirection = when {
            throttle > 0.05f -> ExtendedAdvertisingTankProtocol.STATE_FORWARD.toInt()
            throttle < -0.05f -> ExtendedAdvertisingTankProtocol.STATE_REVERSE.toInt()
            else -> 0
        }
        val steeringDirection = when {
            steering < -0.15f -> ExtendedAdvertisingTankProtocol.STATE_LEFT.toInt()
            steering > 0.15f -> ExtendedAdvertisingTankProtocol.STATE_RIGHT.toInt()
            else -> 0
        }
        val direction = throttleDirection or steeringDirection

        if (direction == 0) {
            resetToIdle()
            return
        }

        pendingIdleJob?.cancel()
        pendingIdleJob = null
        if (!isMoving) {
            movementStartedAtMs = SystemClock.elapsedRealtime()
        }
        currentDirection = direction.toByte()
        currentIntensity = if (throttleDirection != 0) {
            (kotlin.math.abs(throttle) * speedCapPercent)
                .toInt()
                .coerceIn(1, 100)
                .toByte()
        } else {
            0x00
        }
        currentAxis = if (throttleDirection == ExtendedAdvertisingTankProtocol.STATE_FORWARD.toInt()) {
            ExtendedAdvertisingTankProtocol.AXIS_FORWARD
        } else {
            ExtendedAdvertisingTankProtocol.AXIS_IDLE
        }
        isMoving = true

        if (!_isBroadcasting.value) {
            startBroadcasting()
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
        val remainingHoldMs = MIN_ACTIVE_COMMAND_MS -
            (SystemClock.elapsedRealtime() - movementStartedAtMs)
        if (isMoving && remainingHoldMs > 0L) {
            pendingIdleJob?.cancel()
            pendingIdleJob = scope.launch {
                delay(remainingHoldMs)
                applyIdleNow()
            }
            return
        }
        applyIdleNow()
    }

    /** Immediately sends neutral, bypassing the short tap latch for emergency/lifecycle stops. */
    fun forceIdle() {
        pendingIdleJob?.cancel()
        pendingIdleJob = null
        applyIdleNow()
    }

    private fun applyIdleNow() {
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

                // The original app replaces active packets about every 200ms. The
                // controller advertises at 100-131ms, so faster replacement can
                // cancel a packet before it reaches the air.
                val delayMs = if (isMoving) ACTIVE_BROADCAST_INTERVAL_MS else IDLE_BROADCAST_INTERVAL_MS
                delay(delayMs)
            }
        }
    }

    /**
     * Stops continuous BLE broadcast loop.
     */
    fun stopBroadcasting() {
        if (!_isBroadcasting.value) {
            stopActiveAdvertising()
            return
        }
        broadcastLoopJob?.cancel()
        broadcastLoopJob = null
        pendingIdleJob?.cancel()
        pendingIdleJob = null
        _isBroadcasting.value = false
        currentDirection = ExtendedAdvertisingTankProtocol.STATE_IDLE
        currentIntensity = 0x00
        currentAxis = ExtendedAdvertisingTankProtocol.AXIS_IDLE
        isMoving = false

        // Keep neutral on air briefly before disabling advertising. A remote-control
        // stop must not depend on the receiver having heard the previous single packet.
        scope.launch {
            repeat(3) {
                val counter = _rollingCounter.value
                val payload = ExtendedAdvertisingTankProtocol.buildIdleData(counter)
                _lastBroadcastHex.value = ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(payload)
                transmitPacket(payload)
                _rollingCounter.value = (counter + 1) and 0xFF
                delay(60L)
            }
            stopActiveAdvertising()
            Log.i(TAG, "Stopped BLE Tank broadcast loop after neutral burst")
        }
    }

    private fun transmitPacket(manufacturerData: ByteArray) {
        val adv = advertiser ?: return

        val advertiseData = AdvertiseData.Builder()
            .addManufacturerData(ExtendedAdvertisingTankProtocol.COMPANY_ID, manufacturerData)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        transmitLegacyAdvertising(adv, advertiseData)
    }

    private fun transmitLegacyAdvertising(
        adv: BluetoothLeAdvertiser,
        advertiseData: AdvertiseData
    ) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        try {
            if (legacyAdvertisingActive) {
                adv.stopAdvertising(legacyCallback)
                legacyAdvertisingActive = false
            }
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
            if (currentAdvertisingSet == null && !extendedStartPending) {
                extendedStartPending = true
                adv.startAdvertisingSet(parameters, advertiseData, null, null, null, extendedCallback)
            } else if (currentAdvertisingSet != null) {
                currentAdvertisingSet?.setAdvertisingData(advertiseData)
            }
        } catch (e: Exception) {
            extendedStartPending = false
            Log.w(TAG, "Extended advertising unsupported or failed, falling back to legacy: ${e.message}")
            _useExtendedAdvertising.value = false
            transmitLegacyAdvertising(adv, advertiseData)
        }
    }

    private fun stopActiveAdvertising() {
        val adv = advertiser ?: return
        try {
            adv.stopAdvertising(legacyCallback)
            legacyAdvertisingActive = false
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
            extendedStartPending = false
        }
    }

    private val legacyCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            legacyAdvertisingActive = true
            _lastError.value = null
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
            legacyAdvertisingActive = false
        }
    }

    private val extendedCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                extendedStartPending = false
                if (status == ADVERTISE_SUCCESS) {
                    currentAdvertisingSet = advertisingSet
                    _lastError.value = null
                } else {
                    Log.w(TAG, "Extended advertising set failed with status $status, falling back to legacy")
                    _useExtendedAdvertising.value = false
                    _lastError.value = "Extended advertising failed ($status); using legacy mode"
                }
            }

            override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                currentAdvertisingSet = null
                extendedStartPending = false
            }
        }
    } else null
}
