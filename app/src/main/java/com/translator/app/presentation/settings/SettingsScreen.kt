package com.translator.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.datastore.core.DataStore
import com.translator.app.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: DataStore<AppSettings>
) : ViewModel() {
    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch { _settings.value = settingsStore.data.first() }
    }

    fun update(transform: AppSettings.() -> AppSettings) {
        _settings.update(transform)
        viewModelScope.launch { settingsStore.updateData { _settings.value } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = s.apiKey,
                onValueChange = { viewModel.update { copy(apiKey = it) } },
                label = { Text("API Ключ") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Аудио", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Громкость ИИ: ${s.playbackVolume}%")
                Slider(
                    value = s.playbackVolume.toFloat(),
                    onValueChange = { v -> viewModel.update { copy(playbackVolume = v.toInt()) } },
                    valueRange = 0f..100f,
                    modifier = Modifier.width(200.dp)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Чувствительность микрофона: ${s.micGain}%")
                Slider(
                    value = s.micGain.toFloat(),
                    onValueChange = { v -> viewModel.update { copy(micGain = v.toInt()) } },
                    valueRange = 50f..200f,
                    modifier = Modifier.width(200.dp)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Громкоговоритель (Speaker)")
                Switch(checked = s.forceSpeakerOutput, onCheckedChange = { v -> viewModel.update { copy(forceSpeakerOutput = v) } })
            }
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Эхоподавление (AEC)")
                Switch(checked = s.useAec, onCheckedChange = { v -> viewModel.update { copy(useAec = v) } })
            }
        }
    }
}