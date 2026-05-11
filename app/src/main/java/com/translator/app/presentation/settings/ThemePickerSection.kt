// ═══════════════════════════════════════════════════════════
// НОВЫЙ ФАЙЛ
// Путь: app/src/main/java/com/translator/app/presentation/settings/ThemePickerSection.kt
//
// Секция выбора темы для SettingsScreen.
// Вставляется в самый верх настроек (premium-приём — оформление сразу).
//
// Каждая тема показана как карточка-превью с реальным фоном/акцентами
// палитры. Tap → сохранение в AppSettings.themeId.
// ═══════════════════════════════════════════════════════════
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translator.app.R
import com.translator.app.presentation.theme.AppPalette
import com.translator.app.presentation.theme.AppThemeId
import com.translator.app.presentation.theme.LocalAppPalette

/**
 * Используй внутри SettingsScreen:
 *
 *   ThemePickerSection(
 *       selected = AppThemeId.fromName(settings.themeId),
 *       onSelect = { id -> viewModel.update { copy(themeId = id.name) } }
 *   )
 */
@Composable
fun ThemePickerSection(
    selected: AppThemeId,
    onSelect: (AppThemeId) -> Unit
) {
    val palette = LocalAppPalette.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_section_appearance).uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = palette.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeOption(
                preview = AppPalette.Aurora,
                id = AppThemeId.AURORA,
                isSelected = selected == AppThemeId.AURORA,
                titleRes = R.string.theme_aurora,
                descRes = R.string.theme_aurora_desc,
                onClick = { onSelect(AppThemeId.AURORA) }
            )
            ThemeOption(
                preview = AppPalette.BerlinMist,
                id = AppThemeId.BERLIN_MIST,
                isSelected = selected == AppThemeId.BERLIN_MIST,
                titleRes = R.string.theme_berlin,
                descRes = R.string.theme_berlin_desc,
                onClick = { onSelect(AppThemeId.BERLIN_MIST) }
            )
            ThemeOption(
                preview = AppPalette.Sakura,
                id = AppThemeId.SAKURA,
                isSelected = selected == AppThemeId.SAKURA,
                titleRes = R.string.theme_sakura,
                descRes = R.string.theme_sakura_desc,
                onClick = { onSelect(AppThemeId.SAKURA) }
            )
            ThemeOption(
                preview = AppPalette.Obsidian,
                id = AppThemeId.OBSIDIAN,
                isSelected = selected == AppThemeId.OBSIDIAN,
                titleRes = R.string.theme_obsidian,
                descRes = R.string.theme_obsidian_desc,
                onClick = { onSelect(AppThemeId.OBSIDIAN) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    preview: AppPalette,
    id: AppThemeId,
    isSelected: Boolean,
    titleRes: Int,
    descRes: Int,
    onClick: () -> Unit
) {
    val current = LocalAppPalette.current
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "border"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(current.surface)
            .border(
                width = borderWidth,
                color = if (isSelected) current.accentPrimary else current.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Превью — мини-карточка с фоном и градиентной "капсулой" из палитры темы
        ThemePreview(preview)

        Spacer(Modifier.size(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = current.textPrimary
            )
            Text(
                text = stringResource(descRes),
                fontSize = 13.sp,
                color = current.textSecondary
            )
        }

        // Selected indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isSelected) current.accentPrimary else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (isSelected) current.accentPrimary else current.border,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(current.textOnAccent)
                )
            }
        }
    }
}

@Composable
private fun ThemePreview(preview: AppPalette) {
    // Карточка 80×54: фон палитры + миниатюрная "капсула" градиента.
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(preview.background)
            .border(0.5.dp, preview.border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Мини-капсула с градиентом, как сжатая Aurora-капля
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.linearGradient(preview.auraGradient))
        )
    }
}
