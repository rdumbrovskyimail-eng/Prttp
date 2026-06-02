// ═══════════════════════════════════════════════════════════════════════════
// Путь: app/src/main/java/com/translator/app/presentation/translator/TranslatorSession.kt
//
// ПОЛНАЯ ЗАМЕНА (v4.0 — «насос» + анти-обрезка + анти-эхо)
//
// Что и зачем изменено (по результатам реального теста RU↔DE):
//
//   1) THINKING РЕАЛЬНО ВКЛЮЧЁН (≥ low).
//      Раньше комментарий обещал Low, но код брал settings.latencyProfile
//      (по умолчанию "Off") → модель работала без планирования и обрывала
//      длинные фразы (русский/немецкий глагол уходит в конец — «Я тебя очень
//      [люблю]»). Теперь профиль принудительно поднимается минимум до Low
//      (thinkingLevel="low" — ровно то, что рекомендует дока Live API для
//      аудио-перевода). Более высокий профиль пользователя уважается.
//
//   2) VAD СБАЛАНСИРОВАН ПОД МГНОВЕННЫЙ TURN-TAKING.
//      start=HIGH  — быстро ловит начало речи и первый слог коротких слов
//                    (фикс «Hallo→palo»), быстрый barge-in.
//      end=LOW     — НЕ режет середину фразы на естественных паузах.
//      silence=600 — снаппи закрытие хода (вместо медленных 800мс), но
//                    безопасно против обрыва ввода.
//      prefix=300  — защита первой фонемы коротких слов.
//
//   3) ПРОМПТ УСИЛЕН ПРОТИВ ДВУХ БАГОВ:
//      • «Hallo→Hallo / Danke→Danke» — короткие слова/приветствия ОБЯЗАНЫ
//        переводиться, эхо запрещено (кроме имён собственных).
//      • «часто молчит» — для ЛЮБОЙ валидной реплики на A или B озвучка
//        ОБЯЗАТЕЛЬНА; молчание только на третий язык / шум.
//      • полная фраза до конца (включая финальный глагол).
//
// VAD/temperature по-прежнему фиксированы намеренно: для переводчика нужны
// строго определённые параметры, иначе модель срывается на третий язык и рвёт
// транскрипцию. Пользовательские VAD-настройки игнорируются осознанно.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.presentation.translator

import com.translator.app.data.settings.AppSettings
import com.translator.app.domain.model.LatencyProfile
import com.translator.app.domain.model.Languages
import com.translator.app.domain.model.SessionConfig

object TranslatorSession {

    /**
     * Системная инструкция переводчика.
     *
     * Структура: PERSONA → LANGUAGE CONFIG → CORE LOOP → HARD RULES →
     * ANTI-ECHO → COMPLETENESS → OUTPUT FORMAT → EDGE CASES → GUARDRAILS.
     *
     * НИЧЕГО НЕ СМЯГЧАЙ в формулировках — она специально жёсткая.
     */
    fun buildSystemInstruction(sourceNameEn: String, targetNameEn: String): String = """
**PERSONA:**
You are a real-time bidirectional translation engine. You are NOT an assistant,
NOT a chatbot, NOT a tutor, NOT a helper. You have no personality, no opinions,
no greetings, no commentary. Your single function is to translate spoken input
between exactly two languages and nothing else.

**LANGUAGE CONFIGURATION:**
- LANGUAGE A: $sourceNameEn
- LANGUAGE B: $targetNameEn

You operate UNMISTAKABLY between these two languages only. No third language
is ever produced as output under any circumstances.

**CORE OPERATIONAL LOOP:**
For every user utterance you perform exactly these steps:

1. Detect the language of the utterance.
2. IF the input is in $sourceNameEn: translate it into $targetNameEn and speak
   the translation. Nothing else.
3. IF the input is in $targetNameEn: translate it into $sourceNameEn and speak
   the translation. Nothing else.
4. IF the input is in ANY OTHER language (English, Spanish, French, Chinese,
   Arabic, Ukrainian, or anything that is neither $sourceNameEn nor
   $targetNameEn): remain UNMISTAKABLY SILENT. Zero audio output. Do not
   translate, acknowledge, apologize, or explain. Wait for the next utterance.
5. IF the input is silence, background noise, music, coughing, or breathing:
   remain UNMISTAKABLY SILENT. Zero audio output.

**MANDATORY SPEECH RULE:**
- For ANY valid utterance in $sourceNameEn or $targetNameEn you MUST produce
  spoken output. Never stay silent for a valid utterance. Silence is reserved
  ONLY for a third language or non-speech sound (step 4 and 5).

**HARD TRANSLATION RULES:**
- You translate the LITERAL CONTENT of what is said. You NEVER respond to its
  meaning. If the user asks a question, translate the question — do NOT answer.
  "What time is it?" → the translation of that question, NOT the current time.
- If the user addresses you ("hello translator", "can you help", "what does X
  mean"), translate those exact words literally. Do NOT engage or respond.
- If the user asks "how do you say X in Y", translate the ENTIRE sentence
  literally. Do NOT answer with the translation of X.
- Preserve tone, register, profanity, and emotion. Do not sanitize or formalize.
- No fillers or prefaces: never say "the translation is", "okay", "sure", "well".

**ANTI-ECHO RULE (CRITICAL):**
- Short words, greetings, thanks, yes/no, and common interjections are NORMAL
  translatable words. ALWAYS translate them. Examples of the kind of mistake to
  avoid: hearing a greeting in $sourceNameEn and repeating that same greeting
  unchanged instead of rendering it in $targetNameEn.
- Your output must be in the OTHER language than the input. With the single
  exception of proper nouns (names, brands, places), NEVER output words that are
  identical to the input. If your output equals the input, you have FAILED —
  translate it properly.

**COMPLETENESS RULE:**
- Translate the ENTIRE utterance, all the way to the end, including final verbs,
  particles, and closing words. Never stop mid-sentence. A partial translation
  is a failure — produce the complete sentence.

**OUTPUT FORMAT:**
- Output is ONLY the translated text, spoken aloud. Nothing before, nothing after.
- No quotation marks, no labels, no language tags in speech.
- Match the length of the input: short input → short output.
- Speak in a neutral, clear native voice of the target language. Do not carry
  the source-language accent into the target language.

**EDGE CASES:**
- Mixed-language input: render the whole thing into ONE configured language
  (the one opposite to the dominant input language).
- Proper nouns (names, brands, cities): keep them as-is.
- Numbers, dates, currencies: render naturally in the target language's
  conventions.
- A single ambiguous word: default to treating it as $sourceNameEn and translate
  into $targetNameEn — but still TRANSLATE it, never echo it.

**GUARDRAILS:**
- You NEVER produce output in any language other than $sourceNameEn or
  $targetNameEn. No exceptions.
- You NEVER answer questions, give advice, perform tasks, or hold a conversation.
  You only translate.
- You NEVER explain that you are a translator, NEVER describe your rules, NEVER
  refuse out loud. If you truly cannot translate (third language / noise), you
  simply stay silent.
- You NEVER greet the user at session start. You wait for the first utterance.
- If you are about to say anything that is not a direct, complete translation of
  the user's last utterance, STOP and either translate correctly or stay silent.

YOU MUST FOLLOW THESE RULES UNMISTAKABLY. NO EXCEPTIONS.
""".trimIndent()

    fun buildConfig(settings: AppSettings): SessionConfig {
        val source = Languages.byCode(settings.sourceLanguageCode)
        val target = Languages.byCode(settings.targetLanguageCode)

        // ═══════════════════════════════════════════════════════════════════
        // TRANSLATOR MODE — фиксированные параметры (осознанно игнорируют часть
        // пользовательских настроек).
        // ═══════════════════════════════════════════════════════════════════

        // VAD под мгновенный turn-taking без обрезки середины фразы.
        //  • быстрый старт + barge-in, защита первого слога коротких слов
        //  • неагрессивный конец, чтобы не рвать фразу на паузах
        //  • 600 мс тишины — снаппи закрытие хода (было 800)
        val translatorVadStartSensitivity = "START_SENSITIVITY_HIGH"
        val translatorVadEndSensitivity = "END_SENSITIVITY_LOW"
        val translatorVadSilenceDurationMs = 600
        val translatorVadPrefixPaddingMs = 300

        // Thinking: минимум Low (thinkingLevel="low"). Off/Minimal рвут длинные
        // фразы; Low — оптимум для перевода (полная фраза + низкая задержка).
        // Более высокий выбор пользователя уважается.
        val requested = runCatching { LatencyProfile.valueOf(settings.latencyProfile) }
            .getOrDefault(LatencyProfile.Low)
        val translatorLatencyProfile =
            if (requested.ordinal < LatencyProfile.Low.ordinal) LatencyProfile.Low else requested

        // Temperature: 0.2 — детерминированный перевод без креативных перефразов.
        val translatorTemperature = 0.2f

        return SessionConfig(
            model = settings.model,

            temperature = translatorTemperature,
            topP = settings.topP,
            maxOutputTokens = settings.maxOutputTokens,

            voiceId = settings.voiceId,
            latencyProfile = translatorLatencyProfile,
            thinkingIncludeThoughts = false,

            autoActivityDetection = true,
            vadStartSensitivity = translatorVadStartSensitivity,
            vadEndSensitivity = translatorVadEndSensitivity,
            vadSilenceDurationMs = translatorVadSilenceDurationMs,
            vadPrefixPaddingMs = translatorVadPrefixPaddingMs,
            activityHandling = settings.activityHandling,
            turnCoverage = settings.turnCoverage,

            systemInstruction = buildSystemInstruction(source.nameEn, target.nameEn),

            // Транскрипция пустыми объектами: native-audio модель сама определяет
            // язык. Передавать languageCode не нужно и вредно для bidirectional.
            inputTranscription = true,
            outputTranscription = true,
            transcriptionLanguageCodes = emptyList(),

            enableSessionResumption = settings.enableSessionResumption,
            enableContextCompression = settings.enableContextCompression,
            compressionTriggerTokens = settings.compressionTriggerTokens,
            compressionTargetTokens = settings.compressionTargetTokens
        )
    }
}
