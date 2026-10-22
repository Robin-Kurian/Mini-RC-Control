package com.robinz.minitank.protocol

import android.util.Log

/**
 * Safe fallback implementation of [TankProtocol] used until the exact packet protocol
 * for the DL-HG-TSLRX PCB has been calibrated via the developer diagnostics screen.
 *
 * Logs operations without emitting speculative byte sequences.
 */
class UnknownTankProtocol(
    private val onProtocolOperationLogged: ((operation: String, details: String) -> Unit)? = null
) : TankProtocol {

    companion object {
        private const val TAG = "UnknownTankProtocol"
    }

    override fun encodeDrive(throttle: Float, steering: Float, speedCapPercent: Int): ByteArray? {
        val details = "throttle=%.2f, steering=%.2f, speedCap=%d%%".format(throttle, steering, speedCapPercent)
        Log.d(TAG, "Requested encodeDrive ($details) -> Returning null (protocol pending)")
        onProtocolOperationLogged?.invoke("encodeDrive", details)
        return null
    }

    override fun encodeStop(): ByteArray? {
        Log.d(TAG, "Requested encodeStop -> Returning null (protocol pending)")
        onProtocolOperationLogged?.invoke("encodeStop", "Emergency / fail-safe zero stop")
        return null
    }

    override fun encodeLights(enabled: Boolean): ByteArray? {
        val details = "enabled=$enabled (PCB '前灯')"
        Log.d(TAG, "Requested encodeLights ($details) -> Returning null (protocol pending)")
        onProtocolOperationLogged?.invoke("encodeLights", details)
        return null
    }

    override fun encodeSound(enabled: Boolean): ByteArray? {
        val details = "enabled=$enabled (PCB '+SPK-')"
        Log.d(TAG, "Requested encodeSound ($details) -> Returning null (protocol pending)")
        onProtocolOperationLogged?.invoke("encodeSound", details)
        return null
    }

    override fun encodeEngineStart(): ByteArray? {
        Log.d(TAG, "Requested encodeEngineStart -> Returning null (protocol pending)")
        onProtocolOperationLogged?.invoke("encodeEngineStart", "Engine ignition simulation")
        return null
    }
}
