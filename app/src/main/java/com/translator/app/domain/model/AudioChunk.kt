// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ
// Путь: app/src/main/java/com/translator/app/domain/model/AudioChunk.kt
//
// Zero-allocation паттерн для горячего аудио-пути:
//   • AudioBufferPool — пул переиспользуемых ByteArray
//   • AudioChunk      — обёртка с самовозвратом в пул
//
// Контракт: КАЖДЫЙ consumer ОБЯЗАН вызывать AudioChunk.release()
//           в finally-блоке после использования.
// ═══════════════════════════════════════════════════════════
package com.translator.app.domain.model

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lock-light пул переиспользуемых ByteArray фиксированного размера.
 *
 * Назначение: убрать ByteArray-аллокации из capture-loop, которые иначе
 * генерируют ~50 KB/s мусора и вызывают периодические GC-паузы.
 *
 * Поведение при истощении пула: graceful degradation — borrow() выделит
 * новый массив. Это безопасно, но временно увеличит GC-давление.
 *
 * Поведение при переполнении: release() лишнего массива молча его дропает
 * (GC заберёт). Это нормально при network spike, когда consumer отстаёт.
 *
 * Потокобезопасность: ArrayBlockingQueue → безопасно для multi-producer /
 * multi-consumer. Операции poll/offer неблокирующие (~50 нс).
 */
class AudioBufferPool(
    private val bufferSize: Int,
    poolCapacity: Int = 12
) {
    private val pool = ArrayBlockingQueue<ByteArray>(poolCapacity)

    init {
        // Прогреваем пул: с первого же буфера никаких аллокаций.
        repeat(poolCapacity) { pool.offer(ByteArray(bufferSize)) }
    }

    fun borrow(): ByteArray = pool.poll() ?: ByteArray(bufferSize)

    fun release(buf: ByteArray) {
        if (buf.size == bufferSize) pool.offer(buf)
    }
}

/**
 * Аудио-чанк с самовозвратом в пул.
 *
 * Поля:
 *   data   — буфер из пула. Размер фиксирован = bufferSize пула.
 *            ВНИМАНИЕ: размер ≥ length. Читать ТОЛЬКО [0 until length].
 *   length — реально валидных байт в data.
 *
 * Контракт consumer'а:
 *   try { /* читаем chunk.data[0 until chunk.length] */ }
 *   finally { chunk.release() }
 *
 * Двойной release безопасен (AtomicBoolean гарантирует идемпотентность).
 */
class AudioChunk(
    val data: ByteArray,
    val length: Int,
    private val pool: AudioBufferPool
) {
    private val released = AtomicBoolean(false)

    fun release() {
        if (released.compareAndSet(false, true)) {
            pool.release(data)
        }
    }
}
