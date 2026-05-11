// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ
// Путь: app/src/main/java/com/translator/app/presentation/translator/animations/AudioLevelState.kt
//
// Общий "слушатель" audio flow для всех 4 анимаций.
//
// Идея: каждая анимация подписывается на тот же Flow<ByteArray>,
// получает плавно сглаженный уровень громкости [0..1] и
// использует его как драйвер для своей визуализации.
//
// Никаких сборок мусора в hot-path: RMS считается прямо на месте,
// сглаживание — экспоненциальное (Float, без аллокаций).
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator.animations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Подписывается на audioFlow, возвращает сглаженный уровень громкости [0..1].
 *
 * @param audioFlow поток PCM16 little-endian кусков (24 kHz mono).
 * @param active анимация активна (Gemini говорит) — если false, level плавно
 *               затухает в 0 без подписки на flow.
 * @param attack коэффициент атаки (быстрая реакция на нарастание).
 * @param release коэффициент релиза (плавное затухание).
 *
 * Возвращает State<Float> — анимации могут читать его в Canvas без recomposition.
 */
@Composable
fun rememberAudioLevel(
    audioFlow: Flow<ByteArray>,
    active: Boolean,
    attack: Float = 0.35f,
    release: Float = 0.08f
): State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    val lastSampleNanos = remember { mutableLongStateOf(0L) }

    // 1. Когда активен — слушаем flow и обновляем raw level.
    LaunchedEffect(audioFlow, active) {
        if (!active) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        audioFlow.collect { pcm ->
            val rms = computeRms16(pcm)
            // Атака — мгновенная, релиз — плавный (через withFrameNanos ниже).
            level.floatValue = max(level.floatValue, rms)
            lastSampleNanos.longValue = System.nanoTime()
        }
    }

    // 2. Каждый кадр — плавное затухание (если давно не приходил сэмпл).
    LaunchedEffect(active) {
        var prev = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val dtSec = ((now - prev).coerceAtLeast(0L) / 1_000_000_000f).coerceAtMost(0.1f)
                prev = now
                val sinceSample = now - lastSampleNanos.longValue
                val cur = level.floatValue
                level.floatValue = when {
                    !active -> max(0f, cur - release * dtSec * 8f)
                    sinceSample > 60_000_000L /* 60 ms тишины */ ->
                        max(0f, cur - release * dtSec * 8f)
                    else -> cur
                }
            }
        }
    }

    return level
}

/**
 * RMS PCM16 little-endian, нормализован в [0..1] с лёгким "поджимом" к верху.
 * Без аллокаций: один проход по массиву, double-аккумулятор.
 */
private fun computeRms16(pcm: ByteArray): Float {
    if (pcm.size < 2) return 0f
    var sum = 0.0
    val count = pcm.size / 2
    var i = 0
    val end = pcm.size - 1
    while (i < end) {
        val low = pcm[i].toInt() and 0xFF
        val high = pcm[i + 1].toInt()
        val sample = (high shl 8) or low
        val s = if (sample and 0x8000 != 0) sample or 0xFFFF0000.toInt() else sample
        sum += (s * s).toDouble()
        i += 2
    }
    val rms = sqrt(sum / count) / 32768.0
    // Поджимаем нелинейно: тихие звуки виднее, громкие не выходят за 1.0
    val shaped = (rms * 3.2).coerceIn(0.0, 1.0)
    return shaped.toFloat()
}
