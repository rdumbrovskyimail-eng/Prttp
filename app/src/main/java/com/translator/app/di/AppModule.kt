package com.translator.app.di

import com.translator.app.data.AndroidAudioEngine
import com.translator.app.data.GeminiLiveClient
import com.translator.app.data.PersistentConversationRepository
import com.translator.app.domain.AudioEngine
import com.translator.app.domain.ConversationRepository
import com.translator.app.domain.LiveClient
import com.translator.app.learn.core.VoiceScope
import com.translator.app.util.AppLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.content.Context

/**
 * Биндинг абстракций на реализации (только @Binds — должен быть abstract class).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: PersistentConversationRepository
    ): ConversationRepository
}

/**
 * Voice-специфичные инстансы (@Provides — должен быть object).
 * Квалификатор @VoiceScope отделяет их от @LearnScope.
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceProvidesModule {

    @Provides
    @Singleton
    @VoiceScope
    fun provideVoiceLiveClient(
        logger: AppLogger
    ): LiveClient = GeminiLiveClient(logger)

    @Provides
    @Singleton
    @VoiceScope
    fun provideVoiceAudioEngine(
        logger: AppLogger
    ): AudioEngine = AndroidAudioEngine(logger)

}