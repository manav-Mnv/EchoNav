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

    // AndroidView wraps the classic View-based PreviewView in Compose
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind/unbind camera preview when composable enters/leaves composition
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var previewUseCase: Preview? = null

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Preview use case — renders camera feed to PreviewView
                previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // We do NOT unbindAll() because EchoNavService might have ImageAnalysis running.
                // We just bind our Preview use case.
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase
                )

                Log.i("CameraPreviewView", "Preview bound successfully")

            } catch (e: Exception) {
                Log.e("CameraPreviewView", "Camera binding failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            // Unbind the preview when the view goes away
            previewUseCase?.let { cameraProvider?.unbind(it) }
            Log.i("CameraPreviewView", "Preview unbound")
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
