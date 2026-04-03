package com.echonav.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echonav.services.EchoNavService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EchoNavViewModel(application: Application) : AndroidViewModel(application) {

    private var echoNavService: EchoNavService? = null

    private val _uiState = MutableStateFlow(EchoNavUiState())
    val uiState: StateFlow<EchoNavUiState> = _uiState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as EchoNavService.LocalBinder
            echoNavService = localBinder.getService()
            
            // Sync state from Service to ViewModel UI
            viewModelScope.launch {
                echoNavService?.uiState?.collect { state ->
                    _uiState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            echoNavService = null
        }
    }

    init {
        // Start Foreground Service so it doesn't die when app leaves foreground
        val serviceIntent = Intent(application, EchoNavService::class.java)
        ContextCompat.startForegroundService(application, serviceIntent)
        
        // Bind to service to allow communication
        application.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (e: Exception) {
            // Ignore if already unbound
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        // We use a slight delay or cache this if service isn't bound yet.
        // For simplicity, update directly. If it fails, standard state handles it.
        echoNavService?.setCameraPermissionGranted(granted) ?: run {
            // Unbound edge case: update local UI state immediately, then service when it connects
            _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
        }
    }

    fun onTap() {
        echoNavService?.toggleDetection()
    }

    fun onLongPress() {
        echoNavService?.switchMode()
    }
}
