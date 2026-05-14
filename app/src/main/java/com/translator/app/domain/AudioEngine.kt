package com.translator.app.domain

import kotlinx.coroutines.flow.Flow
import com.translator.app.domain.model.MicAudioChunk

interface AudioEngine {

    val micOutput: Flow<MicAudioChunk>
    val isCapturing: Boolean
    val isPlaying: Boolean

    suspend fun startCapture()
    suspend fun stopCapture()
    suspend fun enqueuePlayback(pcmData: ByteArray)
    suspend fun flushPlayback()
    suspend fun onTurnComplete()
    suspend fun initPlayback()
    suspend fun releaseAll()

    fun updateJitterConfig(preBufferChunks: Int, timeoutMs: Long, queueCapacity: Int)
    val playbackSync: Flow<ByteArray>
    fun setPlaybackVolume(gain: Float)
    fun setMicGain(gain: Float)
    fun setSpeakerRouting(forceSpeaker: Boolean)
    fun setPlaybackBoost(boost: Float)
    fun setUseAec(enabled: Boolean)
}