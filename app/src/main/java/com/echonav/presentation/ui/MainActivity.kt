package com.echonav.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.echonav.presentation.ui.theme.EchoNavTheme
import com.echonav.presentation.viewmodel.EchoNavViewModel

/**
 * MainActivity — the single Activity in EchoNav.
 *
 * Responsibilities:
 *   1. Keep screen on (critical for navigation use case)
 *   2. Handle CAMERA permission request lifecycle
 *   3. Route between PermissionScreen and EchoNavScreen
 *   4. Host the single ViewModel instance
 *
 * Architecture note: We deliberately keep this class thin.
 * All business logic lives in EchoNavViewModel.
 */
class MainActivity : ComponentActivity() {

    // ViewModel scoped to this Activity's lifecycle
    private val viewModel: EchoNavViewModel by viewModels()

    // Permission launcher using the modern Activity Result API
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on — users can't tap to wake if they can't see the screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Check permission state immediately (it may already be granted)
        checkAndRequestCameraPermission()

        setContent {
            EchoNavTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()

                    if (state.hasCameraPermission) {
                        // Main navigation screen
                        EchoNavScreen(viewModel = viewModel)
                    } else {
                        // Ask for camera permission before showing camera UI
                        PermissionScreen(
                            onRequestPermission = { requestCameraPermission() }
                        )
                    }
                }
            }
        }
    }

    /**
     * Checks if permission is already granted. If yes, notifies ViewModel immediately.
     * If not, proceeds to the permission request flow.
     */
    private fun checkAndRequestCameraPermission() {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            viewModel.onCameraPermissionResult(true)
        } else {
            // Not granted yet — wait for user to tap the button on PermissionScreen
            viewModel.onCameraPermissionResult(false)
        }
    }

    /**
     * Fires the system permission dialog.
     * Called from PermissionScreen's button.
     */
    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
}
