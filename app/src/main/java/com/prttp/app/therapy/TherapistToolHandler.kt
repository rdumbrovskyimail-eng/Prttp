// Путь: app/src/main/java/com/translator/app/therapy/TherapistToolHandler.kt
//
// Исполняет function-call'ы Gemini поверх PatientRepository и формирует
// ответы (ToolResponse), которые ViewModel отправит через
// LiveClient.sendToolResponse(...).
//
// ВАЖНО про формат args:
//   GeminiLiveClient кладёт args как Map<String,String>, где value = .toString()
//   соответствующего JsonElement. То есть строковые значения ПРИХОДЯТ В
//   КАВЫЧКАХ ("anxiety"), а числа — как есть (7). Поэтому используем unwrap().
//
// Подключение во ViewModel (один новый case в when(event)):
//   is GeminiEvent.ToolCall -> viewModelScope.launch {
//       val responses = toolHandler.handle(event.calls)
//       liveClient.sendToolResponse(responses)
//   }
// ═══════════════════════════════════════════════════════════════════════════
package com.prttp.app.therapy

import com.prttp.app.data.PatientRepository
import com.prttp.app.data.PexelsImageRepository
import com.prttp.app.data.WebResearchRepository
import com.prttp.app.domain.ToolResponse
import com.prttp.app.domain.model.FunctionCall
import com.prttp.app.domain.model.RiskLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TherapistToolHandler @Inject constructor(
    private val repo: PatientRepository,
    private val pexels: PexelsImageRepository,
    private val web: WebResearchRepository
) {

    /** Колбэк наружу: UI может среагировать на кризисный флаг (показать ресурсы). */
    var onCrisisFlag: ((RiskLevel, String) -> Unit)? = null
    var onShowImage: ((String, String) -> Unit)? = null   // (query, caption)
    var onResearch: ((String) -> Unit)? = null            // (query) → показать табличку
    var pexelsApiKey: String = ""
    var geminiApiKey: String = ""

    suspend fun handle(calls: List<FunctionCall>): List<ToolResponse> =
        calls.map { call ->
            val result = runCatching { dispatch(call) }
                .getOrElse { "error: ${it.message}" }
            ToolResponse(name = call.name, id = call.id, result = result)
        }

    private suspend fun dispatch(call: FunctionCall): String {
        val a = call.args
        return when (call.name) {

            ToolName.UPDATE_PROFILE -> {
                repo.upsertFact(
                    category = a.str("category"),
                    key = a.str("key"),
                    value = a.str("value"),
                    confidence = a.num("confidence")?.toFloat() ?: 0.7f
                )
                "ok"
            }

            ToolName.SET_NAME -> { repo.setDisplayName(a.str("name")); "ok" }

            ToolName.SAVE_SESSION_NOTE -> {
                repo.addSessionNote(
                    summary = a.str("summary"),
                    observations = a.str("observations"),
                    techniques = a.str("techniques")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
                "ok"
            }

            ToolName.LOG_MOOD -> {
                val score = a.num("score")?.toInt() ?: return "error: score missing"
                repo.logMood(score, a.str("note")); "ok"
            }

            ToolName.ADD_HOMEWORK -> {
                repo.addHomework(a.str("title"), a.str("detail"), a.str("method")); "ok"
            }

            ToolName.COMPLETE_HOMEWORK -> { repo.completeHomework(a.str("id")); "ok" }

            ToolName.FLAG_CONCERN -> {
                val level = parseRisk(a.str("level"))
                val reason = a.str("reason")
                repo.raiseFlag(level, reason)
                if (level.severity >= RiskLevel.HIGH.severity) onCrisisFlag?.invoke(level, reason)
                "ok"
            }

            ToolName.READ_JOURNAL -> {
                val days = a.num("days")?.toInt() ?: 14
                val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
                val entries = repo.journal.value
                    .filter { it.createdAt >= cutoff }
                    .sortedByDescending { it.createdAt }
                    .take(20)
                if (entries.isEmpty()) "Дневник за последние $days дней пуст."
                else entries.joinToString("\n") { e ->
                    val mood = e.mood?.let { " [настроение $it/10]" } ?: ""
                    "${e.createdAt}$mood: ${e.text}"
                }
            }

            ToolName.READ_PROFILE -> {
                val p = repo.profile.value
                buildString {
                    if (p.displayName.isNotBlank()) append("Имя: ${p.displayName}\n")
                    p.facts.forEach { append("[${it.category}] ${it.key}: ${it.value}\n") }
                    if (isEmpty()) append("Профиль пуст.")
                }
            }

            ToolName.READ_DIALOGUE_HISTORY -> {
                val limit = a.num("limit")?.toInt() ?: 30
                val messages = repo.profile.value.messages
                if (messages.isEmpty()) {
                    "История диалогов пуста."
                } else {
                    // Извлекаем последние N сообщений диалога, переводя роли на понятные ИИ
                    messages.takeLast(limit.coerceIn(1, 100)).joinToString("\n") { msg ->
                        val sender = if (msg.role == "user") "Пациент" else "Терапевт"
                        "[$sender]: ${msg.text}"
                    }
                }
            }

            ToolName.SHOW_IMAGE -> {
                val query   = a.str("query").ifBlank { "calm nature peaceful" }
                val caption = a.str("caption").ifBlank { "" }
                // Запускаем загрузку асинхронно — не блокируем ответ ИИ
                onShowImage?.invoke(query, caption)
                "ok: image requested"
            }

            ToolName.WEB_RESEARCH -> {
                val query = a.str("query")
                val topic = a.str("topic")
                if (query.isBlank()) return "error: query missing"
                onResearch?.invoke(query)                       // UI: «Ищу в интернете, изучаю…»
                val result = web.research(geminiApiKey, query)
                    ?: return "Не удалось найти данные в интернете по запросу: $query"
                repo.addResearchNote(query, topic, result.summary, result.sources)
                buildString {
                    append(result.summary.take(1500))
                    if (result.sources.isNotEmpty()) {
                        append("\n\nИсточники:\n")
                        result.sources.take(4).forEach { append("• $it\n") }
                    }
                }
            }

            else -> "error: unknown tool ${call.name}"
        }
    }

    private fun parseRisk(raw: String): RiskLevel = when (raw.lowercase().trim()) {
        "crisis" -> RiskLevel.CRISIS
        "high"   -> RiskLevel.HIGH
        "moderate" -> RiskLevel.MODERATE
        "low"    -> RiskLevel.LOW
        else     -> RiskLevel.NONE
    }

    // ── разбор args (значения приходят как .toString() JsonElement) ───────────

    private fun Map<String, String>.str(key: String): String = this[key]?.unwrap() ?: ""
    private fun Map<String, String>.num(key: String): Double? =
        this[key]?.unwrap()?.toDoubleOrNull()

    /** Снимает обрамляющие кавычки со строкового JSON-значения. */
    private fun String.unwrap(): String {
        val t = trim()
        return if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\n", "\n")
        } else t
    }
}
