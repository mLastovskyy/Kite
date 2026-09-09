package app.kite.parent.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.EmptyState
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.rowIcon
import app.kite.core.location.Place
import app.kite.core.location.PlaceEvent
import app.kite.core.util.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «Где был» in full: every stop of the chosen day and every arrival and departure that is
 * still on the server. The sections on the map screen show only the first couple of each —
 * the map is for «где сейчас», and a list of twenty visits under it buries that (owner,
 * 07.09.2026). Opened from either «Показать все», closed by the back button or the gesture.
 */
@Composable
fun VisitsScreen(
    dayLabel: String,
    stops: List<Stop>,
    stopAddresses: Map<Int, String?>,
    events: List<PlaceEvent>,
    places: List<Place>,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val zone = remember { ZoneId.systemDefault() }
    val names = remember(places) { places.associate { it.id to it.name } }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "Где был", onBack = onBack)
        Spacer(Modifier.height(20.dp))

        if (stops.isEmpty() && events.isEmpty()) {
            EmptyState(icon = KiteIcons.MapPin, text = "Пока нечего показать: остановок и посещений ещё не было.")
            return@Column
        }

        InsetGroupedList {
            if (stops.isNotEmpty()) {
                InsetGroup(header = "Остановки · $dayLabel", footer = "Остановка — место, где телефон пробыл 15 минут и дольше.") {
                    stops.forEachIndexed { index, stop ->
                        row(
                            title = "${index + 1}. " + (stopAddresses[index] ?: "Остановка"),
                            value = "${clock(stop.fromMs, zone)} – ${clock(stop.toMs, zone)}",
                            subtitle = dwell(stop.dwellMs),
                            icon = rowIcon(KiteIcons.MapPin, colors.info),
                        )
                    }
                }
            }

            if (events.isNotEmpty()) {
                // Grouped by day: the server keeps the last few dozen events, and «в 14:20»
                // means nothing when the row above it is from last Tuesday.
                events.groupBy { dayOf(it.at, zone) }.forEach { (day, dayEvents) ->
                    InsetGroup(header = header(day)) {
                        dayEvents.forEach { event ->
                            val name = names[event.placeId] ?: "Место"
                            row(
                                title = if (event.isEnter) "Прибытие: $name" else "Уход: $name",
                                value = Timestamps.instantOrNull(event.at)?.atZone(zone)?.let(CLOCK::format).orEmpty(),
                                icon = rowIcon(
                                    if (event.isEnter) KiteIcons.CircleCheck else KiteIcons.LogOut,
                                    if (event.isEnter) colors.success else colors.warning,
                                ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

private fun clock(epochMs: Long, zone: ZoneId): String = CLOCK.format(Instant.ofEpochMilli(epochMs).atZone(zone))

private fun dayOf(iso: String?, zone: ZoneId): LocalDate? = Timestamps.instantOrNull(iso)?.atZone(zone)?.toLocalDate()

private fun header(day: LocalDate?): String {
    day ?: return "Ранее"
    val today = LocalDate.now()
    return when (day) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> day.format(DAY)
    }
}

private fun dwell(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> "$minutes мин"
        minutes % 60 == 0L -> "${minutes / 60} ч"
        else -> "${minutes / 60} ч ${minutes % 60} мин"
    }
}
