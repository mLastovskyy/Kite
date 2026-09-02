package app.kite.parent.family

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.UsagePeriodSwitch
import app.kite.core.design.components.rowIcon
import app.kite.core.family.FamilyMember
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import app.kite.parent.home.ChildSwitcher
import app.kite.parent.location.ExternalMap
import app.kite.parent.location.MapPrefs
import app.kite.parent.location.MapStyle
import app.kite.parent.location.MarkerBitmaps
import app.kite.parent.location.ReverseGeocoder

/**
 * «Карта» tab: the selected child's avatar on the map, the address of that spot, freshness,
 * accuracy and battery; «Обновить» and «Открыть в…» (Google, Яндекс, 2ГИС, or any installed
 * map). The map look is switchable («Стандарт / Яркая / Светлая», remembered on this phone).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMapScreen(
    children: List<FamilyMember>,
    selected: FamilyMember?,
    onSelectChild: (FamilyMember) -> Unit,
    locationRemote: DeviceLocationRemote,
    versionName: String,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = remember { MapPrefs(context) }
    val geocoder = remember { ReverseGeocoder(context, versionName) }

    var style by remember { mutableStateOf(prefs.style()) }
    var row by remember { mutableStateOf<DeviceLocationRow?>(null) }
    var marker by remember { mutableStateOf<Bitmap?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var addressLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var openIn by remember { mutableStateOf(false) }

    LaunchedEffect(selected?.id, reloadKey) {
        val child = selected ?: return@LaunchedEffect
        loading = true
        failed = null
        locationRemote.latest(child.id)
            .onSuccess { row = it }
            .onFailure { failed = it.message ?: "Ошибка загрузки" }
        loading = false
    }
    LaunchedEffect(selected?.id, selected?.avatarUrl, selected?.avatarKind) {
        val child = selected ?: return@LaunchedEffect
        marker = null
        marker = runCatching { MarkerBitmaps.forMember(context, child, with(density) { 48.dp.roundToPx() }) }.getOrNull()
    }
    LaunchedEffect(row?.latitude, row?.longitude) {
        val current = row ?: return@LaunchedEffect
        addressLoading = true
        address = geocoder.address(current.latitude, current.longitude)
        addressLoading = false
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

        if (selected == null) {
            Text(
                text = "Здесь появится местоположение детей. Добавьте ребёнка на Главной.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }
        ChildSwitcher(children = children, selected = selected, onSelect = onSelectChild)
        Spacer(Modifier.height(12.dp))
        UsagePeriodSwitch(
            labels = MapStyle.entries.map { it.label },
            selectedIndex = style.ordinal,
            onSelect = {
                style = MapStyle.entries[it]
                prefs.setStyle(style)
            },
        )
        Spacer(Modifier.height(12.dp))

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
                    styleUrl = style.url,
                    marker = marker,
                    modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(icon = KiteIcons.MapPin, tint = colors.accent, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text =
                        when {
                            address != null -> address!!
                            addressLoading -> "Определяем адрес…"
                            else -> "Адрес не определён"
                        },
                        style = typography.headline,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "© OpenStreetMap contributors · OpenFreeMap",
                    style = typography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                )
                Spacer(Modifier.height(16.dp))
                LocationCard(current)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    AppButton(text = "Открыть в…", modifier = Modifier.weight(1f), onClick = { openIn = true })
                    AppButton(
                        text = if (loading) "Обновляем…" else "Обновить",
                        style = AppButtonStyle.Tinted,
                        modifier = Modifier.weight(1f),
                        onClick = { reloadKey++ },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (openIn && row != null) {
        val current = row!!
        val label = selected?.displayName?.ifBlank { "Ребёнок" } ?: "Ребёнок"
        ModalBottomSheet(onDismissRequest = { openIn = false }, containerColor = colors.bgGrouped, dragHandle = null) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Открыть в",
                    style = typography.title3,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(12.dp))
                InsetGroupedList {
                    InsetGroup(footer = "Если приложение не установлено, откроется сайт карт.") {
                        ExternalMap.entries.forEach { app ->
                            row(
                                title = app.label,
                                icon = rowIcon(KiteIcons.Map, colors.accent),
                                showChevron = true,
                                onClick = {
                                    openIn = false
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app.uri(current.latitude, current.longitude, label)))
                                    runCatching { context.startActivity(intent) }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
