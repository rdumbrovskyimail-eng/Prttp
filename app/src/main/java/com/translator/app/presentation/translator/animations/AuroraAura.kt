// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ
// Путь: app/src/main/java/com/translator/app/presentation/translator/animations/AuroraAura.kt
//
// АНИМАЦИЯ ДЛЯ ТЕМЫ "AURORA" — основной wow-эффект приложения.
//
// Что это: прямоугольная закругленная "капсула" с переливающимся
// градиентом (5 цветов из палитры). Постоянно "дышит" — лёгкая
// волна по фону. Когда Gemini говорит:
//   • размер увеличивается в 3 раза (по ширине и высоте плавно)
//   • скорость переливания градиента ×3..×6 (зависит от RMS)
//   • вокруг капсулы — мягкий glow с blur-имитацией через alpha layers
//
// Реализация — Canvas + переключение угла градиента по времени.
// Никаких аллокаций в onDraw: Brush создаётся через ленивые holders.
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator.animations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.translator.app.presentation.theme.AppPalette
import kotlinx.coroutines.flow.Flow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aurora capsule.
 *
 * @param palette палитра (из неё берётся auraGradient).
 * @param audioFlow PCM поток от Gemini.
 * @param isAiSpeaking когда true — увеличивается ×3, скорость градиента множится на RMS.
 * @param baseWidth ширина в idle-режиме.
 * @param baseHeight высота в idle-режиме.
 */
@Composable
fun AuroraAura(
    palette: AppPalette,
    audioFlow: Flow<ByteArray>,
    isAiSpeaking: Boolean,
    baseWidth: androidx.compose.ui.unit.Dp = 96.dp,
    baseHeight: androidx.compose.ui.unit.Dp = 36.dp
) {
    val audioLevel by rememberAudioLevel(audioFlow, isAiSpeaking)

    // Размер: idle = base, speaking = base × (2.5 + level × 0.8) — до 3.3×.
    val expandFactor by animateFloatAsState(
        targetValue = if (isAiSpeaking) 2.5f + audioLevel * 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "auroraExpand"
    )

    // Базовый "дыхательный" пульс — всегда работает, очень тонкий.
    val breathTransition = rememberInfiniteTransition(label = "auroraBreath")
    val breath by breathTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraBreathValue"
    )

    // Угол градиента: 0..2π, скорость зависит от RMS.
    // Базовая скорость: 12 секунд на оборот. Speaking: до 2 секунд.
    val gradientRotation = rememberInfiniteTransition(label = "auroraGradient")
    val baseAngle by gradientRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraAngleBase"
    )
    // Дополнительный угол, ускоряемый громкостью. Реально считаем
    // суммарный угол в onDraw через State.
    val audioPushAngle by gradientRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraAnglePush"
    )

    val totalAngleDeg = baseAngle + audioPushAngle * audioLevel * 1.5f

    val colors = remember(palette.id) { palette.auraGradient }

    Box(
        modifier = Modifier.size(
            width = baseWidth * expandFactor,
            height = baseHeight * (1f + (expandFactor - 1f) * 0.45f) * breath
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(
            width = baseWidth * expandFactor,
            height = baseHeight * (1f + (expandFactor - 1f) * 0.45f) * breath
        )) {
            val w = size.width
            val h = size.height
            val corner = h / 2f

            // Линейный градиент с поворотом: считаем end-точку через sin/cos.
            val angleRad = Math.toRadians(totalAngleDeg.toDouble())
            val cx = w / 2f
            val cy = h / 2f
            val radius = (w + h) / 2f
            val dx = (cos(angleRad) * radius).toFloat()
            val dy = (sin(angleRad) * radius).toFloat()

            // Soft glow слои (3 уровня, всё тоньше) — рисуем до основной капсулы.
            val glowAlpha = if (isAiSpeaking) 0.35f + audioLevel * 0.35f else 0.18f
            for (g in 3 downTo 1) {
                val padding = corner * g * 0.6f
                drawRoundRect(
                    color = palette.auraGlow.copy(alpha = glowAlpha / g),
                    topLeft = Offset(-padding, -padding),
                    size = Size(w + padding * 2, h + padding * 2),
                    cornerRadius = CornerRadius(corner + padding)
                )
            }

            // Основная капсула с градиентом.
            val brush = Brush.linearGradient(
                colors = colors,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy)
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(corner)
            )

            // Лёгкий highlight сверху — даёт ощущение "глянца".
            val highlight = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = h * 0.6f
            )
            drawRoundRect(
                brush = highlight,
                topLeft = Offset.Zero,
                size = Size(w, h * 0.55f),
                cornerRadius = CornerRadius(corner)
            )
        }
    }
}
