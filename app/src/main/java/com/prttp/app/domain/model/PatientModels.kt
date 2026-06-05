package com.prttp.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────
//  ПРОФИЛЬ ПАЦИЕНТА И СТРУКТУРНЫЕ КАТЕГОРИИ АНАЛИЗА
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
    // Базовые клинические категории
    const val PRESENTING_CONCERN = "presenting_concern" // С чем пришел (запрос)
    const val HISTORY            = "history"            // Анамнез жизни и травм
    const val SYMPTOM            = "symptom"            // Наблюдаемые симптомы и аффект
    const val TRIGGER            = "trigger"            // Ситуативные триггеры

    // Новые глубокие категории анализа структуры личности
    const val CORE_BELIEF        = "core_belief"        // Глубинные убеждения (о себе, о людях, о мире)
    const val COGNITIVE_STYLE    = "cognitive_style"    // Когнитивные искажения и стиль мышления
    const val DEFENSE_MECHANISM  = "defense_mechanism"  // Психологические защиты (проекция, вытеснение, изоляция аффекта и др.)
    const val MALADAPTIVE_SCHEMA = "maladaptive_schema" // Ранние дезадаптивные схемы по Янгу (покинутость, недоверие, дефективность и др.)
    const val BEHAVIOR_PATTERN   = "behavior_pattern"   // Паттерны поведения (избегание, компенсация, капитуляция)
    const val EMOTIONAL_BLOCK    = "emotional_block"    // Подавленные чувства и зоны заблокированного аффекта
    const val COPING_STRATEGY    = "coping_strategy"    // Адаптивные ресурсы и копинг-механизмы
    const val THERAPEUTIC_GOAL   = "therapeutic_goal"   // Цели терапии

    val ALL = listOf(
        PRESENTING_CONCERN, HISTORY, SYMPTOM, TRIGGER, CORE_BELIEF,
        COGNITIVE_STYLE, DEFENSE_MECHANISM, MALADAPTIVE_SCHEMA,
        BEHAVIOR_PATTERN, EMOTIONAL_BLOCK, COPING_STRATEGY, THERAPEUTIC_GOAL
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
    val messages: List<ConversationMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val openHomework: List<Homework> get() = homework.filter { !it.done }

    val activeRisk: ClinicalFlag?
        get() = flags.filter { it.active }.maxByOrNull { it.level.severity }
}

@Serializable
@Immutable
data class JournalEntry(
    val id: String,
    val text: String,
    val mood: Int? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Immutable
data class SessionNote(
    val id: String,
    val summary: String,
    val observations: String = "",
    val techniques: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Immutable
data class MoodLog(
    val score: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

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