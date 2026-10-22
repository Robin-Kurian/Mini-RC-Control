package com.robinz.minitank

import com.robinz.minitank.protocol.ExtendedAdvertisingTankProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtendedAdvertisingProtocolTest {

    private val session633566 = byteArrayOf(
        0x04.toByte(), 0x07.toByte(), 0x03.toByte(), 0xFD.toByte(), 0xC1.toByte(),
        0x63.toByte(), 0x35.toByte(), 0x66.toByte()
    )

    private val session303263 = byteArrayOf(
        0x04.toByte(), 0x07.toByte(), 0x03.toByte(), 0xFD.toByte(), 0xC1.toByte(),
        0x30.toByte(), 0x32.toByte(), 0x63.toByte()
    )

    @Test
    fun testConfirmedIdleExamples() {
        // Sample 1: 16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 82 00 00 00 07
        val data1 = ExtendedAdvertisingTankProtocol.buildIdleData(counter = 0x82, customHeader = session633566)
        assertEquals("16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 82 00 00 00 07",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(data1))

        // Sample 2: 16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 83 00 00 00 06
        val data2 = ExtendedAdvertisingTankProtocol.buildIdleData(counter = 0x83, customHeader = session633566)
        assertEquals("16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 83 00 00 00 06",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(data2))

        // Sample 3: 16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 84 00 00 00 01
        val data3 = ExtendedAdvertisingTankProtocol.buildIdleData(counter = 0x84, customHeader = session633566)
        assertEquals("16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 84 00 00 00 01",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(data3))

        // Sample 4: 16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 85 00 00 00 00
        val data4 = ExtendedAdvertisingTankProtocol.buildIdleData(counter = 0x85, customHeader = session633566)
        assertEquals("16 FF 00 00 04 07 03 FD C1 63 35 66 00 00 04 64 00 00 85 00 00 00 00",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(data4))
    }

    @Test
    fun testConfirmedForwardAt30Percent() {
        // Controlled capture result: FORWARD at displayed 30%
        // Full 21-byte Manufacturer Specific Data (after 16 FF):
        // 00 00 04 07 03 FD C1 30 32 63 01 1E 05 64 00 00 97 00 00 00 5D
        val data97 = ExtendedAdvertisingTankProtocol.buildForwardData(
            intensityPercent = 30, // 30 decimal = 0x1E
            counter = 0x97,
            customHeader = session303263
        )
        assertEquals("16 FF 00 00 04 07 03 FD C1 30 32 63 01 1E 05 64 00 00 97 00 00 00 5D",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(data97))

        // 00 00 04 07 03 FD C1 30 32 63 01 1E 05 64 00 00 98 00 00 00 52
        val data98 = ExtendedAdvertisingTankProtocol.buildForwardData(
            intensityPercent = 30,
            counter = 0x98,
            customHeader = session303263
        )
        assertEquals("16 FF 00 00 04 07 03 FD C1 30 32 63 01 1E 05 64 00 00 98 00 00 00 52",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(data98))

        // 00 00 04 07 03 FD C1 30 32 63 01 1E 05 64 00 00 99 00 00 00 53
        val data99 = ExtendedAdvertisingTankProtocol.buildForwardData(
            intensityPercent = 30,
            counter = 0x99,
            customHeader = session303263
        )
        assertEquals("16 FF 00 00 04 07 03 FD C1 30 32 63 01 1E 05 64 00 00 99 00 00 00 53",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(data99))
    }

    @Test
    fun testConfirmedNonIdleExamples() {
        // Sample A: 16 FF 00 00 04 07 03 FD C1 63 35 66 01 06 05 64 00 00 9A 00 00 00 19
        val dataA = ExtendedAdvertisingTankProtocol.buildManufacturerData(
            directionState = 0x01,
            intensity = 0x06,
            axisType = 0x05,
            secondaryByte = 0x64,
            counter = 0x9A,
            customHeader = session633566
        )
        assertEquals("16 FF 00 00 04 07 03 FD C1 63 35 66 01 06 05 64 00 00 9A 00 00 00 19",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(dataA))

        // Sample B: 16 FF 00 00 04 07 03 FD C1 63 35 66 01 08 05 64 00 00 9B 00 00 00 16
        val dataB = ExtendedAdvertisingTankProtocol.buildManufacturerData(
            directionState = 0x01,
            intensity = 0x08,
            axisType = 0x05,
            secondaryByte = 0x64,
            counter = 0x9B,
            customHeader = session633566
        )
        assertEquals("16 FF 00 00 04 07 03 FD C1 63 35 66 01 08 05 64 00 00 9B 00 00 00 16",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(dataB))

        // Sample C: 16 FF 00 00 04 07 03 FD C1 63 35 66 02 06 04 64 00 00 A0 00 00 00 21
        val dataC = ExtendedAdvertisingTankProtocol.buildManufacturerData(
            directionState = 0x02,
            intensity = 0x06,
            axisType = 0x04,
            secondaryByte = 0x64,
            counter = 0xA0,
            customHeader = session633566
        )
        assertEquals("16 FF 00 00 04 07 03 FD C1 63 35 66 02 06 04 64 00 00 A0 00 00 00 21",
            ExtendedAdvertisingTankProtocol.formatFullAdStructureHex(dataC))
    }
}
