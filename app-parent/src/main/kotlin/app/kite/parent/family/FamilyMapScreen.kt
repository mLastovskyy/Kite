package app.kite.parent.family

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.CircleIconButton
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.ScreenLoading
import app.kite.core.design.components.rowIcon
import app.kite.core.family.FamilyMember
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import app.kite.core.location.Place
import app.kite.core.location.PlaceEvent
import app.kite.core.location.PlacesRemote
import app.kite.parent.home.ChildSwitcher
import app.kite.parent.location.AddressSearch
import app.kite.parent.location.ExternalMap
import app.kite.parent.location.MapStyle
import app.kite.parent.location.MarkerBitmaps
import app.kite.parent.location.PlaceEditorScreen
import app.kite.parent.location.PlacesSection
import app.kite.parent.location.ReverseGeocoder
import app.kite.parent.location.deviceCountryCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «Карта»: the child's avatar on one calm map, the address with battery / freshness /
 * accuracy under it, and two floating buttons — open the spot in Google / Яндекс / the phone's
 * maps (with a «Спутник» switch), or ask the phone for a fresh fix. «Обновить» sends the
 * `locate` command and waits for a newer point, so the parent sees the phone answer.
 * Routes and places are deliberately not shown (owner, 04.09.2026: «пока не нужно»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMapScreen(
    familyId: String,
    children: List<FamilyMember>,
    selected: FamilyMember?,
    onSelectChild: (FamilyMember) -> Unit,
    locationRemote: DeviceLocationRemote,
    commandsRemote: CommandsRemote,
    placesRemote: PlacesRemote,
    versionName: String,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val geocoder = remember { ReverseGeocoder(context, versionName) }
    val addressSearch = remember { AddressSearch(versionName, deviceCountryCode(context)) }

    var row by remember { mutableStateOf<DeviceLocationRow?>(null) }
    var marker by remember { mutableStateOf<Bitmap?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var addressLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var openIn by remember { mutableStateOf(false) }
    var satellite by remember { mutableStateOf(false) }

    // Places («Уведомления по местам»): kept, but without radii on screen.
    var places by remember(selected?.id) { mutableStateOf<List<Place>?>(null) }
    var events by remember(selected?.id) { mutableStateOf<List<PlaceEvent>>(emptyList()) }
    var placesKey by remember { mutableIntStateOf(0) }
    var editingPlace by remember { mutableStateOf<Place?>(null) }
    var creatingPlace by remember { mutableStateOf(false) }
    var deletingPlace by remember { mutableStateOf<Place?>(null) }
    var placesError by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = creatingPlace || editingPlace != null) {
        creatingPlace = false
        editingPlace = null
    }

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
    LaunchedEffect(selected?.id, placesKey) {
        val child = selected ?: return@LaunchedEffect
        placesRemote.forChild(child.id)
            .onSuccess {
                places = it
                placesError = null
            }
            .onFailure {
                places = places ?: emptyList()
                placesError = it.message
            }
        events = placesRemote.events(child.id).getOrNull().orEmpty()
    }

    val child = selected

    if (child != null && (creatingPlace || editingPlace != null)) {
        val base = editingPlace
        // Start where the child is; failing that, the last saved place; failing that, the centre of Moscow.
        val startLat = row?.latitude ?: places?.firstOrNull()?.latitude ?: 55.7558
        val startLon = row?.longitude ?: places?.firstOrNull()?.longitude ?: 37.6173
        PlaceEditorScreen(
            initial = base,
            startLatitude = startLat,
            startLongitude = startLon,
            geocoder = geocoder,
            search = addressSearch,
            onSave = { name, lat, lon, notifyEnter, notifyExit ->
                scope.launch {
                    val result =
                        if (base == null) {
                            placesRemote.create(familyId, child.id, name, lat, lon, Place.DEFAULT_RADIUS, notifyEnter, notifyExit)
                        } else {
                            placesRemote.update(
                                base.copy(name = name, latitude = lat, longitude = lon, notifyEnter = notifyEnter, notifyExit = notifyExit),
                            )
                        }
                    result.onFailure { placesError = it.message }
                    creatingPlace = false
                    editingPlace = null
                    placesKey++
                }
            },
            onDelete = { deletingPlace = base },
            onCancel = {
                creatingPlace = false
                editingPlace = null
            },
        )
        deletingPlace?.let { place ->
            AppDialog(
                title = "Удалить место «${place.name}»?",
                message = "Уведомления о нём перестанут приходить.",
                confirmText = "Удалить",
                destructive = true,
                onConfirm = {
                    deletingPlace = null
                    scope.launch {
                        placesRemote.delete(place).onFailure { placesError = it.message }
                        editingPlace = null
                        placesKey++
                    }
                },
                onDismiss = { deletingPlace = null },
            )
        }
        return
    }

    fun togglePlace(place: Place, enter: Boolean?, exit: Boolean?) {
        val updated = place.copy(notifyEnter = enter ?: place.notifyEnter, notifyExit = exit ?: place.notifyExit)
        places = places?.map { if (it.id == place.id) updated else it }
        scope.launch { placesRemote.update(updated).onFailure { placesError = it.message } }
    }

    /** «Обновить»: ask the phone for a fresh fix and wait (up to ~45 s) for a newer point. */
    fun requestFreshFix() {
        val target = child ?: return
        if (locating) return
        scope.launch {
            locating = true
            note = null
            val before = row?.recordedAt
            commandsRemote.send(target.id, familyId, DeviceCommand.LOCATE)
            var updated = false
            repeat(9) {
                if (updated) return@repeat
                delay(5_000)
                val latest = locationRemote.latest(target.id).getOrNull()
                if (latest != null && latest.recordedAt != before) {
                    row = latest
                    updated = true
                }
            }
            if (!updated) {
                // Nothing new: still re-read, and say so instead of pretending.
                locationRemote.latest(target.id).onSuccess { row = it }
                note = "Телефон не ответил — показана последняя известная точка."
            }
            locating = false
        }
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

        if (child == null) {
            Text(
                text = "Здесь появится местоположение детей. Добавьте ребёнка на Главной.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }
        ChildSwitcher(children = children, selected = child, onSelect = onSelectChild)
        Spacer(Modifier.height(16.dp))

        val current = row
        when {
            loading && current == null -> ScreenLoading(caption = "Ищем телефон на карте…")

            failed != null && current == null ->
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = failed!!, style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Повторить", style = AppButtonStyle.Tinted, onClick = { reloadKey++ })
                }

            current == null ->
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Телефон ещё не прислал координаты.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Запросить сейчас", style = AppButtonStyle.Tinted, loading = locating, onClick = { requestFreshFix() })
                }

            else -> {
                Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(14.dp))) {
                    LocationMap(
                        latitude = current.latitude,
                        longitude = current.longitude,
                        styleUrl = MapStyle.DEFAULT.url,
                        marker = null,
                        trail = emptyList(),
                        places = emptyList(),
                        showFallbackPin = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // The child's avatar on the spot, drawn in Compose: the camera keeps the
                    // child at the centre, so this never depends on MapLibre symbol layers.
                    Column(Modifier.align(Alignment.Center).offset(y = (-26).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            KiteAvatar(preset = AvatarPreset.byId(child.avatarKind), size = 42.dp, avatarUrl = child.avatarUrl)
                        }
                        Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
                            val p = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(p, Color.White)
                        }
                    }
                    Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircleIconButton(icon = KiteIcons.Send, onClick = { openIn = true })
                        CircleIconButton(icon = KiteIcons.Refresh, loading = locating, onClick = { requestFreshFix() })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                    AppIcon(icon = KiteIcons.MapPin, tint = colors.accent, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text =
                            when {
                                address != null -> address!!
                                addressLoading -> "Определяем адрес…"
                                else -> "Адрес не определён"
                            },
                            style = typography.headline,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val battery = current.batteryPct
                            if (battery != null) {
                                AppIcon(
                                    icon = KiteIcons.Battery,
                                    tint =
                                    when {
                                        battery <= 20 -> colors.danger
                                        battery <= 50 -> colors.warning
                                        else -> colors.success
                                    },
                                    size = 16.dp,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text =
                                listOfNotNull(
                                    battery?.let { "$it%" },
                                    if (locating) "ждём ответ телефона…" else freshness(current.recordedAt),
                                    current.accuracyM?.let { "±${it.toInt()} м" },
                                ).joinToString(" · "),
                                style = typography.subhead,
                                color = colors.textSecondary,
                            )
                        }
                        note?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(text = it, style = typography.footnote, color = colors.textSecondary)
                        }
                    }
                }
                Text(
                    text = "© OpenStreetMap contributors · OpenFreeMap",
                    style = typography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 32.dp, top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        PlacesSection(
            places = places,
            events = events,
            onAdd = { creatingPlace = true },
            onEdit = { editingPlace = it },
            onToggleEnter = { place, on -> togglePlace(place, enter = on, exit = null) },
            onToggleExit = { place, on -> togglePlace(place, enter = null, exit = on) },
        )
        placesError?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = typography.footnote,
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (openIn && row != null) {
        val current = row!!
        val label = child?.displayName?.ifBlank { "Ребёнок" } ?: "Ребёнок"
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
                    InsetGroup {
                        ExternalMap.entries.forEach { app ->
                            row(
                                title = app.label,
                                icon = rowIcon(KiteIcons.Map, colors.accent),
                                showChevron = true,
                                onClick = {
                                    openIn = false
                                    val intent =
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(app.uri(current.latitude, current.longitude, label, satellite)),
                                        )
                                    runCatching { context.startActivity(intent) }
                                },
                            )
                        }
                    }
                    InsetGroup(footer = "Google и Яндекс откроются со спутниковыми снимками.") {
                        row(title = "Спутник", trailing = { AppSwitch(checked = satellite, onCheckedChange = { satellite = it }) })
                    }
                }
            }
        }
    }
}
