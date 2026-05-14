// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ (Patch 4)
// Путь: app/src/main/java/com/translator/app/util/LogBuffer.kt
//
// Кольцевой буфер логов в памяти. AppLogger пишет сюда параллельно
// с android.util.Log, чтобы UI мог показать последние N записей без
// USB-кабеля.
//
// Потокобезопасен. Максимум 500 записей (старые вытесняются).
// ═══════════════════════════════════════════════════════════
package com.translator.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel { D, I, W, E }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val throwable: String? = null,
) {
    fun formatted(): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(timestamp))
        val levelCh = when (level) {
            LogLevel.D -> "D"
            LogLevel.I -> "I"
            LogLevel.W -> "W"
            LogLevel.E -> "E"
        }
        return "[$time] $levelCh  $message" +
            if (throwable != null) "\n  ↳ $throwable" else ""
    }
}

@Singleton
class LogBuffer @Inject constructor() {

    companion object {
        private const val MAX_SIZE = 500
    }

    private val deque = ArrayDeque<LogEntry>()
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    suspend fun append(level: LogLevel, message: String, throwable: Throwable? = null) = mutex.withLock {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            throwable = throwable?.let { "${it.javaClass.simpleName}: ${it.message}" },
        )
        deque.addLast(entry)
        while (deque.size > MAX_SIZE) {
            deque.removeFirst()
        }
        _entries.value = deque.toList()
    }

    suspend fun clear() = mutex.withLock {
        deque.clear()
        _entries.value = emptyList()
    }

    /** Экспортировать все логи в строку для копирования/шаринга. */
    suspend fun exportAsText(): String = mutex.withLock {
        buildString {
            deque.forEach { appendLine(it.formatted()) }
        }
    }
}