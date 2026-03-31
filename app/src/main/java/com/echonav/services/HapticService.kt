package com.echonav.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * HapticService provides structured vibration feedback for EchoNav.
 *
 * Two feedback levels:
 *   - DANGER:    Strong, pulsed vibration. Signals an immediate obstacle.
 *   - AWARENESS: Single short buzz. General informational feedback.
 *
 * Automatically uses the modern VibrationEffect API (API 26+) for consistent
 * amplitude control across devices.
 *
 * The Vibrator is obtained at construction time — no repeated service lookups
 * during fast-path inference loops.
 */
class HapticService(context: Context) {

    companion object {
        private const val TAG = "HapticService"

        // Danger pattern: [delay, vibrate, pause, vibrate, pause, vibrate]
        // Three strong pulses to simulate urgency
        private val DANGER_PATTERN    = longArrayOf(0L, 200L, 100L, 200L, 100L, 200L)
        private val DANGER_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 200)

        // Awareness: single short pulse
        private const val AWARENESS_DURATION  = 80L
        private const val AWARENESS_AMPLITUDE = 110
    }

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Triggers strong pulsed vibration — used when a hazard is "very close".
     * Interrupts any ongoing vibration.
     */
    fun danger() {
        if (!vibrator.hasVibrator()) return

        try {
            vibrator.cancel() // Stop any ongoing pattern first
            val effect = VibrationEffect.createWaveform(DANGER_PATTERN, DANGER_AMPLITUDES, -1)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(TAG, "Danger vibration failed: ${e.message}")
        }
    }

    /**
     * Triggers a short single buzz — used for general object awareness.
     * Does NOT interrupt an ongoing danger vibration.
     */
    fun awareness() {
        if (!vibrator.hasVibrator()) return

        try {
            val effect = VibrationEffect.createOneShot(AWARENESS_DURATION, AWARENESS_AMPLITUDE)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w(TAG, "Awareness vibration failed: ${e.message}")
        }
    }

    /**
     * Cancels any in-progress vibration.
     */
    fun cancel() {
        vibrator.cancel()
    }
}
