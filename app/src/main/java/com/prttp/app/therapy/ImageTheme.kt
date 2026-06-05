package com.prttp.app.therapy

import kotlinx.serialization.Serializable

@Serializable
enum class ImageTheme(
    val label: String,
    val emoji: String,
    val baseKeywords: String   // добавляется к любому запросу ИИ
) {
    NATURE(    "Природа",    "🌿", "peaceful nature greenery"),
    OCEAN(     "Океан",      "🌊", "calm ocean waves shore"),
    FOREST(    "Лес",        "🌲", "misty forest trees light"),
    MOUNTAINS( "Горы",       "🏔️", "mountain sunrise landscape"),
    COZY(      "Уют",        "☕", "cozy warm interior soft light"),
    LIGHT(     "Свет",       "✨", "golden sunlight rays warmth"),
    ABSTRACT(  "Спокойствие","🔮", "abstract soft colors calm minimal");

    companion object {
        val DEFAULT = NATURE

        /**
         * Обогащает запрос ИИ ключевыми словами выбранной темы.
         * "calm breathing" + OCEAN → "calm breathing calm ocean waves shore"
         */
        fun enrichQuery(aiQuery: String, theme: ImageTheme): String {
            val trimmed = aiQuery.trim()
            // Не добавляем если ключевые слова уже присутствуют
            return if (theme.baseKeywords.split(" ").any { trimmed.contains(it, ignoreCase = true) })
                trimmed
            else
                "$trimmed ${theme.baseKeywords}"
        }
    }
}