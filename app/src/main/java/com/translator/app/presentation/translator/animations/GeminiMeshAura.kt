// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ
// Путь: app/src/main/java/com/translator/app/presentation/translator/animations/GeminiMeshAura.kt
//
// АНИМАЦИЯ ДЛЯ "GEMINI_NEXUS" — стиль Google Gemini Advanced.
//
// Что это: капсула (rounded rect, вытянутая) с обрезкой по форме.
// Внутри плавают 5 размытых цветных «солнц» (radial gradients)
// по математическим орбитам Lissajous. Они смешиваются через
// BlendMode.Plus / Screen → получается мягкое перетекание цветов
// как mesh gradient в дизайне Google.
//
// При речи: капсула расширяется (ниже-выше высота), орбиты ускоряются
// в 4× и солнца «втягиваются» к центру капсулы при peaks.
//
// Технически:
//   • clipPath по rounded rect → всё содержимое строго в капсуле
//   • 5 цветов из palette.auraGradient (aura0..aura4)
//   • Каждое солнце имеет свою частоту в X/Y → Lissajous-фигуры
//   • Радиус солнца ~ половина высоты капсулы (большие размытые круги)
//   • Внешняя обводка — тонкая, в палитре border, плюс soft outer glow
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.translator.app.presentation.theme.AppPalette
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeminiMeshAura(
    palette: AppPalette,
    audioFlow: Flow<ByteArray>,
    isAiSpeaking: Boolean
) {
    val m = rememberAudioMetrics(audioFlow, isAiSpeaking, attack = 0.5f, release = 0.08f)
    val level by m.level
    val peak by m.peak

    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var prev = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val dt = (now - prev).coerceAtLeast(0L) / 1_000_000_000f
                prev = now
                phase.floatValue += dt * (0.55f + level * 3.2f + peak * 1.0f)
            }
        }
    }

    Box(modifier = Modifier.size(width = 260.dp, height = 100.dp)) {
        Canvas(modifier = Modifier.size(width = 260.dp, height = 100.dp)) {
            val w = size.width
            val h = size.height

            // Адаптивная геометрия капсулы: высота растёт от 0.55h до полной h при громких звуках
            val capsuleH = h * (0.55f + level * 0.40f + peak * 0.10f).coerceAtMost(1f)
            val topPad = (h - capsuleH) / 2f
            val corner = capsuleH / 2f

            val capsuleRect = RoundRect(
                left = 6f,
                top = topPad,
                right = w - 6f,
                bottom = topPad + capsuleH,
                cornerRadius = CornerRadius(corner, corner)
            )

            val capsulePath = Path().apply { addRoundRect(capsuleRect) }

            // ── (1) Outer soft glow — реагирует на peak
            val glowStrength = if (isAiSpeaking) 0.20f + peak * 0.35f else 0.10f
            for (g in 3 downTo 1) {
                val pad = corner * g * 0.50f
                drawRoundRect(
                    color = palette.auraGlow.copy(alpha = glowStrength / (g * 1.3f)),
                    topLeft = Offset(6f - pad, topPad - pad),
                    size = Size(w - 12f + pad * 2, capsuleH + pad * 2),
                    cornerRadius = CornerRadius(corner + pad)
                )
            }

            // ── (2) Background filler — surfaceHigh / accentSoft
            drawPath(path = capsulePath, color = palette.accentSoft)

            // ── (3) Floating colored "suns" — Lissajous orbits, blended together
            clipPath(capsulePath) {
                val auraColors = listOf(
                    palette.aura0, palette.aura1, palette.aura2,
                    palette.aura3, palette.aura4
                )
                val capsuleCx = w / 2f
                val capsuleCy = topPad + capsuleH / 2f
                val orbitRx = (w - 12f) / 2f * 0.55f
                val orbitRy = capsuleH / 2f * 0.55f

                // Радиус каждого «солнца» ~ ширина капсулы / 3, чтобы они хорошо перекрывались
                val sunRadiusBase = (w * 0.34f) * (1f + level * 0.2f)

                val t = phase.floatValue
                for (i in 0 until 5) {
                    val freqX = 1.1f + i * 0.45f
                    val freqY = 1.7f + i * 0.30f
                    val phOff = (i * (PI.toFloat() * 0.31f))
                    val sx = capsuleCx + cos(t * freqX + phOff) * orbitRx
                    val sy = capsuleCy + sin(t * freqY + phOff) * orbitRy

                    // При peak — солнца притягиваются к центру (визуальный "вдох")
                    val pull = peak * 0.35f
                    val drawX = sx * (1f - pull) + capsuleCx * pull
                    val drawY = sy * (1f - pull) + capsuleCy * pull

                    val sr = sunRadiusBase * (0.85f + 0.30f * sin(t * 1.7f + i).toFloat() + level * 0.3f)
                    val col = auraColors[i]

                    val sunBrush = Brush.radialGradient(
                        colors = listOf(
                            col.copy(alpha = 0.95f),
                            col.copy(alpha = 0.55f),
                            col.copy(alpha = 0.0f)
                        ),
                        center = Offset(drawX, drawY),
                        radius = sr
                    )
                    // Plus blending — цвета не «грязные», смешиваются как свет
                    drawCircle(
                        brush = sunBrush,
                        radius = sr,
                        center = Offset(drawX, drawY),
                        blendMode = BlendMode.Plus
                    )
                }

                // Поверх — тонкий «глянцевый» горизонтальный градиент
                val gloss = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.0f),
                        Color.Transparent
                    ),
                    startY = topPad,
                    endY = topPad + capsuleH * 0.5f
                )
                drawRoundRect(
                    brush = gloss,
                    topLeft = Offset(6f, topPad),
                    size = Size(w - 12f, capsuleH * 0.55f),
                    cornerRadius = CornerRadius(corner)
                )
            }

            // ── (4) Outer hairline border
            drawPath(
                path = capsulePath,
                color = palette.border.copy(alpha = 0.55f),
                style = Stroke(width = 1f)
            )
        }
    }
}
