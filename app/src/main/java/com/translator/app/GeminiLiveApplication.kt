package com.translator.app

import android.app.Application
import com.translator.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GeminiLiveApplication : Application() {

    @Inject lateinit var appLogger: AppLogger

    override fun onCreate() {
        super.onCreate()
        
        // Инициализируем логгер
        appLogger.init()
        appLogger.d("=== APP STARTED (Gemini Translate) ===")
    }
}