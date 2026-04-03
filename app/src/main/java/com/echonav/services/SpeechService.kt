package com.echonav.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * SpeechService manages the Android TextToSpeech engine for EchoNav.
 *
 * Design decisions:
 * - Single TTS instance per app lifecycle to avoid latency from repeated init.
 * - Uses QUEUE_FLUSH for urgent/danger messages, QUEUE_ADD for routine ones.
 *   This ensures hazard warnings interrupt whatever is currently being spoken.
 * - Speech rate is slightly faster than default (1.1×) for real-time feedback.
 * - Pitch is slightly lower (0.9) for a calmer, more distinct voice.
 */
class SpeechService(private val context: Context) {

    companion object {
        private const val TAG = "SpeechService"
        private const val SPEECH_RATE  = 0.95f   // More natural conversational speed
        private const val SPEECH_PITCH = 1.0f    // Natural pitch
    }

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)

    /**
     * Initializes the TTS engine.
     * Must be called before any speak() calls.
     * Returns true if TTS is ready, false on failure.
     *
     * This is a suspend function that awaits TTS initialization asynchronously.
     */
    suspend fun initialize(): Boolean = suspendCoroutine { continuation ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "TTS language not supported. Trying default locale.")
                    tts?.setLanguage(Locale.getDefault())
                }

                // Try to find a high-quality natural female voice
                try {
                    val voices = tts?.voices
                    if (voices != null) {
                        val preferredVoice = voices.firstOrNull {
                            it.name.contains("en-us-x-tpf-local", ignoreCase = true) ||
                            (it.locale == Locale.US && it.name.contains("female", ignoreCase = true))
                        }
                        if (preferredVoice != null) {
                            tts?.voice = preferredVoice
                            Log.i(TAG, "Selected TTS voice: ${preferredVoice.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set custom voice: ${e.message}")
                }

                // Apply custom speaking rate and pitch for clarity
                tts?.setSpeechRate(SPEECH_RATE)
                tts?.setPitch(SPEECH_PITCH)

                // Listener for debugging (optional — remove in release)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS error for utterance: $utteranceId")
                    }
                })

                isReady.set(true)
                Log.i(TAG, "TTS initialized successfully")
                continuation.resume(true)
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isReady.set(false)
                continuation.resume(false)
            }
        }
    }

    /**
     * Speaks the given text.
     *
     * @param text      The string to speak.
     * @param interrupt If true, flushes any currently-speaking utterance first.
     *                  Use interrupt=true for danger/hazard warnings.
     *                  Use interrupt=false for routine ambient announcements.
     */
    fun speak(text: String, interrupt: Boolean = false) {
        if (!isReady.get()) {
            Log.w(TAG, "TTS not ready, skipping: $text")
            return
        }

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = UUID.randomUUID().toString()

        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Stops any current TTS playback immediately.
     * Call when detection is paused or mode is switched.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Returns true if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Releases the TTS engine. Must be called in onDestroy.
     * After shutdown, initialize() must be called again to reuse.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady.set(false)
        Log.i(TAG, "TTS shut down")
    }
}
