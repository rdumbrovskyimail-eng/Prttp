// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА (v2.0 — Studio Waveform)
// Путь: app/src/main/java/com/translator/app/presentation/translator/animations/BerlinWaveform.kt
//
// Горизонтальный waveform в стиле Apple Voice Memos / iOS Dictation,
// но с реальной DSP-точностью и smooth-роллингом.
//
// 56 баров, ring-буфер. Каждые 28 мс — новый сэмпл (35.7 баров/сек).
// Между сэмплами — критически-затухающий spring к target значению.
// Idle: лёгкая бегущая синусоида.
// Speaking: реальные RMS, последние 14 баров — градиент к accentSecondary.
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator.animations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.translator.app.presentation.theme.AppPalette
import kotlinx.coroutines.flow.Flow
import kotlin.math.exp
import kotlin.math.sin

private const val BARS = 56
private const val PUSH_INTERVAL_NS = 28_000_000L
private const val ACCENT_TAIL = 14

@Composable
fun BerlinWaveform(
    palette: AppPalette,
    audioFlow: Flow<ByteArray>,
    isAiSpeaking: Boolean
) {
    val m = rememberAudioMetrics(audioFlow, isAiSpeaking, attack = 0.6f, release = 0.06f)
    val level by m.level
    val peak by m.peak

    val current  = remember { FloatArray(BARS) }
    val target   = remember { FloatArray(BARS) }
    val head     = remember { intArrayOf(0) }
    val lastPush = remember { longArrayOf(0L) }

    val breathTr = rememberInfiniteTransition(label = "berlinBreath")
    val phase by breathTr.animateFloat(
        initialValue = 0f, targetValue = (Math.PI * 2f).toFloat(),
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "berlinPhase"
    )

    LaunchedEffect(audioFlow, isAiSpeaking) {
        var prev = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val dt = (now - prev).coerceAtLeast(0L) / 1_000_000_000f
                prev = now

                if (now - lastPush[0] > PUSH_INTERVAL_NS) {
                    lastPush[0] = now
                    head[0] = (head[0] + 1) % BARS
                    // Используем смесь level + peak: peak делает «шипы» острее
                    target[head[0]] = if (isAiSpeaking) (level * 0.75f + peak * 0.25f) else 0f
                }

                // Critically-damped exponential approach
                val k = 1f - exp(-dt * 22f)
                for (i in 0 until BARS) {
                    current[i] += (target[i] - current[i]) * k
                }
            }
        }
    }

    Canvas(modifier = Modifier.size(240.dp, 60.dp)) {
        val w = size.width
        val h = size.height
        val barW = w / (BARS * 1.55f)
        val gap = (w - barW * BARS) / (BARS - 1)
        val midY = h / 2f
        val maxHalf = h / 2f - 2f
        val minHalf = 1.2.dp.toPx()

        for (i in 0 until BARS) {
            val ringIdx = (head[0] + 1 + i) % BARS
            val raw = current[ringIdx]

            val idleAmp = if (!isAiSpeaking) {
                (sin(phase + i * 0.19f) * 0.06f + 0.08f).toFloat()
            } else 0f

            val v = (raw + idleAmp).coerceIn(0f, 1f)
            val shaped = v * v * (3f - 2f * v)        // smoothstep
            val half = minHalf + shaped * (maxHalf - minHalf)

            val x = i * (barW + gap)

            // Tail (последние ACCENT_TAIL баров справа) — градиент к accentSecondary
            val color = if (isAiSpeaking && i > BARS - ACCENT_TAIL) {
                val t = (i - (BARS - ACCENT_TAIL)).toFloat() / ACCENT_TAIL
                lerpColor(palette.textMuted, palette.accentSecondary, t)
            } else {
                palette.textSecondary
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x, midY - half),
                size = Size(barW, half * 2f),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red   = a.red   + (b.red   - a.red)   * tt,
        green = a.green + (b.green - a.green) * tt,
        blue  = a.blue  + (b.blue  - a.blue)  * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt
    )
}
