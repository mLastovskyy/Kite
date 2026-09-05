package app.kite.parent.family

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.KiteLoader
import app.kite.core.family.FamilyMember
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import java.time.Duration
import java.time.Instant

private sealed interface LocState {
    data object Loading : LocState

    data class Ready(val row: DeviceLocationRow?) : LocState

    data class Failed(val message: String) : LocState
}

/**
 * Last known location of a child (M7). Shows coordinates, accuracy, freshness and battery,
 * and opens the device's map app via a geo: URI (GMS-free — works on Huawei). The embedded
 * MapLibre map is the next increment; the data pipeline and live position are here now.
 */
@Composable
fun ChildLocationScreen(member: FamilyMember, locationRemote: DeviceLocationRemote, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    var state by remember { mutableStateOf<LocState>(LocState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(member.id, reloadKey) {
        state = LocState.Loading
        locationRemote.latest(member.id)
            .onSuccess { state = LocState.Ready(it) }
            .onFailure { state = LocState.Failed(it.message ?: "Ошибка загрузки") }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Где ребёнок", style = typography.title1, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AppButton(text = "Закрыть", style = AppButtonStyle.Plain, onClick = onClose)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = member.displayName.ifBlank { "Ребёнок" }, style = typography.subhead, color = colors.textSecondary)
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            LocState.Loading ->
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    KiteLoader(size = 64.dp)
                }

            is LocState.Failed ->
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = s.message, style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Повторить", style = AppButtonStyle.Tinted, onClick = { reloadKey++ })
                }

            is LocState.Ready ->
                if (s.row == null) {
                    Text(
                        text = "Местоположение пока не получено. Оно появится, когда телефон ребёнка отправит координаты.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    )
                } else {
                    LocationMap(
                        latitude = s.row.latitude,
                        longitude = s.row.longitude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(16.dp))
                    LocationCard(s.row)
                    Spacer(Modifier.height(20.dp))
                    AppButton(
                        text = "Открыть на карте",
                        onClick = {
                            val uri = Uri.parse("geo:${s.row.latitude},${s.row.longitude}?q=${s.row.latitude},${s.row.longitude}(Ребёнок)")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    AppButton(text = "Обновить", style = AppButtonStyle.Tinted, onClick = { reloadKey++ })
                }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun LocationCard(row: DeviceLocationRow) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase).padding(16.dp)) {
        InfoRow("Координаты", "%.5f, %.5f".format(row.latitude, row.longitude))
        row.accuracyM?.let { InfoRow("Точность", "±${it.toInt()} м") }
        InfoRow("Обновлено", freshness(row.recordedAt))
        row.batteryPct?.let { InfoRow("Заряд", "$it%") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, style = typography.subhead, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(text = value, style = typography.body, color = colors.textPrimary)
    }
}

internal fun freshness(isoTime: String): String {
    val instant = runCatching { Instant.parse(isoTime) }.getOrNull() ?: return "недавно"
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        minutes < 24 * 60 -> "${minutes / 60} ч назад"
        else -> "${minutes / (24 * 60)} дн назад"
    }
}

/** The same age in a row-sized form: a long value squeezes the title into a broken column. */
internal fun freshnessShort(isoTime: String): String {
    val instant = runCatching { Instant.parse(isoTime) }.getOrNull() ?: return "—"
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "сейчас"
        minutes < 60 -> "$minutes мин"
        minutes < 24 * 60 -> "${minutes / 60} ч"
        else -> "${minutes / (24 * 60)} дн"
    }
}
