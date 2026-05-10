package com.translator.app.di

import com.translator.app.data.AndroidAudioEngine
import com.translator.app.data.GeminiLiveClient
import com.translator.app.domain.AudioEngine
import com.translator.app.domain.LiveClient
import com.translator.app.learn.core.LearnScope
import com.translator.app.util.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Learn-специфичные инстансы LiveClient и AudioEngine.
 * Один клиент обслуживает все сессии, включая translator
 * (input + output audio transcription включены для translator).
 */
@Module
@InstallIn(SingletonComponent::class)
object LearnModule {

    @Provides
    @Singleton
    @LearnScope
    fun provideLearnLiveClient(logger: AppLogger): LiveClient =
        GeminiLiveClient(logger)

    @Provides
    @Singleton
    @LearnScope
    fun provideLearnAudioEngine(logger: AppLogger): AudioEngine =
        AndroidAudioEngine(logger)
}