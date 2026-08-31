package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors

/**
 * 1-px hairline. [startInset] aligns it with the row title instead of the full width —
 * the detail DESIGN_SYSTEM.md insists on for list separators.
 */
@Composable
fun HairlineSeparator(modifier: Modifier = Modifier, startInset: Dp = 0.dp) {
    val colors = LocalAppColors.current
    val hairline = with(LocalDensity.current) { 1f.toDp() }
    Box(
        modifier
            .padding(start = startInset)
            .fillMaxWidth()
            .height(hairline)
            .background(colors.separator),
    )
}
