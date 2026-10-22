package com.robinz.minitank.protocol

/**
 * Verified packet encoder for the Mini Tank Extended Advertising / BLE Broadcast protocol.
 *
 * Reconstructed from BTSnoop HCI extended advertising traces (Opcode 0x2037 / LE Set Extended Advertising Data):
 * 23-byte AD Structure:
 *   [16 FF 00 00] [04 07 03 FD C1 S1 S2 S3] [DIR] [INTENSITY] [AXIS] [0x64] [00 00] [COUNTER] [00 00 00] [CHECKSUM]
 *
 * For Android BluetoothLeAdvertiser:
 *   manufacturerId = 0x0000 (AD Type 0xFF, Company ID 0x0000)
 *   manufacturerData (19 bytes):
 *     bytes 0..4  = Base Header: 04 07 03 FD C1
 *     bytes 5..7  = Session / Model Suffix (e.g. 30 32 63 or 63 35 66)
 *     byte 8      = Direction / State (0x01 Forward, 0x00 Idle)
 *     byte 9      = Power Intensity (e.g. 0x1E for 30%, 0x00 Idle)
 *     byte 10     = Axis / Control type (0x05 Forward, 0x04 Idle)
 *     byte 11     = Constant secondary value 0x64 (remains 100 decimal)
 *     bytes 12..13 = 0x00, 0x00
 *     byte 14     = Rolling counter (0x00..0xFF)
 *     bytes 15..17 = 0x00, 0x00, 0x00
 *     byte 18     = Checksum
 *
 * Checksum formula:
 *   checksum = 0xE9 XOR byte[0] XOR byte[1] ... XOR byte[19] (over full 21-byte company + payload content)
 */
object ExtendedAdvertisingTankProtocol {

    const val COMPANY_ID = 0x0000

    // 5-byte base header present across all sessions
    val BASE_HEADER = byteArrayOf(
        0x04.toByte(),
        0x07.toByte(),
        0x03.toByte(),
        0xFD.toByte(),
        0xC1.toByte()
    )

    // Configurable 3-byte session / device suffix (defaults to current controlled session: 30 32 63)
    @Volatile
    var sessionSuffix: ByteArray = byteArrayOf(
        0x30.toByte(),
        0x32.toByte(),
        0x63.toByte()
    )

    // Confirmed control states
    const val STATE_IDLE: Byte = 0x00
    const val STATE_FORWARD: Byte = 0x01
    const val STATE_REVERSE: Byte = 0x02
    const val STATE_LEFT: Byte = 0x04
    const val STATE_RIGHT: Byte = 0x08

    const val AXIS_IDLE: Byte = 0x04
    const val AXIS_FORWARD: Byte = 0x05
    const val AXIS_REVERSE: Byte = 0x04
    const val AXIS_LEFT: Byte = 0x04
    const val AXIS_RIGHT: Byte = 0x04

    // Byte 13 (data[11]) confirmed constant 0x64 (100 decimal)
    const val CONSTANT_SECONDARY_BYTE: Byte = 0x64

    fun getHeader(): ByteArray {
        val s = sessionSuffix
        val header = ByteArray(8)
        System.arraycopy(BASE_HEADER, 0, header, 0, 5)
        header[5] = if (s.isNotEmpty()) s[0] else 0x30
        header[6] = if (s.size > 1) s[1] else 0x32
        header[7] = if (s.size > 2) s[2] else 0x63
        return header
    }

    /**
     * Updates the 3-byte session suffix (e.g. "30 32 63" or "63 35 66").
     */
    fun setSessionSuffixHex(hex: String) {
        val clean = hex.replace(" ", "").replace("0x", "", ignoreCase = true).trim()
        if (clean.length == 6) {
            try {
                sessionSuffix = ByteArray(3) { i ->
                    clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } catch (e: Exception) {
                // Keep existing suffix
            }
        }
    }

    /**
     * Computes the single-byte checksum:
     * checksum = 0xE9 XOR byte[0] XOR byte[1] ... XOR byte[19]
     * where byte[0] and byte[1] are the Company ID (0x00, 0x00).
     */
    fun calculateChecksum(
        directionState: Byte,
        intensity: Byte,
        axisType: Byte,
        secondaryByte: Byte,
        counter: Byte,
        customHeader: ByteArray? = null
    ): Byte {
        var cs = 0xE9
        val header = customHeader ?: getHeader()
        for (b in header) {
            cs = cs xor (b.toInt() and 0xFF)
        }
        cs = cs xor (directionState.toInt() and 0xFF)
        cs = cs xor (intensity.toInt() and 0xFF)
        cs = cs xor (axisType.toInt() and 0xFF)
        cs = cs xor (secondaryByte.toInt() and 0xFF)
        // bytes 14..15 are 0x00
        cs = cs xor (counter.toInt() and 0xFF)
        // bytes 17..19 are 0x00
        return (cs and 0xFF).toByte()
    }

    /**
     * Builds the 19-byte manufacturer data array passed to Android's addManufacturerData(0x0000, data).
     */
    fun buildManufacturerData(
        directionState: Byte,
        intensity: Byte,
        axisType: Byte,
        secondaryByte: Byte = CONSTANT_SECONDARY_BYTE,
        counter: Int,
        customHeader: ByteArray? = null
    ): ByteArray {
        val counterByte = (counter and 0xFF).toByte()
        val header = customHeader ?: getHeader()
        val checksum = calculateChecksum(directionState, intensity, axisType, secondaryByte, counterByte, header)

        val data = ByteArray(19)
        System.arraycopy(header, 0, data, 0, 8)
        data[8] = directionState
        data[9] = intensity
        data[10] = axisType
        data[11] = secondaryByte
        data[12] = 0x00
        data[13] = 0x00
        data[14] = counterByte
        data[15] = 0x00
        data[16] = 0x00
        data[17] = 0x00
        data[18] = checksum

        return data
    }

    /**
     * Confirmed FORWARD command packet builder:
     * directionState = 0x01
     * intensity = requested power percentage (e.g. 30 -> 0x1E)
     * axisType = 0x05
     * secondaryByte = 0x64
     */
    fun buildForwardData(
        intensityPercent: Int,
        counter: Int,
        customHeader: ByteArray? = null
    ): ByteArray {
        val clampedIntensity = intensityPercent.coerceIn(1, 100).toByte()
        return buildManufacturerData(
            directionState = STATE_FORWARD,
            intensity = clampedIntensity,
            axisType = AXIS_FORWARD,
            secondaryByte = CONSTANT_SECONDARY_BYTE,
            counter = counter,
            customHeader = customHeader
        )
    }

    /**
     * Confirmed REVERSE command packet builder:
     * directionState = 0x02
     * intensity = requested power percentage (e.g. 30 -> 0x1E, ramps smoothly)
     * axisType = 0x04
     * secondaryByte = 0x64
     */
    fun buildReverseData(
        intensityPercent: Int,
        counter: Int,
        customHeader: ByteArray? = null
    ): ByteArray {
        val clampedIntensity = intensityPercent.coerceIn(1, 100).toByte()
        return buildManufacturerData(
            directionState = STATE_REVERSE,
            intensity = clampedIntensity,
            axisType = AXIS_REVERSE,
            secondaryByte = CONSTANT_SECONDARY_BYTE,
            counter = counter,
            customHeader = customHeader
        )
    }

    /**
     * Confirmed LEFT command packet builder:
     * directionState = 0x04
     * intensity = 0x00 (button-based turn, no separate intensity applied)
     * axisType = 0x04
     * secondaryByte = 0x64
     */
    fun buildLeftData(
        counter: Int,
        customHeader: ByteArray? = null
    ): ByteArray {
        return buildManufacturerData(
            directionState = STATE_LEFT,
            intensity = 0x00,
            axisType = AXIS_LEFT,
            secondaryByte = CONSTANT_SECONDARY_BYTE,
            counter = counter,
            customHeader = customHeader
        )
    }

    /**
     * Confirmed RIGHT command packet builder:
     * directionState = 0x08
     * intensity = 0x00 (button-based turn, no separate intensity applied)
     * axisType = 0x04
     * secondaryByte = 0x64
     */
    fun buildRightData(
        counter: Int,
        customHeader: ByteArray? = null
    ): ByteArray {
        return buildManufacturerData(
            directionState = STATE_RIGHT,
            intensity = 0x00,
            axisType = AXIS_RIGHT,
            secondaryByte = CONSTANT_SECONDARY_BYTE,
            counter = counter,
            customHeader = customHeader
        )
    }

    /**
     * Confirmed IDLE packet builder:
     * directionState = 0x00
     * intensity = 0x00
     * axisType = 0x04
     * secondaryByte = 0x64
     */
    fun buildIdleData(
        counter: Int,
        customHeader: ByteArray? = null
    ): ByteArray {
        return buildManufacturerData(
            directionState = STATE_IDLE,
            intensity = 0x00,
            axisType = AXIS_IDLE,
            secondaryByte = CONSTANT_SECONDARY_BYTE,
            counter = counter,
            customHeader = customHeader
        )
    }

    /**
     * Formats full 23-byte AD structure string for display:
     * 16 FF 00 00 [19 bytes hex]
     */
    fun formatFullAdStructureHex(manufacturerData: ByteArray): String {
        val sb = StringBuilder("16 FF 00 00")
        for (b in manufacturerData) {
            sb.append(" %02X".format(b))
        }
        return sb.toString()
    }
}
