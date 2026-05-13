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

            // Generation: дефолты Gemini 3 (temperature = 1.0!)
            temperature = settings.temperature,
            topP = settings.topP,
            topK = 0,  // Live API: не шлём, не нужно
            maxOutputTokens = settings.maxOutputTokens,

            // Voice
            voiceId = settings.voiceId,
            languageCode = "",  // gemini-3.1-flash-live-preview игнорирует это поле

            responseModality = "AUDIO",
            latencyProfile = latencyProfile,
            thinkingIncludeThoughts = settings.includeThoughts,

            // Transcription
            inputTranscription = settings.inputTranscription,
            outputTranscription = settings.outputTranscription,
            transcriptionLanguageCodes = settings.transcriptionLanguageCodes,

            // VAD (translator-tuned)
            autoActivityDetection = settings.enableServerVad,
            vadStartSensitivity = settings.vadStartSensitivity,
            vadEndSensitivity = settings.vadEndSensitivity,
            vadSilenceDurationMs = settings.vadSilenceDurationMs,
            vadPrefixPaddingMs = settings.vadPrefixPaddingMs,
            activityHandling = settings.activityHandling,
            turnCoverage = settings.turnCoverage,

            sendAudioStreamEnd = true,

            systemInstruction = SYSTEM_INSTRUCTION,

            // Session: ВКЛЮЧАЕМ resumption и compression — это best practice
            enableSessionResumption = settings.enableSessionResumption,
            sendSessionResumptionConfig = settings.enableSessionResumption,
            enableContextCompression = settings.enableContextCompression,
            sendContextCompressionConfig = settings.enableContextCompression,
            compressionTriggerTokens = settings.compressionTriggerTokens,
            compressionTargetTokens = settings.compressionTargetTokens,

            sendTranscriptionConfig = true,
            enableGoogleSearch = false,
            functionDeclarations = emptyList(),

            // Debug
            logFullSetupJson = settings.logRawWebSocketFrames
        )
    }
}