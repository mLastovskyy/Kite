package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import kotlin.math.abs

private val ROW_HEIGHT = 40.dp
private const val VISIBLE_ROWS = 5

/**
 * iOS-style drum: [items] scroll vertically and snap to the centre row, which sits on a
 * rounded highlight; rows fade and shrink towards the edges. Pure Compose (LazyColumn +
 * snap fling), no library. [selectedIndex] is the initial position; [onSelect] fires as the
 * drum settles on a row.
 */
@Composable
fun WheelPicker(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, width: Dp = 96.dp) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val padRows = VISIBLE_ROWS / 2
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val centreIndex by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - centre) }
                ?.index
                ?.minus(padRows)
                ?.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                ?: selectedIndex
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centreIndex }
            .collect { (scrolling, index) -> if (!scrolling) onSelect(index) }
    }
    Box(modifier.width(width).height(ROW_HEIGHT * VISIBLE_ROWS), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.fillQuaternary),
        )
        LazyColumn(state = state, flingBehavior = fling, modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * VISIBLE_ROWS)) {
            items(items.size + padRows * 2) { slot ->
                val index = slot - padRows
                val label = items.getOrNull(index) ?: ""
                val distance = abs(index - centreIndex)
                val emphasis = (1f - distance * 0.28f).coerceIn(0.25f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .alpha(emphasis)
                        .graphicsLayer {
                            val s = 1f - distance * 0.08f
                            scaleX = s.coerceAtLeast(0.7f)
                            scaleY = s.coerceAtLeast(0.7f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (distance == 0) typography.title3 else typography.body,
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Hours + minutes drums for a duration («2 ч 30 мин»). Minutes step by [minuteStep]; the
 * result is clamped to [minMinutes]..[maxMinutes] by the caller's [onChange] contract.
 */
@Composable
fun DurationWheel(totalMinutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, maxHours: Int = 12, minuteStep: Int = 15) {
    val typography = LocalAppTypography.current
    val colors = LocalAppColors.current
    val hours = (0..maxHours).map { it.toString() }
    val minutes = (0 until 60 step minuteStep).map { it.toString().padStart(2, '0') }
    val h = (totalMinutes / 60).coerceIn(0, maxHours)
    val m = ((totalMinutes % 60) / minuteStep).coerceIn(0, minutes.lastIndex)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(items = hours, selectedIndex = h, onSelect = { onChange(it * 60 + m * minuteStep) })
        Text(text = "ч", style = typography.body, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp))
        WheelPicker(items = minutes, selectedIndex = m, onSelect = { onChange(h * 60 + it * minuteStep) })
        Text(text = "мин", style = typography.body, color = colors.textSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Hours (0–23) + minutes drums for a time of day, in minutes from midnight. */
@Composable
fun ClockWheel(minutesOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, minuteStep: Int = 5, drumWidth: Dp = 64.dp) {
    val typography = LocalAppTypography.current
    val colors = LocalAppColors.current
    val hours = (0..23).map { it.toString().padStart(2, '0') }
    val minutes = (0 until 60 step minuteStep).map { it.toString().padStart(2, '0') }
    val h = (minutesOfDay / 60).coerceIn(0, 23)
    val m = ((minutesOfDay % 60) / minuteStep).coerceIn(0, minutes.lastIndex)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(items = hours, selectedIndex = h, width = drumWidth, onSelect = { onChange(it * 60 + m * minuteStep) })
        Text(text = ":", style = typography.title2, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 2.dp))
        WheelPicker(items = minutes, selectedIndex = m, width = drumWidth, onSelect = { onChange(h * 60 + it * minuteStep) })
    }
}
