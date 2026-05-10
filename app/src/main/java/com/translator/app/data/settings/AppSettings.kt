package com.translator.app.data.settings
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val apiKey: String = "",
    val model: String = "models/gemini-3.1-flash-live-preview",
    val voiceId: String = "Puck",
    val useAec: Boolean = true,
    val playbackVolume: Int = 90,
    val micGain: Int = 100,
    val forceSpeakerOutput: Boolean = true,
    val enableServerVad: Boolean = true,
    val sendAudioStreamEnd: Boolean = true
)