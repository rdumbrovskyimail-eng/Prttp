package com.prttp.app.di

import com.prttp.app.data.AndroidAudioEngine
import com.prttp.app.data.GeminiLiveClient
import com.prttp.app.domain.AudioEngine
import com.prttp.app.domain.LiveClient
import com.prttp.app.util.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLiveClient(logger: AppLogger): LiveClient = GeminiLiveClient(logger)

    @Provides
    @Singleton
    fun provideAudioEngine(logger: AppLogger): AudioEngine = AndroidAudioEngine(logger)
}