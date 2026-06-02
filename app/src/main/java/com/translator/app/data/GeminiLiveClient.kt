// ═══════════════════════════════════════════════════════════════════════════
// Путь: app/src/main/java/com/translator/app/data/GeminiLiveClient.kt
//
// ПОЛНАЯ ЗАМЕНА (v4.0 — разбор кадров вне сетевого потока)
//
// Что и зачем изменено:
//
//   1) РАЗБОР КАДРОВ ВЫНЕСЕН С СЕТЕВОГО ПОТОКА OkHttp.
//      Раньше json.parse(...) + Base64.decode(...) тяжёлых аудио-кадров
//      выполнялись прямо в WebSocketListener.onMessage (поток-читатель
//      OkHttp). На больших inlineData это держало поток-читатель занятым →
//      кадры накапливались → задержки и потери. Теперь onMessage лишь кладёт
//      сырой кадр в Channel и мгновенно возвращается, а отдельная корутина
//      на Dispatchers.Default разбирает кадры последовательно. Управляющие
//      кадры (setupComplete/turnComplete/...) НИКОГДА не теряются (UNLIMITED).
//
//   2) ФИКС ПОРЯДКА turnId.
//      Раньше turnComplete инкрементировал activeTurnId ДО чтения modelTurn
//      в том же кадре, поэтому «хвостовое» аудио помечалось уже СЛЕДУЮЩИМ
//      ходом. Теперь аудио тегируется текущим activeTurnId ПЕРЕД обработкой
//      границ хода (interrupted/turnComplete).
//
//   3) MULTI-PART СОБЫТИЯ 3.1.
//      Согласно докам, одно серверное событие может содержать сразу несколько
//      частей (inlineData + transcript). Разбор обрабатывает ВСЕ части кадра.
//
// Соответствие докам Gemini Live API (v1beta BidiGenerateContent):
//   • setup.model = "models/{model}"
//   • realtimeInput.audio (PCM16 16 kHz) для входа, realtimeInput.text для текста
//   • automaticActivityDetection / activityHandling / turnCoverage — VAD
//   • sessionResumption.handle, contextWindowCompression.slidingWindow
//
// Интерфейс LiveClient сохранён 1:1.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.data

import android.util.Base64
import com.translator.app.domain.LiveClient
import com.translator.app.domain.ToolResponse
import com.translator.app.domain.model.FunctionCall
import com.translator.app.domain.model.GeminiEvent
import com.translator.app.domain.model.MicAudioChunk
import com.translator.app.domain.model.SessionConfig
import com.translator.app.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class GeminiLiveClient(
    private val logger: AppLogger
) : LiveClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // readTimeout=0: серверные кадры приходят асинхронно, тайм-аута чтения нет.
    // pingInterval=20s: стабильнее на мобильной сети, чем дефолтные 60s.
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var webSocket: WebSocket? = null

    private val _events = MutableSharedFlow<GeminiEvent>(
        replay = 0, extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: Flow<GeminiEvent> = _events.asSharedFlow()

    @Volatile override var sessionHandle: String? = null
        private set

    @Volatile override var isReady: Boolean = false
        private set

    private val connectionMutex = Mutex()

    @Volatile private var logRawFrames: Boolean = false
    @Volatile private var droppedAudioChunks: Long = 0L

    // Turn-ID: каждая граница хода (setupComplete / interrupted / turnComplete)
    // начинает новый ход. Стейл-аудио старых ходов отсекается на стороне VM.
    private val currentTurnId = AtomicLong(0L)
    @Volatile private var activeTurnId: Long = 0L

    private var currentConfig: SessionConfig? = null
    @Volatile private var reconnectAttempts: Int = 0

    @Volatile private var closeCompletion: CompletableDeferred<Unit>? = null
    @Volatile private var setupWatchdog: Job? = null

    // Кольцо последних отправленных кадров — для диагностики 1007/1008.
    private val lastSentFrames = java.util.ArrayDeque<String>(3)

    // ════════════════════════════════════════════════════════════════════
    //  RX-PIPELINE: разбор кадров ВНЕ потока-читателя OkHttp
    //  onMessage кладёт сырой кадр сюда и сразу возвращается; парсер-корутина
    //  разбирает кадры на Dispatchers.Default. UNLIMITED — чтобы НИ ОДИН
    //  управляющий кадр не потерялся.
    // ════════════════════════════════════════════════════════════════════
    private val frameChannel = Channel<String>(Channel.UNLIMITED)

    init {
        internalScope.launch(Dispatchers.Default) {
            for (raw in frameChannel) {
                runCatching { parseServerMessage(raw) }
                    .onFailure { logger.e("PARSE ERROR: ${it.message}", it) }
            }
        }
    }

    private fun trackSentFrame(raw: String) {
        synchronized(lastSentFrames) {
            if (lastSentFrames.size >= 3) lastSentFrames.pollFirst()
            lastSentFrames.offerLast(raw.take(2000))
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONNECT / DISCONNECT
    // ════════════════════════════════════════════════════════════════════

    override suspend fun connect(apiKey: String, config: SessionConfig, logRaw: Boolean) {
        connectionMutex.withLock {
            if (webSocket != null) internalDisconnect()

            // Если caller явно не передал handle — обнуляем кэш, чтобы исключить
            // непроизвольный resume (например, при смене языковой пары).
            if (config.sessionHandle == null) sessionHandle = null

            currentConfig = config
            logRawFrames = logRaw
            isReady = false
            droppedAudioChunks = 0L
            synchronized(lastSentFrames) { lastSentFrames.clear() }
            closeCompletion = CompletableDeferred()

            val encodedKey = java.net.URLEncoder.encode(apiKey, "UTF-8")
            val url = "wss://${SessionConfig.WS_HOST}/${SessionConfig.WS_PATH}?key=$encodedKey"
            val handleInfo = if (config.sessionHandle != null) "with handle" else "FRESH (no handle)"
            logger.d("Connecting to ${config.model} — $handleInfo…")

            val request = Request.Builder().url(url).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                    logger.d("WS opened (${response.code}) — sending setup…")
                    reconnectAttempts = 0
                    _events.tryEmit(GeminiEvent.Connected)
                    sendSetup(config)
                    startSetupWatchdog(15_000L)
                }

                // Текстовый кадр → в очередь разбора (НЕ парсим в этом потоке).
                override fun onMessage(ws: WebSocket, text: String) {
                    if (logRawFrames) {
                        val preview = if (text.length > 500) text.take(500) + "…" else text
                        logger.d("RAW ← $preview")
                    }
                    frameChannel.trySend(text)
                }

                // Бинарный кадр → utf8 → в очередь разбора.
                override fun onMessage(ws: WebSocket, bytes: ByteString) {
                    frameChannel.trySend(bytes.utf8())
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    val desc = describeCloseCode(code)
                    logger.d("WS closed: $code $desc reason='$reason'")
                    if (code == 1007 || code == 1008) {
                        synchronized(lastSentFrames) {
                            if (lastSentFrames.isNotEmpty()) {
                                logger.e("⚠ LAST SENT FRAMES before $code:")
                                lastSentFrames.forEachIndexed { i, f -> logger.e("  [$i] $f") }
                            }
                        }
                    }
                    cancelSetupWatchdog()
                    isReady = false
                    closeCompletion?.complete(Unit)
                    _events.tryEmit(GeminiEvent.Disconnected(code, reason))
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    val status = response?.code?.let { " (HTTP $it)" } ?: ""
                    logger.e("WS failure$status: ${t.message}")
                    cancelSetupWatchdog()
                    isReady = false
                    closeCompletion?.complete(Unit)
                    _events.tryEmit(GeminiEvent.Disconnected(1006, t.message ?: "Unknown WS error"))
                }
            })
        }
    }

    private fun startSetupWatchdog(timeoutMs: Long) {
        cancelSetupWatchdog()
        setupWatchdog = internalScope.launch {
            delay(timeoutMs)
            if (!isReady && webSocket != null) {
                logger.e("⚠ SETUP TIMEOUT — no setupComplete in ${timeoutMs}ms")
                _events.tryEmit(GeminiEvent.ConnectionError("Setup timeout."))
                runCatching { webSocket?.close(1000, "setup_timeout") }
            }
        }
    }

    private fun cancelSetupWatchdog() { setupWatchdog?.cancel(); setupWatchdog = null }

    private suspend fun internalDisconnect() {
        cancelSetupWatchdog()
        val ws = webSocket
        if (ws == null) { isReady = false; return }
        val completion = closeCompletion
        runCatching { ws.close(1000, "bye") }
        if (completion != null && !completion.isCompleted) {
            withTimeoutOrNull(2000L) { completion.await() }
        }
        webSocket = null
        isReady = false
        closeCompletion = null
        // Любые stale-chunki после disconnect игнорируются (новый ход).
        activeTurnId = currentTurnId.incrementAndGet()
    }

    override suspend fun disconnect() {
        connectionMutex.withLock { internalDisconnect() }
    }

    /**
     * Сбрасывает локальный sessionHandle, чтобы СЛЕДУЮЩИЙ connect() начал свежую
     * сессию вместо resume из server-side кеша. Серверного API «удаления» сессии
     * у Gemini Live нет — handle просто протухает (resumption ~2 ч, сессия ~24 ч).
     * НЕ блокирующий, безопасно вызывать с любого потока.
     */
    override fun resetSession() {
        val had = sessionHandle != null
        sessionHandle = null
        currentConfig?.let { cfg ->
            if (cfg.sessionHandle != null) currentConfig = cfg.copy(sessionHandle = null)
        }
        if (had) logger.d("🧹 Session handle cleared — next connect() will start fresh")
    }

    // ════════════════════════════════════════════════════════════════════
    //  SETUP (doc-compliant BidiGenerateContentSetup)
    // ════════════════════════════════════════════════════════════════════

    private fun sendSetup(config: SessionConfig) {
        val raw = buildFullSetup(config).toString()
        logger.d("SETUP → ${config.model} (${raw.length} chars)")
        if (logRawFrames) raw.chunked(500).forEachIndexed { i, c -> logger.d("[setup $i] $c") }
        trackSentFrame(raw)
        webSocket?.send(raw)
    }

    private fun buildFullSetup(config: SessionConfig): JsonObject = buildJsonObject {
        put("setup", buildJsonObject {
            // proto требует "models/{model}".
            put("model", if (config.model.startsWith("models/")) config.model else "models/${config.model}")

            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add(JsonPrimitive(config.responseModality)) })
                put("temperature", config.temperature)
                put("topP", config.topP)
                put("maxOutputTokens", config.maxOutputTokens)

                if (config.responseModality == "AUDIO") {
                    put("speechConfig", buildJsonObject {
                        put("voiceConfig", buildJsonObject {
                            put("prebuiltVoiceConfig", buildJsonObject {
                                put("voiceName", config.voiceId)
                            })
                        })
                    })
                }

                // thinkingLevel для 3.1: minimal/low/medium/high (по докам).
                // Для переводчика рекомендуется "low" (см. SessionConfig/buildConfig).
                val thinkingLevel = config.latencyProfile.thinkingLevel
                if (thinkingLevel != null) {
                    put("thinkingConfig", buildJsonObject {
                        put("thinkingLevel", thinkingLevel)
                        put("includeThoughts", config.thinkingIncludeThoughts)
                    })
                }
            })

            if (config.systemInstruction.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", config.systemInstruction) })
                    })
                })
            }

            put("realtimeInputConfig", buildJsonObject {
                put("automaticActivityDetection", buildJsonObject {
                    put("disabled", !config.autoActivityDetection)
                    if (config.autoActivityDetection) {
                        put("startOfSpeechSensitivity", config.vadStartSensitivity)
                        put("endOfSpeechSensitivity", config.vadEndSensitivity)
                        put("prefixPaddingMs", config.vadPrefixPaddingMs)
                        put("silenceDurationMs", config.vadSilenceDurationMs)
                    }
                })
                put("activityHandling", config.activityHandling)
                put("turnCoverage", config.turnCoverage)
            })

            if (config.inputTranscription) put("inputAudioTranscription", buildJsonObject {})
            if (config.outputTranscription) put("outputAudioTranscription", buildJsonObject {})

            // sessionResumption включён, handle == null → сервер начнёт НОВУЮ сессию.
            if (config.enableSessionResumption) {
                put("sessionResumption", buildJsonObject {
                    config.sessionHandle?.let { put("handle", it) }
                })
            }

            if (config.enableContextCompression) {
                put("contextWindowCompression", buildJsonObject {
                    if (config.compressionTriggerTokens > 0L)
                        put("triggerTokens", config.compressionTriggerTokens)
                    put("slidingWindow", buildJsonObject {
                        if (config.compressionTargetTokens > 0L)
                            put("targetTokens", config.compressionTargetTokens)
                    })
                })
            }
        })
    }

    // ════════════════════════════════════════════════════════════════════
    //  CLIENT → SERVER
    // ════════════════════════════════════════════════════════════════════

    override fun sendAudio(chunk: MicAudioChunk) {
        val ws = webSocket
        if (!isReady || ws == null) {
            droppedAudioChunks++
            if (droppedAudioChunks % 50L == 0L) {
                logger.w("Dropped $droppedAudioChunks audio chunks (not ready)")
            }
            chunk.release()
            return
        }
        try {
            // Быстрый путь: строим JSON-строку напрямую, без аллокации JsonObject
            // на каждый аудио-кадр (40 мс). realtimeInput.audio — по докам.
            val b64 = Base64.encodeToString(chunk.data, 0, chunk.length, Base64.NO_WRAP)
            val raw = """{"realtimeInput":{"audio":{"data":"$b64","mimeType":"audio/pcm;rate=${SessionConfig.INPUT_SAMPLE_RATE}"}}}"""
            val logStub = """{"realtimeInput":{"audio":{"data":"<HIDDEN_BASE64>","mimeType":"audio/pcm;rate=${SessionConfig.INPUT_SAMPLE_RATE}"}}}"""
            trackSentFrame(logStub)
            ws.send(raw)
        } catch (e: Exception) {
            logger.e("Audio send failed: ${e.message}")
        } finally {
            chunk.release()
        }
    }

    override fun sendRealtimeText(text: String) {
        val ws = webSocket
        if (!isReady || ws == null) return
        // Для 3.1 текст во время диалога шлётся через realtimeInput.text.
        val raw = buildJsonObject {
            put("realtimeInput", buildJsonObject { put("text", text) })
        }.toString()
        trackSentFrame(raw); ws.send(raw)
    }

    override fun sendAudioStreamEnd() {
        val ws = webSocket
        if (!isReady || ws == null) return
        val raw = """{"realtimeInput":{"audioStreamEnd":true}}"""
        trackSentFrame(raw); ws.send(raw)
    }

    override fun sendTurnComplete() {
        // ПРИМЕЧАНИЕ: в режиме переводчика используется автоматический VAD,
        // поэтому ручной turnComplete не нужен. На Gemini 3.1 clientContent
        // поддерживается лишь для seeding начальной истории — здесь оставлено
        // только для совместимости интерфейса.
        val ws = webSocket
        if (!isReady || ws == null) return
        val raw = buildJsonObject {
            put("clientContent", buildJsonObject { put("turnComplete", true) })
        }.toString()
        trackSentFrame(raw); ws.send(raw)
    }

    override fun sendToolResponse(responses: List<ToolResponse>) {
        val ws = webSocket
        if (!isReady || ws == null) return
        val raw = buildJsonObject {
            put("toolResponse", buildJsonObject {
                put("functionResponses", buildJsonArray {
                    for (resp in responses) {
                        add(buildJsonObject {
                            put("name", resp.name)
                            put("id", resp.id)
                            put("response", buildJsonObject { put("result", resp.result) })
                        })
                    }
                })
            })
        }.toString()
        trackSentFrame(raw); ws.send(raw)
    }

    // ════════════════════════════════════════════════════════════════════
    //  SERVER → CLIENT (выполняется на парсер-корутине, НЕ на потоке OkHttp)
    // ════════════════════════════════════════════════════════════════════

    private fun parseServerMessage(raw: String) {
        val root = json.parseToJsonElement(raw).jsonObject

        if (root.containsKey("setupComplete")) {
            logger.d("✓ SETUP COMPLETE")
            cancelSetupWatchdog()
            isReady = true
            activeTurnId = currentTurnId.incrementAndGet()
            _events.tryEmit(GeminiEvent.SetupComplete)
        }

        root["toolCallCancellation"]?.jsonObject?.let { cancellation ->
            val ids = cancellation["ids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            _events.tryEmit(GeminiEvent.ToolCallCancellation(ids))
        }

        root["toolCall"]?.jsonObject?.let { tc ->
            val calls = tc["functionCalls"]?.jsonArray?.mapNotNull { fc ->
                val obj = fc.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val args = obj["args"]?.jsonObject?.mapValues { it.value.toString() } ?: emptyMap()
                FunctionCall(name, id, args)
            } ?: emptyList()
            if (calls.isNotEmpty()) _events.tryEmit(GeminiEvent.ToolCall(calls))
        }

        root["sessionResumptionUpdate"]?.jsonObject?.let { update ->
            val resumable = update["resumable"]?.jsonPrimitive?.booleanOrNull ?: false
            val newHandle = update["newHandle"]?.jsonPrimitive?.content
            val lastConsumed = update["lastConsumedClientMessageIndex"]?.jsonPrimitive?.longOrNull
            if (newHandle != null && resumable) {
                sessionHandle = newHandle
                _events.tryEmit(GeminiEvent.SessionHandleUpdate(newHandle, resumable, lastConsumed))
            }
        }

        root["goAway"]?.jsonObject?.let { goAway ->
            val timeLeft = goAway["timeLeft"]?.jsonPrimitive?.content
            _events.tryEmit(GeminiEvent.GoAway(timeLeft))
        }

        root["usageMetadata"]?.jsonObject?.let { usage ->
            val prompt = usage["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            val resp = (usage["responseTokenCount"] ?: usage["candidatesTokenCount"])
                ?.jsonPrimitive?.intOrNull ?: 0
            val total = usage["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            _events.tryEmit(GeminiEvent.UsageMetadata(prompt, resp, total))
        }

        root["serverContent"]?.jsonObject?.let { sc ->
            // ── 1. Транскрипция (multi-part: может прийти вместе с аудио) ──
            sc["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { _events.tryEmit(GeminiEvent.InputTranscript(it)) }

            sc["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { _events.tryEmit(GeminiEvent.OutputTranscript(it)) }

            // ── 2. modelTurn: текст + аудио. ВАЖНО: тегируем аудио ТЕКУЩИМ ходом
            //       ДО обработки границ (turnComplete/interrupted ниже), иначе
            //       хвостовое аудио попадёт в следующий ход и будет отброшено. ──
            val turnForThisFrame = activeTurnId
            (sc["modelTurn"]?.jsonObject?.get("parts") as? JsonArray)?.forEach { part ->
                val obj = part.jsonObject
                obj["text"]?.jsonPrimitive?.content?.let {
                    _events.tryEmit(GeminiEvent.ModelText(it))
                }
                obj["inlineData"]?.jsonObject?.let { inline ->
                    val mime = inline["mimeType"]?.jsonPrimitive?.content.orEmpty()
                    if (mime.startsWith("audio/pcm")) {
                        inline["data"]?.jsonPrimitive?.content?.let { b64 ->
                            val pcm = Base64.decode(b64, Base64.DEFAULT)
                            _events.tryEmit(GeminiEvent.AudioChunk(pcm, turnForThisFrame))
                        }
                    }
                }
            }

            // ── 3. Границы хода (инкремент ПОСЛЕ тегирования аудио) ──
            if (sc["interrupted"]?.jsonPrimitive?.booleanOrNull == true) {
                activeTurnId = currentTurnId.incrementAndGet()
                _events.tryEmit(GeminiEvent.Interrupted)
            }
            if (sc["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) {
                activeTurnId = currentTurnId.incrementAndGet()
                _events.tryEmit(GeminiEvent.TurnComplete)
            }
            if (sc["generationComplete"]?.jsonPrimitive?.booleanOrNull == true) {
                _events.tryEmit(GeminiEvent.GenerationComplete)
            }
        }
    }

    private fun describeCloseCode(code: Int): String = when (code) {
        1000 -> "[Normal Closure]"
        1001 -> "[Going Away]"
        1002 -> "[Protocol Error]"
        1003 -> "[Unsupported Data]"
        1006 -> "[Abnormal Closure]"
        1007 -> "[Invalid Frame Payload — JSON/enum]"
        1008 -> "[Policy Violation — модель/ключ]"
        1011 -> "[Internal Server Error]"
        1013 -> "[Try Again Later]"
        4000 -> "[Gemini: Session expired]"
        4001 -> "[Gemini: Invalid setup]"
        4002 -> "[Gemini: Rate limited (429)]"
        4003 -> "[Gemini: Auth failed]"
        else -> "[Code $code]"
    }
}
