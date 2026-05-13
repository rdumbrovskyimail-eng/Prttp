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
    // 5.1: topK удалён — сомнительная поддержка в Live API
    val maxOutputTokens: Int = 8192,
    val responseModality: String = "AUDIO",

    // ═══════════════════ 3. VOICE ═══════════════════
    // 5.2: дефолт изменён с "Puck" на "Aoede" (warm, female, лучше для перевода)
    val voiceId: String = "Aoede",
    // 5.3: languageCode заменён на outputLanguageHint
    /**
     * Output language hint для speechConfig.languageCode.
     * ВНИМАНИЕ: gemini-3.1-flash-live-preview игнорирует это поле,
     * выбирает язык автоматически. Оставлено только для legacy моделей.
     */
    val outputLanguageHint: String = "",

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
    // 5.7: оба включены по дефолту согласно best practices
    val enableSessionResumption: Boolean = true,
    // 5.8: transparentResumption удалён — не существует в официальной спецификации
    val enableContextCompression: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val reconnectBaseDelayMs: Long = 2000L,
    val reconnectMaxDelayMs: Long = 30000L,

    // 5.6: context window compression токены
    /** Триггер сжатия в токенах. 0 = использовать дефолт API (80% от лимита). */
    val compressionTriggerTokens: Long = 25_600L,

    /** Цель сжатия в токенах. 0 = использовать дефолт (trigger/2). */
    val compressionTargetTokens: Long = 12_800L,

    // ═══════════════════ 6. VAD ═══════════════════
    // 5.5: дефолты обновлены для translator-оптимизации
    val enableServerVad: Boolean = true,
    val vadStartSensitivity: String = "START_SENSITIVITY_HIGH",
    val vadEndSensitivity: String = "END_SENSITIVITY_HIGH",
    val vadSilenceDurationMs: Int = 400,
    val vadPrefixPaddingMs: Int = 20,

    // 5.4: новые поля VAD по спецификации
    /** Что входит в "ход" — TURN_INCLUDES_ONLY_ACTIVITY экономит токены */
    val turnCoverage: String = "TURN_INCLUDES_ONLY_ACTIVITY",

    /** Поведение при start-of-speech */
    val activityHandling: String = "START_OF_ACTIVITY_INTERRUPTS",

    // ═══════════════════ 7. TRANSCRIPTION ═══════════════════
    val inputTranscription: Boolean = true,
    val outputTranscription: Boolean = true,

    // 5.10: подсказка ASR для RU↔DE перевода
    /**
     * Список BCP-47 кодов для подсказки ASR.
     * Translator RU↔DE: ["ru", "de"] помогает быстрее определять язык.
     * Пустой = автоопределение.
     */
    val transcriptionLanguageCodes: List<String> = listOf("ru", "de"),

    // ═══════════════════ 8. THINKING / LATENCY ═══════════════════
    /** Off | UltraLow | Low | Balanced | Reasoning */
    val latencyProfile: String = "UltraLow",

    // 5.9: включение thought summaries в ответ
    /** Включить включение thought summaries в ответ (тратит токены) */
    val includeThoughts: Boolean = false,

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