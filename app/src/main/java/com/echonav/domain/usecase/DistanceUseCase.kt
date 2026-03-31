package com.echonav.domain.usecase

import com.echonav.data.model.DetectedObject

/**
 * Estimates perceived distance of a detected object from the camera.
 *
 * This uses bounding box area as a proxy for distance — a well-known
 * heuristic in monocular depth estimation. It works because:
 *   - Larger bounding box area = object fills more of the frame = closer.
 *   - This is approximate but reliable enough for navigation warnings.
 *
 * Calibrated thresholds (normalized area = box.width × box.height):
 *   > 0.25  →  "very close"   (object fills >25% of frame — immediate hazard)
 *   > 0.08  →  "nearby"       (fills 8–25% — be aware, moderate distance)
 *   ≤ 0.08  →  "ahead"        (fills <8% — visible but not an immediate hazard)
 *
 * These values were empirically tuned for a 300×300 model input at typical
 * walking speeds. Adjust if using higher-resolution models or different use cases.
 */
class DistanceUseCase {

    companion object {
        const val THRESHOLD_VERY_CLOSE = 0.25f
        const val THRESHOLD_NEARBY     = 0.08f
    }

    /**
     * Returns a spoken distance label for the given object.
     */
    fun execute(obj: DetectedObject): String {
        return when {
            obj.normalizedArea > THRESHOLD_VERY_CLOSE -> "very close"
            obj.normalizedArea > THRESHOLD_NEARBY     -> "nearby"
            else                                      -> "ahead"
        }
    }

    /**
     * Returns true if the object is close enough to trigger strong haptic feedback.
     */
    fun isDanger(obj: DetectedObject): Boolean =
        obj.normalizedArea > THRESHOLD_VERY_CLOSE
}
