package com.robinz.minitank

import com.robinz.minitank.controller.DriveController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GyroDriveTest {

    private lateinit var controller: DriveController

    @Before
    fun setUp() {
        controller = DriveController(initialSpeedCapPercent = 20)
    }

    @Test
    fun testTiltWithinDeadzoneOutputsZero() {
        // Tilted 2 degrees when deadzone is 3 degrees
        val state = controller.updateGyro(
            pitchDegrees = 2f,
            rollDegrees = 1.5f,
            centerPitchDegrees = 0f,
            centerRollDegrees = 0f,
            maxTiltAngle = 30f,
            deadZoneAngle = 3f
        )
        assertEquals(0.0f, state.rawThrottle, 0.001f)
        assertEquals(0.0f, state.rawSteering, 0.001f)
        assertTrue(state.isZero)
    }

    @Test
    fun testZeroPointCalibrationOffset() {
        // Suppose user holds device tilted at +15 degrees pitch at rest.
        // Calibrating sets centerPitchDegrees = 15f.
        val calibratedCenterPitch = 15f
        val calibratedCenterRoll = -5f

        // When device is at the calibrated center, output must be zero!
        var state = controller.updateGyro(
            pitchDegrees = 15f,
            rollDegrees = -5f,
            centerPitchDegrees = calibratedCenterPitch,
            centerRollDegrees = calibratedCenterRoll
        )
        assertEquals(0.0f, state.rawThrottle, 0.001f)
        assertEquals(0.0f, state.rawSteering, 0.001f)
        assertTrue(state.isZero)

        // Tilting forward to +35 degrees (20 degrees delta beyond center)
        state = controller.updateGyro(
            pitchDegrees = 35f,
            rollDegrees = -5f,
            centerPitchDegrees = calibratedCenterPitch,
            centerRollDegrees = calibratedCenterRoll
        )
        assertTrue("Throttle must be forward positive", state.rawThrottle > 0f)
        assertEquals(0.0f, state.rawSteering, 0.001f)
    }

    @Test
    fun testMaxTiltAngleClamping() {
        // Tilting 60 degrees when max is 30 degrees must clamp to 1.0
        val state = controller.updateGyro(
            pitchDegrees = 60f,
            rollDegrees = 0f,
            centerPitchDegrees = 0f,
            centerRollDegrees = 0f,
            maxTiltAngle = 30f
        )
        assertEquals(1.0f, state.rawThrottle, 0.001f)
    }
}
