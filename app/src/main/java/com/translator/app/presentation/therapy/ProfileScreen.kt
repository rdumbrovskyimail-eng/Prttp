// Путь: app/src/main/java/com/translator/app/presentation/therapy/ProfileScreen.kt
//
// Карточка пациента — то, что ассистент накопил о человеке: факты по
// категориям, динамика настроения, открытые домашние задания, итоги встреч.
// Только чтение (заполняет ассистент), плюс кнопка стереть всё (право на
// забвение) и отметить ДЗ выполненным.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.presentation.therapy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.translator.app.data.PatientRepository
import com.translator.app.domain.model.PatientProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: PatientRepository
) : ViewModel() {
    val profile = repo.profile
    fun completeHomework(id: String) { viewModelScope.launch { repo.completeHomework(id) } }
    fun wipe() { viewModelScope.launch { repo.wipeEverything() } }
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val p by viewModel.profile.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                if (p.displayName.isNotBlank()) p.displayName else "Карточка пациента",
                fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
        }

        // Активный риск — заметным блоком
        p.activeRisk?.let { flag ->
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⚠ Внимание: ${flag.level}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Text(flag.reason)
                    }
                }
            }
        }

        // Динамика настроения (мини-спарклайн текстом; для графика подключи
        // chart_display из проекта или Compose-канвас).
        if (p.moodLogs.isNotEmpty()) {
            item {
                Section("Настроение") {
                    val last = p.moodLogs.takeLast(14)
                    Text(last.joinToString("  ") { "${it.score}" }, fontWeight = FontWeight.Medium)
                    Text("последние ${last.size} замеров (1–10)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        // Открытые ДЗ
        if (p.openHomework.isNotEmpty()) {
            item {
                Section("Домашние задания") {
                    p.openHomework.forEach { hw ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.padding(end = 8.dp)) {
                                Text(hw.title, fontWeight = FontWeight.Medium)
                                if (hw.detail.isNotBlank()) Text(hw.detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.completeHomework(hw.id) }) { Text("Готово") }
                        }
                    }
                }
            }
        }

        // Факты по категориям
        val grouped = p.facts.groupBy { it.category }
        grouped.forEach { (cat, facts) ->
            item {
                Section(categoryLabel(cat)) {
                    facts.sortedByDescending { it.updatedAt }.forEach {
                        Text("• ${it.key}: ${it.value}")
                    }
                }
            }
        }

        // Итоги встреч
        if (p.sessionNotes.isNotEmpty()) {
            item {
                Section("Итоги встреч") {
                    p.sessionNotes.takeLast(8).reversed().forEach {
                        Text("• ${it.summary}", fontWeight = FontWeight.Medium)
                        if (it.techniques.isNotEmpty()) Text("  методы: ${it.techniques.joinToString(", ")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { TextButton(onClick = { confirmWipe = true }) { Text("Стереть все данные", color = MaterialTheme.colorScheme.error) } }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Стереть всё?") },
            text = { Text("Профиль, заметки и дневник будут удалены безвозвратно.") },
            confirmButton = { TextButton(onClick = { viewModel.wipe(); confirmWipe = false }) { Text("Стереть", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun categoryLabel(cat: String) = when (cat) {
    "presenting_concern" -> "С чем обратился"
    "history" -> "Анамнез"
    "symptom" -> "Симптомы"
    "trigger" -> "Триггеры"
    "cognition" -> "Мысли и убеждения"
    "coping" -> "Что помогает"
    "strength" -> "Ресурсы"
    "goal" -> "Цели"
    "relationship" -> "Окружение"
    "preference" -> "Предпочтения в общении"
    "boundary" -> "Осторожные темы"
    "medical" -> "Мед. контекст"
    else -> cat
}
