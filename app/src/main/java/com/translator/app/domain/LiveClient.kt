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
}

data class ToolResponse(
    val name: String,
    val id: String,
    val result: String
)