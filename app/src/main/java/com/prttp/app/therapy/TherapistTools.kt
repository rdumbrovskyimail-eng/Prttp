package com.prttp.app.therapy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ToolName {
    const val UPDATE_PROFILE        = "update_profile"
    const val SET_NAME              = "set_patient_name"
    const val SAVE_SESSION_NOTE     = "save_session_note"
    const val LOG_MOOD              = "log_mood"
    const val ADD_HOMEWORK          = "add_homework"
    const val COMPLETE_HOMEWORK     = "complete_homework"
    const val FLAG_CONCERN          = "flag_clinical_concern"
    const val READ_JOURNAL          = "read_recent_journal"
    const val READ_PROFILE          = "read_full_profile"
    const val READ_DIALOGUE_HISTORY = "read_dialogue_history"
    const val SHOW_IMAGE            = "show_therapeutic_image"
}

object TherapistTools {

    fun declarations(): JsonArray = buildJsonArray {

        add(decl(
            name = ToolName.UPDATE_PROFILE,
            description = "Сохранить или обновить структурированный факт о личности пациента в его долговременном профиле. " +
                "Вызывай инструмент сразу, как только выявил значимый элемент психологической структуры. " +
                "Категории распределять строго: " +
                "presenting_concern (запрос), history (анамнез), symptom (симптом), trigger (триггер), " +
                "core_belief (глубинное убеждение), cognitive_style (когнитивное искажение), " +
                "defense_mechanism (защитный механизм), maladaptive_schema (ранняя схема по Янгу), " +
                "behavior_pattern (паттерн поведения/избегания), emotional_block (зоны подавленного аффекта), " +
                "coping_strategy (адаптивный ресурс), therapeutic_goal (цель терапии).",
            params = {
                strParam("category", "Категория: presenting_concern, history, symptom, trigger, core_belief, cognitive_style, defense_mechanism, maladaptive_schema, behavior_pattern, emotional_block, coping_strategy, therapeutic_goal", true)
                strParam("key", "Короткий ярлык, например 'глубинное убеждение: я никчемен' или 'схема: покинутость/нестабильность'", true)
                strParam("value", "Содержание факта или паттерна своими словами с примером проявления", true)
                numParam("confidence", "Уверенность 0..1: 0.9 — подтверждено пациентом, 0.5 — терапевтическая гипотеза", false)
            }
        ))

        add(decl(
            name = ToolName.SET_NAME,
            description = "Запомнить имя пациента или предпочтительное обращение.",
            params = { strParam("name", "Имя или обращение", true) }
        ))

        add(decl(
            name = ToolName.SAVE_SESSION_NOTE,
            description = "Сохранить клиническую заметку по итогам сессии. Вызывай в конце разговора. Кратко зафиксируй динамику.",
            params = {
                strParam("summary", "Резюме разговора, 2–4 предложения", true)
                strParam("observations", "Наблюдения: аффект, сопротивление, защитные механизмы", false)
                strParam("techniques", "Применённые методы через запятую (экспозиция, когнитивная реструктуризация, работа с режимами схем)", false)
            }
        ))

        add(decl(
            name = ToolName.LOG_MOOD,
            description = "Зафиксировать замер настроения пациента по шкале от 1 до 10.",
            params = {
                numParam("score", "1 — крайне плохо, 10 — отлично", true)
                strParam("note", "Контекст (например: тревога из-за предстоящего звонка)", false)
            }
        ))

        add(decl(
            name = ToolName.ADD_HOMEWORK,
            description = "Назначить домашнее задание или поведенческий эксперимент. Только конкретные, понятные и выполнимые действия.",
            params = {
                strParam("title", "Короткое название задания", true)
                strParam("detail", "Подробная инструкция по выполнению", false)
                strParam("method", "Метод: behavioral_activation, schema_dialogue, ACT_defusion, thought_record", false)
            }
        ))

        add(decl(
            name = ToolName.COMPLETE_HOMEWORK,
            description = "Отметить домашнее задание как выполненное по его уникальному ID.",
            params = { strParam("id", "ID задания из профиля", true) }
        ))

        add(decl(
            name = ToolName.FLAG_CONCERN,
            description = "Зарегистрировать клиническую озабоченность или риск. level=crisis — суицидальные мысли/намерения, тяжелый дистресс, насилие.",
            params = {
                strParam("level", "none, low, moderate, high, crisis", true)
                strParam("reason", "Описание риска на основе наблюдения поведения или слов пациента", true)
            }
        ))

        add(decl(
            name = ToolName.READ_JOURNAL,
            description = "Прочитать последние личные записи из дневника пациента за указанный промежуток.",
            params = { numParam("days", "За сколько дней (по умолчанию 14)", false) }
        ))

        add(decl(
            name = ToolName.READ_PROFILE,
            description = "Перечитать полную терапевтическую карту пациента, чтобы освежить в памяти структуру его личности.",
            params = { }
        ))

        add(decl(
            name = ToolName.READ_DIALOGUE_HISTORY,
            description = "Прочитать дословную историю сообщений (транскрипты диалога) из прошлых сессий. " +
                "Вызывай, если пациент ссылается на свои старые слова, если нужно проверить динамику изменений " +
                "или детально вспомнить точный ход прошлой беседы.",
            params = {
                numParam("limit", "Количество последних сообщений для чтения (по умолчанию 30, максимум 100)", false)
            }
        ))

        add(decl(
            name = ToolName.SHOW_IMAGE,
            description = "Показать пациенту терапевтическое изображение. " +
                "Вызывай при технике заземления, визуализации безопасного места, " +
                "дыхательных упражнениях, позитивном образе. " +
                "НЕ вызывай во время проработки травмы, острого плача или кризиса.",
            params = {
                strParam(
                    "query",
                    "Поисковый запрос на английском для Pexels. " +
                    "Примеры: 'calm ocean waves', 'misty forest peaceful', " +
                    "'warm sunlight meadow', 'cozy reading nook', 'mountain sunrise path'",
                    true
                )
                strParam(
                    "caption",
                    "Подпись на русском для пациента (1-5 слов). " +
                    "Примеры: 'Представь это место', 'Здесь безопасно', 'Дыши вместе с волнами'",
                    true
                )
            }
        ))
    }

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
        private val props = mutableListOf<Pair<String, Pair<String, String>>>()

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