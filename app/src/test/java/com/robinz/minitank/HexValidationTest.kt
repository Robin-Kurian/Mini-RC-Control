package com.robinz.minitank

import com.robinz.minitank.ble.hexToBytes
import com.robinz.minitank.ble.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HexValidationTest {

    @Test
    fun testValidHexParsing() {
        val bytes = "FF 01 02 A4".hexToBytes()
        assertNotNull(bytes)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x01.toByte(), 0x02.toByte(), 0xA4.toByte()),
            bytes
        )

        // Without spaces
        val bytesNoSpaces = "FF0102A4".hexToBytes()
        assertNotNull(bytesNoSpaces)
        assertArrayEquals(bytes, bytesNoSpaces)

        // Lowercase
        val bytesLower = "ff 01 02 a4".hexToBytes()
        assertNotNull(bytesLower)
        assertArrayEquals(bytes, bytesLower)

        // With 0x prefix
        val bytesPrefix = "0xFF 0x01".hexToBytes()
        assertNotNull(bytesPrefix)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x01.toByte()), bytesPrefix)
    }

    @Test
    fun testInvalidHexParsing() {
        // Odd length
        assertNull("F".hexToBytes())
        assertNull("FF 1".hexToBytes())

        // Non-hex characters
        assertNull("GG".hexToBytes())
        assertNull("FF ZZ".hexToBytes())
        assertNull("HELLO".hexToBytes())

        // Empty
        assertNull("".hexToBytes())
        assertNull("   ".hexToBytes())
    }

    @Test
    fun testHexRoundTrip() {
        val original = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val hexString = original.toHex()
        assertEquals("00 7F 80 FF", hexString)
        val roundTrip = hexString.hexToBytes()
        assertNotNull(roundTrip)
        assertArrayEquals(original, roundTrip)
    }
}
