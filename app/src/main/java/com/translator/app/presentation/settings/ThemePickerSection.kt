package com.translator.app.presentation.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.translator.app.presentation.theme.AppPalette
import com.translator.app.presentation.theme.AppThemeId
import com.translator.app.presentation.theme.LocalAppPalette

@Composable
fun ThemePickerSection(
    selected: AppThemeId,
    onSelect: (AppThemeId) -> Unit
) {
    val palette = LocalAppPalette.current

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "ТЕМА И АНИМАЦИЯ ГОЛОСА",
            fontSize = 11.sp, fontWeight = FontWeight.W700,
            letterSpacing = 1.5.sp, color = palette.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeOption(
                preview = AppPalette.Obsidian, id = AppThemeId.OBSIDIAN,
                isSelected = selected == AppThemeId.OBSIDIAN,
                title = "Obsidian",
                desc = "True-black OLED. Энергошар с орбитами.",
                onClick = { onSelect(AppThemeId.OBSIDIAN) }
            )
            ThemeOption(
                preview = AppPalette.Sakura, id = AppThemeId.SAKURA,
                isSelected = selected == AppThemeId.SAKURA,
                title = "Sakura",
                desc = "Тёплый dusty rose + teal. Капли воды.",
                onClick = { onSelect(AppThemeId.SAKURA) }
            )
            ThemeOption(
                preview = AppPalette.Gem, id = AppThemeId.GEM,
                isSelected = selected == AppThemeId.GEM,
                title = "Gem",
                desc = "Кристал-белая Apple-clean. Чернильное Gemini-пятно.",
                onClick = { onSelect(AppThemeId.GEM) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    preview: AppPalette,
    id: AppThemeId,
    isSelected: Boolean,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    val current = LocalAppPalette.current
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) current.accentSoft else current.surface)
            .border(
                width = borderWidth,
                color = if (isSelected) current.accentPrimary else current.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemePreview(preview)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.W700,
                color = current.textPrimary, letterSpacing = (-0.2).sp)
            Text(desc, fontSize = 12.sp, color = current.textSecondary, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isSelected) current.accentPrimary else Color.Transparent)
                .border(1.5.dp,
                    if (isSelected) current.accentPrimary else current.border,
                    CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(current.textOnAccent))
            }
        }
    }
}

@Composable
private fun ThemePreview(preview: AppPalette) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(preview.background)
            .border(0.5.dp, preview.border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.linearGradient(preview.auraGradient))
        )
    }
}