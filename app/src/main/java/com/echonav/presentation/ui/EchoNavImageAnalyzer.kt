package com.echonav.presentation.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.echonav.presentation.viewmodel.EchoNavViewModel
import com.echonav.utils.rotated
import com.echonav.utils.toArgbBitmap

/**
 * EchoNavImageAnalyzer is the bridge between CameraX and TFLite.
 *
 * CameraX calls analyze() for every captured frame on the ImageAnalysis executor.
 * We convert the YUV_420_888 ImageProxy → Bitmap, apply rotation correction,
 * then forward to the ViewModel for inference.
 *
 * Performance notes:
 * - We call image.close() IMMEDIATELY after converting — this is required by
 *   CameraX to free the buffer for the next frame. Forgetting this causes
 *   the camera pipeline to stall.
 * - The Bitmap-to-Matrix rotation is cheap. Bitmap.createBitmap() allocates
 *   a new buffer — acceptable since TFLite also copies internally.
 * - We do NOT scale here; the repository handles resizing to 300×300.
 */
class EchoNavImageAnalyzer(
    private val viewModel: EchoNavViewModel
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "ImageAnalyzer"
    }

    override fun analyze(image: ImageProxy) {
        try {
            // Convert ImageProxy (RGBA_8888) → Bitmap, then correct rotation
            val bitmap: Bitmap = image
                .toArgbBitmap()
                .rotated(image.imageInfo.rotationDegrees.toFloat())

            // Forward to ViewModel for TFLite inference
            viewModel.analyzeFrame(bitmap)

        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error: ${e.message}")
        } finally {
            // CRITICAL: Always close the ImageProxy to release the buffer
            image.close()
        }
    }

    /**
     * Rotates the bitmap by the given degrees.
     * Returns the original bitmap if rotation is 0 (no-op).
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
