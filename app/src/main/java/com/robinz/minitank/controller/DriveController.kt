package com.robinz.minitank.controller

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class ControlMode(val label: String, val description: String) {
    SLIDERS("Dual Sliders", "Split distance throttle & steering sliders"),
    BUTTONS("Classic Buttons", "Multi-touch directional buttons"),
    JOYSTICK("Virtual Joystick", "Single 360° circular proportional thumbstick"),
    GYRO("Gyro Gravity", "Device tilt with artificial horizon & zero-calibration")
}

data class DriveState(
    val rawThrottle: Float = 0f,
    val rawSteering: Float = 0f,
    val cappedThrottle: Float = 0f,
    val speedCapPercent: Int = 20
) {
    val isZero: Boolean
        get() = rawThrottle == 0f && rawSteering == 0f
}

class DriveController(
    initialSpeedCapPercent: Int = 20
) {
    var speedCapPercent: Int = initialSpeedCapPercent.coerceIn(20, 100)
        set(value) {
            field = value.coerceIn(20, 100)
            recalculateCapped()
        }

    var rawThrottle: Float = 0f
        private set

    var rawSteering: Float = 0f
        private set

    var cappedThrottle: Float = 0f
        private set

    private fun recalculateCapped() {
        cappedThrottle = rawThrottle * (speedCapPercent / 100f)
    }

    fun getDriveState(): DriveState = DriveState(
        rawThrottle = rawThrottle,
        rawSteering = rawSteering,
        cappedThrottle = cappedThrottle,
        speedCapPercent = speedCapPercent
    )

    /**
     * Resets both axes immediately to zero (Fail-Safe Stop).
     */
    fun reset() {
        rawThrottle = 0f
        rawSteering = 0f
        cappedThrottle = 0f
    }

    /**
     * Mode 1: Button-based driving.
     * Forward / Reverse and Left / Right multi-touch states.
     */
    fun updateButtons(
        forward: Boolean,
        reverse: Boolean,
        left: Boolean,
        right: Boolean
    ): DriveState {
        rawThrottle = when {
            forward && !reverse -> 1.0f
            reverse && !forward -> -1.0f
            else -> 0.0f
        }
        rawSteering = when {
            right && !left -> 1.0f
            left && !right -> -1.0f
            else -> 0.0f
        }
        recalculateCapped()
        return getDriveState()
    }

    /**
     * Mode 2: Split slider driving (User's preferred default).
     * Speed is proportional to the distance dragged from the center neutral point.
     */
    fun updateSliders(
        throttleDeltaY: Float,
        maxThrottleTravel: Float,
        steeringDeltaX: Float,
        maxSteeringTravel: Float,
        deadZoneFraction: Float = 0.06f
    ): DriveState {
        if (maxThrottleTravel > 0f) {
            // Negative deltaY is upward (forward), positive deltaY is downward (reverse)
            val normalizedY = (-throttleDeltaY / maxThrottleTravel).coerceIn(-1.0f, 1.0f)
            rawThrottle = applyAxisDeadZoneAndCurve(normalizedY, deadZoneFraction)
        } else {
            rawThrottle = 0f
        }

        if (maxSteeringTravel > 0f) {
            // Positive deltaX is right, negative deltaX is left
            val normalizedX = (steeringDeltaX / maxSteeringTravel).coerceIn(-1.0f, 1.0f)
            rawSteering = applyAxisDeadZoneAndCurve(normalizedX, deadZoneFraction)
        } else {
            rawSteering = 0f
        }

        recalculateCapped()
        return getDriveState()
    }

    /**
     * Mode 3: Single circular virtual joystick.
     * Upward (negative dy) produces forward throttle, downward reverse, left/right steering.
     */
    fun updateJoystick(
        deltaX: Float,
        deltaY: Float,
        maxRadius: Float,
        deadZone: Float = 0.08f
    ): DriveState {
        if (maxRadius <= 0f) {
            reset()
            return getDriveState()
        }

        val distance = hypot(deltaX, deltaY)
        val normalizedDist = (distance / maxRadius).coerceIn(0f, 1.0f)

        if (normalizedDist <= deadZone) {
            rawThrottle = 0f
            rawSteering = 0f
        } else {
            // Smooth curve after dead-zone
            val activeFraction = (normalizedDist - deadZone) / (1.0f - deadZone)
            val response = activeFraction * activeFraction * 0.7f + activeFraction * 0.3f

            // In screen coordinates, negative Y is UP (forward)
            val angle = atan2(-deltaY, deltaX)
            rawThrottle = (response * sin(angle)).coerceIn(-1.0f, 1.0f)
            rawSteering = (response * cos(angle)).coerceIn(-1.0f, 1.0f)
        }

        recalculateCapped()
        return getDriveState()
    }

    /**
     * Mode 4: Gyroscope / Gravity tilt driving.
     * Pitch controls throttle, Roll controls steering.
     */
    fun updateGyro(
        pitchDegrees: Float,
        rollDegrees: Float,
        centerPitchDegrees: Float = 0f,
        centerRollDegrees: Float = 0f,
        maxTiltAngle: Float = 30f,
        deadZoneAngle: Float = 3f
    ): DriveState {
        val diffPitch = (pitchDegrees - centerPitchDegrees).coerceIn(-maxTiltAngle, maxTiltAngle)
        val diffRoll = (rollDegrees - centerRollDegrees).coerceIn(-maxTiltAngle, maxTiltAngle)

        // Pitch down (phone tilted away) -> forward throttle
        val normPitch = (diffPitch / maxTiltAngle).coerceIn(-1f, 1f)
        // Roll right (phone tilted right) -> right steering
        val normRoll = (diffRoll / maxTiltAngle).coerceIn(-1f, 1f)

        rawThrottle = applyAxisDeadZoneAndCurve(normPitch, deadZoneAngle / maxTiltAngle)
        rawSteering = applyAxisDeadZoneAndCurve(normRoll, deadZoneAngle / maxTiltAngle)

        recalculateCapped()
        return getDriveState()
    }

    private fun applyAxisDeadZoneAndCurve(value: Float, deadZone: Float): Float {
        val absVal = kotlin.math.abs(value)
        if (absVal <= deadZone) return 0f
        val active = (absVal - deadZone) / (1.0f - deadZone)
        val curved = active * active * 0.7f + active * 0.3f
        return if (value < 0f) -curved else curved
    }
}
