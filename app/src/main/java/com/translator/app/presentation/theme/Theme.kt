// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА (v3.0)
// Путь: app/src/main/java/com/translator/app/presentation/theme/Theme.kt
//
// Корневая тема. Принимает themeId, строит target-палитру,
// оборачивает её в живую интерполяцию (rememberAnimatedPalette)
// и пробрасывает в LocalAppPalette + Material3 ColorScheme.
//
// Смена темы в настройках мгновенно отражается на всём UI —
// без перезапуска, без перемонтирования NavGraph.
// ═══════════════════════════════════════════════════════════
package com.translator.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Composable
fun GeminiLiveTheme(
    themeId: AppThemeId,
    content: @Composable () -> Unit
) {
    val target = AppPalette.byId(themeId)
    // Живая интерполяция — все 22 цветовых поля «перетекают» за ~620 мс.
    val palette = rememberAnimatedPalette(target)

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary           = palette.accentPrimary,
            onPrimary         = palette.textOnAccent,
            background        = palette.background,
            onBackground      = palette.textPrimary,
            surface           = palette.surface,
            onSurface         = palette.textPrimary,
            surfaceVariant    = palette.surfaceHigh,
            onSurfaceVariant  = palette.textSecondary,
            outline           = palette.border,
            error             = palette.statusRecording,
            secondary         = palette.accentSecondary
        )
    } else {
        lightColorScheme(
            primary           = palette.accentPrimary,
            onPrimary         = palette.textOnAccent,
            background        = palette.background,
            onBackground      = palette.textPrimary,
            surface           = palette.surface,
            onSurface         = palette.textPrimary,
            surfaceVariant    = palette.surfaceHigh,
            onSurfaceVariant  = palette.textSecondary,
            outline           = palette.border,
            error             = palette.statusRecording,
            secondary         = palette.accentSecondary
        )
    }

    // System bars — мгновенный свич по target.isDark, чтобы иконки не мигали.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = !target.isDark
            ctrl.isAppearanceLightNavigationBars = !target.isDark
        }
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AppTypography,
            content     = content
        )
    }
}

/**
 * Типографика — нейтральный sans-serif с регулировкой веса и трекинга.
 * Если позже добавишь Inter / SF Pro via Downloadable Fonts —
 * поменяй FontFamily.Default на свой.
 */
private val AppTypography = Typography(
    displayLarge   = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.5).sp),
    displayMedium  = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.2).sp),
    titleLarge     = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W600),
    titleMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W600),
    bodyLarge      = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W500),
    bodyMedium     = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodySmall      = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    labelLarge     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, letterSpacing = 0.2.sp),
    labelMedium    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W500),
    labelSmall     = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 0.6.sp)
)
