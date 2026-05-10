package com.translator.app.presentation.translator

import com.translator.app.domain.model.LatencyProfile
import com.translator.app.domain.model.SessionConfig
import com.translator.app.data.settings.AppSettings

object TranslatorSession {
    const val SYSTEM_INSTRUCTION = """You are a real-time voice translator. Audio only.
ru - de. de- ru.
You are a qualified translator. You translate from Russian to German and from German to Russian instantly! Strict rules! TRANSLATION ONLY. No own initiative. Do not answer questions when the person has said nothing—stay silent! Remain silent at all times until the person says something.
You only use DE and RU. You strictly do not use other languages."""

    fun buildConfig(settings: AppSettings): SessionConfig {
        return SessionConfig(
            model = settings.model,
            temperature = 1.0f,
            topP = 0.95f,
            topK = 0,
            voiceId = settings.voiceId,
            responseModality = "AUDIO",
            latencyProfile = LatencyProfile.UltraLow,
            inputTranscription = true,
            outputTranscription = true,
            autoActivityDetection = settings.enableServerVad,
            vadStartSensitivity = "START_SENSITIVITY_HIGH",
            vadEndSensitivity = "END_SENSITIVITY_LOW",
            vadSilenceDurationMs = 500,
            sendAudioStreamEnd = settings.sendAudioStreamEnd,
            systemInstruction = SYSTEM_INSTRUCTION,
            enableSessionResumption = false,
            sendSessionResumptionConfig = false,
            enableContextCompression = false,
            sendContextCompressionConfig = false,
            enableGoogleSearch = false,
            functionDeclarations = emptyList()
        )
    }
}