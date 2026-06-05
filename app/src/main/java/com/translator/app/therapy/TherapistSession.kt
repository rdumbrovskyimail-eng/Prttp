// Путь: app/src/main/java/com/translator/app/therapy/TherapistSession.kt
//
// «Мозг» пси-ассистента: системная инструкция + сборка SessionConfig.
//
// КЛЮЧЕВАЯ ИДЕЯ «мгновенно считывает базу при каждом подключении»:
//   при каждом connect() мы инжектим актуальный профиль + свежие записи
//   дневника прямо в systemInstruction. Это дёшево и моментально — модель
//   видит контекст пациента с первой секунды, без round-trip'а на чтение.
//   Для глубоких деталей у неё дополнительно есть read-инструменты.
//
// Конфиг сознательно ОТЛИЧАЕТСЯ от переводчика:
//   • thinkingLevel выше (нужно клиническое рассуждение, не скорость);
//   • temperature умеренная (живая, но не разболтанная речь);
//   • barge-in мягкий, длинные паузы разрешены (человек думает/плачет);
//   • toolsJson подключён.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.therapy

import com.translator.app.domain.model.JournalEntry
import com.translator.app.domain.model.LatencyProfile
import com.translator.app.domain.model.PatientProfile
import com.translator.app.domain.model.SessionConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TherapistSession {

    /**
     * Собирает конфиг сессии ассистента.
     *
     * @param profile текущий профиль (инжектится в промпт)
     * @param recentJournal последние записи дневника (инжектятся в промпт)
     * @param voiceId голос (можно дать тёплый — напр. "Aoede"/"Kore")
     * @param model id модели Live
     */
    fun buildConfig(
        profile: PatientProfile,
        recentJournal: List<JournalEntry>,
        voiceId: String = "Aoede",
        model: String = SessionConfig.DEFAULT_MODEL
    ): SessionConfig = SessionConfig(
        model = model,
        responseModality = "AUDIO",
        // Живо, эмпатично, но не выдумчиво.
        temperature = 0.6f,
        topP = 0.95f,
        // Голос терапевта длиннее реплики переводчика — даём запас.
        maxOutputTokens = 8192,
        voiceId = voiceId,

        // ВАЖНО (доки 3.1 Live, июнь 2026): function calling СИНХРОННЫЙ —
        // модель замирает, пока ждёт ответа инструмента. Чтобы голос не «тормозил»
        // на каждый вызов update_profile/log_mood, держим лёгкое «мышление» (Low):
        // отзывчиво в живом разговоре, но всё ещё с клиническим рассуждением.
        // Для более глубокого анализа можно поднять до Balanced ценой задержки.
        latencyProfile = LatencyProfile.Low,
        thinkingIncludeThoughts = false,

        // VAD: человек делает паузы, молчит, плачет — НЕ перебиваем агрессивно.
        autoActivityDetection = true,
        vadStartSensitivity = "START_SENSITIVITY_LOW",
        vadEndSensitivity = "END_SENSITIVITY_LOW",
        vadPrefixPaddingMs = 300,
        vadSilenceDurationMs = 1200,
        activityHandling = "NO_INTERRUPTION",
        turnCoverage = "TURN_INCLUDES_ONLY_ACTIVITY",

        systemInstruction = buildSystemInstruction(profile, recentJournal),

        inputTranscription = true,
        outputTranscription = true,

        enableSessionResumption = true,
        enableContextCompression = true,

        // ПОДКЛЮЧЕНИЕ ИНСТРУМЕНТОВ (см. патч SessionConfig + buildFullSetup
        // в TherapistTools.kt). Поле добавляется в SessionConfig.
        toolsJson = TherapistTools.declarations()
    )

    // ─────────────────────────────────────────────────────────────────────
    //  СИСТЕМНАЯ ИНСТРУКЦИЯ
    // ─────────────────────────────────────────────────────────────────────

    private fun buildSystemInstruction(
        profile: PatientProfile,
        journal: List<JournalEntry>
    ): String = buildString {
        append(TherapistClinicalCore.PROMPT)   // усиленный клинический промпт
        append("\n\n")
        append(renderProfile(profile))         // память пациента — инжект
        append("\n\n")
        append(renderJournal(journal))         // дневник — инжект
    }


    private fun renderProfile(p: PatientProfile): String {
        if (p.facts.isEmpty() && p.sessionNotes.isEmpty() && p.openHomework.isEmpty() &&
            p.displayName.isBlank() && p.moodLogs.isEmpty()
        ) {
            return "═══ ПРОФИЛЬ ПАЦИЕНТА ═══\n(Пусто — это первая встреча. Начни с тёплого знакомства, " +
                "узнай, как к человеку обращаться и что привело.)"
        }
        return buildString {
            append("═══ ПРОФИЛЬ ПАЦИЕНТА (твоя память) ═══\n")
            if (p.displayName.isNotBlank()) append("Обращение: ${p.displayName}\n")

            if (p.facts.isNotEmpty()) {
                append("\nФакты:\n")
                p.facts.sortedByDescending { it.updatedAt }.take(40).forEach {
                    append("• [${it.category}] ${it.key}: ${it.value}\n")
                }
            }
            p.activeRisk?.let {
                append("\n⚠ Активный клинический флаг: ${it.level} — ${it.reason}\n")
            }
            if (p.openHomework.isNotEmpty()) {
                append("\nОткрытые домашние задания (спроси, как с ними):\n")
                p.openHomework.forEach { append("• (${it.id}) ${it.title}\n") }
            }
            if (p.moodLogs.isNotEmpty()) {
                val recent = p.moodLogs.takeLast(5).joinToString(", ") { "${it.score}/10" }
                append("\nПоследние замеры настроения: $recent\n")
            }
            if (p.sessionNotes.isNotEmpty()) {
                append("\nИтоги прошлых встреч:\n")
                p.sessionNotes.takeLast(4).forEach {
                    append("• ${fmt(it.createdAt)}: ${it.summary}\n")
                }
            }
        }
    }

    private fun renderJournal(journal: List<JournalEntry>): String {
        if (journal.isEmpty()) return "═══ ДНЕВНИК ═══\n(Записей пока нет.)"
        return buildString {
            append("═══ ДНЕВНИК ПАЦИЕНТА (его собственные записи, недавние) ═══\n")
            journal.sortedByDescending { it.createdAt }.take(7).forEach { e ->
                val mood = e.mood?.let { " (настроение $it/10)" } ?: ""
                append("• ${fmt(e.createdAt)}$mood: ${e.text.take(400)}\n")
            }
            append("\nОпирайся на дневник бережно: ссылайся на то, что человек сам написал, " +
                "но не цитируй дословно без нужды и не превращай это в проверку.")
        }
    }

    private val dateFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    private fun fmt(ts: Long) = dateFmt.format(Date(ts))
}
