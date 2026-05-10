package com.translator.app.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsStore: DataStore<AppSettings>
) : ViewModel() {
    var apiKey by mutableStateOf("")
        private set
    var isLoaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            apiKey = settingsStore.data.first().apiKey
            isLoaded = true
        }
    }

    fun updateKey(key: String) { apiKey = key }

    fun saveAndContinue(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsStore.updateData { it.copy(apiKey = apiKey.trim()) }
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(
    onNavigateToTranslator: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    if (!viewModel.isLoaded) return
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        if (viewModel.apiKey.isNotEmpty()) onNavigateToTranslator()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Gemini Translate", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A73E8))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Добро пожаловать! Для работы синхронного переводчика необходим API ключ Google Gemini.", fontSize = 15.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = viewModel.apiKey,
            onValueChange = { viewModel.updateKey(it) },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1A73E8), focusedLabelColor = Color(0xFF1A73E8))
        )

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") }) {
            Text("Получить ключ в Google AI Studio", color = Color(0xFF1A73E8))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { viewModel.saveAndContinue(onNavigateToTranslator) },
            enabled = viewModel.apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
        ) {
            Text("Перейти в переводчик", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}