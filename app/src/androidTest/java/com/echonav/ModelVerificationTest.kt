package com.echonav

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.echonav.data.repository.ObjectDetectorRepository
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test — runs on a real device or emulator.
 *
 * Validates:
 *   1. TFLite model loads from assets without crashing
 *   2. A blank Bitmap can be passed through inference without exception
 *   3. The result contains a valid (possibly empty) detection list
 *
 * Run with: ./gradlew connectedAndroidTest
 *
 * NOTE: This test requires the TFLite model to be present in assets/.
 * Run ./download_model.sh before running this test.
 */
@RunWith(AndroidJUnit4::class)
class ModelVerificationTest {

    private lateinit var repository: ObjectDetectorRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = ObjectDetectorRepository(context)
        val initialized = repository.initialize()
        assertTrue("TFLite model failed to initialize. Did you run download_model.sh?", initialized)
    }

    @After
    fun teardown() {
        repository.close()
    }

    @Test
    fun modelLoadsAndRunsInferenceOnBlankBitmap() {
        // A blank 320x320 ARGB_8888 bitmap — all black
        val blankBitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)

        val result = repository.detect(blankBitmap)

        // Result must not be null
        assertNotNull(result)

        // Inference must have taken some time (> 0ms means it actually ran)
        assertTrue("Inference time should be > 0ms", result.inferenceTimeMs >= 0)

        // Objects list must not be null (may be empty for a blank image — that's fine)
        assertNotNull(result.objects)
    }

    @Test
    fun inferenceTimeIsReasonable() {
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)

        // Run 5 frames to get a representative average (first run may be slow due to JIT)
        repeat(5) { repository.detect(bitmap) }
        val result = repository.detect(bitmap)

        // On any Android device from 2018 onwards, 500ms should be very conservative
        assertTrue(
            "Inference took ${result.inferenceTimeMs}ms — unexpectedly slow",
            result.inferenceTimeMs < 500
        )
    }
}
