// ═══════════════════════════════════════════════════════════════════════════
// Путь: app/src/main/java/com/translator/app/presentation/translator/MinimalTranslateScreen.kt
//
// АЛЬТЕРНАТИВНЫЙ ЭКРАН ПЕРЕВОДЧИКА — «Minimal» (профессиональный минимализм).
//
// Дизайн:
//   • Чистый сплошной белый фон.
//   • Сверху слева — очень маленькая пара языков латиницей, чёрным (тап = swap).
//   • Крупные полноширинные карточки: белая заливка + профессиональное синее
//     обрамление, очень большой жирный чёрный текст. Карточки «выпрыгивают»
//     (scale + fade) при появлении.
//   • Внизу — аудио-реактивный ОДНОТОННЫЙ синий эквалайзер (тонкие полосы,
//     без кружков), зависит от громкости воспроизводимого перевода.
//   • Ещё ниже — архитектурная минималистичная кнопка-пилюля (mic / stop).
//
// Экран использует ТОТ ЖЕ TranslatorViewModel и всю его логику. Сигнатура
// совпадает с TranslateScreen — навигация переключается одной строкой.
// Файл самодостаточен и компилируется без правок в других файлах.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.presentation.translator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

// ─── Палитра экрана (самодостаточная, не зависит от тем приложения) ───
private val MinBg = Color(0xFFFFFFFF)
private val MinInk = Color(0xFF0A0A0A)            // «чёрный» текст
private val MinBlue = Color(0xFF2563EB)           // профессиональный сине-голубой
private val MinBlueSoft = Color(0x142563EB)       // лёгкая тень/подложка
private val MinMuted = Color(0xFF9AA0A6)          // приглушённый серый (статус)

@Composable
fun MinimalTranslateScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onBack: () -> Unit,
    viewModel: TranslatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val longMode by viewModel.longPhraseMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isActive = state.connectionStatus != ConnectionStatus.Disconnected

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (state.connectionStatus == ConnectionStatus.Disconnected) viewModel.startSession()
            else viewModel.onMicPermissionGranted()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!isActive) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) viewModel.startSession()
            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler { viewModel.stopSession(); onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
            .systemBarsPadding()
    ) {
        MinimalTopBar(
            sourceName = state.sourceLanguage.nameEn,
            targetName = state.targetLanguage.nameEn,
            status = statusLabel(state.connectionStatus, state.isMicActive, state.isAiSpeaking),
            onSwap = viewModel::swapLanguages,
            onSettings = onNavigateToSettings,
            onLogs = onNavigateToLogs
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (state.pairs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Говорите —\nперевод появится здесь",
                        color = MinMuted,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            } else {
                MinimalPairsList(pairs = state.pairs)
            }
        }

        // Нижний однотонный синий эквалайзер (зависит от аудио).
        AudioEqualizer(
            audioFlow = viewModel.audioPlaybackFlow,
            active = state.isAiSpeaking || state.isMicActive,
            color = MinBlue,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Разговор длинными фразами",
                color = MinInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Switch(
                checked = longMode,
                onCheckedChange = viewModel::setLongPhraseMode,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MinBg,
                    checkedTrackColor = MinBlue
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        MinimalMicButton(
            isActive = state.isMicActive,
            onClick = {
                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) viewModel.toggleMic()
                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 4.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  TOP BAR — маленькая пара языков слева + мини-иконки справа
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MinimalTopBar(
    sourceName: String,
    targetName: String,
    status: String,
    onSwap: () -> Unit,
    onSettings: () -> Unit,
    onLogs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSwap
                )
            ) {
                Text(
                    text = "$sourceName – $targetName",
                    color = MinInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = "Поменять языки",
                    tint = MinBlue,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (status.isNotEmpty()) {
                Text(text = status, color = MinMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        IconButton(onClick = onLogs, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Логи", tint = MinMuted,
                modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = MinInk,
                modifier = Modifier.size(18.dp))
        }
    }
}

private fun statusLabel(
    status: ConnectionStatus,
    isMic: Boolean,
    isAi: Boolean
): String = when {
    status == ConnectionStatus.Connecting   -> "Подключение…"
    status == ConnectionStatus.Reconnecting  -> "Переподключение…"
    status == ConnectionStatus.Disconnected  -> "Не активно"
    isAi -> "Перевод…"
    isMic -> "Слушаю"
    else -> "Готов"
}

// ════════════════════════════════════════════════════════════════════
//  PAIRS — крупные полноширинные карточки с pop-анимацией
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MinimalPairsList(pairs: List<TranslationPair>) {
    val listState = rememberLazyListState()
    val last = pairs.lastOrNull()

    androidx.compose.runtime.LaunchedEffect(
        pairs.size, last?.originalText?.length, last?.translationText?.length
    ) {
        if (pairs.isNotEmpty()) listState.animateScrollToItem(pairs.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(pairs, key = { it.id }) { pair ->
            PopIn(modifier = Modifier.animateItem()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigCard(
                        label = pair.originalLang,
                        text = pair.originalText,
                        emphasized = false
                    )
                    BigCard(
                        label = pair.translationLang,
                        text = pair.translationText,
                        emphasized = true
                    )
                }
            }
        }
    }
}

/** Карточка «выпрыгивает»: scale 0.92→1 + fade. */
@Composable
private fun PopIn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) }
        launch { alpha.animateTo(1f, tween(220)) }
    }
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
        }
    ) { content() }
}

@Composable
private fun BigCard(label: String, text: String, emphasized: Boolean) {
    // emphasized (перевод) — чуть толще обрамление и плотнее тень.
    val borderWidth = if (emphasized) 2.dp else 1.5.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (emphasized) 6.dp else 3.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = MinBlue.copy(alpha = 0.22f),
                ambientColor = Color.Transparent
            )
            .clip(RoundedCornerShape(20.dp))
            .background(MinBg)
            .border(borderWidth, MinBlue, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                color = MinBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = text.ifBlank { "…" },
            color = MinInk,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  AUDIO EQUALIZER — однотонные синие полосы, реагируют на громкость
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AudioEqualizer(
    audioFlow: Flow<ByteArray>,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 11
) {
    // Уровень громкости 0..1: вверх по эмиссии, плавный спад по кадрам.
    var level by remember { mutableFloatStateOf(0f) }

    androidx.compose.runtime.LaunchedEffect(audioFlow) {
        audioFlow.collect { bytes ->
            val amp = pcmPeak(bytes)
            if (amp > level) level = amp
        }
    }
    // Покадровый спад, чтобы полосы мягко опускались, когда звук смолк.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                if (last != 0L) level *= 0.90f
                last = t
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "eqPhase")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "phase"
    )

    val displayLevel = if (active) level.coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val maxH = 56f
        val minH = 5f
        for (i in 0 until bars) {
            // Симметричная огибающая (центр выше краёв) + лёгкая волна.
            val center = (bars - 1) / 2f
            val envelope = 1f - (abs(i - center) / (center + 1f)) * 0.55f
            val wave = 0.55f + 0.45f * sin(phase + i * 0.7f)
            val h = (minH + (maxH - minH) * displayLevel * envelope * wave)
                .coerceIn(minH, maxH)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/** Пиковая нормализованная амплитуда из PCM16 LE (с прореживанием). */
private fun pcmPeak(pcm: ByteArray): Float {
    if (pcm.size < 2) return 0f
    var peak = 0
    var i = 0
    val step = 2 * 8 // каждый 8-й семпл — достаточно для визуала
    while (i + 1 < pcm.size) {
        val lo = pcm[i].toInt() and 0xFF
        val hi = pcm[i + 1].toInt()
        var s = (hi shl 8) or lo
        if (s > 32767) s -= 65536
        val a = abs(s)
        if (a > peak) peak = a
        i += step
    }
    return (peak / 32768f).coerceIn(0f, 1f)
}

// ════════════════════════════════════════════════════════════════════
//  MIC BUTTON — архитектурная минималистичная пилюля
// ════════════════════════════════════════════════════════════════════



@Composable
private fun MinimalMicButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fill = if (isActive) MinBlue else MinBg
    val content = if (isActive) MinBg else MinBlue

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(if (isActive) 0.dp else 2.dp, MinBlue, RoundedCornerShape(16.dp))
            .then(
                if (!isActive) Modifier.background(MinBlueSoft, RoundedCornerShape(16.dp)) else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isActive) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isActive) "Остановить" else "Говорить",
                tint = content,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isActive) "СТОП" else "ГОВОРИТЬ",
                color = content,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}
