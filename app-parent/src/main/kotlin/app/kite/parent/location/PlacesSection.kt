package app.kite.parent.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.KiteLoader
import app.kite.core.design.components.rowIcon
import app.kite.core.location.Place
import app.kite.core.location.PlaceEvent
import app.kite.parent.family.LocationMap
import app.kite.parent.rules.SubScreenHeader
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «Места» (Kids360 «Уведомления по местам»): the saved places with their «Приход»/«Уход»
 * switches, the latest arrivals and departures, and «Добавить место». No radii on screen —
 * the owner wants a place to be a name and a spot, nothing to tune (radius is fixed to
 * [Place.DEFAULT_RADIUS] on the device side).
 */
@Composable
fun PlacesSection(
    places: List<Place>?,
    events: List<PlaceEvent>,
    onAdd: () -> Unit,
    onEdit: (Place) -> Unit,
    onToggleEnter: (Place, Boolean) -> Unit,
    onToggleExit: (Place, Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val zone = remember { ZoneId.systemDefault() }

    Text(text = "Места", style = typography.title3, color = colors.textPrimary, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Приход и уход — уведомлением.",
        style = typography.footnote,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(12.dp))

    when {
        places == null ->
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                KiteLoader(size = 64.dp)
            }
        else ->
            InsetGroupedList {
                if (places.isNotEmpty()) {
                    InsetGroup {
                        places.forEach { place ->
                            custom(separatorInset = 57.dp) {
                                PlaceRow(
                                    place = place,
                                    onClick = { onEdit(place) },
                                    onToggleEnter = { onToggleEnter(place, it) },
                                    onToggleExit = { onToggleExit(place, it) },
                                )
                            }
                        }
                    }
                }
                if (events.isNotEmpty()) {
                    val names = places.associate { it.id to it.name }
                    InsetGroup(header = "Недавно") {
                        events.take(6).forEach { event ->
                            val name = names[event.placeId] ?: "Место"
                            row(
                                title = if (event.isEnter) "Прибытие: $name" else "Уход: $name",
                                value = TIME.format(Instant.parse(event.at).atZone(zone)),
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
    Spacer(Modifier.height(12.dp))
    AppButton(text = "Добавить место", style = AppButtonStyle.Tinted, onClick = onAdd)
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit, onToggleEnter: (Boolean) -> Unit, onToggleExit: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = KiteIcons.MapPin, background = colors.info)
        Spacer(Modifier.width(12.dp))
        Text(text = place.name, style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        Spacer(Modifier.width(8.dp))
        SwitchWithCaption("Приход", place.notifyEnter, onToggleEnter)
        Spacer(Modifier.width(10.dp))
        SwitchWithCaption("Уход", place.notifyExit, onToggleExit)
    }
}

@Composable
private fun SwitchWithCaption(caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppSwitch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.height(2.dp))
        Text(text = caption, style = typography.caption, color = colors.textSecondary)
    }
}

/**
 * «Новое место»: pick the spot by panning the map (the pin is the centre) or by typing an
 * address and choosing a suggestion; name it; choose what to be told about. No radius.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceEditorScreen(
    initial: Place?,
    startLatitude: Double,
    startLongitude: Double,
    geocoder: ReverseGeocoder,
    search: AddressSearch,
    onSave: (name: String, latitude: Double, longitude: Double, notifyEnter: Boolean, notifyExit: Boolean) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var latitude by remember { mutableStateOf(initial?.latitude ?: startLatitude) }
    var longitude by remember { mutableStateOf(initial?.longitude ?: startLongitude) }
    var notifyEnter by remember { mutableStateOf(initial?.notifyEnter ?: true) }
    var notifyExit by remember { mutableStateOf(initial?.notifyExit ?: true) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf<String?>(null) }
    var region by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Address under the map follows the pin (debounced: the map fires idle often while panning).
    LaunchedEffect(latitude, longitude) {
        delay(600)
        address = geocoder.address(latitude, longitude)
    }
    // The area suggestions come from = where the pin started (the child's town, in practice).
    LaunchedEffect(startLatitude, startLongitude) {
        region = geocoder.city(startLatitude, startLongitude)
    }
    // Suggestions: ≥ 1 s after the last keystroke, one request at a time (Nominatim policy).
    // Ranked around the pin so «школа 33» is the one in this town (owner, 04.09.2026).
    LaunchedEffect(query) {
        if (query.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(1_000)
        searching = true
        suggestions = search.suggest(query, nearLatitude = latitude, nearLongitude = longitude)
        searching = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SubScreenHeader(title = if (initial == null) "Новое место" else "Место", onBack = onCancel)
        Spacer(Modifier.height(16.dp))

        InsetGroupedList {
            InsetGroup(header = "Где") {
                custom {
                    Column(Modifier.padding(8.dp)) {
                        AppTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = region?.let { "Адрес или название · $it" } ?: "Адрес или название",
                        )
                        if (searching || suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgGrouped)) {
                                if (searching && suggestions.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        AppSpinner(color = colors.accent, size = 18.dp)
                                    }
                                }
                                suggestions.forEach { s ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                latitude = s.latitude
                                                longitude = s.longitude
                                                query = ""
                                                suggestions = emptyList()
                                                if (name.isBlank()) name = s.title.take(Place.MAX_NAME)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text(text = s.title, style = typography.body, color = colors.textPrimary, maxLines = 1)
                                        Text(text = s.subtitle, style = typography.footnote, color = colors.textSecondary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                custom {
                    Column {
                        Box(Modifier.fillMaxWidth().height(220.dp)) {
                            LocationMap(
                                latitude = latitude,
                                longitude = longitude,
                                styleUrl = MapStyle.DEFAULT.url,
                                onCameraIdle = { lat, lon ->
                                    latitude = lat
                                    longitude = lon
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            text = address ?: "Передвиньте карту, чтобы точка была на месте",
                            style = typography.footnote,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            InsetGroup(header = "Название") {
                custom {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        AppTextField(
                            value = name,
                            onValueChange = {
                                name = it.take(Place.MAX_NAME)
                                error = null
                            },
                            placeholder = "Например: Дом",
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Place.NAME_SUGGESTIONS.forEach { suggestion ->
                                Chip(text = suggestion, selected = name == suggestion, onClick = { name = suggestion })
                            }
                        }
                    }
                }
            }
            InsetGroup(header = "Уведомлять") {
                row(title = "О приходе", trailing = { AppSwitch(checked = notifyEnter, onCheckedChange = { notifyEnter = it }) })
                row(title = "Об уходе", trailing = { AppSwitch(checked = notifyExit, onCheckedChange = { notifyExit = it }) })
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = error!!, style = typography.subhead, color = colors.danger)
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = if (initial == null) "Добавить место" else "Сохранить",
            onClick = {
                if (name.isBlank()) error = "Введите название" else onSave(name.trim(), latitude, longitude, notifyEnter, notifyExit)
            },
        )
        if (initial != null) {
            Spacer(Modifier.height(8.dp))
            AppButton(text = "Удалить место", style = AppButtonStyle.Plain, onClick = onDelete)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.fillQuaternary)
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

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, H:mm")
