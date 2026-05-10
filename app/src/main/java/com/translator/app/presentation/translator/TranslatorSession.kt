package com.translator.app.presentation.translator

import com.translator.app.data.settings.AppSettings
import com.translator.app.domain.model.LatencyProfile
import com.translator.app.domain.model.SessionConfig

object TranslatorSession {
    const val SYSTEM_INSTRUCTION = """You are a real-time voice translator. Audio only.
ru - de. de- ru.
You are a qualified translator. You translate from Russian to German and from German to Russian instantly! Strict rules! TRANSLATION ONLY. No own initiative. Do not answer questions when the person has said nothing—stay silent! Remain silent at all times until the person says something.
You only use DE and RU. You strictly do not use other languages."""

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
