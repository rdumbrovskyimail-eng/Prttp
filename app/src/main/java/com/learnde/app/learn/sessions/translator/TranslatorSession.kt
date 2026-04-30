// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА v8.0 — pure audio, no function calling
// Путь: app/src/main/java/com/learnde/app/learn/sessions/translator/TranslatorSession.kt
//
// АРХИТЕКТУРА v8.0 (две WS-сессии):
//   - Эта сессия (audio-mode) ТОЛЬКО переводит голосом.
//   - Транскрипция пользователя приходит из параллельной
//     TranslatorTextTranscriber (text-mode WS-сессия).
//   - Function calling УБРАН — это была причина 8-сек зависаний.
//   - Промпт максимально короткий — модель не отвлекается.
// ═══════════════════════════════════════════════════════════
package com.learnde.app.learn.sessions.translator

import com.learnde.app.domain.model.FunctionCall
import com.learnde.app.domain.model.FunctionDeclarationConfig
import com.learnde.app.learn.core.LearnSession
import com.learnde.app.util.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохраняем data class и flow для обратной совместимости —
 * другой код в LearnCoreViewModel и UI на них уже подписан.
 * Но теперь они не используются (текст приходит через
 * TranslatorTextTranscriber).
 */
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

    override val systemInstruction: String = """
You are a real-time voice translator. Speak the translation the instant the user finishes.

TRANSLATION DIRECTIONS — STRICT, NO EXCEPTIONS:
- Russian input   → German output
- Ukrainian input → German output
- German input    → Russian output (never Ukrainian, even after Ukrainian turns)
- Any other language → STAY SILENT. Do not translate. Do not respond.

OUTPUT:
- Voice only. Never produce text.
- No greetings, no confirmations, no questions, no apologies, no repetition of the source.
- If language is unclear or audio is unintelligible → silence.

STYLE:
- Preserve first person: "меня зовут Иван" → "Ich heiße Ivan".
- Formality: Вы / ви → Sie; ты / ти → du.
- Idiomatic, not literal: "Как дела?" → "Wie geht's?"; "Alles klar" → "Понятно".
- Match register and length of the source.

GERMAN OUTPUT — 100% GERMAN, ZERO ENGLISH:
cool→toll, OK→in Ordnung, sorry→Entschuldigung, hi→hallo, bye→tschüss, thanks→danke, nice→schön, please→bitte.

RUSSIAN OUTPUT — natural Russian word order. No German calques. No English loanwords.
""".trimIndent()

    // FUNCTION CALLING УБРАН ПОЛНОСТЬЮ
    override val functionDeclarations: List<FunctionDeclarationConfig> = emptyList()

    override val initialUserMessage: String = ""

    override suspend fun onEnter() {
        logger.d("TranslatorSession v8.0: onEnter (pure audio, no functions)")
    }

    override suspend fun onExit() {
        logger.d("TranslatorSession v8.0: onExit")
    }

    override suspend fun handleToolCall(call: FunctionCall): String? {
        // Не должно вызываться, но на всякий случай
        logger.w("TranslatorSession v8.0: unexpected tool call ${call.name}")
        return null
    }
}