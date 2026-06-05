package com.prttp.app.therapy

import com.prttp.app.domain.model.ConversationMessage
import com.prttp.app.domain.model.JournalEntry
import com.prttp.app.domain.model.LatencyProfile
import com.prttp.app.domain.model.PatientProfile
import com.prttp.app.domain.model.SessionConfig
import com.prttp.app.therapy.TherapistSpecializations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TherapistSession {

    fun buildConfig(
        profile: PatientProfile,
        recentJournal: List<JournalEntry>,
        voiceId: String = "Aoede",
        model: String = SessionConfig.DEFAULT_MODEL
    ): SessionConfig = SessionConfig(
        model = model,
        responseModality = "AUDIO",
        temperature = 0.55f,
        topP = 0.95f,
        maxOutputTokens = 8192,
        voiceId = voiceId,

        // Настройка Balanced (thinkingLevel = "medium") задействует клинические рассуждения ИИ перед ответом
        latencyProfile = LatencyProfile.Balanced,
        thinkingIncludeThoughts = false,

        // VAD настройки, препятствующие прерыванию речи на естественных паузах пациента
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

        toolsJson = TherapistTools.declarations()
    )

    private fun buildSystemInstruction(
        profile: PatientProfile,
        recentJournal: List<JournalEntry>
    ): String = buildString {
        append(TherapistClinicalCore.PROMPT)
        append("\n\n")
        append(TherapistSpecializations.toPromptBlock())
        append("\n\n")
        append(renderProfile(profile))
        append("\n\n")
        append("═══ ВИЗУАЛЬНАЯ ТЕМА ПАЦИЕНТА ═══\n")
        append("Предпочитаемая тема изображений: ${profile.imageTheme.label} (${profile.imageTheme.baseKeywords})\n")
        append("При вызове show_therapeutic_image используй запросы в этом стиле.\n")
        append("Пример для темы ${profile.imageTheme.label}: query=\"${profile.imageTheme.baseKeywords} calm\"\n\n")
        append(renderPreviousSessionMessages(profile))
        append("\n\n")
        append(renderJournal(recentJournal))
        append("\n\n")
        append(renderOpeningInstruction(profile))
    }

    private fun renderProfile(p: PatientProfile): String {
        if (p.facts.isEmpty() && p.sessionNotes.isEmpty() && p.openHomework.isEmpty() &&
            p.displayName.isBlank() && p.moodLogs.isEmpty()
        ) {
            return "═══ КАРТА ПАЦИЕНТА ═══\n(Карта пуста. Сессия #1. " +
                "Познакомься тепло, зафиксируй запрос. НЕ используй «На что жалуетесь?»)"
        }
        return buildString {
            append("═══ КАРТА ПАЦИЕНТА ═══\n")
            if (p.displayName.isNotBlank()) append("Имя: ${p.displayName}\n")
            append("Сессия: #${p.sessionCount}\n")
            if (p.facts.isNotEmpty()) {
                append("\nСтруктура личности:\n")
                p.facts.sortedByDescending { it.updatedAt }.take(45).forEach {
                    val c = if (it.confidence >= 0.85f) "✓" else if (it.confidence >= 0.6f) "~" else "?"
                    append("$c [${it.category.uppercase()}] ${it.key}: ${it.value}\n")
                }
            }
            p.activeRisk?.let { append("\n⚠ РИСК: ${it.level} — ${it.reason}\n") }
            if (p.openHomework.isNotEmpty()) {
                append("\nДЗ (проверь в начале):\n")
                p.openHomework.forEach { append("• ${it.title}: ${it.detail}\n") }
            }
            if (p.moodLogs.isNotEmpty()) {
                val recent = p.moodLogs.takeLast(7).joinToString(", ") { "${it.score}/10" }
                append("\nНастроение (последние 7): $recent\n")
            }
            if (p.sessionNotes.isNotEmpty()) {
                append("\nИтоги прошлых встреч:\n")
                p.sessionNotes.takeLast(5).forEach {
                    append("• ${fmt(it.createdAt)}: ${it.summary}\n")
                }
            }
        }
    }

    private fun renderPreviousSessionMessages(p: PatientProfile): String {
        val prev = p.previousSessionMessages
        if (prev.isEmpty()) return ""
        return buildString {
            append("═══ РЕПЛИКИ ПРЕДЫДУЩЕЙ ВСТРЕЧИ ═══\n")
            prev.takeLast(15).forEach { msg ->
                val sender = if (msg.role == ConversationMessage.ROLE_USER) "Пациент" else "Ты"
                append("$sender: ${msg.text}\n")
            }
            append("\nПродолжи нить прошлого разговора там, где остановились.\n")
        }
    }

    private fun renderJournal(journal: List<JournalEntry>): String {
        if (journal.isEmpty()) return "═══ ЛИЧНЫЙ ДНЕВНИК ПАЦИЕНТА ═══\n(Записей пока нет.)"
        return buildString {
            append("═══ ЛИЧНЫЙ ДНЕВНИК ПАЦИЕНТА (его мысли между сессиями) ═══\n")
            journal.sortedByDescending { it.createdAt }.take(8).forEach { e ->
                val mood = e.mood?.let { " (настроение $it/10)" } ?: ""
                append("• ${fmt(e.createdAt)}$mood: ${e.text.take(350)}\n")
            }
            append("\nИспользуй эти записи аккуратно: мягко сошлись на них, если тема " +
                "подходит к диалогу, но не устраивай допрос по написанному.")
        }
    }

    private fun renderOpeningInstruction(p: PatientProfile): String = buildString {
        append("═══ ИНСТРУКЦИЯ ПО ОТКРЫТИЮ ЭТОЙ СЕССИИ ═══\n")
        when {
            p.sessionCount <= 1 -> {
                append("Первая встреча. Тёплое знакомство, без медицинского языка.\n")
                append("«Привет. Я рад, что ты здесь. Как ты сейчас, в эту минуту?»\n")
            }
            p.openHomework.isNotEmpty() -> {
                append("Сессия #${p.sessionCount}. Есть ДЗ — проверь после приветствия.\n")
                append("Не критикуй если не выполнено — исследуй что мешало.\n")
            }
            p.moodLogs.lastOrNull()?.score?.let { it <= 4 } == true -> {
                append("Сессия #${p.sessionCount}. Последнее настроение ${p.moodLogs.last().score}/10.\n")
                append("«Как ты сейчас? Прошлый раз было непросто.» Следи за кризисом.\n")
            }
            else -> {
                val name = if (p.displayName.isNotBlank()) p.displayName else "тебя"
                append("Сессия #${p.sessionCount}. «Привет, $name. Как ты?»\n")
                append("Дай пациенту задать направление встречи.\n")
            }
        }
    }

    private val dateFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    private fun fmt(ts: Long) = dateFmt.format(Date(ts))
}