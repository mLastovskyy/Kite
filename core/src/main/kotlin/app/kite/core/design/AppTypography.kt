package app.kite.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kite.core.R

/**
 * Inter (SIL OFL), bundled — the closest free metric match to SF Pro.
 * SF Pro / SF Symbols must never ship in an Android APK (Apple license).
 */
val InterFontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/**
 * Type scale from DESIGN_SYSTEM.md. Body is 17sp — that single choice does most of the
 * iOS-like feel. All sizes are in sp, so the user's font scale is respected.
 */
@Immutable
data class AppTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
)

private fun interStyle(size: Int, weight: FontWeight, tracking: Double): TextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

fun kiteTypography(): AppTypography = AppTypography(
    largeTitle = interStyle(34, FontWeight.Bold, -0.4),
    title1 = interStyle(28, FontWeight.Bold, -0.3),
    title2 = interStyle(22, FontWeight.SemiBold, -0.2),
    title3 = interStyle(20, FontWeight.SemiBold, -0.2),
    headline = interStyle(17, FontWeight.SemiBold, -0.1),
    body = interStyle(17, FontWeight.Normal, -0.1),
    callout = interStyle(16, FontWeight.Normal, 0.0),
    subhead = interStyle(15, FontWeight.Normal, 0.0),
    footnote = interStyle(13, FontWeight.Normal, 0.0),
    caption = interStyle(12, FontWeight.Normal, 0.0),
)

val LocalAppTypography = staticCompositionLocalOf { kiteTypography() }
