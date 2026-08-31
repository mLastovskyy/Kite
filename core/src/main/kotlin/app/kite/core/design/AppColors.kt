package app.kite.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Accent triple. The parent app is iOS blue; the child app (Kite Jr) is deliberately warm
 * so it does not read as a supervision app in the app drawer (DESIGN_SYSTEM.md).
 */
@Immutable
data class AccentColors(val accent: Color, val accentLight: Color, val accentDeep: Color) {
    companion object {
        val Parent = AccentColors(
            accent = Color(0xFF007AFF),
            accentLight = Color(0xFF4AA8FF),
            accentDeep = Color(0xFF0043BE),
        )
        val Child = AccentColors(
            accent = Color(0xFFFF9500),
            accentLight = Color(0xFFFFC44D),
            accentDeep = Color(0xFFE86A00),
        )
    }
}

/**
 * Full token set from DESIGN_SYSTEM.md. Feature code must read these via [LocalAppColors]
 * and never use MaterialTheme.colorScheme directly.
 */
@Immutable
data class AppColors(
    val accent: Color,
    val accentLight: Color,
    val accentDeep: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val bgGrouped: Color,
    val bgBase: Color,
    val bgElevated: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val fillQuaternary: Color,
    val isDark: Boolean,
)

fun lightAppColors(accents: AccentColors = AccentColors.Parent): AppColors = AppColors(
    accent = accents.accent,
    accentLight = accents.accentLight,
    accentDeep = accents.accentDeep,
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    danger = Color(0xFFFF3B30),
    info = Color(0xFF5856D6),
    bgGrouped = Color(0xFFF2F2F7),
    bgBase = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFFFFFFF),
    separator = Color(0x66C6C6C8), // #C6C6C8 at 40%
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43), // #3C3C43 at 60%
    textTertiary = Color(0x4D3C3C43), // #3C3C43 at 30%
    fillQuaternary = Color(0x14747480),
    isDark = false,
)

fun darkAppColors(accents: AccentColors = AccentColors.Parent): AppColors = AppColors(
    accent = accents.accent,
    accentLight = accents.accentLight,
    accentDeep = accents.accentDeep,
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    danger = Color(0xFFFF3B30),
    info = Color(0xFF5856D6),
    bgGrouped = Color(0xFF000000),
    bgBase = Color(0xFF1C1C1E),
    bgElevated = Color(0xFF2C2C2E),
    separator = Color(0x99545458), // #545458 at 60%
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5), // #EBEBF5 at 60%
    textTertiary = Color(0x4DEBEBF5), // #EBEBF5 at 30%
    // The doc defines fillQuaternary for light only; dark value mirrors iOS (~18%).
    fillQuaternary = Color(0x2E767680),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { lightAppColors() }
