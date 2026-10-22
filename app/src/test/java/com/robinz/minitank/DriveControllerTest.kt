package com.robinz.minitank

import com.robinz.minitank.controller.DriveController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class DriveControllerTest {

    private lateinit var controller: DriveController

    @Before
    fun setUp() {
        controller = DriveController(initialSpeedCapPercent = 20)
    }

    @Test
    fun testSpeedCapCalculation() {
        // At 20% speed cap and full forward throttle (1.0), capped throttle must be exactly 0.20
        controller.speedCapPercent = 20
        val state20 = controller.updateButtons(forward = true, reverse = false, left = false, right = false)
        assertEquals(1.0f, state20.rawThrottle, 0.001f)
        assertEquals(0.20f, state20.cappedThrottle, 0.001f)

        // At 50% cap, full throttle -> 0.50
        controller.speedCapPercent = 50
        assertEquals(0.50f, controller.cappedThrottle, 0.001f)

        // At 100% cap, full throttle -> 1.00
        controller.speedCapPercent = 100
        assertEquals(1.00f, controller.cappedThrottle, 0.001f)

        // Values below 20% must be clamped to 20%
        controller.speedCapPercent = 10
        assertEquals(20, controller.speedCapPercent)
    }

    @Test
    fun testButtonModeAxesState() {
        // Forward only
        var state = controller.updateButtons(forward = true, reverse = false, left = false, right = false)
        assertEquals(1.0f, state.rawThrottle, 0.001f)
        assertEquals(0.0f, state.rawSteering, 0.001f)

        // Reverse only
        state = controller.updateButtons(forward = false, reverse = true, left = false, right = false)
        assertEquals(-1.0f, state.rawThrottle, 0.001f)
        assertEquals(0.0f, state.rawSteering, 0.001f)

        // Left only
        state = controller.updateButtons(forward = false, reverse = false, left = true, right = false)
        assertEquals(0.0f, state.rawThrottle, 0.001f)
        assertEquals(-1.0f, state.rawSteering, 0.001f)

        // Right only
        state = controller.updateButtons(forward = false, reverse = false, left = false, right = true)
        assertEquals(0.0f, state.rawThrottle, 0.001f)
        assertEquals(1.0f, state.rawSteering, 0.001f)

        // Opposing buttons cancel to zero
        state = controller.updateButtons(forward = true, reverse = true, left = true, right = true)
        assertEquals(0.0f, state.rawThrottle, 0.001f)
        assertEquals(0.0f, state.rawSteering, 0.001f)
        assertTrue(state.isZero)
    }

    @Test
    fun testJoystickDeadZoneAndClamping() {
        val radius = 100f
        val deadZone = 0.08f // 8%

        // Deflection inside dead-zone (e.g. 5 pixels out of 100 = 0.05 < 0.08)
        var state = controller.updateJoystick(deltaX = 0f, deltaY = -5f, maxRadius = radius, deadZone = deadZone)
        assertEquals(0.0f, state.rawThrottle, 0.001f)
        assertEquals(0.0f, state.rawSteering, 0.001f)
        assertTrue(state.isZero)

        // Deflection outside dead-zone (e.g. 50 pixels upward = -50 deltaY)
        state = controller.updateJoystick(deltaX = 0f, deltaY = -50f, maxRadius = radius, deadZone = deadZone)
        assertTrue("Throttle must be positive for upward deflection", state.rawThrottle > 0f)
        assertEquals(0.0f, state.rawSteering, 0.001f)

        // Huge deflection outside radius (e.g. 250 pixels) must be clamped to unit circle (<= 1.0)
        state = controller.updateJoystick(deltaX = 0f, deltaY = -250f, maxRadius = radius, deadZone = deadZone)
        assertEquals(1.0f, state.rawThrottle, 0.001f)
        assertEquals(0.0f, state.rawSteering, 0.001f)
    }

    @Test
    fun testResponseCurveProgressiveControl() {
        val radius = 100f
        // Halfway travel should NOT be 0.5; it should follow the smooth progressive curve
        val stateHalf = controller.updateJoystick(deltaX = 0f, deltaY = -54f, maxRadius = radius, deadZone = 0.08f)
        // With curve: active = (0.54 - 0.08) / 0.92 = 0.5
        // response = 0.5^2 * 0.7 + 0.5 * 0.3 = 0.175 + 0.15 = 0.325
        assertTrue(stateHalf.rawThrottle in 0.25f..0.40f)
        assertTrue("Half travel must not jump directly to max throttle", stateHalf.rawThrottle < 0.5f)
    }

    @Test
    fun testPointerReleaseReturnsAxesToZero() {
        controller.updateButtons(forward = true, reverse = false, left = true, right = false)
        assertFalse(controller.getDriveState().isZero)

        // Fail-safe reset
        controller.reset()
        val state = controller.getDriveState()
        assertEquals(0.0f, state.rawThrottle, 0.0001f)
        assertEquals(0.0f, state.rawSteering, 0.0001f)
        assertEquals(0.0f, state.cappedThrottle, 0.0001f)
        assertTrue(state.isZero)
    }

    @Test
    fun testSplitSliderDistanceThrottle() {
        val maxTravel = 100f

        // Small movement within deadzone (e.g. 4 pixels)
        var state = controller.updateSliders(
            throttleDeltaY = -4f,
            maxThrottleTravel = maxTravel,
            steeringDeltaX = 0f,
            maxSteeringTravel = maxTravel,
            deadZoneFraction = 0.06f
        )
        assertEquals(0.0f, state.rawThrottle, 0.001f)

        // 50% travel upward (-50 deltaY)
        state = controller.updateSliders(
            throttleDeltaY = -50f,
            maxThrottleTravel = maxTravel,
            steeringDeltaX = 0f,
            maxSteeringTravel = maxTravel,
            deadZoneFraction = 0.06f
        )
        assertTrue("Throttle must be positive", state.rawThrottle > 0f)

        // Full travel downward (100 deltaY) -> full reverse
        state = controller.updateSliders(
            throttleDeltaY = 100f,
            maxThrottleTravel = maxTravel,
            steeringDeltaX = 0f,
            maxSteeringTravel = maxTravel,
            deadZoneFraction = 0.06f
        )
        assertEquals(-1.0f, state.rawThrottle, 0.001f)
    }
}
