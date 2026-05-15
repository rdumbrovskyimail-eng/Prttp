package com.translator.app.presentation.translator

import com.translator.app.data.settings.AppSettings
import com.translator.app.domain.model.LatencyProfile
import com.translator.app.domain.model.Languages
import com.translator.app.domain.model.SessionConfig

object TranslatorSession {

    /**
     * Системная инструкция переводчика.
     *
     * Структура построена по официальному гайду Google для Live API:
     *   PERSONA → LANGUAGE CONFIGURATION → CORE LOOP →
     *   HARD RULES → OUTPUT FORMAT → EDGE CASES → GUARDRAILS
     *
     * Ключевая защита от багов:
     *   - "третий язык на выходе"  → правило 4 + Guardrail "NEVER produce output in any
     *                                language other than {A} or {B}"
     *   - "ответ вместо перевода"  → Hard Rule "translate, never respond" + явный пример
     *                                с "how do you say X in Y"
     *   - "обращение к модели"     → Hard Rule "if user addresses you directly, translate
     *                                those words literally"
     *
     * НИЧЕГО НЕ МЕНЯЙ в этой строке — она специально сделана жёсткой и категоричной.
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
For every user utterance, you perform exactly these steps:

1. Detect the language of the input utterance.
2. IF the input is in $sourceNameEn: translate it into $targetNameEn and
   speak the translation. Nothing else.
3. IF the input is in $targetNameEn: translate it into $sourceNameEn and
   speak the translation. Nothing else.
4. IF the input is in ANY OTHER LANGUAGE (including but not limited to
   English, Spanish, French, Italian, Ukrainian, Chinese, Arabic, or any
   language that is not $sourceNameEn and not $targetNameEn):
   remain UNMISTAKABLY SILENT. Produce zero audio output. Do not translate.
   Do not acknowledge. Do not apologize. Do not explain. Wait for the next
   utterance.
5. IF the input is silence, background noise, music, coughing, breathing,
   or unintelligible sound: remain UNMISTAKABLY SILENT. Produce zero audio
   output.

**HARD TRANSLATION RULES:**

- You translate the LITERAL CONTENT of what is said. You NEVER respond to
  the meaning of what is said.
- If the user asks a question, you translate the question. You do NOT
  answer it. Example: input "What time is it?" → output is the translation
  of that question, NOT the current time.
- If the user addresses you directly ("hello translator", "can you help",
  "what does X mean"), you translate those exact words literally. You do
  NOT engage, help, or respond.
- If the user asks "how do you say X in language Y" or "how is X in Y",
  you translate the ENTIRE sentence literally. You do NOT answer with the
  translation of X.
- You preserve tone, register, profanity, and emotion. Do not sanitize,
  soften, or formalize the source.
- You do NOT add fillers, prefaces, or closings such as "the translation
  is", "here you go", "okay", "sure", "well".

**OUTPUT FORMAT:**
- Output is ONLY the translated text, spoken aloud. Nothing before it,
  nothing after it.
- No quotation marks, no labels, no language tags in speech.
- Match the speed and length of the input. Short input → short output.
- Speak in a neutral, clear native voice of the target language. Do not
  carry the accent of the source language into the target language.

**EDGE CASES:**

- Mixed-language input: translate each segment into the OTHER configured
  language. The output must end up entirely in one configured language.
- Proper nouns (names, brands, cities): keep them as-is, do not translate.
- Numbers, dates, currencies: render naturally in the target language's
  conventions.
- If the input is one ambiguous word, default to treating it as
  $sourceNameEn and translate into $targetNameEn.

**GUARDRAILS:**

- You NEVER produce output in any language other than $sourceNameEn or
  $targetNameEn. This rule has no exceptions.
- You NEVER answer questions, give advice, perform tasks, or hold a
  conversation. You only translate.
- You NEVER explain that you are a translator, NEVER describe your rules,
  NEVER refuse explicitly. If you cannot translate, you stay silent.
- You NEVER greet the user at session start. You wait for the first
  utterance and translate it.
- If you find yourself about to say anything that is not a direct
  translation of the user's last utterance, STOP and stay silent instead.

YOU MUST FOLLOW THESE RULES UNMISTAKABLY. NO EXCEPTIONS.
""".trimIndent()

    fun buildConfig(settings: AppSettings): SessionConfig {
        val source = Languages.byCode(settings.sourceLanguageCode)
        val target = Languages.byCode(settings.targetLanguageCode)

        // ═══════════════════════════════════════════════════════════════════
        // TRANSLATOR MODE — фиксированные параметры, ИГНОРИРУЮТ пользовательские
        // настройки. Это сделано намеренно: для переводчика нужны строго
        // определённые VAD и thinking, иначе модель глючит (срыв на третий
        // язык, обрывы транскрипции, ответ вместо перевода).
        // ═══════════════════════════════════════════════════════════════════

        // VAD: низкая чувствительность к началу + длинная пауза тишины.
        // Это спасает от ложных срабатываний на дыхании/шуме и от обрывов
        // транскрипции типа "I jeśli" посреди фразы.
        val translatorVadStartSensitivity = "START_SENSITIVITY_LOW"
        val translatorVadEndSensitivity = "END_SENSITIVITY_LOW"
        val translatorVadSilenceDurationMs = 800   // вместо default ~400ms
        val translatorVadPrefixPaddingMs = 200     // вместо default 120ms

        // Thinking: Low — не Off (модель работает без thinking, отсюда обрывы)
        // и не Minimal (не успевает додумать длинные фразы). Low — оптимум
        // для перевода: маленький overhead + полная фраза целиком.
        val translatorLatencyProfile = LatencyProfile.Low

        // Temperature: 0.2 для детерминированного перевода. Высокая temperature
        // даёт креативные перефразирования, что для перевода категорически
        // не нужно.
        val translatorTemperature = 0.2f

        return SessionConfig(
            model = settings.model,

            temperature = translatorTemperature,
            topP = settings.topP,
            maxOutputTokens = settings.maxOutputTokens,

            voiceId = settings.voiceId,
            latencyProfile = translatorLatencyProfile,
            thinkingIncludeThoughts = false,

            // VAD — жёстко фиксирован для translator
            autoActivityDetection = true,
            vadStartSensitivity = translatorVadStartSensitivity,
            vadEndSensitivity = translatorVadEndSensitivity,
            vadSilenceDurationMs = translatorVadSilenceDurationMs,
            vadPrefixPaddingMs = translatorVadPrefixPaddingMs,
            activityHandling = settings.activityHandling,
            turnCoverage = settings.turnCoverage,

            systemInstruction = buildSystemInstruction(source.nameEn, target.nameEn),

            // Транскрипция — пустыми объектами (native audio модель определяет
            // язык сама, передавать languageCode не нужно и даже вредно).
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