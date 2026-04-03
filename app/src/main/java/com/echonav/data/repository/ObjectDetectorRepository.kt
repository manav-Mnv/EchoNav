package com.echonav.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.echonav.data.model.DetectedObject
import com.echonav.data.model.DetectionResult
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * ObjectDetectorRepository wraps TensorFlow Lite Task Vision's ObjectDetector.
 *
 * Architecture note: This is a pure data-layer class. It knows nothing about
 * ViewModel state, TTS, or vibration. It simply accepts a Bitmap and returns
 * a DetectionResult. The ViewModel orchestrates the rest.
 *
 * Model: mobilenet_v1_1.0_224_quant.tflite (or EfficientDet-Lite0)
 * Both models are bundled in the assets/ folder.
 *
 * PERFORMANCE:
 * - NNAPI acceleration is attempted first (Android neural network hardware).
 * - Falls back to CPU if unavailable.
 * - Input resolution kept at 300×300 — sufficient for navigation, fast enough
 *   for real-time (typically 50–120ms per frame on mid-range hardware).
 */
class ObjectDetectorRepository(private val context: Context) {

    companion object {
        private const val TAG = "ObjectDetectorRepo"

        // TFLite model filename in assets/
        private const val MODEL_FILE = "efficientdet_lite2.tflite"
        // Labels filename in assets/
        private const val LABELS_FILE = "coco_labels.txt"

        // Only objects in this set are voiced/haptic — reduces noise
        private val ALLOWED_LABELS = setOf(
            "person", "chair", "door", "couch", "sofa",
            "bed", "toilet", "sink", "table", "dining table",
            "car", "bicycle", "motorcycle", "bus", "truck",
            "bottle", "cup", "obstacle", "wall", "stairs", "step"
        )

        // Minimum confidence to consider a detection valid (increased for higher precision)
        private const val MIN_CONFIDENCE = 0.45f

        // Maximum number of concurrent detections to process
        private const val MAX_RESULTS = 5

        // Model input size
        private const val INPUT_SIZE = 300
    }

    private var objectDetector: ObjectDetector? = null
    private val imageProcessor: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .build()
    }

    /**
     * Initializes the TFLite ObjectDetector.
     * Call this once from ViewModel.init() — not on the main thread.
     *
     * @return true if initialization succeeded, false on error.
     */
    fun initialize(): Boolean {
        return try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(MIN_CONFIDENCE)
                .setMaxResults(MAX_RESULTS)
                .setBaseOptions(
                    BaseOptions.builder()
                        // Try NNAPI (neural network hardware acceleration)
                        // Falls back to CPU automatically if unavailable
                        .useNnapi()
                        .build()
                )
                .build()

            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                MODEL_FILE,
                options
            )

            Log.i(TAG, "ObjectDetector initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectDetector: ${e.message}")
            // Try CPU-only fallback
            initializeCpuFallback()
        }
    }

    /**
     * CPU-only fallback if NNAPI is unavailable on this device.
     */
    private fun initializeCpuFallback(): Boolean {
        return try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(MIN_CONFIDENCE)
                .setMaxResults(MAX_RESULTS)
                .setBaseOptions(
                    BaseOptions.builder()
                        .setNumThreads(4) // Use 4 CPU threads for better throughput
                        .build()
                )
                .build()

            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                MODEL_FILE,
                options
            )

            Log.i(TAG, "ObjectDetector initialized with CPU fallback")
            true
        } catch (e: Exception) {
            Log.e(TAG, "CPU fallback initialization also failed: ${e.message}")
            false
        }
    }

    /**
     * Runs inference on a camera Bitmap.
     *
     * This function is designed to be called on a background thread (via ViewModel
     * and CameraX's Analyzer dispatcher). Never call on the main thread.
     *
     * @param bitmap Camera preview frame. Will be resized internally.
     * @return DetectionResult containing filtered, mapped detections.
     */
    fun detect(bitmap: Bitmap): DetectionResult {
        val detector = objectDetector ?: return DetectionResult(emptyList(), 0L)

        val startTime = System.currentTimeMillis()

        // Wrap bitmap in TensorImage (no copy if already the right format)
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

        // Run inference
        val rawResults: List<Detection> = try {
            detector.detect(tensorImage)
        } catch (e: Exception) {
            Log.w(TAG, "Inference error: ${e.message}")
            emptyList()
        }

        val inferenceTime = System.currentTimeMillis() - startTime

        // Map TFLite Detection → our domain model, filter by allowed labels
        val mappedObjects = rawResults
            .mapNotNull { detection ->
                val category = detection.categories.maxByOrNull { it.score } ?: return@mapNotNull null
                val label = category.label.lowercase().trim()

                // Only process labels in our navigation-relevant set
                if (ALLOWED_LABELS.none { label.contains(it) }) return@mapNotNull null

                // TFLite bounding box is in pixel space relative to the INPUT_SIZE.
                // Normalize to [0..1] for resolution-independent logic downstream.
                val box = detection.boundingBox
                val normalizedBox = RectF(
                    box.left   / INPUT_SIZE.toFloat(),
                    box.top    / INPUT_SIZE.toFloat(),
                    box.right  / INPUT_SIZE.toFloat(),
                    box.bottom / INPUT_SIZE.toFloat()
                )

                // Clamp to [0,1] to handle edge cases
                normalizedBox.left   = normalizedBox.left.coerceIn(0f, 1f)
                normalizedBox.top    = normalizedBox.top.coerceIn(0f, 1f)
                normalizedBox.right  = normalizedBox.right.coerceIn(0f, 1f)
                normalizedBox.bottom = normalizedBox.bottom.coerceIn(0f, 1f)

                DetectedObject(
                    label      = formatLabel(label),
                    confidence = category.score,
                    boundingBox = normalizedBox
                )
            }
            .sortedByDescending { it.confidence }

        return DetectionResult(mappedObjects, inferenceTime)
    }

    /**
     * Formats raw model label into natural speech.
     * e.g., "dining table" → "table", "couch" → "couch"
     */
    private fun formatLabel(raw: String): String = when {
        raw.contains("person") || raw.contains("people") -> "person"
        raw.contains("dining table") || raw.contains("table") -> "table"
        raw.contains("couch") || raw.contains("sofa") -> "couch"
        raw.contains("chair") -> "chair"
        raw.contains("door") -> "door"
        raw.contains("car") -> "car"
        raw.contains("bicycle") || raw.contains("bike") -> "bicycle"
        raw.contains("stairs") || raw.contains("step") -> "stairs"
        else -> raw
    }

    /**
     * Release TFLite resources. Call when detection is permanently stopped
     * (e.g., Activity onDestroy).
     */
    fun close() {
        objectDetector?.close()
        objectDetector = null
        Log.i(TAG, "ObjectDetector closed")
    }
}
