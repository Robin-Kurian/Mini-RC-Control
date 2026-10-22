package com.robinz.minitank.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.robinz.minitank.protocol.TankProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@SuppressLint("MissingPermission")
class BleTankClient(
    private val context: Context,
    private val tankProtocol: TankProtocol
) {
    companion object {
        private const val TAG = "BleTankClient"
        private const val MAX_LOGS = 500
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var activeDeviceAddress: String? = null
    private var activeDeviceName: String? = null

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val scannedDevices: StateFlow<List<DiscoveredDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredServices: StateFlow<List<DiscoveredService>> = _discoveredServices.asStateFlow()

    private val _diagnosticsLogs = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    val diagnosticsLogs: StateFlow<List<DiagnosticLog>> = _diagnosticsLogs.asStateFlow()

    // Identified GATT handles
    private var writableCharacteristic: BluetoothGattCharacteristic? = null
    private var preferredWriteType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

    // Sequential GATT queue & lock
    private val gattMutex = Mutex()
    private var activeGattDeferred: CompletableDeferred<Boolean>? = null

    // Rate limiting & movement coalescing
    private var lastDriveSendTime = 0L
    private var pendingDriveJob: Job? = null
    private var latestDrivePacket: ByteArray? = null

    // Fail-safe callback
    var onEmergencyStopTriggered: (() -> Unit)? = null

    init {
        log(LogType.INFO, null, null, "BleTankClient initialized")
    }

    // ---------------------------------------------------------
    // Logging Helpers
    // ---------------------------------------------------------
    fun log(type: LogType, uuid: UUID?, hexPayload: String?, message: String) {
        val entry = DiagnosticLog(
            type = type,
            characteristicUuid = uuid,
            hexPayload = hexPayload,
            message = message
        )
        Log.d(TAG, entry.formatDisplay())
        val current = _diagnosticsLogs.value
        val updated = if (current.size >= MAX_LOGS) {
            current.drop(current.size - MAX_LOGS + 1) + entry
        } else {
            current + entry
        }
        _diagnosticsLogs.value = updated
    }

    fun clearLogs() {
        _diagnosticsLogs.value = emptyList()
    }

    fun exportLogsText(): String {
        val sb = StringBuilder()
        sb.append("Robin'z Mini Tank BLE Diagnostic Log\n")
        sb.append("Device: ${activeDeviceName ?: "Unknown"} (${activeDeviceAddress ?: "None"})\n")
        sb.append("Generated: ${java.util.Date()}\n")
        sb.append("--------------------------------------------------\n")
        for (item in _diagnosticsLogs.value) {
            sb.append(item.formatDisplay()).append("\n")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------
    // Scanning
    // ---------------------------------------------------------
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName
            val address = device.address ?: return
            val rssi = result.rssi

            val serviceUuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
            val hasFff0 = serviceUuids.contains(BleConstants.SERVICE_TANK)

            val current = _scannedDevices.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.address.equals(address, ignoreCase = true) }
            val entry = DiscoveredDevice(name = name, address = address, rssi = rssi, hasFff0Service = hasFff0)

            if (existingIndex >= 0) {
                current[existingIndex] = entry
            } else {
                current.add(entry)
            }
            _scannedDevices.value = current.sortedWith(
                compareByDescending<DiscoveredDevice> { it.hasFff0Service }
                    .thenByDescending { it.isLikelyTank }
                    .thenByDescending { it.isNamed }
                    .thenByDescending { it.rssi }
            )
        }

        override fun onScanFailed(errorCode: Int) {
            log(LogType.ERROR, null, null, "BLE Scan failed with code: $errorCode")
            _isScanning.value = false
        }
    }

    fun startScan(filterFff0Only: Boolean = false) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            log(LogType.WARNING, null, null, "Cannot start scan: Bluetooth is disabled or unavailable")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        if (_isScanning.value) return

        _scannedDevices.value = emptyList()
        _isScanning.value = true
        log(LogType.INFO, null, null, "Started BLE scan (filterFff0Only=$filterFff0Only)")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = if (filterFff0Only) {
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BleConstants.SERVICE_TANK))
                    .build()
            )
        } else {
            emptyList()
        }

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            log(LogType.ERROR, null, null, "Exception starting scan: ${e.message}")
            _isScanning.value = false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
        _isScanning.value = false
        log(LogType.INFO, null, null, "Stopped BLE scan")
    }

    // Watchdog & connection management
    private var connectionWatchdogJob: Job? = null

    // ---------------------------------------------------------
    // Connection Lifecycle
    // ---------------------------------------------------------
    private fun closeGattSynchronous() {
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = null
        activeGattDeferred?.cancel()
        activeGattDeferred = null

        val gatt = bluetoothGatt
        bluetoothGatt = null
        writableCharacteristic = null
        _discoveredServices.value = emptyList()

        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Exception on gatt.disconnect()", e)
            }
            try {
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Exception on gatt.close()", e)
            }
        }
    }

    fun connect(address: String, name: String? = null) {
        stopScan()
        closeGattSynchronous()

        activeDeviceAddress = address
        activeDeviceName = name
        _connectionState.value = ConnectionState.Connecting(address, name)
        log(LogType.INFO, null, null, "Connecting to tank: ${name ?: "Unknown"} ($address)...")

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is disabled")
            return
        }

        // Connection Watchdog: 12 seconds max before declaring timeout
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = scope.launch {
            delay(12000L)
            if (_connectionState.value is ConnectionState.Connecting ||
                _connectionState.value is ConnectionState.DiscoveringServices
            ) {
                log(LogType.ERROR, null, null, "Connection watchdog timed out after 12s")
                closeGattSynchronous()
                _connectionState.value = ConnectionState.Error("Connection timed out. Check that tank is powered and nearby.")
            }
        }

        // Dispatch on Main thread to ensure proper Android Looper binding
        scope.launch(Dispatchers.Main) {
            try {
                val remoteDevice = adapter.getRemoteDevice(address)
                val newGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    remoteDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    remoteDevice.connectGatt(context, false, gattCallback)
                }
                bluetoothGatt = newGatt
                if (newGatt == null) {
                    connectionWatchdogJob?.cancel()
                    _connectionState.value = ConnectionState.Error("Failed to initiate GATT connection")
                }
            } catch (e: Exception) {
                connectionWatchdogJob?.cancel()
                log(LogType.ERROR, null, null, "Failed to connect: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "Connect error")
            }
        }
    }

    fun disconnect(cleanShutdown: Boolean = true) {
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = null
        triggerFailSafeStop()
        closeGattSynchronous()
        if (cleanShutdown) {
            _connectionState.value = ConnectionState.Disconnected
            log(LogType.INFO, null, null, "Disconnected from tank")
        }
    }

    // ---------------------------------------------------------
    // GATT Callbacks
    // ---------------------------------------------------------
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val address = gatt?.device?.address ?: activeDeviceAddress.orEmpty()
            val name = gatt?.device?.name ?: activeDeviceName
            log(LogType.INFO, null, null, "GATT status changed: status=$status, newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errorMsg = when (status) {
                    133 -> "Tank connection rejected (GATT 133). Please turn the tank power off and on again."
                    8 -> "Connection timed out (GATT 8). Tank may be out of range or battery low."
                    19, 22 -> "Connection closed by tank peripheral (GATT $status)."
                    else -> "GATT Error ($status)"
                }
                log(LogType.ERROR, null, null, errorMsg)
                triggerFailSafeStop()
                closeGattSynchronous()
                _connectionState.value = ConnectionState.Error(errorMsg)
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log(LogType.INFO, null, null, "Connected to $address. Requesting high priority and discovering services...")
                    // Request high priority for low latency RC controls
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    _connectionState.value = ConnectionState.DiscoveringServices(address)
                    scope.launch(Dispatchers.Main) {
                        delay(250) // Recommended BLE stability delay before discovery
                        val started = gatt?.discoverServices() ?: false
                        if (!started) {
                            log(LogType.ERROR, null, null, "Failed to initiate service discovery")
                            _connectionState.value = ConnectionState.Error("Service discovery failed to start")
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log(LogType.WARNING, null, null, "Disconnected from $address")
                    triggerFailSafeStop()
                    closeGattSynchronous()
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            gatt ?: return
            connectionWatchdogJob?.cancel()
            connectionWatchdogJob = null

            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(LogType.ERROR, null, null, "Service discovery failed with status $status")
                _connectionState.value = ConnectionState.Error("Service discovery failed ($status)")
                return
            }

            log(LogType.INFO, null, null, "Services discovered successfully! Inspecting characteristics...")
            inspectServicesAndCharacteristics(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val uuid = characteristic?.uuid
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (!success) {
                log(LogType.ERROR, uuid, null, "Write failed with status $status")
            }
            activeGattDeferred?.complete(success)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val uuid = characteristic?.uuid ?: return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: byteArrayOf()
            val hex = bytes.toHex()
            log(LogType.INFO, uuid, hex, "Characteristic read: $hex (status=$status)")
            updateCharacteristicLastValue(uuid, hex)
            activeGattDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val uuid = characteristic?.uuid ?: return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: byteArrayOf()
            val hex = bytes.toHex()
            log(LogType.RX_NOTIFY, uuid, hex, "Received Notification/Indication")
            updateCharacteristicLastValue(uuid, hex)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            log(LogType.INFO, descriptor?.characteristic?.uuid, null, "Descriptor write completed with status $status")
            activeGattDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun inspectServicesAndCharacteristics(gatt: BluetoothGatt) {
        val servicesList = mutableListOf<DiscoveredService>()
        var primaryTankWritable: BluetoothGattCharacteristic? = null
        var fallbackWritable: BluetoothGattCharacteristic? = null

        val standardIgnoredServices = setOf(
            UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"), // Generic Access
            UUID.fromString("00001801-0000-1000-8000-00805f9b34fb"), // Generic Attribute
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), // Device Info
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")  // Battery
        )

        for (service in gatt.services) {
            val charList = mutableListOf<DiscoveredCharacteristic>()
            val isTankService = service.uuid == BleConstants.SERVICE_TANK
            val isStandardIgnored = standardIgnoredServices.contains(service.uuid)

            for (char in service.characteristics) {
                val props = mutableSetOf<CharacteristicProperty>()
                val propFlags = char.properties
                val canRead = (propFlags and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                val canWrite = (propFlags and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                val canWriteNoResp = (propFlags and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                val canNotify = (propFlags and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                val canIndicate = (propFlags and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                if (canRead) props.add(CharacteristicProperty.READ)
                if (canWrite) props.add(CharacteristicProperty.WRITE)
                if (canWriteNoResp) props.add(CharacteristicProperty.WRITE_NO_RESPONSE)
                if (canNotify) props.add(CharacteristicProperty.NOTIFY)
                if (canIndicate) props.add(CharacteristicProperty.INDICATE)

                charList.add(
                    DiscoveredCharacteristic(
                        uuid = char.uuid,
                        properties = props,
                        canRead = canRead,
                        canWrite = canWrite,
                        canWriteNoResp = canWriteNoResp,
                        canNotify = canNotify,
                        canIndicate = canIndicate
                    )
                )

                // Select best writable characteristic
                if (canWriteNoResp || canWrite) {
                    if (isTankService) {
                        if (primaryTankWritable == null || char.uuid == BleConstants.CHAR_FFF2 || canWriteNoResp) {
                            primaryTankWritable = char
                        }
                    } else if (!isStandardIgnored && fallbackWritable == null) {
                        fallbackWritable = char
                    }
                }
            }
            servicesList.add(DiscoveredService(uuid = service.uuid, characteristics = charList))
        }

        _discoveredServices.value = servicesList
        val chosen = primaryTankWritable ?: fallbackWritable
        writableCharacteristic = chosen

        if (chosen != null) {
            val hasNoResp = (chosen.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            preferredWriteType = if (hasNoResp) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            log(
                LogType.INFO,
                chosen.uuid,
                null,
                "Ready writable characteristic: ${chosen.uuid} (writeType=${if (hasNoResp) "NO_RESP" else "DEFAULT"}, isFff0=${chosen == primaryTankWritable})"
            )
        } else {
            log(LogType.WARNING, null, null, "Warning: No writable characteristic found on this device")
        }

        // Set Connected state immediately so UI is enabled and ready to drive
        val address = gatt.device?.address ?: activeDeviceAddress.orEmpty()
        val name = gatt.device?.name ?: activeDeviceName
        _connectionState.value = ConnectionState.Connected(address, name)
        log(LogType.INFO, null, null, "Device fully ready for communication!")

        // Enable default notifications in non-blocking background job with timeout
        scope.launch {
            try {
                withTimeoutOrNull(4000L) {
                    enableDefaultNotifications(gatt)
                }
            } catch (e: Exception) {
                log(LogType.WARNING, null, null, "Notification setup skipped: ${e.message}")
            }
        }
    }

    private suspend fun enableDefaultNotifications(gatt: BluetoothGatt) {
        val tankService = gatt.getService(BleConstants.SERVICE_TANK)
        if (tankService != null) {
            val candidates = listOf(BleConstants.CHAR_FFF1, BleConstants.CHAR_FFF2, BleConstants.CHAR_FFF4)
            for (uuid in candidates) {
                val char = tankService.getCharacteristic(uuid) ?: continue
                val canNotify = (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                val canIndicate = (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                if (canNotify || canIndicate) {
                    log(LogType.INFO, uuid, null, "Enabling notifications on $uuid")
                    setCharacteristicNotification(char, true)
                    delay(80)
                }
            }
        } else {
            // Fallback: check for notify characteristics in other services
            for (service in gatt.services) {
                for (char in service.characteristics) {
                    val canNotify = (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    if (canNotify) {
                        setCharacteristicNotification(char, true)
                        delay(80)
                        break
                    }
                }
            }
        }
    }

    private fun updateCharacteristicLastValue(uuid: UUID, hex: String) {
        val currentServices = _discoveredServices.value.map { service ->
            val updatedChars = service.characteristics.map { char ->
                if (char.uuid == uuid) char.copy(lastValueHex = hex) else char
            }
            service.copy(characteristics = updatedChars)
        }
        _discoveredServices.value = currentServices
    }

    // ---------------------------------------------------------
    // Serialized GATT Execution
    // ---------------------------------------------------------
    suspend fun executeGattOperation(
        operationName: String,
        action: (deferred: CompletableDeferred<Boolean>) -> Boolean
    ): Boolean = gattMutex.withLock {
        val deferred = CompletableDeferred<Boolean>()
        activeGattDeferred = deferred

        val started = action(deferred)
        if (!started) {
            activeGattDeferred = null
            return false
        }

        val result = withTimeoutOrNull(BleConstants.GATT_TIMEOUT_MS) {
            deferred.await()
        } ?: false

        activeGattDeferred = null
        return result
    }

    suspend fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enable: Boolean
    ): Boolean {
        val gatt = bluetoothGatt ?: return false
        val success = gatt.setCharacteristicNotification(characteristic, enable)
        if (!success) {
            log(LogType.ERROR, characteristic.uuid, null, "setCharacteristicNotification returned false")
            return false
        }
        val descriptor = characteristic.getDescriptor(BleConstants.DESCRIPTOR_CCCD) ?: return true
        val value = if (enable) {
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }

        @Suppress("DEPRECATION")
        descriptor.value = value

        return executeGattOperation("writeDescriptor") {
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    suspend fun readCharacteristic(uuid: UUID): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = findCharacteristic(uuid) ?: return false
        return executeGattOperation("readCharacteristic") {
            gatt.readCharacteristic(char)
        }
    }

    suspend fun writeCustomHex(uuid: UUID, hex: String): Boolean {
        val bytes = hex.hexToBytes() ?: return false
        val gatt = bluetoothGatt ?: return false
        val char = findCharacteristic(uuid) ?: return false

        val canNoResp = (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val writeType = if (canNoResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        @Suppress("DEPRECATION")
        char.value = bytes
        char.writeType = writeType

        log(LogType.TX_WRITE, uuid, hex, "Manual Write ($writeType)")

        return if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            gattMutex.withLock {
                @Suppress("DEPRECATION")
                val sent = gatt.writeCharacteristic(char)
                delay(20) // Inter-packet pacing
                sent
            }
        } else {
            executeGattOperation("writeCharacteristic") {
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        }
    }

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        val gatt = bluetoothGatt ?: return null
        for (service in gatt.services) {
            val char = service.getCharacteristic(uuid)
            if (char != null) return char
        }
        return null
    }

    // ---------------------------------------------------------
    // Drive Control & Coalescing
    // ---------------------------------------------------------
    fun sendDriveCommand(throttle: Float, steering: Float, speedCapPercent: Int) {
        val encodedBytes = tankProtocol.encodeDrive(throttle, steering, speedCapPercent)
        if (encodedBytes == null) {
            // Protocol pending: UnknownTankProtocol logs this request safely without sending fake bytes
            return
        }

        latestDrivePacket = encodedBytes
        val now = System.currentTimeMillis()
        if (now - lastDriveSendTime >= BleConstants.RATE_LIMIT_DRIVE_MS) {
            lastDriveSendTime = now
            dispatchMovementPacket(encodedBytes)
        } else {
            // Coalesce: cancel pending job and schedule newest packet
            pendingDriveJob?.cancel()
            pendingDriveJob = scope.launch {
                val delayTime = BleConstants.RATE_LIMIT_DRIVE_MS - (System.currentTimeMillis() - lastDriveSendTime)
                if (delayTime > 0) delay(delayTime)
                latestDrivePacket?.let {
                    lastDriveSendTime = System.currentTimeMillis()
                    dispatchMovementPacket(it)
                }
            }
        }
    }

    private fun dispatchMovementPacket(packet: ByteArray) {
        val char = writableCharacteristic ?: return
        val gatt = bluetoothGatt ?: return

        scope.launch {
            gattMutex.withLock {
                @Suppress("DEPRECATION")
                char.value = packet
                char.writeType = preferredWriteType
                log(LogType.TX_WRITE, char.uuid, packet.toHex(), "Drive packet")
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        }
    }

    /**
     * Priority Stop: Discards any queued movement packets and transmits emergency stop packet bursts.
     */
    fun sendStopCommand() {
        pendingDriveJob?.cancel()
        latestDrivePacket = null

        val stopBytes = tankProtocol.encodeStop()
        if (stopBytes == null) {
            log(LogType.INFO, null, null, "Immediate stop requested (protocol pending)")
            return
        }

        val char = writableCharacteristic ?: return
        val gatt = bluetoothGatt ?: return

        scope.launch {
            gattMutex.withLock {
                for (i in 1..BleConstants.STOP_BURST_COUNT) {
                    @Suppress("DEPRECATION")
                    char.value = stopBytes
                    char.writeType = preferredWriteType
                    log(LogType.TX_WRITE, char.uuid, stopBytes.toHex(), "STOP burst #$i")
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                    delay(BleConstants.STOP_BURST_INTERVAL_MS)
                }
            }
        }
    }

    fun triggerFailSafeStop() {
        sendStopCommand()
        onEmergencyStopTriggered?.invoke()
    }

    // ---------------------------------------------------------
    // Secondary Controls
    // ---------------------------------------------------------
    fun setLights(enabled: Boolean) {
        val bytes = tankProtocol.encodeLights(enabled)
        if (bytes == null) {
            log(LogType.INFO, null, null, "Lights $enabled requested (protocol pending, PCB '前灯')")
            return
        }
        dispatchSecondaryPacket(bytes, "Lights $enabled")
    }

    fun setSound(enabled: Boolean) {
        val bytes = tankProtocol.encodeSound(enabled)
        if (bytes == null) {
            log(LogType.INFO, null, null, "Sound $enabled requested (protocol pending, PCB '+SPK-')")
            return
        }
        dispatchSecondaryPacket(bytes, "Sound $enabled")
    }

    fun triggerEngineStart() {
        val bytes = tankProtocol.encodeEngineStart()
        if (bytes == null) {
            log(LogType.INFO, null, null, "Engine start requested (protocol pending)")
            return
        }
        dispatchSecondaryPacket(bytes, "Engine Start")
    }

    private fun dispatchSecondaryPacket(packet: ByteArray, label: String) {
        val char = writableCharacteristic ?: return
        val gatt = bluetoothGatt ?: return
        scope.launch {
            gattMutex.withLock {
                @Suppress("DEPRECATION")
                char.value = packet
                char.writeType = preferredWriteType
                log(LogType.TX_WRITE, char.uuid, packet.toHex(), label)
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        }
    }
}

// ---------------------------------------------------------
// Hex Helpers
// ---------------------------------------------------------
fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray? {
    val clean = this.replace(" ", "").replace("0x", "", ignoreCase = true).trim()
    if (clean.isEmpty() || clean.length % 2 != 0) return null
    return try {
        ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    } catch (e: Exception) {
        null
    }
}
