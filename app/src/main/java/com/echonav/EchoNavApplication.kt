package com.echonav

import android.app.Application

/**
 * Application entry point for EchoNav.
 * Kept minimal — no dependency injection framework needed at this scale.
 * All heavy initialization happens lazily in the ViewModel layer.
 */
class EchoNavApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Nothing to eagerly initialize — keep startup fast for accessibility
    }
}
