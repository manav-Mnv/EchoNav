package com.echonav.presentation.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echonav.data.model.AppMode
import com.echonav.data.model.DetectedObject
import com.echonav.data.repository.ObjectDetectorRepository
import com.echonav.domain.usecase.BuildAnnouncementUseCase
import com.echonav.domain.usecase.DirectionUseCase
import com.echonav.domain.usecase.DistanceUseCase
import com.echonav.domain.usecase.FeedbackFilterUseCase
import com.echonav.services.HapticService
import com.echonav.services.SpeechService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * EchoNavViewModel is the single source of truth for the app.
 *
 * It orchestrates:
 *   1. TFLite model initialization
 *   2. Frame-by-frame inference via analyzeFrame()
 *   3. Feedback decisions (speech + haptic) using domain use cases
 *   4. UI state updates via StateFlow
 *
 * The ViewModel survives configuration changes. Services are tied to
 * AndroidViewModel's Application context, not the Activity.
 */
class EchoNavViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "EchoNavViewModel"
    }

    // ── Services ────────────────────────────────────────────────────────────
    private val speechService   = SpeechService(application)
    private val hapticService   = HapticService(application)
    private val repository      = ObjectDetectorRepository(application)

    // ── Use Cases ────────────────────────────────────────────────────────────
    private val directionUseCase    = DirectionUseCase()
    private val distanceUseCase     = DistanceUseCase()
    private val feedbackFilter      = FeedbackFilterUseCase()
    private val buildAnnouncement   = BuildAnnouncementUseCase(directionUseCase, distanceUseCase)

    // ── UI State ─────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(EchoNavUiState())
    val uiState: StateFlow<EchoNavUiState> = _uiState.asStateFlow()

    // ── Init ─────────────────────────────────────────────────────────────────
    init {
        initializeServices()
    }

    /**
     * Initializes TTS and TFLite on a background thread.
     * UI shows a "loading" state until both are ready.
     */
    private fun initializeServices() {
        viewModelScope.launch(Dispatchers.IO) {
            // Initialize TTS (suspend call — waits for TTS engine to be ready)
            val ttsReady = speechService.initialize()
            if (!ttsReady) {
                Log.e(TAG, "TTS failed to initialize")
            }

            // Initialize TFLite model
            val modelReady = repository.initialize()

            _uiState.update { it.copy(isModelReady = modelReady) }

            // Welcome announcement — only after TTS is ready
            if (ttsReady && modelReady) {
                speechService.speak("Welcome to EchoNav. Tap anywhere to start navigation.", interrupt = false)
            } else if (ttsReady) {
                speechService.speak("EchoNav loaded, but object detection failed. Please check model file.", interrupt = false)
            }
        }
    }

    // ── Camera Permission ────────────────────────────────────────────────────

    /**
     * Called from MainActivity after permission result is known.
     */
    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (!granted) {
            speechService.speak("Camera permission is required for navigation. Please grant it in settings.", interrupt = true)
        }
    }

    // ── Detection Control ────────────────────────────────────────────────────

    /**
     * Single tap handler — toggles detection on/off.
     */
    fun onTap() {
        val current = _uiState.value
        if (!current.isModelReady || !current.hasCameraPermission) {
            speechService.speak("Not ready yet. Please wait.", interrupt = true)
            return
        }

        if (current.isDetecting) {
            stopDetection()
        } else {
            startDetection()
        }
    }

    /**
     * Long press handler — cycles through AppModes.
     */
    fun onLongPress() {
        val nextMode = _uiState.value.currentMode.next()
        _uiState.update { it.copy(currentMode = nextMode) }
        feedbackFilter.reset()
        speechService.speak(nextMode.announcement(), interrupt = true)
        hapticService.awareness()
        Log.d(TAG, "Mode switched to: $nextMode")
    }

    private fun startDetection() {
        feedbackFilter.reset()
        _uiState.update { it.copy(isDetecting = true, detectedObjects = emptyList()) }
        speechService.speak("Detection started.", interrupt = false)
        Log.i(TAG, "Detection started")
    }

    private fun stopDetection() {
        _uiState.update { it.copy(isDetecting = false, detectedObjects = emptyList()) }
        feedbackFilter.reset()
        speechService.stop()
        hapticService.cancel()
        speechService.speak("Detection paused.", interrupt = false)
        Log.i(TAG, "Detection stopped")
    }

    // ── Frame Analysis ───────────────────────────────────────────────────────

    /**
     * Called by CameraX's ImageAnalysis use case for every camera frame.
     *
     * This runs on the ImageAnalysis executor thread (not main thread).
     * Heavy operations (inference) are appropriate here.
     *
     * @param bitmap A cropped, rotated Bitmap from the camera frame.
     */
    fun analyzeFrame(bitmap: Bitmap) {
        val state = _uiState.value

        // Guard: don't process if detection is paused
        if (!state.isDetecting || !state.isModelReady) return

        // Run TFLite inference
        val result = repository.detect(bitmap)
        val objects = result.objects

        // Update UI with raw detections + inference time
        _uiState.update {
            it.copy(
                detectedObjects = objects,
                inferenceTimeMs = result.inferenceTimeMs
            )
        }

        // Determine which objects to announce based on current mode
        val candidates: List<DetectedObject> = when (state.currentMode) {
            AppMode.EXPLORE -> objects                      // All detected objects
            AppMode.FOCUS   -> listOfNotNull(objects.maxByOrNull { it.normalizedArea }) // Closest only
            AppMode.SILENT  -> emptyList()                 // No voice, haptic still runs
        }

        // Process each candidate through the feedback pipeline
        for (obj in candidates) {
            val distance = distanceUseCase.execute(obj)

            // Smart filter: only announce if meaningful change happened
            if (feedbackFilter.shouldAnnounce(obj, distance)) {
                val announcement = buildAnnouncement.execute(obj)

                // Update the last announcement in UI state (for debug overlay)
                _uiState.update { it.copy(lastAnnouncement = announcement) }

                // Speak — interrupt immediately if it's a danger/caution
                val isDanger = distanceUseCase.isDanger(obj)
                speechService.speak(announcement, interrupt = isDanger)

                // Haptic feedback
                if (isDanger) {
                    hapticService.danger()
                } else {
                    hapticService.awareness()
                }

                Log.d(TAG, "Announced: $announcement (${result.inferenceTimeMs}ms)")
            }
        }

        // Even in SILENT mode, trigger danger haptic for very-close objects
        if (state.currentMode == AppMode.SILENT) {
            val danger = objects.firstOrNull { distanceUseCase.isDanger(it) }
            if (danger != null) {
                val distance = distanceUseCase.execute(danger)
                if (feedbackFilter.shouldAnnounce(danger, distance)) {
                    hapticService.danger()
                }
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        speechService.shutdown()
        repository.close()
        hapticService.cancel()
        Log.i(TAG, "ViewModel cleared — resources released")
    }
}
