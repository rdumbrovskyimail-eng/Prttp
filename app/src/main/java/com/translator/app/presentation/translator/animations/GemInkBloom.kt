// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ
// Путь: app/src/main/java/com/translator/app/presentation/translator/animations/GemInkBloom.kt
//
// АНИМАЦИЯ ДЛЯ "GEM" — чернильное Gemini-Sparkle пятно.
//
// Концепция:
//   Розлитое жирное чернильное пятно с зелёным ядром в центре, переливается
//   к краям бирюзой, синим, янтарём, красным и фиолетовым. Снизу — vertical
//   "фонарик", делает пятно объёмным как капля жидкости на стекле. Снаружи —
//   мягкое зелёное свечение.
//
// Поведение:
//   • Idle: плавное "дыхание" + медленный drift цветовых ядер (Lissajous).
//   • Speaking: ускорение в 4×, центр расширяется, цвета насыщаются и пульсируют.
//   • При peak — лёгкое "вспышка" в центре + расширение glow за капсулой.
//
// Технически:
//   • Один rounded прямоугольник (capsule)
//   • clipPath по нему — всё содержимое строго в форме капсулы
//   • 5 концентрических цветовых ядер (aura0..aura4) дрейфуют по Lissajous-орбитам
//   • Каждое ядро = radial gradient с большим радиусом, BlendMode.Plus → "светятся"
//   • Верхний glass-highlight + нижний vertical "фонарик"
//   • Outer glow — 3 концентрических rounded rect с убывающим alpha
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator.animations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun GemInkBloom(
    palette: AppPalette,
    audioFlow: Flow<ByteArray>,
    isAiSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val m = rememberAudioMetrics(audioFlow, isAiSpeaking, attack = 0.55f, release = 0.08f)
    val level by m.level
    val peak by m.peak

    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var prev = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val dt = (now - prev).coerceAtLeast(0L) / 1_000_000_000f
                prev = now
                // Idle ≈ 0.40, speaking ≈ 0.40 + 3.5 → ускорение в 4× с пиковыми всплесками
                phase.floatValue += dt * (0.40f + level * 3.5f + peak * 1.2f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(72.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            val w = size.width
            val h = size.height

            // ── Capsule геометрия (прямоугольник со скруглением = высота/2)
            val pad = 8f
            val capL = pad
            val capT = pad
            val capR = w - pad
            val capB = h - pad
            val capW = capR - capL
            val capH = capB - capT
            val corner = capH / 2f
            val capCx = (capL + capR) / 2f
            val capCy = (capT + capB) / 2f

            val capsuleRect = RoundRect(
                left = capL, top = capT, right = capR, bottom = capB,
                cornerRadius = CornerRadius(corner, corner)
            )
            val capsulePath = Path().apply { addRoundRect(capsuleRect) }

            // ── (1) Outer soft glow — зелёное свечение вокруг капсулы
            val glowStrength = if (isAiSpeaking) 0.22f + peak * 0.35f + level * 0.20f else 0.12f
            for (g in 4 downTo 1) {
                val gp = corner * g * 0.55f
                drawRoundRect(
                    color = palette.auraGlow.copy(alpha = glowStrength / (g * 1.2f)),
                    topLeft = Offset(capL - gp, capT - gp),
                    size = Size(capW + gp * 2f, capH + gp * 2f),
                    cornerRadius = CornerRadius(corner + gp)
                )
            }

            // ── (2) Базовый фон капсулы — почти прозрачный, чтобы цвета "проявлялись"
            drawPath(path = capsulePath, color = Color(0xFFFAFCFE))

            // ── (3) Чернильное пятно — 5 цветных ядер, дрейфующих по Lissajous
            clipPath(capsulePath) {
                val t = phase.floatValue

                // Орбитальные радиусы (как далеко может уйти центр ядра от центра капсулы)
                val orbitRx = capW * 0.30f
                val orbitRy = capH * 0.45f

                // Базовый радиус ядра — половина ширины капсулы (огромный → растекается)
                val coreRBase = capW * 0.45f * (1f + level * 0.25f + peak * 0.15f)

                val cores = listOf(
                    // (цвет, freqX, freqY, phaseOffset, weightForCenter)
                    Triple(palette.aura0, 0.0f to 0.0f, 0.0f),               // ЗЕЛЁНЫЙ — ЯДРО, всегда в центре-верху
                    Triple(palette.aura1, 1.30f to 1.70f, 0.4f),             // бирюза
                    Triple(palette.aura2, 0.90f to 1.10f, 1.6f),             // синий
                    Triple(palette.aura3, 1.50f to 0.80f, 2.7f),             // янтарь
                    Triple(palette.aura4, 1.10f to 1.40f, 3.9f)              // красный
                )

                cores.forEachIndexed { i, triple ->
                    val color = triple.first
                    val (freqX, freqY) = triple.second
                    val phOff = triple.third

                    // Ядро #0 (зелёное) — приклеено к центру, лишь чуть дышит
                    val cx: Float
                    val cy: Float
                    if (i == 0) {
                        cx = capCx + sin(t * 0.7f) * orbitRx * 0.08f
                        // Чуть приподнято вверх — там доминирующий зелёный
                        cy = capCy - capH * 0.06f + cos(t * 0.5f) * orbitRy * 0.08f
                    } else {
                        cx = capCx + cos(t * freqX + phOff) * orbitRx
                        cy = capCy + sin(t * freqY + phOff) * orbitRy
                    }

                    // Радиус — пульсация
                    val sizeMod = 0.85f + 0.20f * sin(t * 1.6f + i * 0.8f).toFloat() +
                                  level * 0.25f + peak * 0.15f
                    val coreR = coreRBase * sizeMod * if (i == 0) 1.15f else 0.90f

                    // Прозрачность — центральное ядро всегда плотнее
                    val coreAlpha = if (i == 0) 0.95f else 0.75f

                    val brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = coreAlpha),
                            color.copy(alpha = coreAlpha * 0.5f),
                            color.copy(alpha = 0f)
                        ),
                        center = Offset(cx, cy),
                        radius = coreR
                    )
                    drawCircle(
                        brush = brush,
                        radius = coreR,
                        center = Offset(cx, cy),
                        blendMode = BlendMode.Plus
                    )
                }

                // ── (4) "Фонарик снизу" — vertical light gradient, делает пятно объёмным
                val lampBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.22f)
                    ),
                    startY = capT,
                    endY = capB
                )
                drawRoundRect(
                    brush = lampBrush,
                    topLeft = Offset(capL, capT),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(corner)
                )

                // ── (5) Top glass highlight — тонкий блик сверху, имитирует стекло
                val glossBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    startY = capT,
                    endY = capT + capH * 0.55f
                )
                drawRoundRect(
                    brush = glossBrush,
                    topLeft = Offset(capL, capT),
                    size = Size(capW, capH * 0.55f),
                    cornerRadius = CornerRadius(corner)
                )
            }

            // ── (6) Hairline border — sky blue, очень тонкий
            drawPath(
                path = capsulePath,
                color = palette.border.copy(alpha = 0.65f),
                style = Stroke(width = 1f)
            )
        }
    }
}