package com.echonav.domain.usecase

import com.echonav.data.model.DetectedObject

/**
 * Prevents announcement spam by filtering redundant detections.
 *
 * Two conditions must BOTH be true for an object to generate feedback:
 *   1. COOLDOWN: At least [COOLDOWN_MS] ms has passed since this label was
 *                last announced. Prevents constant repetition of the same object.
 *   2. CHANGE:   Either the label is new (not seen recently), or its distance
 *                category has changed meaningfully (e.g., moved from "nearby"
 *                to "very close"). This ensures approaching hazards still speak.
 *
 * State is kept in-memory. Cleared when detection is stopped.
 */
class FeedbackFilterUseCase {

    companion object {
        // How long to suppress repeat announcements of the same label
        private const val COOLDOWN_MS = 3_000L

        // A label is "new" if it hasn't been seen within this window
        private const val FORGET_WINDOW_MS = 10_000L
    }

    // Maps label → (last announced timestamp, last distance category)
    private val lastSeenMap = mutableMapOf<String, LastSeen>()

    /**
     * Determines whether this object should trigger feedback.
     *
     * @param obj      The detected object to evaluate.
     * @param distance The distance label computed by DistanceUseCase.
     * @return true if the object should be announced, false if suppressed.
     */
    fun shouldAnnounce(obj: DetectedObject, distance: String): Boolean {
        val now = System.currentTimeMillis()
        val label = obj.label
        val previous = lastSeenMap[label]

        val shouldAnnounce = when {
            // Never seen before → always announce
            previous == null -> true

            // Within cooldown → suppress (prevents spam)
            now - previous.timestamp < COOLDOWN_MS -> false

            // Distance changed significantly → re-announce (approaching hazard)
            previous.distanceCategory != distance -> true

            // Object was "forgotten" (hasn't been visible recently) → re-announce
            now - previous.timestamp > FORGET_WINDOW_MS -> true

            // Same distance, within cooldown window → suppress
            else -> false
        }

        if (shouldAnnounce) {
            lastSeenMap[label] = LastSeen(timestamp = now, distanceCategory = distance)
        }

        return shouldAnnounce
    }

    /** Clears all tracking state. Call when detection is stopped. */
    fun reset() {
        lastSeenMap.clear()
    }

    private data class LastSeen(
        val timestamp: Long,
        val distanceCategory: String
    )
}
