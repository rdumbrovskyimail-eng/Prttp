package com.learnde.app.di

import com.learnde.app.data.AndroidAudioEngine
import com.learnde.app.data.GeminiLiveClient
import com.learnde.app.domain.AudioEngine
import com.learnde.app.domain.LiveClient
import com.learnde.app.learn.core.LearnScope
import com.learnde.app.learn.core.TranscriberScope
import com.learnde.app.util.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Learn-специфичные инстансы LiveClient и AudioEngine.
 *
 * Два независимых LiveClient:
 *  • @LearnScope       — основной voice-клиент (translator, a1, и т.д.)
 *  • @TranscriberScope — параллельный text-клиент для транскрипта translator-сессии
 *
 * AudioEngine один на оба клиента — микрофон шарится через fan-out.
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

    @Provides
    @Singleton
    @TranscriberScope
    fun provideTranscriberLiveClient(logger: AppLogger): LiveClient =
        GeminiLiveClient(logger)
}