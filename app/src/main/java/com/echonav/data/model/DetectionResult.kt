package com.echonav.data.model

/**
 * The complete output of a single camera frame's inference pass.
 *
 * @param objects   List of objects detected above the confidence threshold.
 *                  Ordered by confidence descending.
 * @param inferenceTimeMs How long the TFLite inference took in milliseconds.
 *                         Used for performance monitoring and logging.
 */
data class DetectionResult(
    val objects: List<DetectedObject>,
    val inferenceTimeMs: Long
)
