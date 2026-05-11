// ═══════════════════════════════════════════════════════════
// ПОЛНАЯ ЗАМЕНА
// Путь: app/src/main/java/com/translator/app/presentation/theme/Theme.kt
//
// Обёртка темы: принимает AppThemeId, подставляет нужную AppPalette
// через CompositionLocal, настраивает system bars (status/navigation)
// под цвет фона.
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

/**
 * Корневая тема приложения.
 *
 * @param themeId выбранная пользователем тема (4 варианта).
 * @param content контент.
 *
 * Параллельно прокидывается:
 *   • AppPalette через LocalAppPalette — для всех custom Composable
 *   • Material3 ColorScheme — для встроенных Material3 компонент
 *     (IconButton ripple, TextField, Switch и т.д.)
 */
@Composable
fun GeminiLiveTheme(
    themeId: AppThemeId,
    content: @Composable () -> Unit
) {
    val palette = AppPalette.byId(themeId)

    // Material3 ColorScheme — для совместимости с библиотечными компонентами.
    // Custom UI читает напрямую из LocalAppPalette.
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accentPrimary,
            onPrimary = palette.textOnAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.statusRecording,
            secondary = palette.accentSecondary
        )
    } else {
        lightColorScheme(
            primary = palette.accentPrimary,
            onPrimary = palette.textOnAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.statusRecording,
            secondary = palette.accentSecondary
        )
    }

    // Системные бары прозрачные, иконки контрастные.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !palette.isDark
            controller.isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

/**
 * Типографика — нейтральная, без "андроидного" характера.
 * Используем системный sans-serif с регулировкой веса.
 * Если в проекте добавишь Inter через google-fonts — поменяй здесь
 * FontFamily.Default на FontFamily(Font(googleFont = ...)).
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.W600, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.W600),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W600),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W500, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W600, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W500),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W500, letterSpacing = 0.5.sp)
)
