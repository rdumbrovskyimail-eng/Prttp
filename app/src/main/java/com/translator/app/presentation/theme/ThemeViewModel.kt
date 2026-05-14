package com.translator.app.presentation.theme

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.translator.app.data.settings.AppSettings
import com.translator.app.presentation.translator.reveal.MessageRevealId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsStore: DataStore<AppSettings>
) : ViewModel() {
    val themeId = settingsStore.data
        .map { AppThemeId.fromName(it.themeId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeId.GEM)

    val revealId = settingsStore.data
        .map { MessageRevealId.fromName(it.messageRevealId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MessageRevealId.SOFT_FADE)
}