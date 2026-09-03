package app.kite.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/** Icon slot for a row: 29dp rounded square with a solid background. */
class RowIcon(val background: Color, val content: @Composable BoxScope.() -> Unit)

/** Collects the rows of one [InsetGroup]; rows are declared, then composed with separators. */
class InsetGroupScope internal constructor() {
    internal class Entry(val separatorInset: Dp, val content: @Composable () -> Unit)

    internal val entries = mutableListOf<Entry>()

    /** Standard row: `[icon] title … value [trailing] [chevron]` (DESIGN_SYSTEM.md anatomy). */
    fun row(
        title: String,
        value: String? = null,
        icon: RowIcon? = null,
        showChevron: Boolean = false,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        // Separator starts where the title starts: 16dp padding, plus 29dp icon + 12dp gap.
        val inset = if (icon != null) 57.dp else 16.dp
        entries += Entry(inset) { InsetRow(title, value, icon, showChevron, enabled, onClick, trailing) }
    }

    /** Free-form cell (button stacks, charts, …); manages its own padding. */
    fun custom(separatorInset: Dp = 16.dp, content: @Composable () -> Unit) {
        entries += Entry(separatorInset, content)
    }
}

/**
 * One section of the inset-grouped list: 16dp side margins, 10dp corner card, footnote
 * header/footer (not uppercase), left-inset separators between rows.
 */
@Composable
fun InsetGroup(modifier: Modifier = Modifier, header: String? = null, footer: String? = null, content: InsetGroupScope.() -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = InsetGroupScope().apply(content)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                text = header,
                style = typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bgBase),
        ) {
            scope.entries.forEachIndexed { index, entry ->
                entry.content()
                if (index < scope.entries.lastIndex) {
                    HairlineSeparator(startInset = entry.separatorInset)
                }
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                style = typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
    }
}

/** Column of [InsetGroup]s with standard spacing, for non-lazy screens. */
@Composable
fun InsetGroupedList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

@Composable
private fun InsetRow(
    title: String,
    value: String?,
    icon: RowIcon?,
    showChevron: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val clickModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
        } else {
            Modifier
        }
    // Press feedback: instant flash to fillQuaternary, no ripple (DESIGN_SYSTEM.md).
    val background = if (pressed && onClick != null) colors.fillQuaternary else Color.Transparent
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .background(background)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        // The value may take at most this much; the title always keeps the majority. A fixed dp
        // cap squeezed titles into one-letter columns on narrow phones.
        val valueMax = maxWidth * 0.42f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(29.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(icon.background),
                    contentAlignment = Alignment.Center,
                    content = icon.content,
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = typography.body,
                color = if (enabled) colors.textPrimary else colors.textTertiary,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (value != null) {
                // The title owns the leftover width (weight); the value is capped so a long one
                // wraps/ellipsises instead of squeezing the title into a one-letter column. Values
                // are meant to be short («2 ч», «Выкл») — long explanations belong in a footer.
                Text(
                    text = value,
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = valueMax),
                )
            }
            if (trailing != null) {
                if (value != null) Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                Chevron(color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun Chevron(color: Color) {
    Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        val path =
            Path().apply {
                moveTo(stroke / 2, stroke / 2)
                lineTo(size.width - stroke / 2, size.height / 2)
                lineTo(stroke / 2, size.height - stroke / 2)
            }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
