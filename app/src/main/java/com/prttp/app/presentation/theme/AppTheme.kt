package com.prttp.app.presentation.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeId(val displayKey: String) {
    OBSIDIAN("theme_obsidian"),
    SAKURA("theme_sakura"),
    GEM("theme_gem");

    companion object {
        fun fromName(name: String?): AppThemeId =
            entries.firstOrNull { it.name == name } ?: GEM
    }
}

@Immutable
data class AppPalette(
    val id: AppThemeId,
    val isDark: Boolean,

    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHigh: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnAccent: Color,

    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentSoft: Color,

    val border: Color,
    val divider: Color,

    val statusRecording: Color,
    val statusOk: Color,
    val statusWarning: Color,

    val aura0: Color,
    val aura1: Color,
    val aura2: Color,
    val aura3: Color,
    val aura4: Color,
    val auraGlow: Color
) {
    val auraGradient: List<Color> get() = listOf(aura0, aura1, aura2, aura3, aura4)

    companion object {

        // ─── 1. OBSIDIAN — true-black OLED ─────────────────
        val Obsidian = AppPalette(
            id = AppThemeId.OBSIDIAN, isDark = true,
            background       = Color(0xFF000000),
            surface          = Color(0xFF0B0D0F),
            surfaceElevated  = Color(0xFF131519),
            surfaceHigh      = Color(0xFF1A1D23),
            textPrimary      = Color(0xFFF0F1F5),
            textSecondary    = Color(0xFFA5A9B8),
            textMuted        = Color(0xFF5D6171),
            textOnAccent     = Color(0xFF0B0D0F),
            accentPrimary    = Color(0xFF14B8A6),
            accentSecondary  = Color(0xFFA78BFA),
            accentSoft       = Color(0x2214B8A6),
            border           = Color(0xFF1F1F26),
            divider          = Color(0xFF1A1A1F),
            statusRecording  = Color(0xFFF87171),
            statusOk         = Color(0xFF34D399),
            statusWarning    = Color(0xFFFBBF24),
            aura0 = Color(0xFF0F766E),
            aura1 = Color(0xFF14B8A6),
            aura2 = Color(0xFF22D3EE),
            aura3 = Color(0xFFA78BFA),
            aura4 = Color(0xFF7C3AED),
            auraGlow = Color(0xFF22D3EE)
        )

        // ─── 2. SAKURA — тёплый dusty rose + teal ──────────
        val Sakura = AppPalette(
            id = AppThemeId.SAKURA, isDark = false,
            background       = Color(0xFFFCF8F3),
            surface          = Color(0xFFFFFCF7),
            surfaceElevated  = Color(0xFFFFFFFF),
            surfaceHigh      = Color(0xFFF6EFE5),
            textPrimary      = Color(0xFF3A2E26),
            textSecondary    = Color(0xFF6B5A4F),
            textMuted        = Color(0xFFA89589),
            textOnAccent     = Color(0xFFFFFFFF),
            accentPrimary    = Color(0xFFB45F8F),
            accentSecondary  = Color(0xFF5B8C8C),
            accentSoft       = Color(0xFFF5E0EA),
            border           = Color(0xFFEADDD0),
            divider          = Color(0xFFF1E8DC),
            statusRecording  = Color(0xFFC04A6A),
            statusOk         = Color(0xFF5B8C5B),
            statusWarning    = Color(0xFFC68642),
            aura0 = Color(0xFFB45F8F),
            aura1 = Color(0xFFD58BB0),
            aura2 = Color(0xFF5B8C8C),
            aura3 = Color(0xFF8FB5B5),
            aura4 = Color(0xFFB45F8F),
            auraGlow = Color(0xFFB45F8F)
        )

        // ─── 3. GEM — Apple-clean + Gemini sparkle ─────────
        val Gem = AppPalette(
            id = AppThemeId.GEM, isDark = false,
            background       = Color(0xFFFFFFFF),
            surface          = Color(0xFFFFFFFF),
            surfaceElevated  = Color(0xFFFFFFFF),
            surfaceHigh      = Color(0xFFF5F8FC),
            textPrimary      = Color(0xFF0A0A0A),
            textSecondary    = Color(0xFF6E6E73),
            textMuted        = Color(0xFFAEAEB2),
            textOnAccent     = Color(0xFFFFFFFF),
            accentPrimary    = Color(0xFF4285F4),
            accentSecondary  = Color(0xFF34A853),
            accentSoft       = Color(0xFFE8F0FE),
            border           = Color(0xFFBAE6FD),
            divider          = Color(0xFF7DD3FC),
            statusRecording  = Color(0xFF34A853),
            statusOk         = Color(0xFF34A853),
            statusWarning    = Color(0xFFFBBC04),
            aura0 = Color(0xFF34A853),
            aura1 = Color(0xFF06B6D4),
            aura2 = Color(0xFF4285F4),
            aura3 = Color(0xFFFBBC04),
            aura4 = Color(0xFFEA4335),
            auraGlow = Color(0xFF34A853)
        )

        fun byId(id: AppThemeId): AppPalette = when (id) {
            AppThemeId.OBSIDIAN -> Obsidian
            AppThemeId.SAKURA   -> Sakura
            AppThemeId.GEM      -> Gem
        }
    }
}

private const val THEME_TRANSITION_MS = 620

@Composable
fun rememberAnimatedPalette(target: AppPalette): AppPalette {
    val spec = tween<Color>(durationMillis = THEME_TRANSITION_MS, easing = FastOutSlowInEasing)
    return AppPalette(
        id = target.id,
        isDark = target.isDark,
        background       = animateColorAsState(target.background,      spec, label = "bg").value,
        surface          = animateColorAsState(target.surface,         spec, label = "sf").value,
        surfaceElevated  = animateColorAsState(target.surfaceElevated, spec, label = "sfE").value,
        surfaceHigh      = animateColorAsState(target.surfaceHigh,     spec, label = "sfH").value,
        textPrimary      = animateColorAsState(target.textPrimary,     spec, label = "tP").value,
        textSecondary    = animateColorAsState(target.textSecondary,   spec, label = "tS").value,
        textMuted        = animateColorAsState(target.textMuted,       spec, label = "tM").value,
        textOnAccent     = animateColorAsState(target.textOnAccent,    spec, label = "tA").value,
        accentPrimary    = animateColorAsState(target.accentPrimary,   spec, label = "aP").value,
        accentSecondary  = animateColorAsState(target.accentSecondary, spec, label = "aS").value,
        accentSoft       = animateColorAsState(target.accentSoft,      spec, label = "aSf").value,
        border           = animateColorAsState(target.border,          spec, label = "br").value,
        divider          = animateColorAsState(target.divider,         spec, label = "dv").value,
        statusRecording  = animateColorAsState(target.statusRecording, spec, label = "sR").value,
        statusOk         = animateColorAsState(target.statusOk,        spec, label = "sO").value,
        statusWarning    = animateColorAsState(target.statusWarning,   spec, label = "sW").value,
        aura0            = animateColorAsState(target.aura0,           spec, label = "g0").value,
        aura1            = animateColorAsState(target.aura1,           spec, label = "g1").value,
        aura2            = animateColorAsState(target.aura2,           spec, label = "g2").value,
        aura3            = animateColorAsState(target.aura3,           spec, label = "g3").value,
        aura4            = animateColorAsState(target.aura4,           spec, label = "g4").value,
        auraGlow         = animateColorAsState(target.auraGlow,        spec, label = "gl").value
    )
}

val LocalAppPalette = staticCompositionLocalOf { AppPalette.Gem }