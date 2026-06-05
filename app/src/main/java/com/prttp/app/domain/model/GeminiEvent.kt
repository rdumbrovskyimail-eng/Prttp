package com.prttp.app.domain.model

sealed interface GeminiEvent {

    data object Connected : GeminiEvent
    data object SetupComplete : GeminiEvent
    data class Disconnected(val code: Int, val reason: String) : GeminiEvent
    data class ConnectionError(val message: String) : GeminiEvent

    /** PCM-аудио модели. turnId — ID хода, к которому относится этот чанк. */
    data class AudioChunk(val pcmData: ByteArray, val turnId: Long) : GeminiEvent {
        override fun equals(other: Any?): Boolean =
            other is AudioChunk && turnId == other.turnId && pcmData.contentEquals(other.pcmData)
        override fun hashCode(): Int = 31 * turnId.hashCode() + pcmData.contentHashCode()
    }

    data class ModelText(val text: String) : GeminiEvent
    data class InputTranscript(val text: String) : GeminiEvent
    data class OutputTranscript(val text: String) : GeminiEvent

    data object Interrupted : GeminiEvent
    data object TurnComplete : GeminiEvent
    data object GenerationComplete : GeminiEvent

    data class ToolCall(val calls: List<FunctionCall>) : GeminiEvent
    data class ToolCallCancellation(val ids: List<String>) : GeminiEvent

    data class SessionHandleUpdate(
        val handle: String,
        val resumable: Boolean = true,
        val lastConsumedIndex: Long? = null
    ) : GeminiEvent

    data class GoAway(val timeLeft: String? = null) : GeminiEvent

    data class UsageMetadata(
        val promptTokens: Int,
        val responseTokens: Int,
        val totalTokens: Int
    ) : GeminiEvent
}

data class FunctionCall(
    val name: String,
    val id: String,
    val args: Map<String, String>
)