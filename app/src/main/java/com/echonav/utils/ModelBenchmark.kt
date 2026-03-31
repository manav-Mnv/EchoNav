package com.echonav.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.echonav.data.repository.ObjectDetectorRepository

/**
 * ModelBenchmark — a simple in-process performance profiler.
 *
 * Not used at runtime. Call from a debug menu or a test to measure
 * how fast inference is on the target device.
 *
 * Usage (from a debug Activity or ViewModel):
 *
 *   ModelBenchmark(context).run(iterations = 30)
 *
 * Outputs to Logcat under the "ModelBenchmark" tag.
 */
class ModelBenchmark(private val context: Context) {

    companion object {
        private const val TAG = "ModelBenchmark"
    }

    /**
     * Runs [iterations] inference passes on a synthetic blank bitmap,
     * then logs min/max/average latency.
     *
     * @param iterations Number of frames to benchmark (default 20)
     * @param warmup     Number of warm-up passes before measuring (default 5)
     */
    fun run(iterations: Int = 20, warmup: Int = 5) {
        val repo = ObjectDetectorRepository(context)
        if (!repo.initialize()) {
            Log.e(TAG, "Model failed to initialize. Aborting benchmark.")
            return
        }

        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val timings = mutableListOf<Long>()

        Log.i(TAG, "Starting benchmark: $warmup warm-up + $iterations measured passes")

        // Warm-up (JIT compilation, NNAPI cache)
        repeat(warmup) { repo.detect(bitmap) }

        // Measured passes
        repeat(iterations) {
            val result = repo.detect(bitmap)
            timings += result.inferenceTimeMs
        }

        val min = timings.min()
        val max = timings.max()
        val avg = timings.average()
        val p95 = timings.sorted()[((iterations * 0.95).toInt()).coerceAtMost(iterations - 1)]

        Log.i(TAG, "──────────────────────────────────")
        Log.i(TAG, "  Benchmark Results ($iterations frames)")
        Log.i(TAG, "  Min : ${min}ms")
        Log.i(TAG, "  Avg : ${"%.1f".format(avg)}ms")
        Log.i(TAG, "  P95 : ${p95}ms")
        Log.i(TAG, "  Max : ${max}ms")
        Log.i(TAG, "  FPS (avg) : ${"%.1f".format(1000.0 / avg)}")
        Log.i(TAG, "──────────────────────────────────")

        repo.close()
    }
}
