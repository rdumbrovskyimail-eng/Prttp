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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prttp.app.therapy.TherapistSpecializations

@Composable
fun SpecializationsScreen(
    currentTheme: com.prttp.app.therapy.ImageTheme,
    onThemeSelected: (com.prttp.app.therapy.ImageTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = Brush.verticalGradient(
        listOf(Color(0xFF0E1A24), Color(0xFF132A2E), Color(0xFF0E1A24))
    )
    LazyColumn(
        modifier = modifier.fillMaxSize().background(bg),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Запросы, с которыми работает терапевт",
                color = Color(0xFFCFE3E0),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ИИ читает этот список при каждой сессии",
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
            Spacer(Modifier.height(8.dp))
            ImageThemePickerCard(
                currentTheme = currentTheme,
                onSelect = onThemeSelected
            )
        }
    }
}

@Composable
fun ImageThemePickerCard(
    currentTheme: com.prttp.app.therapy.ImageTheme,
    onSelect: (com.prttp.app.therapy.ImageTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFF1A2D2C)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Тема терапевтических изображений",
                color = Color(0xFF6FE3C9),
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "ИИ будет подбирать образы в этом стиле",
                color = Color(0x776FE3C9),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            // Сетка тем 2 колонки
            com.prttp.app.therapy.ImageTheme.values()
                .toList()
                .chunked(2)
                .forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        row.forEach { theme ->
                            val selected = theme == currentTheme
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) Color(0x336FE3C9) else Color(0x0AFFFFFF)
                                    )
                                    .border(
                                        width = if (selected) 1.dp else 0.5.dp,
                                        color = if (selected) Color(0xFF6FE3C9) else Color(0x226FE3C9),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelect(theme) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(theme.emoji, fontSize = 20.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = theme.label,
                                        color = if (selected) Color(0xFF6FE3C9) else Color(0x99CFE3E0),
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        // Заполнить пустую ячейку если нечётное кол-во
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
        }
    }
}

@Composable
private fun SpecBlock(title: String, items: List<String>, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2D2C)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
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
                    Box(Modifier.size(7.dp).background(accent.copy(alpha = 0.7f), CircleShape))
                    Text(text = item, color = Color(0xFFCFE3E0), fontSize = 14.sp)
                }
            }
        }
    }
}