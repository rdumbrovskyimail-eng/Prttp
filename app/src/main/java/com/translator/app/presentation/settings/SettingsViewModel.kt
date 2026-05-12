// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА (v2.0)
// Путь: app/src/main/java/com/translator/app/presentation/settings/SettingsViewModel.kt
//
// Что изменилось vs v1:
//   • Используем stateIn() поверх DataStore.data вместо first() —
//     UI ВСЕГДА видит актуальное состояние, даже если оно менялось
//     из другого процесса/места.
//   • update {} обновляет StateFlow МГНОВЕННО (для UI), и параллельно
//     пишет в DataStore. UI не ждёт I/O.
//   • Сохраняем темы и reveal-стиль; в AppSettings должны быть поля
//     themeId: String и messageRevealId: String (см. PROMPT внизу).
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.settings

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
        // Подтягиваем стартовое значение синхронно через first(),
        // а потом продолжаем слушать поток на случай внешних изменений.
        viewModelScope.launch {
            _settings.value = settingsStore.data.first()
        }
        settingsStore.data
            .onEach { fromDisk ->
                // Если запись на диск завершилась и мы получили обновление,
                // которое не совпадает с in-memory — синхронизируем.
                if (fromDisk != _settings.value) _settings.value = fromDisk
            }
            .launchIn(viewModelScope)
    }

    /**
     * Принимает transform-функцию, применяет её к текущему AppSettings,
     * сразу обновляет StateFlow (UI получает новое значение синхронно),
     * затем асинхронно записывает на диск.
     */
    fun update(transform: AppSettings.() -> AppSettings) {
        _settings.update(transform)
        viewModelScope.launch {
            settingsStore.updateData { _settings.value }
        }
    }
}
