package com.robinz.minitank.protocol

import kotlin.math.roundToInt

enum class ProtocolProfile(val displayName: String, val description: String) {
    DELI_JODU_AA55(
        "Deli / JODU Standard (AA 80 80..55)",
        "Most common Chinese BLE RC car protocol. 6-byte frame, centered at 0x80 (128)."
    ),
    DELI_JODU_ZERO_CENTER(
        "Deli / JODU Zero-Center (AA 00 00..55)",
        "6-byte frame, neutral at 0x00, signed directional magnitude."
    ),
    FRAMED_7E_7F(
        "7E-7F Framing (7E 04..7F)",
        "Standard toy car protocol starting with 0x7E and ending with 0x7F."
    ),
    DUAL_TRACK_PWM(
        "Dual Track PWM (Tank Differential)",
        "Independent left and right caterpillar track speed bytes."
    ),
    SIMPLE_4BYTE(
        "Simple 4-Byte (FF Dir Speed Ck)",
        "Directional command byte + speed byte."
    )
}

class ConfigurableTankProtocol(
    var activeProfile: ProtocolProfile = ProtocolProfile.DELI_JODU_AA55
) : TankProtocol {

    var lightsOn: Boolean = false
    var soundOn: Boolean = false
    var engineOn: Boolean = false

    override fun encodeDrive(throttle: Float, steering: Float, speedCapPercent: Int): ByteArray? {
        val capMultiplier = (speedCapPercent.coerceIn(20, 100) / 100f)
        val effThrottle = (throttle * capMultiplier).coerceIn(-1f, 1f)
        val effSteering = (steering * capMultiplier).coerceIn(-1f, 1f)

        return when (activeProfile) {
            ProtocolProfile.DELI_JODU_AA55 -> encodeDeliJoduAA55(effThrottle, effSteering)
            ProtocolProfile.DELI_JODU_ZERO_CENTER -> encodeDeliJoduZeroCenter(effThrottle, effSteering)
            ProtocolProfile.FRAMED_7E_7F -> encodeFramed7E(effThrottle, effSteering)
            ProtocolProfile.DUAL_TRACK_PWM -> encodeDualTrack(effThrottle, effSteering)
            ProtocolProfile.SIMPLE_4BYTE -> encodeSimple4Byte(effThrottle, effSteering)
        }
    }

    override fun encodeStop(): ByteArray? {
        return encodeDrive(0f, 0f, 100)
    }

    override fun encodeLights(enabled: Boolean): ByteArray? {
        lightsOn = enabled
        return encodeStop()
    }

    override fun encodeSound(enabled: Boolean): ByteArray? {
        soundOn = enabled
        return encodeStop()
    }

    override fun encodeEngineStart(): ByteArray? {
        engineOn = true
        return encodeStop()
    }

    /**
     * Deli / JODU Standard 6-byte packet:
     * [0xAA, Throttle (0..255, 128=neutral), Steering (0..255, 128=neutral), AuxFlags, Checksum, 0x55]
     */
    private fun encodeDeliJoduAA55(throttle: Float, steering: Float): ByteArray {
        // Map -1.0..+1.0 to 0..255 where 128 is neutral
        val tInt = ((throttle * 127f) + 128f).roundToInt().coerceIn(0, 255)
        val sInt = ((steering * 127f) + 128f).roundToInt().coerceIn(0, 255)

        var aux = 0
        if (lightsOn) aux = aux or 0x01
        if (soundOn) aux = aux or 0x02
        if (engineOn) aux = aux or 0x04

        // XOR checksum across payload bytes
        val checksum = (tInt xor sInt xor aux) and 0xFF

        return byteArrayOf(
            0xAA.toByte(),
            tInt.toByte(),
            sInt.toByte(),
            aux.toByte(),
            checksum.toByte(),
            0x55.toByte()
        )
    }

    /**
     * Deli / JODU Zero-Center:
     * 0x00 is neutral. Positive is forward/right, Negative is reverse/left.
     */
    private fun encodeDeliJoduZeroCenter(throttle: Float, steering: Float): ByteArray {
        val tInt = if (throttle >= 0) {
            (throttle * 100f).roundToInt().coerceIn(0, 100)
        } else {
            // Negative signed magnitude (0x80 | abs)
            ((-throttle * 100f).roundToInt().coerceIn(0, 100)) or 0x80
        }

        val sInt = if (steering >= 0) {
            (steering * 100f).roundToInt().coerceIn(0, 100)
        } else {
            ((-steering * 100f).roundToInt().coerceIn(0, 100)) or 0x80
        }

        var aux = 0
        if (lightsOn) aux = aux or 0x01
        if (soundOn) aux = aux or 0x02

        val checksum = (tInt xor sInt xor aux) and 0xFF

        return byteArrayOf(
            0xAA.toByte(),
            tInt.toByte(),
            sInt.toByte(),
            aux.toByte(),
            checksum.toByte(),
            0x55.toByte()
        )
    }

    /**
     * 7E Framing:
     * [0x7E, 0x04, throttle, steering, flags, 0x7F]
     */
    private fun encodeFramed7E(throttle: Float, steering: Float): ByteArray {
        val tInt = ((throttle * 127f) + 128f).roundToInt().coerceIn(0, 255)
        val sInt = ((steering * 127f) + 128f).roundToInt().coerceIn(0, 255)
        var aux = 0
        if (lightsOn) aux = aux or 0x01
        if (soundOn) aux = aux or 0x02

        return byteArrayOf(
            0x7E.toByte(),
            0x04.toByte(),
            tInt.toByte(),
            sInt.toByte(),
            aux.toByte(),
            0x7F.toByte()
        )
    }

    /**
     * Dual track differential PWM:
     * Computes left and right caterpillar track power from throttle and steering.
     */
    private fun encodeDualTrack(throttle: Float, steering: Float): ByteArray {
        var left = throttle + steering
        var right = throttle - steering
        val maxVal = maxOf(kotlin.math.abs(left), kotlin.math.abs(right), 1.0f)
        left /= maxVal
        right /= maxVal

        val leftInt = ((left * 127f) + 128f).roundToInt().coerceIn(0, 255)
        val rightInt = ((right * 127f) + 128f).roundToInt().coerceIn(0, 255)

        var aux = 0
        if (lightsOn) aux = aux or 0x01
        if (soundOn) aux = aux or 0x02

        val checksum = (leftInt xor rightInt xor aux) and 0xFF

        return byteArrayOf(
            0xAA.toByte(),
            leftInt.toByte(),
            rightInt.toByte(),
            aux.toByte(),
            checksum.toByte(),
            0x55.toByte()
        )
    }

    /**
     * Simple 4-byte command:
     * [0xFF, Direction, Speed, Checksum]
     */
    private fun encodeSimple4Byte(throttle: Float, steering: Float): ByteArray {
        val speed = (maxOf(kotlin.math.abs(throttle), kotlin.math.abs(steering)) * 100f).roundToInt().coerceIn(0, 100)
        val dir = when {
            speed < 5 -> 0x00 // Stop
            throttle > 0.3f && kotlin.math.abs(steering) <= 0.3f -> 0x01 // Forward
            throttle < -0.3f && kotlin.math.abs(steering) <= 0.3f -> 0x02 // Reverse
            steering < -0.3f -> 0x03 // Left
            steering > 0.3f -> 0x04 // Right
            else -> 0x00
        }
        val checksum = (0xFF + dir + speed) and 0xFF
        return byteArrayOf(0xFF.toByte(), dir.toByte(), speed.toByte(), checksum.toByte())
    }
}
