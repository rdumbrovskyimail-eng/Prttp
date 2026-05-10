// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА
// Путь: app/src/main/java/com/translator/app/presentation/translator/TranslatorViewModel.kt
//
// ВОЗВРАЩЕНО ИЗ LearnCoreViewModel:
//   [+] Reconnect с экспоненциальным backoff
//   [+] Авто-ротация API ключей при rate-limit
//   [+] Запуск/остановка GeminiLiveForegroundService через
//       startForegroundService (Android 8+ требует именно его для FGS)
//   [+] Передача всех настроек в AudioEngine
//   [+] Логирование RAW WS-кадров (logRawWebSocketFrames)
//   [+] Watchdog зависшего ответа модели (5000ms, как для translator)
//   [+] Авто-старт микрофона через 150ms после SetupComplete,
//       но ТОЛЬКО если RECORD_AUDIO разрешен (иначе ждём действия пользователя)
//   [+] Interrupted делает audioEngine.flushPlayback() (мгновенный сброс
//       audio buffer при перебивании)
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
    @Volatile private var fgsStarted: Boolean = false

    private val pairMutex = Mutex()

    init {
        viewModelScope.launch { audioEngine.initPlayback() }
        observeGeminiEvents()
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

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
            // КРИТИЧНО: на Android 8+ FGS должен стартоваться через
            // startForegroundService, а не startService. Иначе на Android 12+
            // отбираются права на VOICE_COMMUNICATION → AudioRecord UOE.
            startForegroundServiceSafe(settings.forceSpeakerOutput)

            connectInternal()
        }
    }

    private fun startForegroundServiceSafe(forceSpeaker: Boolean) {
        if (!hasMicPermission()) {
            logger.w("FGS not started — RECORD_AUDIO not granted yet")
            return
        }
        runCatching {
            val intent = GeminiLiveForegroundService.startIntent(appContext, forceSpeaker)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            fgsStarted = true
            logger.d("FGS started (forceSpeaker=$forceSpeaker)")
        }.onFailure {
            fgsStarted = false
            logger.e("FGS start failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun stopForegroundServiceSafe() {
        if (!fgsStarted) return
        runCatching {
            appContext.startService(GeminiLiveForegroundService.stopIntent(appContext))
        }
        fgsStarted = false
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

            stopForegroundServiceSafe()

            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    isMicActive = false,
                    isAiSpeaking = false
                )
            }
        }
    }

    /**
     * Вызывается из UI после того, как RECORD_AUDIO permission получен.
     * Если FGS ещё не запустился (потому что permission не было) — стартуем сейчас.
     */
    fun onMicPermissionGranted() {
        if (!fgsStarted) {
            startForegroundServiceSafe(cachedSettings.forceSpeakerOutput)
        }
        if (!_state.value.isMicActive &&
            (_state.value.connectionStatus == ConnectionStatus.Ready ||
             _state.value.connectionStatus == ConnectionStatus.Recording)
        ) {
            startMic()
        }
    }

    private fun startMic() {
        if (!hasMicPermission()) {
            logger.w("startMic skipped — no RECORD_AUDIO permission")
            return
        }
        if (_state.value.isMicActive) {
            logger.d("startMic skipped — already active")
            return
        }
        // Если FGS ещё не стартовал — стартуем сейчас (после permission grant)
        if (!fgsStarted) startForegroundServiceSafe(cachedSettings.forceSpeakerOutput)

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
                        // Translator UX: мгновенный старт микрофона через 150ms,
                        // но ТОЛЬКО если RECORD_AUDIO уже разрешён.
                        viewModelScope.launch {
                            delay(150)
                            if (hasMicPermission() &&
                                !_state.value.isMicActive &&
                                _state.value.connectionStatus == ConnectionStatus.Ready
                            ) {
                                startMic()
                            } else if (!hasMicPermission()) {
                                logger.d("Auto-start mic deferred — waiting for RECORD_AUDIO permission")
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
            delay(5000)

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