package com.translator.app.domain

import com.translator.app.domain.model.GeminiEvent
import com.translator.app.domain.model.MicAudioChunk
import com.translator.app.domain.model.SessionConfig
import kotlinx.coroutines.flow.Flow

interface LiveClient {
    val events: Flow<GeminiEvent>
    val sessionHandle: String?
    val isReady: Boolean

    suspend fun connect(apiKey: String, config: SessionConfig, logRaw: Boolean = false)
    fun sendAudio(chunk: MicAudioChunk)
    fun sendRealtimeText(text: String)
    fun sendAudioStreamEnd()
    fun sendTurnComplete()
    fun sendToolResponse(responses: List<ToolResponse>)
    suspend fun disconnect()

    /**
     * Сбрасывает локально хранимый sessionHandle.
     *
     * ВАЖНО: согласно официальной документации Gemini Live API, если в
     * BidiGenerateContentSetup.sessionResumption.handle передать null (то есть
     * вообще не передавать handle), сервер начнёт совершенно новую сессию
     * без восстановления кеша/истории. Никаких других серверных вызовов для
     * "очистки сессии" не существует — старая сессия просто остаётся в
     * хранилище сервера и истечёт сама (по умолчанию через 24 часа,
     * resumption tokens — через 2 часа).
     *
     * Метод НЕ обращается к сети — он только чистит наше состояние, чтобы
     * следующий connect() гарантированно был fresh start.
     *
     * Используется при смене языковой пары, чтобы Gemini не восстановил
     * предыдущий system instruction и историю переводов на старом языке.
     */
    fun resetSession()
}

data class ToolResponse(
    val name: String,
    val id: String,
    val result: String
)
