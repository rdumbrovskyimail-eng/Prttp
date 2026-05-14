package com.translator.app.presentation.settings

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

enum class ModelTestState { IDLE, TESTING, SUCCESS, ERROR }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: DataStore<AppSettings>
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    // Состояния для UI проверки модели
    private val _modelTestState = MutableStateFlow(ModelTestState.IDLE)
    val modelTestState = _modelTestState.asStateFlow()

    private val _modelTestError = MutableStateFlow<String?>(null)
    val modelTestError = _modelTestError.asStateFlow()

    init {
        settingsStore.data
            .distinctUntilChanged()
            .onEach { fromDisk ->
                _settings.update { current ->
                    if (current == fromDisk) current else fromDisk
                }
            }
            .launchIn(viewModelScope)
    }

    fun update(transform: AppSettings.() -> AppSettings) {
        _settings.update(transform)
        viewModelScope.launch {
            settingsStore.updateData { it.transform() }
        }
    }

    fun resetModelTestState() {
        _modelTestState.value = ModelTestState.IDLE
        _modelTestError.value = null
    }

    /**
     * Открывает реальный WebSocket для проверки существования и доступности модели.
     */
    fun testAndApplyModel(modelName: String, onSuccessClearCustom: () -> Unit) {
        val apiKey = settings.value.apiKey
        if (apiKey.isEmpty()) {
            _modelTestState.value = ModelTestState.ERROR
            _modelTestError.value = "Сначала введите API ключ"
            return
        }

        _modelTestState.value = ModelTestState.TESTING
        _modelTestError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey")
                .build()

            val isResolved = java.util.concurrent.atomic.AtomicBoolean(false)
            val normalized = if (modelName.startsWith("models/")) modelName else "models/$modelName"

            fun resolve(state: ModelTestState, err: String? = null, onSuccess: Boolean = false) {
                if (!isResolved.compareAndSet(false, true)) return
                _modelTestState.value = state
                _modelTestError.value = err
                if (onSuccess) {
                    update { copy(model = modelName) }
                    viewModelScope.launch(Dispatchers.Main) { onSuccessClearCustom() }
                }
            }

            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    ws.send("""{"setup": {"model": "$normalized"}}""")
                }
                override fun onMessage(ws: WebSocket, text: String) {
                    if (text.contains("setupComplete")) {
                        resolve(ModelTestState.SUCCESS, onSuccess = true)
                        ws.close(1000, "ok")
                    }
                }
                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    resolve(ModelTestState.ERROR, "Ошибка $code: $reason")
                }
                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    resolve(ModelTestState.ERROR, t.message ?: "Ошибка сети")
                }
            })

            delay(6000)
            if (!isResolved.get()) {
                resolve(ModelTestState.ERROR, "Таймаут ответа от сервера")
                ws.cancel()
            }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}