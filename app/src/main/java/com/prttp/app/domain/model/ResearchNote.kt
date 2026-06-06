package com.prttp.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class ResearchNote(
    val id: String,
    val query: String,
    val topic: String = "",
    val summary: String,
    val sources: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)