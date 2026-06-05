package com.prttp.app.presentation.specializations

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prttp.app.therapy.TherapistSpecializations

@Composable
fun SpecializationsScreen(modifier: Modifier = Modifier) {
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