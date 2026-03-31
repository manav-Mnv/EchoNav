package com.echonav.domain.usecase

import com.echonav.data.model.DetectedObject

/**
 * Determines the spoken direction label for a detected object.
 *
 * The phone is assumed to be in a shirt-pocket (portrait) position,
 * so left/right in the camera frame maps directly to the user's left/right.
 *
 * Split zones (by normalized X center):
 *   0.0 – 0.38  →  "on your left"
 *   0.38 – 0.62 →  "ahead"
 *   0.62 – 1.0  →  "on your right"
 *
 * These zones are slightly asymmetric to give a wider "ahead" window,
 * reducing false left/right announcements from near-center objects.
 */
class DirectionUseCase {

    fun execute(obj: DetectedObject): String {
        return when {
            obj.centerX < 0.38f -> "on your left"
            obj.centerX > 0.62f -> "on your right"
            else                -> "ahead"
        }
    }
}
