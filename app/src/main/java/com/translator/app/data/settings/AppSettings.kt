// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА
// Путь: app/src/main/java/com/translator/app/data/settings/AppSettings.kt
//
// Возвращены ВСЕ настройки старой версии, кроме Learn-специфичных.
// ═══════════════════════════════════════════════════════════
package com.translator.app.data.settings

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { AUTO, LIGHT, DARK }

@Serializable
data class AppSettings(

    // ═══════════════════ 1. AUTH ═══════════════════
    val apiKey: String = "",
    val apiKeyBackup: String = "",
    val autoRotateKeys: Boolean = false,

    // ═══════════════════ 2. MODEL ═══════════════════
    val model: String = "models/gemini-3.1-flash-live-preview",
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 0,
    val maxOutputTokens: Int = 8192,
    val responseModality: String = "AUDIO",

    // ═══════════════════ 3. VOICE ═══════════════════
    val voiceId: String = "Puck",
    val languageCode: String = "",

    // ═══════════════════ 4. AUDIO ═══════════════════
    val useAec: Boolean = true,
    val jitterPreBufferChunks: Int = 3,
    val jitterTimeoutMs: Long = 150L,
    val playbackQueueCapacity: Int = 256,
    val sendAudioStreamEnd: Boolean = true,

    /** Громкость АИ от 0 до 100 (передаётся как 0..1 в AudioTrack.setVolume). */
    val playbackVolume: Int = 100,

    /** Чувствительность микрофона 50..200% (передаётся как 0.5..2.0 в AGC). */
    val micGain: Int = 100,

    /** Принудительное использование громкоговорителя (FGS). */
    val forceSpeakerOutput: Boolean = true,

    /**
     * Программное усиление штатного динамика (boost output).
     * 1.0 = без буста, 1.6 = +60% (по умолчанию — мощный, но не клипует речь),
     * 2.0 = максимум.
     * Применяется до AudioTrack.write() с soft-clip защитой.
     */
    val playbackBoost: Float = 1.6f,

    // ═══════════════════ 5. SESSION / RECONNECT ═══════════════════
    val enableSessionResumption: Boolean = false,
    val transparentResumption: Boolean = false,
    val enableContextCompression: Boolean = false,
    val maxReconnectAttempts: Int = 5,
    val reconnectBaseDelayMs: Long = 2000L,
    val reconnectMaxDelayMs: Long = 30000L,

    // ═══════════════════ 6. VAD ═══════════════════
    val enableServerVad: Boolean = true,
    val vadStartSensitivity: String = "START_SENSITIVITY_HIGH",
    val vadEndSensitivity: String = "END_SENSITIVITY_LOW",
    val vadSilenceDurationMs: Int = 500,
    val vadPrefixPaddingMs: Int = 20,

    // ═══════════════════ 7. TRANSCRIPTION ═══════════════════
    val inputTranscription: Boolean = true,
    val outputTranscription: Boolean = true,

    // ═══════════════════ 8. THINKING / LATENCY ═══════════════════
    /** Off | UltraLow | Low | Balanced | Reasoning */
    val latencyProfile: String = "UltraLow",

    // ═══════════════════ 9. UI / THEME ═══════════════════
    val themeMode: ThemeMode = ThemeMode.AUTO,
    /** ID выбранной темы: AURORA | BERLIN_MIST | SAKURA | OBSIDIAN. По умолчанию Aurora. */
    val themeId: String = "AURORA",
    val messageRevealId: String = "SOFT_FADE",

    // ═══════════════════ 10. DEBUG ═══════════════════
    val showDebugLog: Boolean = true,
    val logRawWebSocketFrames: Boolean = false,
    val showUsageMetadata: Boolean = false
)
