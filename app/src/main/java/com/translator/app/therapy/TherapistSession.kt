package com.translator.app.therapy

import com.translator.app.domain.model.JournalEntry
import com.translator.app.domain.model.LatencyProfile
import com.translator.app.domain.model.PatientProfile
import com.translator.app.domain.model.SessionConfig
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
        // Умеренная температура для баланса между эмпатичной живостью и строгим клиническим ведением
        temperature = 0.55f,
        topP = 0.95f,
        maxOutputTokens = 8192,
        voiceId = voiceId,

        // Переводим ИИ на профиль Balanced (thinkingLevel = "medium").
        // Модель берет паузу перед ответом для глубокого анализа дезадаптивных схем и когнитивного стиля.
        latencyProfile = LatencyProfile.Balanced,
        thinkingIncludeThoughts = false,

        // VAD настройки, препятствующие агрессивному перебиванию терапевта во время пауз пациента
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
        append(renderProfile(profile))
        append("\n\n")
        append(renderJournal(recentJournal))
    }

    private fun renderProfile(p: PatientProfile): String {
        if (p.facts.isEmpty() && p.sessionNotes.isEmpty() && p.openHomework.isEmpty() &&
            p.displayName.isBlank() && p.moodLogs.isEmpty()
        ) {
            return "═══ ПРОФИЛЬ ПАЦИЕНТА ═══\n(Карта пуста — это первая встреча. " +
                "Узнай имя пациента и проведи первичное бережное знакомство. Зафиксируй запрос.)"
        }
        return buildString {
            append("═══ ТЕРАПЕВТИЧЕСКАЯ КАРТА ПАЦИЕНТА (твоя память) ═══\n")
            if (p.displayName.isNotBlank()) append("Имя пациента: ${p.displayName}\n")

            if (p.facts.isNotEmpty()) {
                append("\nВыявленная структура личности (факты и гипотезы):\n")
                p.facts.sortedByDescending { it.updatedAt }.take(45).forEach {
                    append("• [${it.category.uppercase()}] ${it.key}: ${it.value}\n")
                }
            }
            p.activeRisk?.let {
                append("\n⚠ АКТИВНЫЙ РИСК: ${it.level} — ${it.reason}\n")
            }
            if (p.openHomework.isNotEmpty()) {
                append("\nТекущие домашние задания (узнай, как успехи):\n")
                p.openHomework.forEach { append("• (${it.id}) ${it.title}: ${it.detail}\n") }
            }
            if (p.moodLogs.isNotEmpty()) {
                val recent = p.moodLogs.takeLast(7).joinToString(", ") { "${it.score}/10" }
                append("\nДинамика настроения (последние сессии): $recent\n")
            }
            if (p.sessionNotes.isNotEmpty()) {
                append("\nИтоги прошлых встреч:\n")
                p.sessionNotes.takeLast(5).forEach {
                    append("• ${fmt(it.createdAt)}: ${it.summary}\n")
                }
            }
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

    private val dateFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    private fun fmt(ts: Long) = dateFmt.format(Date(ts))
}