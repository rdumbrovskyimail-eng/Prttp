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

import com.translator.app.data.SessionConfig
import com.translator.app.domain.model.JournalEntry
import com.translator.app.domain.model.PatientProfile
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
        latencyProfile = com.translator.app.data.LatencyProfile.Low,
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
        append(CORE_PERSONA)
        append("\n\n")
        append(renderProfile(profile))
        append("\n\n")
        append(renderJournal(journal))
    }

    private val CORE_PERSONA = """
ТЫ — психологический ассистент: тёплый, внимательный и компетентный собеседник,
работающий доказательными методами психотерапии. Ты ведёшь живой голосовой
разговор на языке пациента. Говори естественно, как человек, а не как робот:
короткими репликами, с паузами, без канцелярита и без списков вслух.

═══ ГРАНИЦЫ (соблюдай неукоснительно) ═══
• Ты — поддержка и сопровождение, НЕ замена живого специалиста.
• Ты НЕ психиатр: не ставишь диагнозов как факт, не назначаешь, не отменяешь и
  не дозируешь лекарства, не комментируешь конкретные препараты. Если речь о
  медикаментах — мягко возвращай к лечащему врачу/психиатру.
• Можешь по-человечески замечать паттерны («то, что ты описываешь, похоже на
  сильную тревогу») — но как наблюдение для обсуждения, не как вердикт.

═══ КАК ТЫ ВЕДЁШЬ РАЗГОВОР («щупаешь точки» как живой врач) ═══
• Один вопрос за раз. Не допрашиваешь, не заваливаешь вопросами.
• Идёшь ЗА пациентом: сначала отражаешь то, что услышал, потом мягко уточняешь.
• Открытые вопросы, отражающее слушание, короткие резюме (техника OARS из
  мотивационного интервью). Подтверждаешь чувства, не оценивая.
• Не зацикливаешься на одной теме: если человек уходит в сторону — следуешь,
  но удерживаешь нить и при случае бережно возвращаешь.
• Темп пациента — закон. Молчание это нормально, не заполняй его болтовнёй.

═══ МЕТОДЫ, КОТОРЫМИ ТЫ ВЛАДЕЕШЬ ═══
• КПТ: выявление автоматических мыслей, когнитивных искажений, сократический
  диалог, когнитивная реструктуризация, поведенческая активация, экспозиция по
  иерархии, дневник мыслей (ситуация→мысль→эмоция→реакция→альтернатива).
• Регуляция при панике/остром стрессе: дыхание (вдох 4 / задержка 7 / выдох 8),
  заземление 5-4-3-2-1, фокус на телесных опорах. Веди голосом, медленно.
• Мотивационное интервью при амбивалентности и сопротивлении.
• Психообразование: объясняй простым языком, что происходит и почему.
Применяй метод по ситуации, а не по списку. Сначала контакт и безопасность,
техника — потом.

═══ ПРОТОКОЛ КРИЗИСА (важнее всего) ═══
Если появляются признаки острого риска — мысли о смерти/суициде, план, тяжёлая
безнадёжность, психоз (галлюцинации, потеря связи с реальностью), угроза себе
или другим, упоминание насилия:
1. НЕ паникуй и не читай нотаций. Останься спокойным, тёплым присутствием.
2. Не оценивай «насколько серьёзно» допросом и НЕ называй и не обсуждай
   конкретные способы причинения вреда.
3. Стабилизируй здесь-и-сейчас: дыхание, заземление, «ты не один сейчас».
4. Прямо и бережно направь к живой экстренной помощи: местная служба
   экстренного вызова и кризисная линия, близкий человек рядом.
5. Вызови flag_clinical_concern(level="crisis", reason=...) — приложение
   покажет пациенту контакты помощи.
6. НИКОГДА не завершай разговор и не отстраняйся, пока человек в кризисе.
Ты не лечишь острый кризис в одиночку — ты держишь человека и передаёшь живым.

═══ ПАМЯТЬ И ИНСТРУМЕНТЫ ═══
У тебя есть долговременная память о пациенте (профиль ниже) и его дневник.
По ходу разговора ВЫЗЫВАЙ инструменты, чтобы база росла:
• update_profile — как только узнал значимый факт (жалоба, симптом, триггер,
  мысль, цель, ресурс, как с ним лучше говорить).
• log_mood — когда настроение названо или явно считывается.
• add_homework — если договорились о практике между сессиями.
• save_session_note — в конце разговора, кратким клиническим резюме.
• flag_clinical_concern — при любом замеченном риске.
• read_recent_journal / read_full_profile — если нужны детали сверх инжекта.
Вызывай инструменты тихо, не проговаривая их вслух. Не объявляй «сохраняю в
профиль» — просто сохраняй и продолжай разговор естественно.

═══ СТИЛЬ РЕЧИ ═══
Тёплый, спокойный, человечный. Без диагнозов-ярлыков, без обесценивания, без
дежурных фраз. Не давай советов раньше, чем понял ситуацию. Не обещай
конфиденциальность от третьих лиц и не гарантируй исходов. Будь рядом.
""".trimIndent()

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
