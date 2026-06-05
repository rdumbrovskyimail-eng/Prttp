// Путь: app/src/main/java/com/translator/app/presentation/therapy/TherapyViewModel.kt
//
// ViewModel сессии пси-ассистента. Сшивает воедино:
//   • connect к Gemini Live с инжектом профиля+дневника (TherapistSession),
//   • микрофонный цикл и проигрывание (тот же AudioEngine, что у переводчика),
//   • обработку function-call'ов (GeminiEvent.ToolCall → TherapistToolHandler),
//   • кризисное состояние для баннера,
//   • лёгкий авто-реконнект и мониторинг сети.
//
// Построен по образцу TranslatorViewModel, чтобы поведение транспорта/аудио
// совпадало с уже отлаженным переводчиком.
//
// ПРИМЕЧАНИЕ про общий LiveClient: он @Singleton и общий с переводчиком.
// Поэтому перед стартом терапии убедись, что сессия переводчика остановлена
// (в навигации останавливай один экран перед открытием другого). Если нужна
// полная изоляция — заведи отдельный qualified LiveClient в DI.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.presentation.therapy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.GeminiLiveForegroundService
import com.translator.app.data.NetworkMonitor
import com.translator.app.data.PatientRepository
import com.translator.app.data.settings.AppSettings
import com.translator.app.domain.AudioEngine
import com.translator.app.domain.LiveClient
import com.translator.app.domain.model.GeminiEvent
import com.translator.app.domain.model.RiskLevel
import com.translator.app.domain.model.SessionConfig
import com.translator.app.therapy.TherapistSession
import com.translator.app.therapy.TherapistToolHandler
import com.translator.app.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class TherapyViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val liveClient: LiveClient,
    private val audioEngine: AudioEngine,
    private val networkMonitor: NetworkMonitor,
    private val settingsStore: DataStore<AppSettings>,
    private val repo: PatientRepository,
    private val toolHandler: TherapistToolHandler,
    private val logger: AppLogger
) : ViewModel() {

    private val _state = MutableStateFlow(TherapyUiState())
    val state = _state.asStateFlow()
    val audioPlaybackFlow = audioEngine.playbackSync

    private var micJob: Job? = null
    private var reconnectJob: Job? = null
    private val sessionMutex = Mutex()

    private val lastSeenTurnId = AtomicLong(Long.MIN_VALUE)
    private val hasModelOutputThisTurn = AtomicBoolean(false)
    private val networkLost = AtomicBoolean(false)

    @Volatile private var cachedSettings: AppSettings = AppSettings()
    @Volatile private var activeApiKey: String = ""
    @Volatile private var fgsStarted = false
    @Volatile private var connected = false

    init {
        viewModelScope.launch { audioEngine.initPlayback() }
        observeEvents()
        observeNetwork()
        observeProfileName()

        // Кризисный флаг от ассистента → поднимаем баннер на экране.
        toolHandler.onCrisisFlag = { level, reason ->
            if (level.severity >= RiskLevel.HIGH.severity) {
                _state.update { it.copy(crisis = CrisisLevel.Elevated, crisisReason = reason) }
            }
        }
    }

    private fun observeProfileName() {
        viewModelScope.launch {
            repo.profile.collect { p ->
                _state.update { it.copy(patientName = p.displayName) }
            }
        }
    }

    // ── публичные действия экрана ─────────────────────────────────────────────

    fun startSession() {
        if (connected) return
        viewModelScope.launch {
            val settings = settingsStore.data.first()
            cachedSettings = settings
            if (settings.apiKey.isBlank()) {
                _state.update { it.copy(phase = TherapyPhase.Idle, lastCaption = "API-ключ не задан в настройках.") }
                return@launch
            }
            activeApiKey = settings.apiKey
            _state.update { it.copy(phase = TherapyPhase.Connecting) }

            // Аудио-настройки как у переводчика.
            audioEngine.setPlaybackVolume(settings.playbackVolume / 100f)
            audioEngine.setMicGain(settings.micGain / 100f)
            audioEngine.setSpeakerRouting(settings.forceSpeakerOutput)
            audioEngine.setUseAec(settings.useAec)

            startForegroundServiceSafe(settings.forceSpeakerOutput)
            runCatching { liveClient.resetSession() }
            connect(freshSession = true)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            reconnectJob?.cancel(); reconnectJob = null
            micJob?.cancel(); micJob = null
            runCatching { audioEngine.stopCapture() }
            runCatching { audioEngine.flushPlayback() }
            runCatching { liveClient.disconnect() }
            runCatching { liveClient.resetSession() }
            stopForegroundServiceSafe()
            connected = false
            _state.update { it.copy(phase = TherapyPhase.Idle, micMuted = false) }
        }
    }

    fun toggleMute() {
        val muting = !_state.value.micMuted
        _state.update { it.copy(micMuted = muting) }
        if (muting) stopMic() else startMic()
    }

    fun dismissCrisisBanner() {
        _state.update { it.copy(crisis = CrisisLevel.None) }
    }

    /** Вызывается после выдачи разрешения на микрофон. */
    fun onMicPermissionGranted() {
        if (!fgsStarted) startForegroundServiceSafe(cachedSettings.forceSpeakerOutput)
        if (connected && !_state.value.micMuted) startMic()
    }

    // ── подключение ───────────────────────────────────────────────────────────

    private suspend fun connect(freshSession: Boolean) = sessionMutex.withLock {
        // Профиль и дневник — мгновенно из памяти репозитория, инжектятся в промпт.
        val profile = repo.profile.value
        val journal = repo.journal.value
        val base = TherapistSession.buildConfig(
            profile = profile,
            recentJournal = journal,
            voiceId = cachedSettings.voiceId,
            model = cachedSettings.model
        )
        val handle = if (freshSession) null else liveClient.sessionHandle
        val config: SessionConfig = base.copy(sessionHandle = handle)

        runCatching {
            liveClient.connect(activeApiKey, config, logRaw = cachedSettings.logRawWebSocketFrames)
        }.onFailure { e ->
            logger.e("therapy connect failed: ${e.message}", e)
            _state.update { it.copy(phase = TherapyPhase.Idle, lastCaption = "Не удалось подключиться: ${e.message}") }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            _state.update { it.copy(phase = TherapyPhase.Reconnecting) }
            delay(cachedSettings.reconnectBaseDelayMs)
            connect(freshSession = false)
        }
    }

    // ── микрофон ───────────────────────────────────────────────────────────────

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun startMic() {
        if (!hasMicPermission() || micJob != null) return
        if (!fgsStarted) startForegroundServiceSafe(cachedSettings.forceSpeakerOutput)
        micJob = viewModelScope.launch {
            audioEngine.startCapture()
            audioEngine.micOutput.collect { chunk -> liveClient.sendAudio(chunk) }
        }
    }

    private fun stopMic() {
        micJob?.cancel(); micJob = null
        viewModelScope.launch { runCatching { audioEngine.stopCapture() } }
    }

    // ── события Gemini ──────────────────────────────────────────────────────────

    private fun observeEvents() {
        viewModelScope.launch {
            liveClient.events.collect { event ->
                when (event) {
                    is GeminiEvent.SetupComplete -> {
                        connected = true
                        _state.update { it.copy(phase = TherapyPhase.Listening) }
                        viewModelScope.launch {
                            delay(150)
                            if (!_state.value.micMuted) startMic()
                        }
                    }

                    is GeminiEvent.AudioChunk -> {
                        if (event.turnId < lastSeenTurnId.get()) return@collect
                        if (event.turnId > lastSeenTurnId.get()) lastSeenTurnId.set(event.turnId)
                        audioEngine.enqueuePlayback(event.pcmData)
                        hasModelOutputThisTurn.set(true)
                        if (_state.value.phase != TherapyPhase.AssistantSpeaking) {
                            _state.update { it.copy(phase = TherapyPhase.AssistantSpeaking) }
                        }
                    }

                    // СУБТИТР последней реплики ассистента (для доступности).
                    is GeminiEvent.OutputTranscript -> {
                        _state.update { it.copy(lastCaption = event.text.take(220)) }
                    }

                    // ★ ГЛАВНОЕ: ассистент пишет/читает базу через function calls.
                    is GeminiEvent.ToolCall -> {
                        viewModelScope.launch {
                            val responses = toolHandler.handle(event.calls)
                            liveClient.sendToolResponse(responses) // синхронно — отвечаем сразу
                        }
                    }

                    is GeminiEvent.ToolCallCancellation -> { /* sync-режим: no-op */ }

                    is GeminiEvent.Interrupted -> {
                        runCatching { audioEngine.flushPlayback() }
                        hasModelOutputThisTurn.set(false)
                        lastSeenTurnId.set(Long.MIN_VALUE)
                        if (connected) _state.update { it.copy(phase = TherapyPhase.Listening) }
                    }

                    is GeminiEvent.TurnComplete -> {
                        audioEngine.onTurnComplete()
                        hasModelOutputThisTurn.set(false)
                        lastSeenTurnId.set(Long.MIN_VALUE)
                        if (connected) _state.update { it.copy(phase = TherapyPhase.Listening) }
                    }

                    is GeminiEvent.GoAway -> scheduleReconnect()

                    is GeminiEvent.Disconnected -> {
                        connected = false
                        micJob?.cancel(); micJob = null
                        runCatching { audioEngine.stopCapture() }
                        val permanent = event.code in setOf(1008, 4001, 4003)
                        val graceful = event.code == 1000 || event.code == 1001
                        if (permanent) {
                            _state.update { it.copy(phase = TherapyPhase.Idle, lastCaption = "Связь прервана (${event.code}).") }
                        } else if (!graceful) {
                            scheduleReconnect()
                        } else {
                            _state.update { it.copy(phase = TherapyPhase.Idle) }
                        }
                    }

                    is GeminiEvent.ConnectionError -> {
                        _state.update { it.copy(lastCaption = event.message) }
                    }

                    else -> {}
                }
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { ok ->
                if (!ok) { networkLost.set(true); return@collect }
                if (networkLost.compareAndSet(true, false) && activeApiKey.isNotEmpty() && !connected) {
                    scheduleReconnect()
                }
            }
        }
    }

    // ── foreground service ──────────────────────────────────────────────────────

    private fun startForegroundServiceSafe(forceSpeaker: Boolean) {
        if (!hasMicPermission()) return
        runCatching {
            val intent = GeminiLiveForegroundService.startIntent(appContext, forceSpeaker)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent)
            else appContext.startService(intent)
            fgsStarted = true
        }.onFailure { fgsStarted = false; logger.e("FGS start failed: ${it.message}") }
    }

    private fun stopForegroundServiceSafe() {
        if (!fgsStarted) return
        runCatching { appContext.startService(GeminiLiveForegroundService.stopIntent(appContext)) }
        fgsStarted = false
    }

    override fun onCleared() {
        super.onCleared()
        toolHandler.onCrisisFlag = null
        endSession()
    }
}
