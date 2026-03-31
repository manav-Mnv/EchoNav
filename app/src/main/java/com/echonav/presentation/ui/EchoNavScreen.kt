package com.echonav.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echonav.data.model.AppMode
import com.echonav.presentation.viewmodel.EchoNavViewModel

/**
 * EchoNavScreen — the entire visible UI of the app.
 *
 * Design philosophy:
 *   - Near-black background to save OLED battery and reduce visual distraction.
 *   - Minimal text — the app communicates via voice, not UI.
 *   - Large, full-screen tap target — easy for non-visual users.
 *   - Debug info shown only in a subtle bottom bar (would be hidden in production).
 *   - All interactive elements have contentDescription for TalkBack support.
 *
 * The camera preview fills the entire screen behind the overlay.
 * Touch gesture layer sits on top and handles tap / long-press.
 */
@Composable
fun EchoNavScreen(viewModel: EchoNavViewModel) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Near-black
    ) {
        // ── Camera Preview ─────────────────────────────────────────────────
        // Always shown if permission is granted, even when detection is paused
        if (state.hasCameraPermission) {
            CameraPreviewView(
                modifier     = Modifier.fillMaxSize(),
                viewModel    = viewModel,
                isDetecting  = state.isDetecting
            )
        }

        // ── Dark overlay to dim camera preview ─────────────────────────────
        // Keeps UI readable and reduces distracting visual noise
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x88000000)) // 53% black overlay
        )

        // ── Touch Interaction Layer ─────────────────────────────────────────
        // Full-screen tap target — accessible and easy to hit
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (state.isDetecting)
                        "Navigation active. Tap to stop. Long press to switch mode."
                    else
                        "Navigation paused. Tap to start. Long press to switch mode."
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap       = { viewModel.onTap() },
                        onLongPress = { viewModel.onLongPress() }
                    )
                }
        )

        // ── Center Status Indicator ─────────────────────────────────────────
        Column(
            modifier          = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pulse indicator circle — filled when active
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isDetecting) Color(0xFF00E5FF)  // Cyan when active
                        else Color(0xFF424242)                      // Dark grey when idle
                    )
                    .alpha(if (state.isDetecting) 1f else 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status text
            Text(
                text = when {
                    !state.isModelReady           -> "Loading…"
                    !state.hasCameraPermission    -> "Camera permission needed"
                    state.isDetecting             -> "Navigating"
                    else                          -> "Tap to start"
                },
                color     = Color.White,
                fontSize  = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Mode label
            Text(
                text      = "Mode: ${state.currentMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                color     = Color(0xFFB0BEC5), // Cool grey
                fontSize  = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // ── Top Mode Badge ──────────────────────────────────────────────────
        ModeIndicator(
            mode     = state.currentMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp)
        )

        // ── Bottom Debug Bar ───────────────────────────────────────────────
        // Shows last announcement and inference time — useful during testing.
        // In a release build you could hide this behind a BuildConfig.DEBUG check.
        DebugBar(
            lastAnnouncement = state.lastAnnouncement,
            inferenceTimeMs  = state.inferenceTimeMs,
            objectCount      = state.detectedObjects.size,
            modifier         = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Small pill badge showing the current mode at the top of the screen.
 */
@Composable
fun ModeIndicator(mode: AppMode, modifier: Modifier = Modifier) {
    val (color, label) = when (mode) {
        AppMode.EXPLORE -> Pair(Color(0xFF00E5FF), "EXPLORE")
        AppMode.FOCUS   -> Pair(Color(0xFFFFD740), "FOCUS")
        AppMode.SILENT  -> Pair(Color(0xFF78909C), "SILENT")
    }

    Surface(
        modifier  = modifier,
        color     = color.copy(alpha = 0.15f),
        shape     = MaterialTheme.shapes.small,
        tonalElevation = 0.dp
    ) {
        Text(
            text      = label,
            color     = color,
            fontSize  = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier  = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * Debug info bar along the bottom edge.
 * Shows last announcement and performance metrics.
 */
@Composable
fun DebugBar(
    lastAnnouncement: String,
    inferenceTimeMs: Long,
    objectCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier          = modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (lastAnnouncement.isNotEmpty()) {
            Text(
                text      = "» $lastAnnouncement",
                color     = Color(0xFF80CBC4),
                fontSize  = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines  = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Text(
            text      = buildString {
                if (objectCount > 0) append("$objectCount obj  ")
                if (inferenceTimeMs > 0) append("${inferenceTimeMs}ms")
            }.ifEmpty { "Waiting for objects…" },
            color     = Color(0xFF546E7A),
            fontSize  = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
