package com.prttp.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class ConversationMessage(
    val role: String,
    val text: String,
    val sessionId: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"

        fun user(text: String, sessionId: String = "") = ConversationMessage(ROLE_USER, text, sessionId)
        fun model(text: String, sessionId: String = "") = ConversationMessage(ROLE_MODEL, text, sessionId)
    }
}