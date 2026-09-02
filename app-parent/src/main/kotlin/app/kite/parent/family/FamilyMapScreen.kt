package app.kite.parent.family

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
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
import app.kite.core.design.components.AppSpinner
import app.kite.core.family.FamilyMember
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow

/**
 * «Карта» tab: the latest position of each child on one screen. Children are switched with
 * chips; the map re-centres on the selected one. Offline the tiles do not load, but the
 * coordinates, freshness and battery still show from the last sync.
 */
@Composable
fun FamilyMapScreen(members: List<FamilyMember>, locationRemote: DeviceLocationRemote) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    val children = remember(members) { members.filterNot { it.isParent } }

    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = children.firstOrNull { it.id == selectedId } ?: children.firstOrNull()
    var row by remember { mutableStateOf<DeviceLocationRow?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected?.id, reloadKey) {
        val child = selected ?: return@LaunchedEffect
        loading = true
        failed = null
        locationRemote.latest(child.id)
            .onSuccess { row = it }
            .onFailure { failed = it.message ?: "Ошибка загрузки" }
        loading = false
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
        Text(text = "Карта", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))

        if (children.isEmpty()) {
            Text(
                text = "Здесь появится местоположение детей. Добавьте ребёнка на вкладке «Семья».",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }

        if (children.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                children.forEach { child ->
                    AppButton(
                        text = child.displayName.ifBlank { "Ребёнок" },
                        style = if (child.id == selected?.id) AppButtonStyle.Filled else AppButtonStyle.Tinted,
                        onClick = { selectedId = child.id },
                        modifier = Modifier.height(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Text(text = selected?.displayName?.ifBlank { "Ребёнок" } ?: "", style = typography.subhead, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
        }

        val current = row
        when {
            loading && current == null ->
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    AppSpinner(color = colors.accent, size = 28.dp)
                }

            failed != null && current == null ->
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = failed!!, style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Повторить", style = AppButtonStyle.Tinted, onClick = { reloadKey++ })
                }

            current == null ->
                Text(
                    text = "Местоположение пока не получено. Оно появится, когда телефон ребёнка отправит координаты.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )

            else -> {
                LocationMap(
                    latitude = current.latitude,
                    longitude = current.longitude,
                    modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(16.dp))
                LocationCard(current)
                Spacer(Modifier.height(16.dp))
                AppButton(
                    text = "Открыть в картах",
                    onClick = {
                        val uri = Uri.parse(
                            "geo:${current.latitude},${current.longitude}?q=${current.latitude},${current.longitude}(Ребёнок)",
                        )
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                AppButton(text = if (loading) "Обновляем…" else "Обновить", style = AppButtonStyle.Tinted, onClick = { reloadKey++ })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
