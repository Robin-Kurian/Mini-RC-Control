package com.robinz.minitank.protocol

/**
 * Protocol interface for encoding tank commands into raw Bluetooth Low Energy byte packets.
 *
 * All operations return null if the byte encoding for the target hardware is unknown.
 * NEVER invent or guess command bytes to prevent uncontrolled motor runaway on physical hardware.
 */
interface TankProtocol {
    /**
     * Encodes drive motion.
     * @param throttle Normalized forward/reverse speed (-1.0f full reverse to +1.0f full forward).
     * @param steering Normalized turn rate (-1.0f full left to +1.0f full right).
     * @param speedCapPercent Configured maximum speed cap percentage (20 to 100).
     * @return Byte array ready for GATT transmission, or null if format is unconfirmed.
     */
    fun encodeDrive(throttle: Float, steering: Float, speedCapPercent: Int): ByteArray?

    /**
     * Encodes fail-safe stop.
     * @return Byte array for emergency/neutral stop, or null if format is unconfirmed.
     */
    fun encodeStop(): ByteArray?

    /**
     * Encodes front headlights toggle (discovered as "前灯" on car PCB DL-HG-TSLRX).
     * @param enabled True to turn on headlights, false to turn off.
     * @return Byte array or null.
     */
    fun encodeLights(enabled: Boolean): ByteArray?

    /**
     * Encodes horn/sound effect trigger (discovered as "+SPK-" on car PCB DL-HG-TSLRX).
     * @param enabled True for sound active, false for silent.
     * @return Byte array or null.
     */
    fun encodeSound(enabled: Boolean): ByteArray?

    /**
     * Encodes engine start/ignition simulation packet.
     * @return Byte array or null.
     */
    fun encodeEngineStart(): ByteArray?
}
