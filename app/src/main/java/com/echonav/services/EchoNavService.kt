package com.echonav.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.echonav.data.model.AppMode
import com.echonav.data.model.DetectedObject
import com.echonav.data.repository.ObjectDetectorRepository
import com.echonav.domain.usecase.BuildAnnouncementUseCase
import com.echonav.domain.usecase.DirectionUseCase
import com.echonav.domain.usecase.DistanceUseCase
import com.echonav.domain.usecase.FeedbackFilterUseCase
import com.echonav.presentation.ui.MainActivity
import com.echonav.presentation.ui.EchoNavImageAnalyzer
import com.echonav.presentation.viewmodel.EchoNavUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class EchoNavService : LifecycleService() {

    companion object {
        private const val TAG = "EchoNavService"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "EchoNav_Service_Channel"
    }

    // Binder for ViewModel
    inner class LocalBinder : Binder() {
        fun getService(): EchoNavService = this@EchoNavService
    }
    private val binder = LocalBinder()

    // ── Services & UseCases ────────────────────────────────────────────────
    private lateinit var speechService: SpeechService
    private lateinit var hapticService: HapticService
    private lateinit var repository: ObjectDetectorRepository
    private val directionUseCase = DirectionUseCase()
    private val distanceUseCase = DistanceUseCase()
    private val feedbackFilter = FeedbackFilterUseCase()
    private val buildAnnouncement = BuildAnnouncementUseCase(directionUseCase, distanceUseCase)

    // ── CameraX ────────────────────────────────────────────────────────────
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    // ── UI State ─────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(EchoNavUiState())
    val uiState: StateFlow<EchoNavUiState> = _uiState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        speechService = SpeechService(this)
        hapticService = HapticService(this)
        repository = ObjectDetectorRepository(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        initializeServices()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun initializeServices() {
        lifecycleScope.launch(Dispatchers.IO) {
            val ttsReady = speechService.initialize()
            val modelReady = repository.initialize()

            _uiState.update { it.copy(isModelReady = modelReady) }

            if (ttsReady && modelReady) {
                speechService.speak("Welcome to EchoNav. Tap anywhere to start navigation.", interrupt = false)
            } else if (ttsReady) {
                speechService.speak("Initialization failed. Please check model file.", interrupt = false)
            }
        }
    }

    fun setCameraPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (!granted) {
            speechService.speak("Camera permission is required.", interrupt = true)
        }
    }

    fun toggleDetection() {
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

    fun switchMode() {
        val nextMode = _uiState.value.currentMode.next()
        _uiState.update { it.copy(currentMode = nextMode) }
        feedbackFilter.reset()
        speechService.speak(nextMode.announcement(), interrupt = true)
        hapticService.awareness()
    }

    private fun startDetection() {
        feedbackFilter.reset()
        _uiState.update { it.copy(isDetecting = true, detectedObjects = emptyList()) }
        speechService.speak("Detection started.", interrupt = false)
        startCameraAnalysis()
    }

    private fun stopDetection() {
        _uiState.update { it.copy(isDetecting = false, detectedObjects = emptyList()) }
        feedbackFilter.reset()
        speechService.stop()
        hapticService.cancel()
        speechService.speak("Detection paused.", interrupt = false)
        cameraProvider?.unbind(androidx.camera.core.UseCase::class.java.cast(null)) // Generic unbind if needed, actually we just unbind analysis
        cameraProvider?.unbindAll() // Fallback unbind
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                // Downscale analysis input significantly to prevent memory leaks in background
                .setTargetResolution(android.util.Size(640, 480))
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor) { image ->
                        try {
                            val bitmap = com.echonav.utils.toArgbBitmap(image).let { bmp ->
                                com.echonav.utils.rotated(bmp, image.imageInfo.rotationDegrees.toFloat())
                            }
                            analyzeFrame(bitmap)
                        } catch (e: Exception) {
                            Log.w(TAG, "Analysis error: \${e.message}")
                        } finally {
                            image.close()
                        }
                    }
                }

            try {
                cameraProvider?.unbind(imageAnalysis) // only unbind analysis if already bound
                cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed: \${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(bitmap: Bitmap) {
        val state = _uiState.value
        if (!state.isDetecting || !state.isModelReady) return

        val result = repository.detect(bitmap)
        val objects = result.objects

        _uiState.update {
            it.copy(detectedObjects = objects, inferenceTimeMs = result.inferenceTimeMs)
        }

        val candidates = when (state.currentMode) {
            AppMode.EXPLORE -> objects
            AppMode.FOCUS   -> listOfNotNull(objects.maxByOrNull { it.normalizedArea })
            AppMode.SILENT  -> emptyList()
        }

        for (obj in candidates) {
            val distance = distanceUseCase.execute(obj)

            if (feedbackFilter.shouldAnnounce(obj, distance)) {
                val announcement = buildAnnouncement.execute(obj)
                _uiState.update { it.copy(lastAnnouncement = announcement) }

                val isDanger = distanceUseCase.isDanger(obj)
                speechService.speak(announcement, interrupt = isDanger)

                if (isDanger) hapticService.danger() else hapticService.awareness()
            }
        }

        if (state.currentMode == AppMode.SILENT) {
            objects.firstOrNull { distanceUseCase.isDanger(it) }?.let { danger ->
                val distance = distanceUseCase.execute(danger)
                if (feedbackFilter.shouldAnnounce(danger, distance)) {
                    hapticService.danger()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EchoNav Background Navigation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps EchoNav active while navigating."
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EchoNav is Active")
            .setContentText("Continuous object detection is running.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService.shutdown()
        repository.close()
        hapticService.cancel()
        analysisExecutor.shutdown()
    }
}
