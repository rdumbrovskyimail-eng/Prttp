// ПОЛНАЯ ЗАМЕНА ФАЙЛА:
// app/src/main/java/com/prttp/app/presentation/specializations/SpecializationsScreen.kt

package com.prttp.app.presentation.specializations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.prttp.app.data.PatientRepository
import com.prttp.app.domain.model.PatientProfile
import com.prttp.app.therapy.ImageTheme
import com.prttp.app.therapy.TherapistSpecializations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────

@HiltViewModel
class SpecializationsViewModel @Inject constructor(
    private val repo: PatientRepository
) : ViewModel() {

    val profile = repo.profile

    fun setTheme(theme: ImageTheme) {
        viewModelScope.launch { repo.setImageTheme(theme) }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SpecializationsScreen(
    viewModel: SpecializationsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E1A24), Color(0xFF132A2E), Color(0xFF0E1A24))
                )
            ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Запросы, с которыми работает терапевт",
                color = Color(0xFFCFE3E0),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "ИИ читает этот список при каждой сессии",
                color = Color(0x776FE3C9),
                fontSize = 12.sp
            )
        }

        item {
            SpecBlock(
                title = "Эмоциональные и личностные запросы",
                items = TherapistSpecializations.BLOCK_A,
                accent = Color(0xFF6FE3C9)
            )
        }

        item {
            SpecBlock(
                title = "Отношения, травмы, жизненные кризисы",
                items = TherapistSpecializations.BLOCK_B,
                accent = Color(0xFFB39DDB)
            )
        }

        item {
            ImageThemePickerCard(
                currentTheme = profile.imageTheme,
                onSelect = viewModel::setTheme
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Composables
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun SpecBlock(title: String, items: List<String>, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2D2C)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.7f))
                    )
                    Text(item, color = Color(0xFFCFE3E0), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ImageThemePickerCard(
    currentTheme: ImageTheme,
    onSelect: (ImageTheme) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2D2C)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Тема визуальных образов",
                color = Color(0xFF6FE3C9),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ИИ подбирает изображения в этом стиле",
                color = Color(0x776FE3C9),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ImageTheme.entries.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    row.forEach { theme ->
                        val selected = theme == currentTheme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Color(0x336FE3C9) else Color(0x0AFFFFFF)
                                )
                                .border(
                                    width = if (selected) 1.dp else 0.5.dp,
                                    color = if (selected) Color(0xFF6FE3C9) else Color(0x226FE3C9),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelect(theme) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(theme.emoji, fontSize = 20.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    theme.label,
                                    color = if (selected) Color(0xFF6FE3C9) else Color(0x99CFE3E0),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
