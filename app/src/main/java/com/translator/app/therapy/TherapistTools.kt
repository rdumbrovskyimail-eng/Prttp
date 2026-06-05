// Путь: app/src/main/java/com/translator/app/therapy/TherapistTools.kt
//
// Объявления function-call инструментов, через которые Gemini САМ пишет и
// читает базу пациента во время живого голосового разговора.
//
// Как это связано с существующим кодом:
//   • Gemini присылает GeminiEvent.ToolCall(List<FunctionCall>) — это уже
//     парсится в GeminiLiveClient.
//   • TherapistToolHandler исполняет вызовы поверх PatientRepository и
//     отвечает через LiveClient.sendToolResponse(List<ToolResponse>).
//
// Чтобы Gemini вообще ЗНАЛ про инструменты, их декларации надо добавить в
// setup-кадр. Для этого:
//   1) в data class SessionConfig добавить поле:
//          val toolsJson: JsonArray? = null
//   2) в GeminiLiveClient.buildFullSetup(...) внутри put("setup", { ... })
//      дописать:
//          config.toolsJson?.let { tools ->
//              put("tools", buildJsonArray {
//                  add(buildJsonObject { put("functionDeclarations", tools) })
//              })
//          }
//   (формат по докам Live API: setup.tools[].functionDeclarations[])
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.therapy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Имена инструментов — единый источник для деклараций и обработчика. */
object ToolName {
    const val UPDATE_PROFILE   = "update_profile"
    const val SET_NAME         = "set_patient_name"
    const val SAVE_SESSION_NOTE = "save_session_note"
    const val LOG_MOOD         = "log_mood"
    const val ADD_HOMEWORK      = "add_homework"
    const val COMPLETE_HOMEWORK = "complete_homework"
    const val FLAG_CONCERN      = "flag_clinical_concern"
    const val READ_JOURNAL      = "read_recent_journal"
    const val READ_PROFILE      = "read_full_profile"
}

object TherapistTools {

    /** JSON-массив functionDeclarations для setup.tools. */
    fun declarations(): JsonArray = buildJsonArray {

        // — запись структурированного факта о пациенте —
        add(decl(
            name = ToolName.UPDATE_PROFILE,
            description = "Сохранить или обновить структурированный факт о пациенте в его " +
                "долговременном профиле. Вызывай, как только узнал что-то клинически " +
                "значимое: основную жалобу, симптом, триггер, автоматическую мысль, цель, " +
                "ресурс, предпочтение в общении. Один вызов = один факт.",
            params = {
                strParam("category", "Категория: presenting_concern, history, symptom, trigger, " +
                    "cognition, coping, strength, goal, relationship, preference, boundary, medical", true)
                strParam("key", "Короткий ярлык факта, например 'триггер: открытое пространство'", true)
                strParam("value", "Содержание факта своими словами", true)
                numParam("confidence", "Уверенность 0..1: 0.9 — наблюдал прямо, 0.5 — гипотеза", false)
            }
        ))

        add(decl(
            name = ToolName.SET_NAME,
            description = "Запомнить, как пациент просит к нему обращаться.",
            params = { strParam("name", "Имя или обращение", true) }
        ))

        // — заметка сессии (вызывать ближе к концу разговора) —
        add(decl(
            name = ToolName.SAVE_SESSION_NOTE,
            description = "Сохранить заметку по итогам сессии. Вызывай в конце разговора или " +
                "когда тема логически завершилась. Кратко, по-клинически, без воды.",
            params = {
                strParam("summary", "Резюме разговора, 2–4 предложения", true)
                strParam("observations", "Наблюдения: аффект, динамика, важные темы", false)
                strParam("techniques", "Применённые методы через запятую (КПТ-реструктуризация, " +
                    "заземление 5-4-3-2-1, мотивационное интервью...)", false)
            }
        ))

        add(decl(
            name = ToolName.LOG_MOOD,
            description = "Зафиксировать срез настроения пациента по шкале 1..10, если он его " +
                "назвал или его явно можно оценить из разговора.",
            params = {
                numParam("score", "1 — очень плохо, 10 — отлично", true)
                strParam("note", "Короткий контекст", false)
            }
        ))

        add(decl(
            name = ToolName.ADD_HOMEWORK,
            description = "Назначить домашнее задание / поведенческий эксперимент между сессиями. " +
                "Только конкретное и выполнимое.",
            params = {
                strParam("title", "Что сделать, одной фразой", true)
                strParam("detail", "Как именно, когда, сколько", false)
                strParam("method", "Метод: behavioral_activation, exposure, thought_record...", false)
            }
        ))

        add(decl(
            name = ToolName.COMPLETE_HOMEWORK,
            description = "Отметить домашнее задание выполненным по его id (id берётся из профиля).",
            params = { strParam("id", "id задания", true) }
        ))

        // — клинический флаг риска (драйвит баннер экстренной помощи в UI) —
        add(decl(
            name = ToolName.FLAG_CONCERN,
            description = "Поднять клинический флаг, если замечен риск. level=crisis при " +
                "суицидальных мыслях/плане, психозе, угрозе для себя/других — в этом случае " +
                "ты ОБЯЗАН одновременно перейти к стабилизации и направить к живой экстренной " +
                "помощи. Это сигнал приложению показать ресурсы помощи, НЕ диагноз.",
            params = {
                strParam("level", "none, low, moderate, high, crisis", true)
                strParam("reason", "На основании чего (наблюдение, не вердикт)", true)
            }
        ))

        // — чтение (если инжекта в промпт мало и нужны детали) —
        add(decl(
            name = ToolName.READ_JOURNAL,
            description = "Прочитать последние записи дневника пациента (его собственные тексты), " +
                "чтобы опереться на них в разговоре.",
            params = { numParam("days", "За сколько последних дней (по умолчанию 14)", false) }
        ))

        add(decl(
            name = ToolName.READ_PROFILE,
            description = "Перечитать полный актуальный профиль пациента, если нужно освежить детали.",
            params = { }
        ))
    }

    // ── helpers для сборки схемы (OpenAPI-подмножество, как в Gemini) ──────────

    private inline fun decl(
        name: String,
        description: String,
        params: ParamBuilder.() -> Unit
    ) = buildJsonObject {
        put("name", name)
        put("description", description)
        val b = ParamBuilder().apply(params)
        put("parameters", buildJsonObject {
            put("type", "object")
            put("properties", b.properties())
            if (b.required.isNotEmpty()) put("required", buildJsonArray { b.required.forEach { add(it) } })
        })
    }

    class ParamBuilder {
        val required = mutableListOf<String>()
        private val props = mutableListOf<Pair<String, Pair<String, String>>>() // name -> (type, desc)

        fun strParam(name: String, desc: String, isRequired: Boolean) {
            props += name to ("string" to desc); if (isRequired) required += name
        }
        fun numParam(name: String, desc: String, isRequired: Boolean) {
            props += name to ("number" to desc); if (isRequired) required += name
        }

        fun properties() = buildJsonObject {
            props.forEach { (n, td) ->
                put(n, buildJsonObject { put("type", td.first); put("description", td.second) })
            }
        }
    }
}
