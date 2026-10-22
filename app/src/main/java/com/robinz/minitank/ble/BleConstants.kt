package com.robinz.minitank.ble

import java.util.UUID

object BleConstants {
    val SERVICE_TANK: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHAR_FFF1: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val CHAR_FFF2: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val CHAR_FFF4: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")

    val DESCRIPTOR_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val RATE_LIMIT_DRIVE_MS = 55L // ~18 updates per second (15-20 Hz range)
    const val GATT_TIMEOUT_MS = 3000L
    const val STOP_BURST_COUNT = 3
    const val STOP_BURST_INTERVAL_MS = 40L
}
