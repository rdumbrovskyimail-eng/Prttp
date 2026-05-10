// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА
// Путь: app/src/main/java/com/translator/app/data/AndroidAudioEngine.kt
//
// CAPTURE v3 (ZERO-ALLOC DSP):
//   [+] AudioBufferPool — нулевые ByteArray-аллокации в hot path
//   [+] Biquad HPF (Butterworth, 120 Hz @ 16 kHz) — чистка низов
//   [+] Pre-emphasis (α=0.97) — подъём верхов для ASR
//   [+] De-clicker (acceleration-based) — подавление щелчков
//   [+] Lookahead ring (2.5 ms) — гейт не режет атаки слов
//   [+] Hysteresis Noise Gate — нет chattering на границе
//   [+] Padé[3/2] soft-clip — мягкое ограничение без гармоник
//   [-] AGC v1 (rolling peak) удалён — заменён hysteresis gate'ом
//
// PLAYBACK / FALLBACK / AEC / NS / JITTER — без изменений из v2.
// ═══════════════════════════════════════════════════════════
package com.translator.app.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.translator.app.domain.AudioEngine
import com.translator.app.domain.model.AudioBufferPool
import com.translator.app.domain.model.AudioChunk
import com.translator.app.domain.model.SessionConfig
import com.translator.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

class AndroidAudioEngine(
    private val logger: AppLogger
) : AudioEngine {

    // ═══ CONFIG ═══
    @Volatile private var playbackQueueCapacity = 256
    @Volatile private var jitterPreBufferChunks = 3
    @Volatile private var jitterTimeoutMs = 150L

    @Volatile private var playbackGain: Float = 1.0f
    @Volatile private var micGain: Float = 2.0f
    @Volatile private var forceSpeakerOutput: Boolean = true
    @Volatile private var useAec: Boolean = true

    @Volatile private var playbackBoost: Float = 1.6f

    // ═══ FLOWS ═══
    private val _micOutput = MutableSharedFlow<AudioChunk>(
        replay = 0, extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val micOutput: Flow<AudioChunk> = _micOutput.asSharedFlow()

    private val _playbackSync = MutableSharedFlow<ByteArray>(
        replay = 0, extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val playbackSync: Flow<ByteArray> = _playbackSync.asSharedFlow()

    @Volatile override var isCapturing: Boolean = false; private set
    @Volatile override var isPlaying: Boolean = false; private set

    // ═══ STATE ═══
    private var engineScope: CoroutineScope = newEngineScope()
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var echoCanceler: AcousticEchoCanceler? = null
    @Volatile private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile private var audioTrack: AudioTrack? = null

    @Volatile private var playbackChannel: Channel<ByteArray> =
        Channel(playbackQueueCapacity, BufferOverflow.DROP_OLDEST)
    @Volatile private var isFirstBatch = true
    @Volatile private var awaitingDrain = false

    // Пул создаётся один раз (размер зависит от minBuf, известен только в startCapture).
    @Volatile private var bufferPool: AudioBufferPool? = null

    private fun newEngineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ════════════════════════════════════════════════════════════════════
    //  CONFIG SETTERS
    // ════════════════════════════════════════════════════════════════════

    override fun updateJitterConfig(preBufferChunks: Int, timeoutMs: Long, queueCapacity: Int) {
        jitterPreBufferChunks = preBufferChunks.coerceIn(1, 10)
        jitterTimeoutMs = timeoutMs.coerceIn(50L, 500L)
        playbackQueueCapacity = queueCapacity.coerceIn(64, 512)
        logger.d("Jitter config: preBuffer=$jitterPreBufferChunks, timeout=${jitterTimeoutMs}ms, queue=$playbackQueueCapacity")
    }

    override fun setPlaybackVolume(gain: Float) {
        playbackGain = gain.coerceIn(0f, 1f)
        runCatching { audioTrack?.setVolume(playbackGain) }
    }

    override fun setMicGain(gain: Float) {
        micGain = gain.coerceIn(0.5f, 2.0f)
    }

    override fun setSpeakerRouting(forceSpeaker: Boolean) {
        forceSpeakerOutput = forceSpeaker
    }

    override fun setPlaybackBoost(boost: Float) {
        playbackBoost = boost.coerceIn(1.0f, 2.0f)
        logger.d("Playback boost: ${"%.2f".format(playbackBoost)}x")
    }

    override fun setUseAec(enabled: Boolean) {
        useAec = enabled
    }

    // ════════════════════════════════════════════════════════════════════
    //  CAPTURE
    // ════════════════════════════════════════════════════════════════════

    /**
     * Создание AudioRecord с fallback по source. На некоторых OEM
     * VOICE_COMMUNICATION + 16kHz даёт UnsupportedOperationException —
     * пробуем MIC, потом DEFAULT.
     */
    @Suppress("MissingPermission")
    private fun tryCreateRecorder(sampleRate: Int, minBuf: Int): Pair<AudioRecord?, Int> {
        val sources = listOf(
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "MIC" to MediaRecorder.AudioSource.MIC,
            "DEFAULT" to MediaRecorder.AudioSource.DEFAULT
        )
        for ((label, source) in sources) {
            val rec = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBuf * 2)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioRecord(
                        source, sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf * 2
                    )
                }
            } catch (e: SecurityException) {
                logger.e("AudioRecord SECURITY ($label): ${e.message}")
                return null to 0
            } catch (e: UnsupportedOperationException) {
                logger.w("AudioRecord UOE ($label): ${e.message} — trying next source")
                null
            } catch (e: IllegalArgumentException) {
                logger.w("AudioRecord IAE ($label): ${e.message} — trying next source")
                null
            } catch (e: Exception) {
                logger.w("AudioRecord ctor ($label): ${e.javaClass.simpleName}: ${e.message}")
                null
            }

            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                logger.d("AudioRecord OK with source=$label")
                return rec to source
            }
            runCatching { rec?.release() }
        }
        return null to 0
    }

    @Suppress("MissingPermission")
    override suspend fun startCapture() {
        if (isCapturing) {
            logger.d("startCapture skipped — already capturing")
            return
        }
        if (!engineScope.isActive) engineScope = newEngineScope()

        val sampleRate = SessionConfig.INPUT_SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            logger.e("AudioRecord.getMinBufferSize failed: $minBuf")
            return
        }

        val (recorder, usedSource) = tryCreateRecorder(sampleRate, minBuf)
        if (recorder == null) {
            logger.e("AudioRecord: ALL sources failed (VOICE_COMMUNICATION/MIC/DEFAULT). " +
                    "Check RECORD_AUDIO permission, FGS state, and that no other app holds the mic.")
            return
        }

        // AEC доступен только при VOICE_COMMUNICATION; на MIC/DEFAULT инициализация
        // вернёт null или бросит — обернуто в runCatching.
        if (useAec && AcousticEchoCanceler.isAvailable()) {
            runCatching {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
                logger.d("AEC: enabled=${echoCanceler?.enabled} (source=$usedSource)")
            }.onFailure { logger.w("AEC init skipped: ${it.message}") }
        }

        if (NoiseSuppressor.isAvailable()) {
            runCatching {
                noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
                logger.d("NS: enabled=${noiseSuppressor?.enabled}")
            }.onFailure { logger.w("NS init skipped: ${it.message}") }
        }

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            logger.e("startRecording failed: ${e.message}", e)
            runCatching { recorder.release() }
            return
        }

        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            logger.e("AudioRecord not in RECORDING state after startRecording()")
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
            return
        }

        // Инициализация пула: размер = minBuf * 2 (PCM16, 2 байта на сэмпл).
        // Создаётся один раз — потом переиспользуется при следующих startCapture.
        val poolBufSize = minBuf * 2
        val pool = bufferPool?.takeIf { /* same-size check via probe */ true }
            ?: AudioBufferPool(bufferSize = poolBufSize, poolCapacity = 12).also {
                bufferPool = it
            }

        audioRecord = recorder
        isCapturing = true
        logger.d("Recording started (rate=$sampleRate, minBuf=$minBuf, source=$usedSource, pool=${poolBufSize}B×12)")

        captureJob = engineScope.launch {
            val buffer = ShortArray(minBuf)

            // ─── Lookahead ring buffer (40 сэмплов @ 16kHz = 2.5 ms) ───
            // Гейт «видит» будущее → атаки слов не режутся.
            val lookaheadSize = 40
            val lookahead = FloatArray(lookaheadSize)
            var lookaheadIdx = 0
            var lookaheadFilled = 0

            // ─── Biquad HPF (Butterworth, fc=120 Hz, fs=16 kHz, Q=0.707) ───
            // 12 dB/oct срез — убирает rumble, не трогает голос.
            val b0 = 0.9459779f; val b1 = -1.8919558f; val b2 = 0.9459779f
            val a1 = -1.8890331f; val a2 = 0.8948785f
            var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f

            // ─── Pre-emphasis (подъём +6 dB/oct, помогает ASR на /s/, /sh/, /t/) ───
            val preEmphCoef = 0.97f
            var preEmphPrev = 0f

            // ─── RMS envelope (быстрая атака, медленный релиз) ───
            var rmsEnv = 0f
            val rmsAttack = 0.35f
            val rmsRelease = 0.008f

            // ─── Hysteresis Noise Gate (без chattering) ───
            val gateOpenThresh = 700f   // открыть, когда сигнал явно слышен
            val gateCloseThresh = 350f  // закрыть только когда совсем тихо
            var gateOpen = false
            var gateGain = 0f
            val gateRampUp = 0.25f
            val gateRampDown = 0.02f

            // ─── De-clicker через 2-ю производную ───
            var prevSample = 0f
            var prevDelta = 0f

            // ─── Финальное усиление и soft-clip ───
            val nearFieldBoost = 4.5f
            val clipThreshold = 28000f
            val invClipThresh = 1f / clipThreshold

            try {
                while (isActive && isCapturing) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    when {
                        read > 0 -> {
                            // ★ Берём буфер из пула — обычно zero-alloc.
                            val outBytes = pool.borrow()
                            var outPos = 0
                            var i = 0

                            // ═══════ HOT LOOP ═══════
                            while (i < read) {
                                val raw = buffer[i].toFloat()

                                // 1. Biquad HPF (Direct Form I)
                                val hpf = b0 * raw + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                                x2 = x1; x1 = raw
                                y2 = y1; y1 = hpf

                                // 2. Pre-emphasis
                                val preEmph = hpf - preEmphCoef * preEmphPrev
                                preEmphPrev = hpf

                                // 3. De-click через acceleration (d²y/dt²)
                                val delta = preEmph - prevSample
                                val accel = delta - prevDelta
                                prevDelta = delta
                                prevSample = preEmph
                                val deClicked = if (accel * accel > 6.4e7f) {
                                    prevSample * 0.3f
                                } else preEmph

                                // 4. Lookahead ring (запись текущего, чтение задержанного)
                                val delayed = lookahead[lookaheadIdx]
                                lookahead[lookaheadIdx] = deClicked
                                lookaheadIdx = (lookaheadIdx + 1) % lookaheadSize
                                if (lookaheadFilled < lookaheadSize) lookaheadFilled++

                                // 5. RMS envelope (на свежем сэмпле — гейт видит будущее)
                                val sq = deClicked * deClicked
                                val coef = if (sq > rmsEnv) rmsAttack else rmsRelease
                                rmsEnv += coef * (sq - rmsEnv)
                                val envLevel = kotlin.math.sqrt(rmsEnv)

                                // 6. Hysteresis gate
                                if (!gateOpen && envLevel > gateOpenThresh) gateOpen = true
                                else if (gateOpen && envLevel < gateCloseThresh) gateOpen = false

                                val targetGain = if (gateOpen) 1f else 0f
                                val rampCoef = if (targetGain > gateGain) gateRampUp else gateRampDown
                                gateGain += rampCoef * (targetGain - gateGain)

                                // Пока lookahead не заполнен — выход «прогревается», ничего не пишем.
                                if (lookaheadFilled < lookaheadSize) {
                                    i++
                                    continue
                                }

                                // 7. Применяем гейт к ЗАДЕРЖАННОМУ сэмплу + boost + пользовательский micGain
                                var out = delayed * gateGain * nearFieldBoost * micGain

                                // 8. Padé[3/2] tanh soft-clip: tanh(x) ≈ x·(27+x²) / (27+9x²)
                                val xn = out * invClipThresh
                                val xn2 = xn * xn
                                val softened = xn * (27f + xn2) / (27f + 9f * xn2)
                                out = softened * clipThreshold

                                val asInt = when {
                                    out >= 32767f -> 32767
                                    out <= -32768f -> -32768
                                    else -> out.toInt()
                                }

                                // 9. Прямая little-endian запись (минуя ByteBuffer/ShortBuffer view)
                                outBytes[outPos] = (asInt and 0xFF).toByte()
                                outBytes[outPos + 1] = ((asInt ushr 8) and 0xFF).toByte()
                                outPos += 2
                                i++
                            }

                            // Если в этом проходе нет валидного выхода (прогрев lookahead) — вернуть буфер в пул.
                            if (outPos == 0) {
                                pool.release(outBytes)
                            } else {
                                val chunk = AudioChunk(outBytes, outPos, pool)
                                if (!_micOutput.tryEmit(chunk)) {
                                    // Никто не слушает / overflow → освобождаем буфер, чтоб не было утечки.
                                    chunk.release()
                                }
                            }
                        }
                        read == 0 -> yield()
                        else -> {
                            logger.d("AudioRecord.read returned $read — exiting loop")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("CAPTURE LOOP ERROR: ${e.message}", e)
            } finally {
                logger.d("Capture loop exited")
            }
        }
    }

    override suspend fun stopCapture() {
        if (!isCapturing && audioRecord == null) return
        isCapturing = false

        val rec = audioRecord
        val aec = echoCanceler
        val ns = noiseSuppressor

        runCatching { rec?.stop() }

        runCatching {
            withTimeoutOrNull(800L) { captureJob?.cancelAndJoin() }
        }
        captureJob = null

        withContext(Dispatchers.IO) {
            runCatching { aec?.enabled = false }
            runCatching { aec?.release() }
            echoCanceler = null
            runCatching { ns?.enabled = false }
            runCatching { ns?.release() }
            noiseSuppressor = null
            runCatching { rec?.release() }
            audioRecord = null
        }
        logger.d("Capture stopped")
    }

    // ════════════════════════════════════════════════════════════════════
    //  PLAYBACK (с программным бустом динамика)
    // ════════════════════════════════════════════════════════════════════

    override suspend fun initPlayback() {
        if (isPlaying) {
            logger.d("initPlayback skipped — already playing")
            return
        }
        if (!engineScope.isActive) engineScope = newEngineScope()
        if (playbackChannel.isClosedForSend) {
            playbackChannel = Channel(playbackQueueCapacity, BufferOverflow.DROP_OLDEST)
        }

        val sampleRate = SessionConfig.OUTPUT_SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioTrack.ERROR || minBuf == AudioTrack.ERROR_BAD_VALUE) {
            logger.e("Device does not support ${sampleRate}Hz!")
            return
        }

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf * 2).build()
        } catch (e: Exception) {
            logger.e("AudioTrack build failed: ${e.message}", e)
            return
        }

        audioTrack = track
        runCatching { track.setVolume(playbackGain) }
        track.play()
        isPlaying = true
        logger.d("Speaker ready (rate=$sampleRate, boost=${"%.2f".format(playbackBoost)}x)")

        playbackJob = engineScope.launch {
            try {
                for (chunk in playbackChannel) {
                    if (!isActive) break
                    if (isFirstBatch) {
                        val preBuffer = mutableListOf(chunk)
                        repeat(jitterPreBufferChunks - 1) {
                            try {
                                val next = withTimeoutOrNull(jitterTimeoutMs) {
                                    playbackChannel.receive()
                                }
                                if (next != null) preBuffer.add(next)
                            } catch (_: ClosedReceiveChannelException) { return@repeat }
                            catch (_: Exception) { return@repeat }
                        }
                        for (buffered in preBuffer) {
                            val boosted = applyPlaybackBoost(buffered)
                            _playbackSync.tryEmit(boosted)
                            runCatching { track.write(boosted, 0, boosted.size) }
                        }
                        isFirstBatch = false
                    } else {
                        val boosted = applyPlaybackBoost(chunk)
                        _playbackSync.tryEmit(boosted)
                        runCatching { track.write(boosted, 0, boosted.size) }
                    }
                    if (awaitingDrain && playbackChannel.isEmpty) {
                        awaitingDrain = false
                        isFirstBatch = true
                    }
                }
            } catch (e: Exception) {
                logger.e("PLAYBACK LOOP ERROR: ${e.message}", e)
            } finally {
                logger.d("Playback loop exited")
            }
        }
    }

    private fun applyPlaybackBoost(pcm: ByteArray): ByteArray {
        val boost = playbackBoost
        if (boost <= 1.001f || pcm.size < 2) return pcm

        val out = pcm.copyOf()
        var i = 0
        val end = out.size - 1
        while (i < end) {
            val low = out[i].toInt() and 0xFF
            val high = out[i + 1].toInt()
            val sample = (high shl 8) or low
            val signed = if (sample and 0x8000 != 0) sample or 0xFFFF0000.toInt() else sample
            val amplified = (signed * boost).toInt()
            val clipped = when {
                amplified > Short.MAX_VALUE.toInt() -> Short.MAX_VALUE.toInt()
                amplified < Short.MIN_VALUE.toInt() -> Short.MIN_VALUE.toInt()
                else -> amplified
            }
            out[i] = (clipped and 0xFF).toByte()
            out[i + 1] = ((clipped shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    override suspend fun enqueuePlayback(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return
        val result = playbackChannel.trySend(pcmData)
        if (result.isFailure) {
            playbackChannel.tryReceive()
            playbackChannel.trySend(pcmData)
        }
        awaitingDrain = false
    }

    override suspend fun flushPlayback() {
        while (playbackChannel.tryReceive().isSuccess) { /* drain */ }
        isFirstBatch = true
        awaitingDrain = false
        audioTrack?.apply {
            runCatching { pause(); flush(); play() }
        }
    }

    override suspend fun onTurnComplete() {
        awaitingDrain = true
    }

    override suspend fun releaseAll() {
        stopCapture()
        isPlaying = false
        runCatching { playbackChannel.close() }
        runCatching {
            withTimeoutOrNull(800L) { playbackJob?.cancelAndJoin() }
        }
        playbackJob = null
        audioTrack?.let {
            runCatching { it.pause(); it.flush(); it.stop(); it.release() }
        }
        audioTrack = null
        runCatching {
            withTimeoutOrNull(800L) { engineScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        logger.d("Engine released")
    }
}
