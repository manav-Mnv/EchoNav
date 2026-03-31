package com.echonav.data.model

/**
 * EchoNav operating modes.
 *
 * - EXPLORE: Announces all detected objects — broad awareness for open environments.
 * - FOCUS:   Only announces the closest (largest) object — useful in crowds or narrow paths.
 * - SILENT:  Camera still runs (for future vibration-only feature), but TTS is suppressed.
 */
enum class AppMode {
    EXPLORE,
    FOCUS,
    SILENT;

    /** Returns the next mode in a cycle: EXPLORE → FOCUS → SILENT → EXPLORE */
    fun next(): AppMode = when (this) {
        EXPLORE -> FOCUS
        FOCUS   -> SILENT
        SILENT  -> EXPLORE
    }

    /** Human-readable announcement for TTS */
    fun announcement(): String = when (this) {
        EXPLORE -> "Explore mode. All objects will be announced."
        FOCUS   -> "Focus mode. Closest object only."
        SILENT  -> "Silent mode. Haptic feedback only."
    }
}
