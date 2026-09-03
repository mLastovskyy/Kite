package app.kite.parent.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.rowIcon
import app.kite.core.location.TrailPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * «Маршруты»: a day picker (Сегодня, Вчера, then weekdays back to 7 days), the day's
 * summary, and its stops with times and addresses. The polyline itself is drawn on the map
 * above by [app.kite.parent.family.LocationMap]; this section only lists.
 */
@Composable
fun RouteSection(
    dayOffset: Int,
    onDayChange: (Int) -> Unit,
    points: List<TrailPoint>?,
    stops: List<Stop>,
    stopAddresses: Map<Int, String?>,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val zone = remember { ZoneId.systemDefault() }

    Text(text = "Маршрут", style = typography.title3, color = colors.textPrimary, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { offset ->
            val label =
                when (offset) {
                    0 -> "Сегодня"
                    1 -> "Вчера"
                    else -> LocalDate.now(
                        zone,
                    ).minusDays(offset.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).replaceFirstChar {
                        it.uppercase()
                    }
                }
            DayChip(text = label, selected = offset == dayOffset, onClick = { onDayChange(offset) })
        }
    }
    Spacer(Modifier.height(12.dp))

    when {
        points == null ->
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                AppSpinner(color = colors.accent, size = 22.dp)
            }
        points.isEmpty() ->
            Text(
                text = if (dayOffset ==
                    0
                ) {
                    "Сегодня маршрута пока нет — точки появятся, когда телефон подвигается."
                } else {
                    "За этот день маршрута нет."
                },
                style = typography.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        else -> {
            val km = Routes.pathMeters(points) / 1000.0
            val first = Routes.epochMs(points.first().recordedAt)
            val last = Routes.epochMs(points.last().recordedAt)
            Text(
                text = "С ${clock(first, zone)} до ${clock(last, zone)} · ≈ ${"%.1f".format(km)} км · остановок: ${stops.size}",
                style = typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (stops.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                InsetGroupedList {
                    InsetGroup(header = "Остановки") {
                        stops.forEachIndexed { index, stop ->
                            row(
                                title = stopAddresses[index] ?: if (stopAddresses.containsKey(index)) "Остановка" else "Определяем адрес…",
                                value = "${clock(stop.fromMs, zone)} – ${clock(stop.toMs, zone)}",
                                icon = rowIcon(KiteIcons.MapPin, colors.info),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.bgBase)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else colors.textPrimary,
        )
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

private fun clock(epochMs: Long, zone: ZoneId): String = CLOCK.format(Instant.ofEpochMilli(epochMs).atZone(zone))
