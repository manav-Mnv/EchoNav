package com.echonav.data.model

import android.graphics.RectF

/**
 * Represents a single detected object from the TFLite model.
 *
 * @param label      Human-readable class name (e.g., "person", "chair")
 * @param confidence Detection confidence score [0.0 – 1.0]
 * @param boundingBox Normalized bounding box [0.0 – 1.0] in image space
 *                    (left, top, right, bottom relative to image width/height)
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
) {
    /**
     * Horizontal center of the bounding box [0.0 – 1.0].
     * Used to determine left / center / right direction.
     */
    val centerX: Float get() = (boundingBox.left + boundingBox.right) / 2f

    /**
     * Normalized area of the bounding box [0.0 – 1.0].
     * Larger area → object is closer to the camera.
     */
    val normalizedArea: Float get() = boundingBox.width() * boundingBox.height()
}
