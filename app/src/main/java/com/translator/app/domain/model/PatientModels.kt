package com.translator.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────
//  ПРОФИЛЬ ПАЦИЕНТА
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class ProfileFact(
    val category: String,
    val key: String,
    val value: String,
    val confidence: Float = 0.7f,
    val updatedAt: Long = System.currentTimeMillis()
)

object ProfileCategory {
    const val PRESENTING_CONCERN = "presenting_concern"
    const val HISTORY            = "history"
    const val SYMPTOM            = "symptom"
    const val TRIGGER            = "trigger"
    const val COGNITION          = "cognition"
    const val COPING             = "coping"
    const val STRENGTH           = "strength"
    const val GOAL               = "goal"
    const val RELATIONSHIP       = "relationship"
    const val PREFERENCE         = "preference"
    const val BOUNDARY           = "boundary"
    const val MEDICAL            = "medical"

    val ALL = listOf(
        PRESENTING_CONCERN, HISTORY, SYMPTOM, TRIGGER, COGNITION, COPING,
        STRENGTH, GOAL, RELATIONSHIP, PREFERENCE, BOUNDARY, MEDICAL
    )
}

@Serializable
@Immutable
data class PatientProfile(
    val displayName: String = "",
    val facts: List<ProfileFact> = emptyList(),
    val sessionNotes: List<SessionNote> = emptyList(),
    val moodLogs: List<MoodLog> = emptyList(),
    val homework: List<Homework> = emptyList(),
    val flags: List<ClinicalFlag> = emptyList(),
    // Полноценная пошаговая история сообщений
    val messages: List<ConversationMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val openHomework: List<Homework> get() = homework.filter { !it.done }

    val activeRisk: ClinicalFlag?
        get() = flags.filter { it.active }.maxByOrNull { it.level.severity }
}

// ─────────────────────────────────────────────────────────────────────────
//  ДНЕВНИК
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class JournalEntry(
    val id: String,
    val text: String,
    val mood: Int? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────
//  ЗАМЕТКИ СЕССИЙ
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class SessionNote(
    val id: String,
    val summary: String,
    val observations: String = "",
    val techniques: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────
//  НАСТРОЕНИЕ
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class MoodLog(
    val score: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────
//  ДОМАШНИЕ ЗАДАНИЯ
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class Homework(
    val id: String,
    val title: String,
    val detail: String = "",
    val method: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null
)

// ─────────────────────────────────────────────────────────────────────────
//  КЛИНИЧЕСКИЕ ФЛАГИ
// ─────────────────────────────────────────────────────────────────────────

@Serializable
enum class RiskLevel(val severity: Int) {
    NONE(0), LOW(1), MODERATE(2), HIGH(3), CRISIS(4)
}

@Serializable
@Immutable
data class ClinicalFlag(
    val id: String,
    val level: RiskLevel,
    val reason: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)