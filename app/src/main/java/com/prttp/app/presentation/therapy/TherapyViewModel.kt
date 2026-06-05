package com.prttp.app.presentation.therapy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prttp.app.GeminiLiveForegroundService
import com.prttp.app.data.NetworkMonitor
import com.prttp.app.data.PatientRepository
import com.prttp.app.data.PexelsImageRepository
import com.prttp.app.data.settings.AppSettings
import com.prttp.app.domain.AudioEngine
import com.prttp.app.domain.LiveClient
import com.prttp.app.domain.model.ConversationMessage
import com.prttp.app.domain.model.FunctionCall
import com.prttp.app.domain.model.GeminiEvent
import com.prttp.app.domain.model.RiskLevel
import com.prttp.app.domain.model.SessionConfig
import com.prttp.app.therapy.ImageTheme
import com.prttp.app.therapy.TherapistSession
import com.prttp.app.therapy.TherapistToolHandler
import com.prttp.app.util.AppLogger
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

private const val STUCK_TURN_TIMEOUT_MS = 45_000L
private const val RESPONSE_TIMEOUT_MS   = 30_000L

@HiltViewModel
class TherapyViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val liveClient: LiveClient,
    private val audioEngine: AudioEngine,
    private val networkMonitor: NetworkMonitor,
    private val settingsStore: DataStore<AppSettings>,
    private val repo: PatientRepository,
    private val toolHandler: TherapistToolHandler,
    private val pexels: PexelsImageRepository,
    private val logger: AppLogger
) : ViewModel() {

    private val _state = MutableStateFlow(TherapyUiState())
    val state = _state.asStateFlow()
    val audioPlaybackFlow = audioEngine.playbackSync

    private var micJob: Job? = null
    private var reconnectJob: Job? = null
    private var stuckTurnWatchdogJob: Job? = null
    private var responseTimeoutJob: Job? = null
    private var imageDismissJob: Job? = null
    private val sessionMutex = Mutex()

    companion object {
        private const val STUCK_TURN_TIMEOUT_MS = 45_000L
        private const val RESPONSE_TIMEOUT_MS   = 30_000L
    }

    private val lastSeenTurnId = AtomicLong(Long.MIN_VALUE)
    private val hasModelOutputThisTurn = AtomicBoolean(false)
    private val networkLost = AtomicBoolean(false)

    private val accumulatedUserText = StringBuilder()
    private val accumulatedModelText = StringBuilder()

    @Volatile private var cachedSettings: AppSettings = AppSettings()
    @Volatile private var activeApiKey: String = ""
    @Volatile private var fgsStarted = false
    @Volatile private var connected = false

    init {
        viewModelScope.launch { audioEngine.initPlayback() }
        observeEvents()
        observeNetwork()
        observeProfileName()

        toolHandler.onCrisisFlag = { level, reason ->
            if (level.severity >= RiskLevel.HIGH.severity) {
                _state.update { it.copy(crisis = CrisisLevel.Elevated, crisisReason = reason) }
            }
        }

        toolHandler.onShowImage = { query, caption ->
            loadTherapyImage(query, caption)
        }
    }

    private fun observeProfileName() {
        viewModelScope.launch {
            repo.profile.collect { p ->
                _state.update { it.copy(patientName = p.displayName, sessionCount = p.sessionCount) }
            }
        }
    }

    fun startSession() {
        if (connected || _state.value.phase == TherapyPhase.Connecting) return
        viewModelScope.launch {
            val settings = settingsStore.data.first()
            cachedSettings = settings
            if (settings.apiKey.isBlank()) {
                _state.update { it.copy(phase = TherapyPhase.Idle, lastCaption = "API-ключ не задан в настройках.") }
                return@launch
            }
            repo.startNewSession()
            activeApiKey = settings.apiKey
            _state.update { it.copy(phase = TherapyPhase.Connecting, sessionCount = repo.getSessionCount()) }

            audioEngine.setPlaybackVolume(settings.playbackVolume / 100f)
            audioEngine.setMicGain(settings.micGain / 100f)
            audioEngine.setSpeakerRouting(settings.forceSpeakerOutput)
            audioEngine.setUseAec(settings.useAec)

            toolHandler.pexelsApiKey = settings.pexelsApiKey
            startForegroundServiceSafe(settings.forceSpeakerOutput)
            runCatching { liveClient.resetSession() }
            connect(freshSession = true)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            stuckTurnWatchdogJob?.cancel(); stuckTurnWatchdogJob = null
            responseTimeoutJob?.cancel(); responseTimeoutJob = null
            imageDismissJob?.cancel(); imageDismissJob = null
            _state.update { it.copy(therapyImage = null, imageLoading = false) }
            saveAccumulatedTurnMessages()
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

    fun switchImageTheme(theme: ImageTheme) {
        viewModelScope.launch {
            repo.setImageTheme(theme)
            _state.update { it.copy(currentImageTheme = theme) }
            val current = _state.value.therapyImage ?: return@launch
            loadTherapyImage(current.query, current.caption)
        }
    }

    fun onMicPermissionGranted() {
        if (!fgsStarted) startForegroundServiceSafe(cachedSettings.forceSpeakerOutput)
        if (connected && !_state.value.micMuted) startMic()
    }

    private suspend fun connect(freshSession: Boolean) = sessionMutex.withLock {
        val profile = repo.getProfile()
        val journal = repo.getJournal()
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

    private fun saveAccumulatedTurnMessages() {
        val userText = accumulatedUserText.toString().trim()
        if (userText.isNotEmpty()) {
            viewModelScope.launch { repo.addMessage(ConversationMessage.ROLE_USER, userText) }
            accumulatedUserText.clear()
        }
        val modelText = accumulatedModelText.toString().trim()
        if (modelText.isNotEmpty()) {
            viewModelScope.launch { repo.addMessage(ConversationMessage.ROLE_MODEL, modelText) }
            accumulatedModelText.clear()
        }
    }

    private fun formatToolCalls(calls: List<FunctionCall>): String {
        if (calls.isEmpty()) return ""
        return calls.joinToString(" | ") { call ->
            val cleanArgs = call.args.mapValues { (_, v) -> unwrapJsonString(v) }
            when (call.name) {
                "update_profile" -> {
                    val cat = categoryTranslate(cleanArgs["category"] ?: "")
                    val key = cleanArgs["key"] ?: ""
                    "⚡ Сохранение [$cat] ➜ $key"
                }
                "set_patient_name" -> "⚡ Запись имени: ${cleanArgs["name"]}"
                "save_session_note" -> "⚡ Архивация: запись итогов встречи"
                "log_mood" -> "⚡ Фиксация настроения: ${cleanArgs["score"]}/10"
                "add_homework" -> "⚡ Назначение ДЗ: ${cleanArgs["title"]}"
                "complete_homework" -> "⚡ Выполнение ДЗ (ID: ${cleanArgs["id"]})"
                "flag_clinical_concern" -> "⚠ Контроль рисков: флаг [${cleanArgs["level"]?.uppercase()}]"
                "read_recent_journal" -> "⚡ Чтение дневника за ${cleanArgs["days"] ?: "14"} дн."
                "read_full_profile" -> "⚡ Анализ всей терапевтической карты"
                "read_dialogue_history" -> "⚡ Анализ архива: чтение последних ${cleanArgs["limit"] ?: "30"} реплик диалога"
                else -> "⚡ Вызов: ${call.name}"
            }
        }
    }

    private fun unwrapJsonString(raw: String): String {
        val t = raw.trim()
        return if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\n", "\n")
        } else t
    }

    private fun categoryTranslate(cat: String): String = when (cat.lowercase().trim()) {
        "presenting_concern" -> "Запрос"
        "history" -> "Анамнез"
        "symptom" -> "Симптом"
        "trigger" -> "Триггер"
        "core_belief" -> "Убеждение"
        "cognitive_style" -> "Искажение"
        "defense_mechanism" -> "Защита"
        "maladaptive_schema" -> "Схема"
        "behavior_pattern" -> "Паттерн"
        "emotional_block" -> "Аффект"
        "coping_strategy" -> "Копинг"
        "therapeutic_goal" -> "Цель"
        else -> cat
    }

    private fun startStuckTurnWatchdog() {
        stuckTurnWatchdogJob?.cancel()
        stuckTurnWatchdogJob = viewModelScope.launch {
            delay(STUCK_TURN_TIMEOUT_MS)
            if (_state.value.phase == TherapyPhase.AssistantSpeaking) {
                logger.w("stuckTurnWatchdog: TurnComplete не пришёл за ${STUCK_TURN_TIMEOUT_MS}ms")
                runCatching { audioEngine.flushPlayback() }
                runCatching { audioEngine.onTurnComplete() }
                hasModelOutputThisTurn.set(false)
                lastSeenTurnId.set(Long.MIN_VALUE)
                saveAccumulatedTurnMessages()
                if (connected) _state.update { it.copy(phase = TherapyPhase.Listening, lastCaption = "") }
            }
        }
    }

    private fun startResponseTimeoutWatchdog() {
        responseTimeoutJob?.cancel()
        responseTimeoutJob = viewModelScope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            if (_state.value.phase == TherapyPhase.Listening && connected && !hasModelOutputThisTurn.get()) {
                logger.w("responseTimeout: ИИ не ответил за ${RESPONSE_TIMEOUT_MS}ms")
                scheduleReconnect()
            }
        }
    }

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
                        if (hasModelOutputThisTurn.compareAndSet(false, true)) {
                            startStuckTurnWatchdog()
                            responseTimeoutJob?.cancel()
                        }
                        if (_state.value.phase != TherapyPhase.AssistantSpeaking) {
                            _state.update { it.copy(phase = TherapyPhase.AssistantSpeaking) }
                        }
                    }

                    is GeminiEvent.InputTranscript -> {
                        accumulatedUserText.append(event.text)
                        startResponseTimeoutWatchdog()
                    }

                    is GeminiEvent.OutputTranscript -> {
                        accumulatedModelText.append(event.text)
                        _state.update { it.copy(lastCaption = event.text.take(220)) }
                    }

                    is GeminiEvent.ToolCall -> {
                        viewModelScope.launch {
                            val statusText = formatToolCalls(event.calls)
                            _state.update { it.copy(activeActionStatus = statusText) }

                            val responses = toolHandler.handle(event.calls)
                            liveClient.sendToolResponse(responses)

                            delay(3500)
                            _state.update { it.copy(activeActionStatus = "") }
                        }
                    }

                    is GeminiEvent.ToolCallCancellation -> {}

                    is GeminiEvent.Interrupted -> {
                        stuckTurnWatchdogJob?.cancel(); stuckTurnWatchdogJob = null
                        responseTimeoutJob?.cancel(); responseTimeoutJob = null
                        runCatching { audioEngine.flushPlayback() }
                        hasModelOutputThisTurn.set(false)
                        lastSeenTurnId.set(Long.MIN_VALUE)
                        if (connected) _state.update { it.copy(phase = TherapyPhase.Listening) }
                        saveAccumulatedTurnMessages()
                    }

                    is GeminiEvent.TurnComplete -> {
                        stuckTurnWatchdogJob?.cancel(); stuckTurnWatchdogJob = null
                        responseTimeoutJob?.cancel(); responseTimeoutJob = null
                        audioEngine.onTurnComplete()
                        hasModelOutputThisTurn.set(false)
                        lastSeenTurnId.set(Long.MIN_VALUE)
                        if (connected) _state.update { it.copy(phase = TherapyPhase.Listening, lastCaption = "") }
                        saveAccumulatedTurnMessages()
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

    private fun loadTherapyImage(query: String, caption: String) {
        viewModelScope.launch {
            // Проверяем что фичя включена и ключ задан
            val settings = settingsStore.data.first()
            if (!settings.therapyImagesEnabled || settings.pexelsApiKey.isBlank()) return@launch

            _state.update { it.copy(imageLoading = true) }

            val theme = repo.getImageTheme()
            val image = pexels.fetchImage(
                apiKey  = settings.pexelsApiKey,
                query   = query,
                caption = caption,
                theme   = theme
            )

            _state.update { it.copy(therapyImage = image, imageLoading = false, currentImageTheme = theme) }

            if (image != null) {
                // Авто-сброс через 40 секунд
                imageDismissJob?.cancel()
                imageDismissJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(40_000L)
                    _state.update { it.copy(therapyImage = null) }
                }
            }
        }
    }

    fun dismissTherapyImage() {
        imageDismissJob?.cancel()
        _state.update { it.copy(therapyImage = null, imageLoading = false) }
    }

    override fun onCleared() {
        super.onCleared()
        toolHandler.onCrisisFlag = null
        endSession()
    }
}