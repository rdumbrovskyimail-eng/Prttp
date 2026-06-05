// Путь: app/src/main/java/com/translator/app/domain/model/PatientModels.kt
//
// Доменные модели «пси-ассистента».
//
// ВСЁ хранится локально и шифруется (см. PatientRepository + KeystoreCrypto).
// Это чувствительные данные о психическом здоровье — они НИКОГДА не уходят
// на наши серверы и не логируются в открытом виде.
//
// Поток данных:
//   • Профиль (PatientProfile) — структурированная картина пациента, которую
//     ассистент сам дополняет через function-call `update_profile`.
//   • Дневник (JournalEntry) — пишет ЧЕЛОВЕК; ассистент только читает.
//   • Заметки сессий (SessionNote) — пишет ассистент после/во время разговора.
//   • Замеры настроения (MoodLog) — короткие срезы 1..10.
//   • Домашние задания (Homework) — поведенческие эксперименты, КПТ-практики.
//   • Клинические флаги (ClinicalFlag) — внутренние пометки риска/наблюдений.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────
//  ПРОФИЛЬ ПАЦИЕНТА
// ─────────────────────────────────────────────────────────────────────────

/**
 * Один структурированный факт о пациенте.
 *
 * Сознательно не делаем жёсткую схему «поле=колонка»: клиническая картина
 * расширяется непредсказуемо. Вместо этого — список фактов по категориям,
 * который ассистент дополняет инкрементально.
 *
 * @param category одна из [ProfileCategory]
 * @param key короткий ярлык факта ("сон", "триггер: метро", "цель терапии")
 * @param value содержимое факта
 * @param confidence насколько ассистент уверен (0..1): гипотеза vs наблюдение
 * @param updatedAt когда факт записан/обновлён
 */
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
    const val PRESENTING_CONCERN = "presenting_concern" // с чем пришёл
    const val HISTORY            = "history"            // анамнез, предыстория
    const val SYMPTOM            = "symptom"            // наблюдаемые симптомы
    const val TRIGGER            = "trigger"            // триггеры
    const val COGNITION          = "cognition"          // автоматические мысли, убеждения
    const val COPING             = "coping"             // что помогает / копинг-стратегии
    const val STRENGTH           = "strength"           // ресурсы, сильные стороны
    const val GOAL               = "goal"               // цели терапии
    const val RELATIONSHIP       = "relationship"       // значимые люди, поддержка
    const val PREFERENCE         = "preference"         // как с ним лучше говорить
    const val BOUNDARY           = "boundary"           // темы, которые трогать осторожно
    const val MEDICAL            = "medical"            // мед.контекст (БЕЗ назначений)

    val ALL = listOf(
        PRESENTING_CONCERN, HISTORY, SYMPTOM, TRIGGER, COGNITION, COPING,
        STRENGTH, GOAL, RELATIONSHIP, PREFERENCE, BOUNDARY, MEDICAL
    )
}

/**
 * Полный профиль пациента — то, что ассистент «мгновенно считывает»
 * при каждом подключении (инжектится в systemInstruction).
 */
@Serializable
@Immutable
data class PatientProfile(
    val displayName: String = "",
    val facts: List<ProfileFact> = emptyList(),
    val sessionNotes: List<SessionNote> = emptyList(),
    val moodLogs: List<MoodLog> = emptyList(),
    val homework: List<Homework> = emptyList(),
    val flags: List<ClinicalFlag> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Активный (невыполненный) список домашних заданий. */
    val openHomework: List<Homework> get() = homework.filter { !it.done }

    /** Самый свежий клинический флаг наивысшего риска (для UI-баннера). */
    val activeRisk: ClinicalFlag?
        get() = flags.filter { it.active }.maxByOrNull { it.level.severity }
}

// ─────────────────────────────────────────────────────────────────────────
//  ДНЕВНИК (пишет человек, читает ассистент)
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class JournalEntry(
    val id: String,
    val text: String,
    /** Настроение в момент записи, 1..10 (опционально). */
    val mood: Int? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────
//  ЗАМЕТКИ СЕССИЙ (пишет ассистент)
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class SessionNote(
    val id: String,
    /** Краткое резюме разговора, 2–4 предложения. */
    val summary: String,
    /** Что наблюдалось: аффект, темы, динамика. */
    val observations: String = "",
    /** Какие методы применялись (КПТ-реструктуризация, заземление, МИ...). */
    val techniques: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────
//  НАСТРОЕНИЕ
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class MoodLog(
    /** 1 = очень плохо, 10 = отлично. */
    val score: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────
//  ДОМАШНИЕ ЗАДАНИЯ / ПОВЕДЕНЧЕСКИЕ ЭКСПЕРИМЕНТЫ
// ─────────────────────────────────────────────────────────────────────────

@Serializable
@Immutable
data class Homework(
    val id: String,
    val title: String,
    val detail: String = "",
    /** К какому методу относится (например "behavioral_activation"). */
    val method: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null
)

// ─────────────────────────────────────────────────────────────────────────
//  КЛИНИЧЕСКИЕ ФЛАГИ / РИСК
// ─────────────────────────────────────────────────────────────────────────

@Serializable
enum class RiskLevel(val severity: Int) {
    NONE(0),        // наблюдение без риска
    LOW(1),         // лёгкое снижение настроения, стресс
    MODERATE(2),    // выраженный дистресс, требует внимания
    HIGH(3),        // признаки кризиса — нужна эскалация на живую помощь
    CRISIS(4)       // острый риск (суицидальные мысли/план, психоз) — НЕМЕДЛЕННО
}

/**
 * Внутренняя клиническая пометка. Когда уровень >= HIGH, UI обязан показать
 * баннер с локальными ресурсами экстренной помощи (см. CrisisResources/экран).
 *
 * ВАЖНО: флаг — это сигнал «нужна живая помощь», а НЕ диагноз.
 */
@Serializable
@Immutable
data class ClinicalFlag(
    val id: String,
    val level: RiskLevel,
    /** Почему выставлен — на основании чего (наблюдение, не вердикт). */
    val reason: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
