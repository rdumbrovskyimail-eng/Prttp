// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА
// Путь: app/src/main/java/com/translator/app/presentation/translator/TranslatorViewModel.kt
//
// ВОЗВРАЩЕНО:
//   [+] Reconnect с экспоненциальным backoff (как в старой версии)
//   [+] Авто-ротация API ключей при rate-limit
//   [+] Запуск/остановка GeminiLiveForegroundService (стабильность фона)
//   [+] Передача всех настроек в AudioEngine: jitter, boost, AEC, gain
//   [+] Лог-параметр logRawWebSocketFrames
//   [+] Watchdog зависшего ответа модели
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.GeminiLiveForegroundService
import com.translator.app.data.settings.AppSettings
import com.translator.app.domain.AudioEngine
import com.translator.app.domain.LiveClient
import com.translator.app.domain.model.GeminiEvent
import com.translator.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class ConnectionStatus { Disconnected, Connecting, Reconnecting, Ready, Recording }

data class TranslatorState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isMicActive: Boolean = false,
    val isAiSpeaking: Boolean = false,
    val pairs: List<TranslationPair> = emptyList(),
    val error: String? = null,
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
    val totalTokens: Int = 0
)

@HiltViewModel
class TranslatorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val liveClient: LiveClient,
    private val audioEngine: AudioEngine,
    private val settingsStore: DataStore<AppSettings>,
    private val logger: AppLogger
) : ViewModel() {

    private val _state = MutableStateFlow(TranslatorState())
    val state = _state.asStateFlow()
    val audioPlaybackFlow = audioEngine.playbackSync

    private var micJob: Job? = null
    private var stuckTurnWatchdogJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0

    @Volatile private var nextPairId: Long = 1L
    @Volatile private var currentOpenPairId: Long? = null
    @Volatile private var lastAiAudioChunkAtMs: Long = 0L
    @Volatile private var hasModelOutputThisTurn: Boolean = false

    @Volatile private var cachedSettings: AppSettings = AppSettings()
    @Volatile private var activeApiKey: String = ""

    private val pairMutex = Mutex()

    init {
        viewModelScope.launch { audioEngine.initPlayback() }
        observeGeminiEvents()
    }

    fun toggleMic() {
        if (_state.value.isMicActive) stopMic() else startMic()
    }

    fun startSession() {
        // Защита от двойного запуска (LaunchedEffect + повторная навигация)
        if (_state.value.connectionStatus != ConnectionStatus.Disconnected) {
            logger.d("startSession ignored — already ${_state.value.connectionStatus}")
            return
        }
        viewModelScope.launch {
            val settings = settingsStore.data.first()
            cachedSettings = settings

            if (settings.apiKey.isEmpty()) {
                _state.update { it.copy(error = "API ключ не задан") }
                return@launch
            }
            activeApiKey = settings.apiKey

            _state.update {
                it.copy(connectionStatus = ConnectionStatus.Connecting, pairs = emptyList(), error = null)
            }
            currentOpenPairId = null
            reconnectAttempt = 0

            // ─── Применить ВСЕ аудио-настройки ───
            audioEngine.updateJitterConfig(
                preBufferChunks = settings.jitterPreBufferChunks,
                timeoutMs = settings.jitterTimeoutMs,
                queueCapacity = settings.playbackQueueCapacity
            )
            audioEngine.setPlaybackVolume(settings.playbackVolume / 100f)
            audioEngine.setMicGain(settings.micGain / 100f)
            audioEngine.setSpeakerRouting(settings.forceSpeakerOutput)
            audioEngine.setPlaybackBoost(settings.playbackBoost)
            audioEngine.setUseAec(settings.useAec)

            // ─── Запустить FGS для стабильности в фоне ───
            runCatching {
                appContext.startService(
                    GeminiLiveForegroundService.startIntent(appContext, settings.forceSpeakerOutput)
                )
            }.onFailure { logger.e("FGS start failed: ${it.message}") }

            connectInternal()
        }
    }

    private suspend fun connectInternal() {
        val config = TranslatorSession.buildConfig(cachedSettings)
        runCatching {
            liveClient.connect(activeApiKey, config, logRaw = cachedSettings.logRawWebSocketFrames)
        }.onFailure { e ->
            logger.e("connect failed: ${e.message}", e)
            _state.update { it.copy(connectionStatus = ConnectionStatus.Disconnected, error = e.message) }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0

            micJob?.cancel()
            audioEngine.stopCapture()
            liveClient.disconnect()

            runCatching {
                appContext.startService(GeminiLiveForegroundService.stopIntent(appContext))
            }

            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    isMicActive = false,
                    isAiSpeaking = false
                )
            }
        }
    }

    private fun startMic() {
        _state.update { it.copy(isMicActive = true, connectionStatus = ConnectionStatus.Recording) }
        micJob = viewModelScope.launch {
            launch { audioEngine.micOutput.collect { chunk -> liveClient.sendAudio(chunk) } }
            audioEngine.startCapture()
        }
    }

    private fun stopMic() {
        micJob?.cancel()
        viewModelScope.launch {
            audioEngine.stopCapture()
            if (cachedSettings.sendAudioStreamEnd) {
                liveClient.sendAudioStreamEnd()
            }
            _state.update { it.copy(isMicActive = false, connectionStatus = ConnectionStatus.Ready) }
        }
    }

    private fun observeGeminiEvents() {
        viewModelScope.launch {
            liveClient.events.collect { event ->
                when (event) {
                    is GeminiEvent.SetupComplete -> {
                        reconnectAttempt = 0
                        _state.update { it.copy(connectionStatus = ConnectionStatus.Ready) }
                        // ═══ Translator UX: мгновенный старт микрофона как в LearnCoreViewModel ═══
                        // Старая логика: после SetupComplete + GREETING_WARMUP_MS=150ms → startMic().
                        // Пользователь сразу может говорить, без тапа по кнопке.
                        viewModelScope.launch {
                            delay(150)
                            if (!_state.value.isMicActive &&
                                _state.value.connectionStatus == ConnectionStatus.Ready
                            ) {
                                startMic()
                            }
                        }
                    }
                    is GeminiEvent.AudioChunk -> {
                        lastAiAudioChunkAtMs = System.currentTimeMillis()
                        hasModelOutputThisTurn = true
                        _state.update { it.copy(isAiSpeaking = true) }
                        audioEngine.enqueuePlayback(event.pcmData)
                        startStuckTurnWatchdog()
                    }
                    is GeminiEvent.InputTranscript -> {
                        val pairId = currentOpenPairId ?: openNewPair()
                        updatePair(pairId) {
                            val newText = it.originalText + event.text
                            it.copy(originalText = newText, originalLang = detectLang(newText))
                        }
                    }
                    is GeminiEvent.OutputTranscript -> {
                        val pairId = currentOpenPairId ?: openNewPair()
                        updatePair(pairId) {
                            val newText = it.translationText + event.text
                            it.copy(translationText = newText, translationLang = detectLang(newText))
                        }
                        hasModelOutputThisTurn = true
                        startStuckTurnWatchdog()
                    }
                    is GeminiEvent.Interrupted -> {
                        // Перебили модель — мгновенно сбрасываем audio buffer (как в LearnCoreViewModel)
                        runCatching { audioEngine.flushPlayback() }
                        _state.update { it.copy(isAiSpeaking = false) }
                        hasModelOutputThisTurn = false
                        stuckTurnWatchdogJob?.cancel()

                        currentOpenPairId?.let { pairId ->
                            updatePair(pairId) {
                                it.copy(
                                    originalIsFinal = true, translationIsFinal = true,
                                    originalIsRefined = true, translationIsRefined = true
                                )
                            }
                        }
                        currentOpenPairId = null
                    }
                    is GeminiEvent.TurnComplete -> {
                        audioEngine.onTurnComplete()
                        _state.update { it.copy(isAiSpeaking = false) }
                        hasModelOutputThisTurn = false
                        stuckTurnWatchdogJob?.cancel()

                        currentOpenPairId?.let { pairId ->
                            updatePair(pairId) {
                                it.copy(
                                    originalIsFinal = true, translationIsFinal = true,
                                    originalIsRefined = true, translationIsRefined = true
                                )
                            }
                        }
                        currentOpenPairId = null
                    }
                    is GeminiEvent.GenerationComplete -> {
                        _state.update { it.copy(isAiSpeaking = false) }
                        stuckTurnWatchdogJob?.cancel()
                        currentOpenPairId?.let { pairId ->
                            updatePair(pairId) { it.copy(translationIsFinal = true) }
                        }
                    }
                    is GeminiEvent.UsageMetadata -> {
                        if (cachedSettings.showUsageMetadata) {
                            _state.update {
                                it.copy(
                                    promptTokens = event.promptTokens,
                                    responseTokens = event.responseTokens,
                                    totalTokens = event.totalTokens
                                )
                            }
                        }
                    }
                    is GeminiEvent.Disconnected -> {
                        _state.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.Disconnected,
                                isMicActive = false,
                                isAiSpeaking = false
                            )
                        }
                        audioEngine.stopCapture()
                        // Reconnect только если это не штатное закрытие
                        if (event.code != 1000 && event.code != 1001) {
                            scheduleReconnect()
                        }
                    }
                    is GeminiEvent.ConnectionError -> {
                        val msg = event.message
                        val isRateLimit = msg.contains("429") || msg.contains("rate", ignoreCase = true)
                        if (isRateLimit && cachedSettings.autoRotateKeys &&
                            cachedSettings.apiKeyBackup.isNotEmpty()
                        ) {
                            activeApiKey = if (activeApiKey == cachedSettings.apiKey)
                                cachedSettings.apiKeyBackup else cachedSettings.apiKey
                            logger.d("Rate limit — rotating to backup key")
                        }
                        _state.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.Disconnected,
                                isMicActive = false,
                                isAiSpeaking = false,
                                error = msg
                            )
                        }
                        audioEngine.stopCapture()
                        scheduleReconnect()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun scheduleReconnect() {
        val maxAttempts = cachedSettings.maxReconnectAttempts
        if (reconnectAttempt >= maxAttempts) {
            logger.e("Max reconnect attempts ($maxAttempts) reached — giving up")
            reconnectAttempt = 0
            return
        }
        val baseDelay = cachedSettings.reconnectBaseDelayMs
        val maxDelay = cachedSettings.reconnectMaxDelayMs
        val delayMs = (baseDelay * (1L shl reconnectAttempt)).coerceAtMost(maxDelay)

        reconnectAttempt++
        _state.update { it.copy(connectionStatus = ConnectionStatus.Reconnecting) }
        logger.d("Reconnect attempt #$reconnectAttempt in ${delayMs}ms")

        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            connectInternal()
        }
    }

    private suspend fun openNewPair(): Long = pairMutex.withLock {
        val id = nextPairId++
        currentOpenPairId = id
        _state.update { it.copy(pairs = it.pairs + TranslationPair(id = id)) }
        id
    }

    private suspend fun updatePair(id: Long, transform: (TranslationPair) -> TranslationPair) =
        pairMutex.withLock {
            _state.update { s ->
                val idx = s.pairs.indexOfFirst { it.id == id }
                if (idx < 0) return@update s
                val newList = s.pairs.toMutableList().apply { set(idx, transform(s.pairs[idx])) }
                s.copy(pairs = newList)
            }
        }

    private fun detectLang(text: String): String {
        if (text.isBlank()) return ""
        val hasCyrillic = text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        return if (hasCyrillic) "RU" else "DE"
    }

    private fun startStuckTurnWatchdog() {
        stuckTurnWatchdogJob?.cancel()
        stuckTurnWatchdogJob = viewModelScope.launch {
            delay(5000)  // STUCK_TURN_TIMEOUT_TRANSLATOR_MS

            // Не паникуем, если модель ВСЁ ЕЩЁ играет аудио (просто длинный ответ)
            val now = System.currentTimeMillis()
            val sinceLastAudio = now - lastAiAudioChunkAtMs
            if (lastAiAudioChunkAtMs > 0L && sinceLastAudio < 2_000L) {
                logger.d("Stuck-turn watchdog: model still playing (${sinceLastAudio}ms ago), restarting")
                startStuckTurnWatchdog()
                return@launch
            }

            if (hasModelOutputThisTurn) {
                logger.w("⚠ STUCK_TURN_DETECTED — force-finalizing")
                runCatching { audioEngine.flushPlayback() }
                runCatching { audioEngine.onTurnComplete() }
                hasModelOutputThisTurn = false
                lastAiAudioChunkAtMs = 0L
                _state.update { it.copy(isAiSpeaking = false) }
                // Финализируем текущую пару
                pairMutex.withLock {
                    val openId = currentOpenPairId
                    if (openId != null) {
                        _state.update { s ->
                            val idx = s.pairs.indexOfFirst { it.id == openId }
                            if (idx < 0) return@update s
                            val finalized = s.pairs[idx].copy(
                                originalIsFinal = true, translationIsFinal = true,
                                originalIsRefined = true, translationIsRefined = true
                            )
                            s.copy(pairs = s.pairs.toMutableList().apply { set(idx, finalized) })
                        }
                        currentOpenPairId = null
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
