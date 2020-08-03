
package com.example.background

import android.app.Application
import timber.log.Timber
import timber.log.Timber.DebugTree

class BlurApplication() : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }
}
