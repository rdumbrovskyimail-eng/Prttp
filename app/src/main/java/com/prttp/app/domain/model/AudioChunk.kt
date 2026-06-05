package com.prttp.app.domain.model

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lock-light пул переиспользуемых ByteArray фиксированного размера.
 * Назначение: убрать аллокации из capture-loop (~50 KB/s мусора → GC паузы).
 */
class AudioBufferPool(
    private val bufferSize: Int,
    poolCapacity: Int = 32
) {
    private val pool = ArrayBlockingQueue<ByteArray>(poolCapacity)

    init { repeat(poolCapacity) { pool.offer(ByteArray(bufferSize)) } }

    fun borrow(): ByteArray = pool.poll() ?: ByteArray(bufferSize)

    fun release(buf: ByteArray) {
        if (buf.size == bufferSize) pool.offer(buf)
    }
}

/**
 * Аудио-чанк микрофонного потока с самовозвратом в пул.
 * Контракт consumer'а:
 *   try { /* читаем chunk.data[0 until chunk.length] */ }
 *   finally { chunk.release() }
 * Двойной release безопасен (AtomicBoolean).
 */
class MicAudioChunk(
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