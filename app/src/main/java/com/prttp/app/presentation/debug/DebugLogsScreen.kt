// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ
// Путь: app/src/main/java/com/translator/app/presentation/debug/DebugLogsScreen.kt
//
// Экран просмотра логов с фильтрами по уровню (D/I/W/E),
// автоскроллом, копированием в буфер обмена, очисткой.
// Адаптировано из старой версии (com.learnde.app).
// ═══════════════════════════════════════════════════════════
package com.prttp.app.presentation.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prttp.app.util.LogBuffer
import com.prttp.app.util.LogEntry
import com.prttp.app.util.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DebugLogsViewModel @Inject constructor(
    private val buffer: LogBuffer,
) : ViewModel() {

    val entries get() = buffer.entries

    fun clear() {
        runCatching { buffer.clear() }
    }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        runCatching { buffer.exportAsText() }.getOrDefault("")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(
    onBack: () -> Unit,
    vm: DebugLogsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allEntries by vm.entries.collectAsStateWithLifecycle(initialValue = emptyList())

    var activeLevels by remember {
        mutableStateOf(setOf(LogLevel.D, LogLevel.I, LogLevel.W, LogLevel.E))
    }
    var autoScroll by remember { mutableStateOf(true) }

    val filtered by remember(allEntries, activeLevels) {
        derivedStateOf { allEntries.filter { it.level in activeLevels } }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(filtered.size, autoScroll) {
        if (autoScroll && filtered.isNotEmpty()) {
            runCatching {
                listState.animateScrollToItem(filtered.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Логи (${filtered.size}/${allEntries.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            "Автоскролл",
                            tint = if (autoScroll)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val text = vm.export()
                            if (text.isNotEmpty()) {
                                copyToClipboard(context, text)
                                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Нет логов для копирования", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, "Копировать")
                    }
                    IconButton(onClick = { vm.clear() }) {
                        Icon(Icons.Filled.Delete, "Очистить", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LevelChip("D", LogLevel.D, Color(0xFF9AA0A6), activeLevels) {
                    activeLevels = toggleLevel(activeLevels, LogLevel.D)
                }
                LevelChip("I", LogLevel.I, Color(0xFF1A73E8), activeLevels) {
                    activeLevels = toggleLevel(activeLevels, LogLevel.I)
                }
                LevelChip("W", LogLevel.W, Color(0xFFF29900), activeLevels) {
                    activeLevels = toggleLevel(activeLevels, LogLevel.W)
                }
                LevelChip("E", LogLevel.E, Color(0xFFD93025), activeLevels) {
                    activeLevels = toggleLevel(activeLevels, LogLevel.E)
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (allEntries.isEmpty()) "Логов пока нет"
                        else "Нет логов с выбранными уровнями",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(filtered, key = { idx, e -> "$idx-${e.timestamp}" }) { _, entry ->
                        LogLine(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelChip(
    label: String,
    level: LogLevel,
    color: Color,
    active: Set<LogLevel>,
    onClick: () -> Unit,
) {
    val isSelected = level in active
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(6.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.D -> Color(0xFF9AA0A6)
        LogLevel.I -> Color(0xFF1A73E8)
        LogLevel.W -> Color(0xFFF29900)
        LogLevel.E -> Color(0xFFD93025)
    }
    val highlightBg = if (entry.level == LogLevel.E)
        Color(0xFFD93025).copy(alpha = 0.06f) else Color.Transparent

    Column(
        Modifier
            .fillMaxWidth()
            .background(highlightBg)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = runCatching { entry.formatted() }.getOrDefault("[error formatting log]"),
            fontSize = 10.sp,
            color = color,
            fontFamily = FontFamily.Monospace,
            lineHeight = 13.sp,
        )
    }
}

private fun toggleLevel(set: Set<LogLevel>, level: LogLevel): Set<LogLevel> =
    if (level in set) set - level else set + level

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("logs", text))
    }
}
