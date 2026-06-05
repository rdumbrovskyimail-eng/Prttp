// Путь: app/src/main/java/com/translator/app/presentation/therapy/JournalScreen.kt
//
// Экран дневника. Пишет ЧЕЛОВЕК. Ассистент эти записи только читает (инжект в
// промпт при старте + инструмент read_recent_journal).
//
// Свой маленький ViewModel поверх PatientRepository — список реактивный,
// новые записи видны сразу и попадут в следующую сессию.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.presentation.therapy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.translator.app.data.PatientRepository
import com.translator.app.domain.model.JournalEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repo: PatientRepository
) : ViewModel() {
    val entries = repo.journal

    fun add(text: String, mood: Int?) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.addJournalEntry(text.trim(), mood, emptyList()) }
    }

    fun delete(id: String) { viewModelScope.launch { repo.deleteJournalEntry(id) } }
}

@Composable
fun JournalScreen(
    viewModel: JournalViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var composing by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (!composing) ExtendedFloatingActionButton(
                onClick = { composing = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Запись") }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty() && !composing) {
                Text(
                    "Здесь будет твой дневник.\nЗаписывай мысли и состояние — ассистент будет это учитывать.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (composing) item { Composer(onSave = { t, m -> viewModel.add(t, m); composing = false }, onCancel = { composing = false }) }
                items(entries.sortedByDescending { it.createdAt }, key = { it.id }) { e ->
                    JournalCard(e, onDelete = { viewModel.delete(e.id) })
                }
            }
        }
    }
}

@Composable
private fun Composer(onSave: (String, Int?) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf(5f) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("Что у тебя сейчас на душе?") }
            )
            Spacer(Modifier.height(12.dp))
            Text("Настроение: ${mood.toInt()}/10", fontWeight = FontWeight.Medium)
            Slider(value = mood, onValueChange = { mood = it }, valueRange = 1f..10f, steps = 8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Отмена") }
                TextButton(onClick = { onSave(text, mood.toInt()) }) { Text("Сохранить") }
            }
        }
    }
}

@Composable
private fun JournalCard(e: JournalEntry, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(e.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                e.mood?.let { Text("$it/10", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(6.dp))
            Text(e.text)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

private val df = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
private fun fmt(ts: Long) = df.format(Date(ts))
