package com.translator.app.presentation.therapy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable // <-- Добавлен недостающий импорт для сборки анимации
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Фазы разговора для индикатора присутствия. */
enum class TherapyPhase { Idle, Connecting, Listening, AssistantSpeaking, Reconnecting }

/** Уровень риска для баннера (зеркало RiskLevel в domain). */
enum class CrisisLevel { None, Elevated }

data class TherapyUiState(
    val phase: TherapyPhase = TherapyPhase.Idle,
    val patientName: String = "",
    val micMuted: Boolean = false,
    val crisis: CrisisLevel = CrisisLevel.None,
    val crisisReason: String = "",
    val lastCaption: String = "",
    // Текущий статус операции ИИ в реальном времени
    val activeActionStatus: String = ""
)

@Composable
fun TherapyScreen(
    state: TherapyUiState,
    onToggleMute: () -> Unit,
    onEndSession: () -> Unit,
    onOpenResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = Brush.verticalGradient(
        listOf(Color(0xFF0E1A24), Color(0xFF132A2E), Color(0xFF0E1A24))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // ── Кризисный баннер ──
        AnimatedVisibility(
            visible = state.crisis == CrisisLevel.Elevated,
            enter = slideInVertically { -it } + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            CrisisBanner(reason = state.crisisReason, onOpenResources = onOpenResources)
        }

        // ── Центр: фаза + дышащая точка присутствия ──
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PresenceOrb(phase = state.phase)

            Spacer(Modifier.height(28.dp))

            Text(
                text = phaseLabel(state.phase, state.patientName),
                color = Color(0xFFCFE3E0),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            if (state.lastCaption.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = state.lastCaption,
                    color = Color(0x99CFE3E0),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Монитор телеметрии ИИ ──
        AnimatedVisibility(
            visible = state.activeActionStatus.isNotBlank(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 124.dp, start = 24.dp, end = 24.dp)
        ) {
            ActionStatusCapsule(status = state.activeActionStatus)
        }

        // ── Низ: микрофон + завершить ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp, start = 40.dp, end = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundControl(
                muted = state.micMuted,
                onClick = onToggleMute
            )

            IconButton(
                onClick = onEndSession,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0x22FFFFFF), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Завершить", tint = Color(0xFFCFE3E0))
            }
        }
    }
}

@Composable
private fun ActionStatusCapsule(status: String) {
    Box(
        modifier = Modifier
            .background(Color(0xE60E181D), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF40E0C0).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = status,
            color = Color(0xFF6FE3C9),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PresenceOrb(phase: TherapyPhase) {
    val transition = rememberInfiniteTransition(label = "breath")
    val durationMs = when (phase) {
        TherapyPhase.AssistantSpeaking -> 1400
        TherapyPhase.Listening -> 2600
        TherapyPhase.Connecting, TherapyPhase.Reconnecting -> 900
        TherapyPhase.Idle -> 4000
    }
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(durationMs), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(170.dp)
            .scale(scale)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF6FE3C9), Color(0xFF2E7E78), Color(0x0011302E))
                ),
                CircleShape
            )
    )
}

@Composable
private fun RoundControl(muted: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(if (muted) Color(0x33FF6B6B) else Color(0x2240E0C0), CircleShape)
    ) {
        Icon(
            imageVector = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (muted) "Включить микрофон" else "Выключить микрофон",
            tint = Color(0xFFCFE3E0)
        )
    }
}

@Composable
private fun CrisisBanner(reason: String, onOpenResources: () -> Unit) {
    Surface(
        color = Color(0xFF2A1B1B),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Если тебе сейчас тяжело — ты не один.",
                color = Color(0xFFFFD9D9), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Рядом есть живая помощь, и к ней можно обратиться прямо сейчас.",
                color = Color(0xCCFFD9D9), fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpenResources,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE05B5B)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Показать контакты помощи", color = Color.White) }
        }
    }
}

private fun phaseLabel(phase: TherapyPhase, name: String): String {
    val who = if (name.isNotBlank()) ", $name" else ""
    return when (phase) {
        TherapyPhase.Idle -> "Я рядом$who. Можешь начать, когда будешь готов."
        TherapyPhase.Connecting -> "Подключаюсь…"
        TherapyPhase.Reconnecting -> "Восстанавливаю связь…"
        TherapyPhase.Listening -> "Слушаю тебя."
        TherapyPhase.AssistantSpeaking -> "…"
    }
}