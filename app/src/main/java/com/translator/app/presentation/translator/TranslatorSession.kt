package com.translator.app.presentation.translator

import com.translator.app.data.settings.AppSettings
import com.translator.app.domain.model.LatencyProfile
import com.translator.app.domain.model.SessionConfig

object TranslatorSession {
    const val SYSTEM_INSTRUCTION = """Ты профессиональный переводчик, отвечаешь мгновенно. Если к тебе говорят на русском, ты переводишь фразу или слово на немецкий. Если к тебе говорят на немецком, ты переводишь фразу или слово на русский. Больше ничего нет! Только молниеносный корректный перевод. Не отвечай человеку, не думай ничего лишнего. 

Ru - De . De - Ru ."""

    fun buildConfig(settings: AppSettings): SessionConfig {
        val latencyProfile = runCatching {
            LatencyProfile.valueOf(settings.latencyProfile)
        }.getOrDefault(LatencyProfile.UltraLow)

        return SessionConfig(
            model = settings.model,
            // Translator-fixed как в старой версии (быстрая реакция, минимум отсебятины)
            temperature = 1.0f,
            topP = 0.95f,
            topK = 0,
            maxOutputTokens = 8192,
            voiceId = settings.voiceId,
            languageCode = "",  // Translator: пусто — Gemini сам определяет язык RU/DE
            responseModality = "AUDIO",
            latencyProfile = latencyProfile,
            inputTranscription = settings.inputTranscription,
            outputTranscription = settings.outputTranscription,
            autoActivityDetection = settings.enableServerVad,
            // Translator-tuned VAD: HIGH старт (быстро ловит тихую речь),
            // LOW конец (даёт договорить), 500ms silence, 150ms prefix.
            vadStartSensitivity = settings.vadStartSensitivity,
            vadEndSensitivity = settings.vadEndSensitivity,
            vadSilenceDurationMs = settings.vadSilenceDurationMs,
            vadPrefixPaddingMs = 150,  // КРИТИЧНО для скорости: 150мс preroll
            sendAudioStreamEnd = settings.sendAudioStreamEnd,
            systemInstruction = SYSTEM_INSTRUCTION,
            // Translator: НЕ шлём resumption/compression блоки — они только увеличивают latency
            enableSessionResumption = false,
            transparentResumption = false,
            sendSessionResumptionConfig = false,
            enableContextCompression = false,
            sendContextCompressionConfig = false,
            sendTranscriptionConfig = true,
            enableGoogleSearch = false,
            functionDeclarations = emptyList(),
            logFullSetupJson = settings.logRawWebSocketFrames
        )
    }
}