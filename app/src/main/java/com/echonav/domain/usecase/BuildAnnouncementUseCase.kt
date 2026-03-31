package com.echonav.domain.usecase

import com.echonav.data.model.DetectedObject

/**
 * Builds the final spoken announcement string for a detected object.
 *
 * Examples:
 *   "Person ahead, very close"
 *   "Chair on your left"
 *   "Door ahead, nearby"
 *   "Car on your right, very close — danger"
 *
 * The sentence is kept short and clear — optimized for fast TTS delivery
 * so the user doesn't have to wait long to hear a warning.
 */
class BuildAnnouncementUseCase(
    private val directionUseCase: DirectionUseCase,
    private val distanceUseCase: DistanceUseCase
) {
    /**
     * @param obj The detected object to announce.
     * @return A complete spoken sentence.
     */
    fun execute(obj: DetectedObject): String {
        val direction = directionUseCase.execute(obj)
        val distance  = distanceUseCase.execute(obj)
        val label     = obj.label.replaceFirstChar { it.uppercase() }

        // Build concise natural-language sentence
        val base = when (distance) {
            "very close" -> "$label $direction, very close"
            "nearby"     -> "$label $direction"
            else         -> "$label $direction, ahead"
        }

        // Append urgency suffix for immediate hazards
        return if (distanceUseCase.isDanger(obj)) "$base. Caution." else base
    }
}
