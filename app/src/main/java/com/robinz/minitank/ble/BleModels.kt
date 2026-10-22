package com.robinz.minitank.ble

import java.util.UUID

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val deviceAddress: String, val deviceName: String?) : ConnectionState
    data class DiscoveringServices(val deviceAddress: String) : ConnectionState
    data class Connected(val deviceAddress: String, val deviceName: String?) : ConnectionState
    data class Reconnecting(val deviceAddress: String, val attempt: Int) : ConnectionState
    data class Error(val message: String) : ConnectionState

    val isConnected: Boolean
        get() = this is Connected

    val label: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Scanning -> "Scanning..."
            is Connecting -> "Connecting..."
            is DiscoveringServices -> "Discovering..."
            is Connected -> "Connected"
            is Reconnecting -> "Reconnecting..."
            is Error -> "Error: $message"
        }
}

data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val hasFff0Service: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isNamed: Boolean
        get() = !name.isNullOrBlank()

    val isLikelyTank: Boolean
        get() {
            if (hasFff0Service) return true
            val n = name?.lowercase() ?: return false
            return n.contains("tank") || n.contains("car") || n.contains("robot") ||
                   n.contains("esp32") || n.contains("esp") || n.contains("jdy") ||
                   n.contains("hm-") || n.contains("micro") || n.contains("rc") ||
                   n.contains("robin") || n.contains("smart") || n.contains("ble")
        }

    val isPeripheralOrTag: Boolean
        get() {
            val n = name?.lowercase() ?: return true
            return n.contains("mouse") || n.contains("keyboard") || n.contains("trackpad") ||
                   n.contains("tag") || n.contains("airtag") || n.contains("watch") ||
                   n.contains("band") || n.contains("tv") || n.contains("buds") ||
                   n.contains("headset") || n.contains("audio") || n.contains("speaker")
        }
}

enum class CharacteristicProperty {
    READ,
    WRITE,
    WRITE_NO_RESPONSE,
    NOTIFY,
    INDICATE
}

data class DiscoveredCharacteristic(
    val uuid: UUID,
    val properties: Set<CharacteristicProperty>,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canWriteNoResp: Boolean,
    val canNotify: Boolean,
    val canIndicate: Boolean,
    val notificationsEnabled: Boolean = false,
    val lastValueHex: String? = null
)

data class DiscoveredService(
    val uuid: UUID,
    val characteristics: List<DiscoveredCharacteristic>
)

enum class LogType {
    RX_NOTIFY,
    TX_WRITE,
    INFO,
    WARNING,
    ERROR
}

data class DiagnosticLog(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogType,
    val characteristicUuid: UUID? = null,
    val hexPayload: String? = null,
    val message: String
) {
    fun formatDisplay(): String {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(timestamp))
        val prefix = when (type) {
            LogType.RX_NOTIFY -> "[RX NOTIFY]"
            LogType.TX_WRITE -> "[TX WRITE]"
            LogType.INFO -> "[INFO]"
            LogType.WARNING -> "[WARN]"
            LogType.ERROR -> "[ERR]"
        }
        val uuidStr = characteristicUuid?.let { " (${it.toString().substring(4, 8).uppercase()})" } ?: ""
        val payloadStr = hexPayload?.let { " => $it" } ?: ""
        return "$timeStr $prefix$uuidStr $message$payloadStr"
    }
}
