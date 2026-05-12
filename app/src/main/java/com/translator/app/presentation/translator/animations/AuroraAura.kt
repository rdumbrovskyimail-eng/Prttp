// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА (v2.0 — Glass Capsule)
// Путь: app/src/main/java/com/translator/app/presentation/translator/animations/AuroraAura.kt
//
// Премиальная закруглённая капсула. При тишине: мягкое дыхание + неспешный
// градиент. При речи: экспансия ×3, ускоренное переливание, мульти-слойный
// glow расходится в стороны. На пиках — лёгкий «толчок» от velocity.
//
// Особенности:
//   • Двойной слой градиента (внутри + outer halo) для глубины
//   • Glow строится из 4 концентрических softRoundRect с убывающим alpha
//   • Highlight сверху — vertical gradient, имитирует glass-блик
//   • Pinhole inner-shadow по углам — добавляет «литое стекло»
//   • Все размеры — Float (zero-alloc), без Dp в hot-path
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.translator.animations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.translator.app.presentation.theme.AppPalette
import kotlinx.coroutines.flow.Flow
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuroraAura(
    palette: AppPalette,
    audioFlow: Flow<ByteArray>,
    isAiSpeaking: Boolean,
    baseWidth: Dp = 100.dp,
    baseHeight: Dp = 38.dp
) {
    val metrics = rememberAudioMetrics(audioFlow, isAiSpeaking, attack = 0.55f, release = 0.08f)
    val level by metrics.level
    val peak by metrics.peak

    // Размер: idle = 1×, speaking = 2.4×..3.4× по level + добавка от peak.
    val expand by animateFloatAsState(
        targetValue = if (isAiSpeaking) 2.4f + level * 0.7f + peak * 0.3f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "auroraExpand"
    )

    val breathTr = rememberInfiniteTransition(label = "auroraBreath")
    val breath by breathTr.animateFloat(
        initialValue = 0.96f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraBreathV"
    )

    val rotTr = rememberInfiniteTransition(label = "auroraRot")
    val angleBase by rotTr.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraAngleBase"
    )
    val anglePush by rotTr.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraAnglePush"
    )
    val totalAngle = angleBase + anglePush * level * 1.6f

    val colors = remember(
        palette.aura0, palette.aura1, palette.aura2, palette.aura3, palette.aura4
    ) { palette.auraGradient }

    val w = baseWidth * expand
    val h = baseHeight * (1f + (expand - 1f) * 0.42f) * breath

    Box(modifier = Modifier.size(w, h), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(w, h)) {
            val sw = size.width
            val sh = size.height
            val corner = sh / 2f

            // ── (1) Outer glow halo — 4 слоя с убывающим alpha
            val glowStrength = if (isAiSpeaking) 0.32f + level * 0.42f + peak * 0.18f else 0.16f
            for (g in 4 downTo 1) {
                val pad = corner * g * 0.55f
                drawRoundRect(
                    color = palette.auraGlow.copy(alpha = glowStrength / (g * 1.1f)),
                    topLeft = Offset(-pad, -pad),
                    size = Size(sw + pad * 2, sh + pad * 2),
                    cornerRadius = CornerRadius(corner + pad)
                )
            }

            // ── (2) Body: вращающийся linear gradient
            val rad = Math.toRadians(totalAngle.toDouble())
            val cx = sw / 2f; val cy = sh / 2f
            val diag = (sw + sh) / 2f
            val dx = (cos(rad) * diag).toFloat()
            val dy = (sin(rad) * diag).toFloat()
            val brush = Brush.linearGradient(
                colors = colors,
                start = Offset(cx - dx, cy - dy),
                end   = Offset(cx + dx, cy + dy)
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset.Zero,
                size = Size(sw, sh),
                cornerRadius = CornerRadius(corner)
            )

            // ── (3) Glass highlight — vertical top
            val hi = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.26f),
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                startY = 0f, endY = sh * 0.65f
            )
            drawRoundRect(
                brush = hi,
                topLeft = Offset.Zero,
                size = Size(sw, sh * 0.58f),
                cornerRadius = CornerRadius(corner)
            )

            // ── (4) Bottom inner-shadow — придаёт «вес»
            if (!palette.isDark) {
                val lo = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)),
                    startY = sh * 0.5f, endY = sh
                )
                drawRoundRect(
                    brush = lo,
                    topLeft = Offset(0f, sh * 0.5f),
                    size = Size(sw, sh * 0.5f),
                    cornerRadius = CornerRadius(corner)
                )
            }
        }
    }
}
