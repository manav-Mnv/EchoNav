package com.echonav.presentation.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.echonav.presentation.viewmodel.EchoNavViewModel
import java.util.concurrent.Executors

/**
 * CameraPreviewView hosts a CameraX PreviewView inside Jetpack Compose.
 *
 * This composable sets up the full CameraX pipeline:
 *   1. Preview use case   → shows live viewfinder
 *   2. ImageAnalysis use case → feeds frames to TFLite analyzer
 *
 * The camera is automatically released via DisposableEffect when
 * this composable leaves the composition (e.g., screen off, app backgrounded).
 *
 * Camera configuration:
 *   - Back-facing lens (user points phone forward from shirt pocket)
 *   - STRATEGY_KEEP_ONLY_LATEST drops frames if analysis is slow
 *     (prevents a queue buildup that would introduce lag)
 *   - Single background thread for analysis executor
 */
@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    viewModel: EchoNavViewModel,
    isDetecting: Boolean
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Single-thread executor for image analysis — one frame at a time
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer         = remember { EchoNavImageAnalyzer(viewModel) }

    // AndroidView wraps the classic View-based PreviewView in Compose
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind/unbind camera when composable enters/leaves composition
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Preview use case — renders camera feed to PreviewView
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // ImageAnalysis use case — feeds frames to TFLite
                val imageAnalysis = ImageAnalysis.Builder()
                    // Drop frames if analysis thread is busy — prevents backlog
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // RGB_565 or YUV_420_888 — let CameraX choose optimal
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, analyzer)
                    }

                // Use back camera — phone is pointing forward from pocket
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind any previous bindings before rebinding
                cameraProvider.unbindAll()

                // Bind to lifecycle — camera auto-starts/stops with app lifecycle
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.i("CameraPreviewView", "Camera bound successfully")

            } catch (e: Exception) {
                Log.e("CameraPreviewView", "Camera binding failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            // Shut down analysis executor when composable leaves composition
            analysisExecutor.shutdown()
            Log.i("CameraPreviewView", "Analysis executor shut down")
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
