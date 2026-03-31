package com.echonav.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Utility extensions used throughout EchoNav.
 * Grouped here to keep other files clean.
 */

// ── ImageProxy → Bitmap ───────────────────────────────────────────────────

/**
 * Converts a CameraX ImageProxy (RGBA_8888 or YUV_420_888) to an ARGB_8888 Bitmap.
 *
 * When ImageAnalysis is configured with OUTPUT_IMAGE_FORMAT_RGBA_8888 (which we do),
 * the plane[0] buffer is a direct RGBA byte buffer — we copy it into a Bitmap.
 *
 * This is faster than YUV conversion and avoids a separate color-space step.
 */
fun ImageProxy.toArgbBitmap(): Bitmap {
    val plane = planes[0]
    val buffer: ByteBuffer = plane.buffer.rewind() as ByteBuffer

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)
    return bitmap
}

// ── Bitmap rotation ───────────────────────────────────────────────────────

/**
 * Returns a new Bitmap rotated by [degrees].
 * Returns [this] unmodified if degrees == 0 (avoids unnecessary allocation).
 */
fun Bitmap.rotated(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

// ── Float formatting ──────────────────────────────────────────────────────

/** Formats a Float to one decimal place. Used in debug overlays. */
fun Float.toOneDecimal(): String = "%.1f".format(this)

/** Converts a confidence [0..1] to a percentage string, e.g. "87%" */
fun Float.toConfidencePercent(): String = "${(this * 100).toInt()}%"
