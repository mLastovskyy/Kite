package app.kite.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * Screen-time charts shared by both apps: the parent draws them from synced aggregates, the
 * child from its own Room table, so everything here takes plain numbers and no data source.
 * Compose Canvas, no chart library (DESIGN_SYSTEM.md).
 */

/** One app row in the ranked list. */
data class UsageAppItem(val packageName: String, val label: String, val totalMs: Long)

/** One coloured piece of a stacked weekly bar. */
data class WeekStackPart(val color: Color, val ms: Long)

// iOS Screen Time rank palette: #1 blue, #2 teal, #3 orange, the rest grey.
val UsageRankColors = listOf(Color(0xFF007AFF), Color(0xFF30B0C7), Color(0xFFFF9500))
val UsageRestColor = Color(0xFF8E8E93)

fun usageRankColor(index: Int): Color = UsageRankColors.getOrElse(index) { UsageRestColor }

/** «2 ч 14 мин» / «56 мин» — the only usage duration format in the product. */
fun formatUsageMs(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
        hours > 0 -> "$hours ч"
        else -> "$minutes мин"
    }
}

/** iOS segmented control; [labels] are short («День», «Неделя»). */
@Composable
fun UsagePeriodSwitch(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.separator.copy(alpha = 0.5f))
            .padding(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) colors.bgBase else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                // One line per segment, shrinking rather than wrapping («Всегда заблокированы»).
                FitText(
                    text = label,
                    style = typography.subhead,
                    color = if (active) colors.textPrimary else colors.textSecondary,
                    minFontSize = 11.sp,
                )
            }
        }
    }
}

/** Caption, the big total, and an optional comparison line under it. */
@Composable
fun UsageTotalHeader(caption: String, totalMs: Long, note: String? = null) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Text(text = caption, style = typography.subhead, color = colors.textSecondary)
    Text(text = formatUsageMs(totalMs), style = typography.largeTitle, color = colors.textPrimary)
    if (note != null) {
        Text(text = note, style = typography.footnote, color = colors.textSecondary)
    }
}

/** 24 hourly bars in one colour, with a 0/6/12/18/24 axis. */
@Composable
fun HourBarsCard(hourly: List<Long>, barColor: Color = LocalAppColors.current.accent) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val separator = colors.separator
    val bars = List(24) { hourly.getOrElse(it) { 0L } }
    val max = (bars.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val slot = size.width / 24f
            val barWidth = slot * 0.62f
            bars.forEachIndexed { index, value ->
                val barHeight = (value.toFloat() / max) * (size.height - 2f)
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2.5f, barWidth / 2.5f),
                    )
                }
            }
            drawLine(separator, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24").forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = typography.caption,
                    color = colors.textTertiary,
                    textAlign =
                    when (index) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Seven daily bars. [stacks] may be empty (bars are then drawn in [fallbackColor]);
 * otherwise each bar is stacked bottom-up from its parts. The dashed line is the average.
 */
@Composable
fun WeekBarsCard(
    labels: List<String>,
    totals: List<Long>,
    stacks: List<List<WeekStackPart>> = emptyList(),
    averageMs: Long = 0L,
    fallbackColor: Color = LocalAppColors.current.accent,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val separator = colors.separator
    val max = (totals.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val slot = size.width / totals.size.coerceAtLeast(1)
            val barWidth = slot * 0.5f
            totals.forEachIndexed { index, total ->
                if (total <= 0L) return@forEachIndexed
                val fullHeight = (total.toFloat() / max) * (size.height - 2f)
                val x = index * slot + (slot - barWidth) / 2f
                val parts = stacks.getOrElse(index) { emptyList() }
                if (parts.isEmpty()) {
                    drawRoundRect(
                        color = fallbackColor,
                        topLeft = Offset(x, size.height - fullHeight),
                        size = Size(barWidth, fullHeight),
                        cornerRadius = CornerRadius(barWidth / 2.5f, barWidth / 2.5f),
                    )
                    return@forEachIndexed
                }
                var bottom = size.height
                parts.forEach { part ->
                    val partHeight = (part.ms.toFloat() / total) * fullHeight
                    if (partHeight > 0f) {
                        drawRect(part.color, topLeft = Offset(x, bottom - partHeight), size = Size(barWidth, partHeight))
                        bottom -= partHeight
                    }
                }
            }
            if (averageMs > 0) {
                val y = size.height - (averageMs.toFloat() / max) * (size.height - 2f)
                drawLine(
                    color = separator,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
            drawLine(separator, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = typography.caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Colour dots + labels for the stacked weekly chart. */
@Composable
fun UsageLegend(items: List<UsageAppItem>) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(usageRankColor(index)))
                Spacer(Modifier.size(4.dp))
                Text(text = item.label, style = typography.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** Ranked app list: initial-letter tile, label, duration, thin share bar under the row. */
@Composable
fun UsageAppsCard(
    items: List<UsageAppItem>,
    header: String = "Приложения",
    onItemClick: ((UsageAppItem) -> Unit)? = null,
    iconFor: (@Composable (UsageAppItem) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    if (items.isEmpty()) return
    val maxMs = items.first().totalMs.coerceAtLeast(1L)

    Text(
        text = header,
        style = typography.footnote,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase)) {
        items.forEachIndexed { index, item ->
            val rankColor = usageRankColor(index)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (onItemClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemClick(item) },
                            )
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconFor != null) {
                        iconFor(item)
                    } else {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(rankColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = item.label.take(1).uppercase(), style = typography.headline, color = rankColor)
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = item.label,
                        style = typography.body,
                        color = colors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = formatUsageMs(item.totalMs), style = typography.subhead, color = colors.textSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.separator)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (item.totalMs.toFloat() / maxMs).coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(rankColor),
                    )
                }
            }
            if (index < items.lastIndex) HairlineSeparator(startInset = 60.dp)
        }
    }
}
