package com.echonav.presentation.viewmodel

import com.echonav.data.model.AppMode
import com.echonav.data.model.DetectedObject

/**
 * Immutable snapshot of the entire UI state.
 *
 * The Composable UI reads ONLY from this class — it never calls services directly.
 * This keeps the UI layer purely declarative and the ViewModel easily testable.
 *
 * @param isDetecting        Whether the camera + ML pipeline is currently active.
 * @param currentMode        Active AppMode (EXPLORE / FOCUS / SILENT).
 * @param detectedObjects    Most recent set of detected objects (may be empty).
 * @param lastAnnouncement   The last spoken sentence for visual/debug display.
 * @param isModelReady       Whether TFLite has finished initializing.
 * @param hasCameraPermission Whether CAMERA permission has been granted.
 * @param inferenceTimeMs    Last inference duration (for debug overlay).
 */
data class EchoNavUiState(
    val isDetecting: Boolean          = false,
    val currentMode: AppMode          = AppMode.EXPLORE,
    val detectedObjects: List<DetectedObject> = emptyList(),
    val lastAnnouncement: String      = "",
    val isModelReady: Boolean         = false,
    val hasCameraPermission: Boolean  = false,
    val inferenceTimeMs: Long         = 0L
)
