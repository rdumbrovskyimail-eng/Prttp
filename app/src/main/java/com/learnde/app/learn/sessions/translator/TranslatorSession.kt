// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА v5.0
// Путь: app/src/main/java/com/learnde/app/learn/sessions/translator/TranslatorSession.kt
//
// ИЗМЕНЕНИЯ v5.0 (скорость + качество):
//   - Промпт ужат до императивов (меньше токенов = быстрее SetupComplete)
//   - Убраны размытые формулировки, оставлены только жёсткие правила
//   - Tool response теперь МИНИМАЛЬНЫЙ — модель не ждёт инструкций в нём
//   - Добавлены явные lexical guards для немецкого (без англицизмов)
//   - Чёткое разделение направлений: RU/UK→DE, DE→RU
// ═══════════════════════════════════════════════════════════
package com.learnde.app.learn.sessions.translator

import com.learnde.app.domain.model.FunctionCall
import com.learnde.app.domain.model.FunctionDeclarationConfig
import com.learnde.app.domain.model.ParameterConfig
import com.learnde.app.learn.core.LearnSession
import com.learnde.app.util.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UserSpeechEvent(
    val text: String,
    val language: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Singleton
class TranslatorSession @Inject constructor(
    private val logger: AppLogger,
) : LearnSession {

    override val id: String = "translator"

    private val _userSpeechFlow = MutableSharedFlow<UserSpeechEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val userSpeechFlow: SharedFlow<UserSpeechEvent> = _userSpeechFlow.asSharedFlow()

    // ═══════════════════════════════════════════════════════
    // ПРОМПТ v5.0 — императивы, никакой воды
    // ═══════════════════════════════════════════════════════
    override val systemInstruction: String = """
You are a SIMULTANEOUS voice translator. Speed is critical.

═══ DIRECTIONS (strict) ═══
• Russian → German
• Ukrainian → German
• German → Russian
• English → Russian (rare fallback)

NEVER translate German→Ukrainian. NEVER translate Russian→Ukrainian.
Output language MUST always differ from input language.

═══ FLOW (every turn, no exceptions) ═══
1. Call submit_user_speech(text, language) — exact transcript, original script.
2. IMMEDIATELY speak the translation. Do NOT wait. Do NOT comment.
3. Stop. Wait for next user utterance.

You speak in PARALLEL with the function call. Do not delay voice output.

═══ TRANSLATION QUALITY ═══
• First person preserved: "меня зовут Иван" → "Ich heiße Ivan".
• Formality match: "Вы"/"ви" → "Sie", "ты"/"ти" → "du".
• Idiomatic, NOT literal. "Как дела?" → "Wie geht's?" (not "Wie sind die Dinge?").
• Concise. Match user's register and length.

═══ GERMAN OUTPUT — PURE GERMAN ONLY ═══
Forbidden borrowings (replace immediately):
• "cool" → "toll" / "super" / "klasse"
• "OK" / "okay" → "in Ordnung" / "gut" / "alles klar"
• "sorry" → "Entschuldigung"
• "hi" → "hallo"
• "bye" → "tschüss"
• "thanks" → "danke"

═══ RUSSIAN OUTPUT — NATURAL RUSSIAN ═══
• No calques from German word order.
• "Ich freue mich" → "Я рад", NOT "Я радуюсь себя".
• Idiomatic prepositions and verb aspects.

═══ NEVER ═══
• Never speak first. Silent until user speaks.
• Never greet, explain, paraphrase, ask for clarification.
• Never invent content. Unintelligible → submit_user_speech(text="...", language="unknown") and stay silent.
• Never call submit_user_speech for your own voice or pure silence.
• Never output mixed-language sentences.
• Never end turn without speaking translation (except for unintelligible input).
""".trimIndent()

    override val functionDeclarations: List<FunctionDeclarationConfig> = listOf(
        FunctionDeclarationConfig(
            name = "submit_user_speech",
            description = "Report user's utterance for UI display. Call FIRST every turn, " +
                "then immediately speak the translation in parallel.",
            parameters = mapOf(
                "text" to ParameterConfig(
                    type = "STRING",
                    description = "Exact transcript in original language and script. " +
                        "Use \"...\" if unintelligible.",
                ),
                "language" to ParameterConfig(
                    type = "STRING",
                    description = "ISO code: \"ru\", \"uk\", \"de\", \"en\", or \"unknown\".",
                ),
            ),
            required = listOf("text", "language"),
        ),
    )

    override val initialUserMessage: String = ""

    override suspend fun onEnter() {
        logger.d("TranslatorSession v5.0: onEnter (parallel function + voice)")
    }

    override suspend fun onExit() {
        logger.d("TranslatorSession v5.0: onExit")
    }

    override suspend fun handleToolCall(call: FunctionCall): String? {
        return when (call.name) {
            "submit_user_speech" -> {
                val text = (call.args["text"] as? String)?.trim().orEmpty()
                val lang = (call.args["language"] as? String)?.trim()?.lowercase().orEmpty()

                if (text.isNotEmpty() && text != "...") {
                    logger.d("TranslatorSession: user speech [$lang]: $text")
                    _userSpeechFlow.tryEmit(
                        UserSpeechEvent(text = text, language = lang.ifEmpty { "unknown" })
                    )
                } else {
                    logger.d("TranslatorSession: skipping unintelligible/empty")
                }

                // МИНИМАЛЬНЫЙ ответ — модель уже знает что делать дальше из system prompt.
                // Никаких "system_instruction" внутри tool response — это замедляет.
                """{"ok":true}"""
            }
            else -> """{"ok":false}"""
        }
    }
}