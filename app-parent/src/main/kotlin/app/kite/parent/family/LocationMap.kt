package app.kite.parent.family

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.kite.core.design.LocalAppColors
import app.kite.parent.location.MapStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A coordinate for the trail polyline. */
data class GeoPointUi(val latitude: Double, val longitude: Double)

/** A saved place drawn as a circle of [radiusM] metres. */
data class PlaceCircleUi(val latitude: Double, val longitude: Double, val radiusM: Double)

private const val MARKER_SOURCE = "kite-child"
private const val MARKER_LAYER = "kite-child-layer"
private const val MARKER_DOT_LAYER = "kite-child-dot"
private const val MARKER_IMAGE = "kite-child-avatar"
private const val TRAIL_SOURCE = "kite-trail"
private const val TRAIL_LAYER = "kite-trail-layer"
private const val PLACES_SOURCE = "kite-places"
private const val PLACES_FILL_LAYER = "kite-places-fill"
private const val PLACES_LINE_LAYER = "kite-places-line"
private const val STOPS_SOURCE = "kite-stops"
private const val STOPS_LAYER = "kite-stops-layer"
private const val SELF_SOURCE = "kite-self"
private const val SELF_LAYER = "kite-self-layer"
private const val SELF_COLOR = "#007AFF"

/**
 * MapLibre map on OpenFreeMap tiles (no key, commercial use allowed — CLAUDE.md pin), GMS-free.
 * Draws, bottom to top: the saved [places] as translucent circles, the day's [trail] as a
 * polyline, and the child's avatar [marker] on the coordinate (bottom-anchored). Without a
 * marker bitmap a Compose pin marks the camera target. [styleUrl] switches the map look.
 * The camera follows the child at street level; «мои места» is what frames a whole set.
 * Tiles need internet; offline it degrades to the attribution background. NEEDS_DEVICE_TEST.
 */
@Composable
fun LocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    styleUrl: String = MapStyle.LIBERTY.url,
    marker: Bitmap? = null,
    selfLatitude: Double? = null,
    selfLongitude: Double? = null,
    controller: MapController? = null,
    trail: List<GeoPointUi> = emptyList(),
    stops: List<GeoPointUi> = emptyList(),
    places: List<PlaceCircleUi> = emptyList(),
    centrePin: Boolean = false,
    onCameraIdle: ((latitude: Double, longitude: Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = colors.accent.toArgb()
    val placeColor = colors.info.toArgb()

    // MapLibre must be initialised before a MapView is created, and the view may be created
    // exactly once: a second onCreate spins up a second renderer on the same surface.
    val loadColor = colors.bgGrouped.toArgb()
    val mapView = remember {
        MapLibre.getInstance(context)
        val options = MapLibreMapOptions.createFromAttributes(context).foregroundLoadColor(loadColor)
        MapView(context, options).apply {
            onCreate(null)
            // The map asks the page to keep its hands off the gesture. This is the interop
            // contract Compose honours; consuming the events in Compose instead cancels the
            // gesture inside the map and leaves it dead to every touch that follows.
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }
        }
    }
    val target = remember(latitude, longitude) { LatLng(latitude, longitude) }
    val overlays =
        remember(marker, trail, stops, places, accent, placeColor, selfLatitude, selfLongitude) {
            Overlays(marker, trail, stops, places, accent, placeColor, selfLatitude, selfLongitude, !centrePin)
        }
    val pointTarget = remember(centrePin, target) { target.takeIf { !centrePin } }
    val idle = rememberUpdatedState(onCameraIdle)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var framedTarget by remember { mutableStateOf<LatLng?>(null) }
    // While the parent is exploring the map, a new fix must not yank the camera back.
    val lastTouchAt = remember { mutableLongStateOf(0L) }

    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        started = true
                        mapView.onStart()
                    }
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> {
                        started = false
                        mapView.onStop()
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // The style loads once per URL. Restarting it on every recomposition is what used to
    // leave the map grey: a poll or a spinner elsewhere on the screen cancelled the load.
    LaunchedEffect(map, styleUrl) {
        val ready = map ?: return@LaunchedEffect
        style = null
        ready.setStyle(Style.Builder().fromUri(styleUrl)) { style = it }
    }

    // Sources and layers are rebuilt only when their contents actually change.
    LaunchedEffect(style, overlays, pointTarget) {
        overlays.apply(style ?: return@LaunchedEffect, target)
    }

    LaunchedEffect(controller, map, target) {
        controller?.attach(map, target) { lastTouchAt.longValue = 0L }
    }

    LaunchedEffect(style, target, overlays) {
        val ready = map ?: return@LaunchedEffect
        if (style == null || idle.value != null || framedTarget == target) return@LaunchedEffect
        val first = framedTarget == null
        if (!first && System.currentTimeMillis() - lastTouchAt.longValue < FOLLOW_PAUSE_MS) return@LaunchedEffect
        frame(ready, target, animate = !first)
        framedTarget = target
    }

    Box(modifier.watchTouches { lastTouchAt.longValue = System.currentTimeMillis() }, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { ready ->
                        ready.uiSettings.isRotateGesturesEnabled = true
                        ready.moveCamera(CameraUpdateFactory.newLatLngZoom(target, START_ZOOM))
                        // Place picker: the parent pans, the centre is the pick.
                        ready.addOnCameraIdleListener {
                            val centre = ready.cameraPosition.target ?: return@addOnCameraIdleListener
                            idle.value?.invoke(centre.latitude, centre.longitude)
                        }
                        map = ready
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        )
        // Only the place picker draws a pin in Compose: there the mark belongs to the centre
        // of the screen, not to a coordinate. Everywhere else the child is a map layer that
        // stays on its own spot while the parent pans.
        if (centrePin) MapPin(color = colors.accent, modifier = Modifier.offset(y = (-16).dp))
    }
}

/**
 * Notes that the parent touched the map, so a new fix does not yank the camera away. It only
 * watches — nothing is consumed, because a consumed event makes Compose cancel the gesture
 * inside the embedded map view.
 */
private fun Modifier.watchTouches(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            onTouch()
        }
    }
}

/**
 * Camera commands for a [LocationMap] drawn elsewhere in the layout: «вернуть к ребёнку» and
 * the zoom pair. Held by the screen, so the buttons can sit outside the map composable.
 */
class MapController {
    private var map: MapLibreMap? = null
    private var target: LatLng? = null
    private var resumeFollow: () -> Unit = {}

    internal fun attach(map: MapLibreMap?, target: LatLng, resumeFollow: () -> Unit) {
        this.map = map
        this.target = target
        this.resumeFollow = resumeFollow
    }

    fun recenter() {
        val ready = map ?: return
        val point = target ?: return
        resumeFollow()
        ready.animateCamera(CameraUpdateFactory.newLatLngZoom(point, maxOf(ready.cameraPosition.zoom, START_ZOOM)))
    }

    /** Jump to a point the parent picked elsewhere — an address from search, say. */
    fun moveTo(latitude: Double, longitude: Double) {
        val ready = map ?: return
        resumeFollow()
        ready.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), maxOf(ready.cameraPosition.zoom, START_ZOOM)),
        )
    }

    /** Frames everything given — «показать все мои места» — instead of following the child. */
    fun fit(points: List<Pair<Double, Double>>) {
        val ready = map ?: return
        if (points.isEmpty()) return
        resumeFollow()
        if (points.size == 1) {
            ready.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].first, points[0].second), START_ZOOM))
            return
        }
        val bounds = LatLngBounds.Builder().also { b -> points.forEach { b.include(LatLng(it.first, it.second)) } }.build()
        runCatching { ready.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX)) }
    }

    fun zoomBy(delta: Double) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta))
    }
}

@Composable
fun rememberMapController(): MapController = remember { MapController() }

/** Breathing room around a fitted set of points. */
private const val FIT_PADDING_PX = 96

/** How long a pan or pinch keeps the camera under the parent's control. */
private const val FOLLOW_PAUSE_MS = 30_000L

/** Street level: close enough to read the block, wide enough to see where it is. */
private const val START_ZOOM = 15.0

/**
 * Follow the child at street level — the same shot the «вернуть к ребёнку» arrow gives
 * (owner, 07.09.2026). Fitting the whole day's route on open zoomed the map so far out that
 * the point the parent came for was a speck; the route is still there to pan along, and
 * «мои места» has its own fit.
 */
private fun frame(map: MapLibreMap, target: LatLng, animate: Boolean) {
    runCatching {
        val update = CameraUpdateFactory.newLatLngZoom(target, maxOf(map.cameraPosition.zoom, START_ZOOM))
        if (animate) map.animateCamera(update) else map.moveCamera(update)
    }
}

/** The three overlays as GeoJSON sources + layers; (re)applied idempotently. */
private class Overlays(
    private val marker: Bitmap?,
    val trail: List<GeoPointUi>,
    private val stops: List<GeoPointUi>,
    private val places: List<PlaceCircleUi>,
    private val accent: Int,
    private val placeColor: Int,
    private val selfLatitude: Double? = null,
    private val selfLongitude: Double? = null,
    private val childPoint: Boolean = true,
) {
    fun apply(style: Style, target: LatLng) {
        runCatching {
            listOf(MARKER_LAYER, MARKER_DOT_LAYER, TRAIL_LAYER, STOPS_LAYER, PLACES_LINE_LAYER, PLACES_FILL_LAYER, SELF_LAYER)
                .forEach { style.removeLayer(it) }
            listOf(MARKER_SOURCE, TRAIL_SOURCE, STOPS_SOURCE, PLACES_SOURCE, SELF_SOURCE).forEach { style.removeSource(it) }

            if (places.isNotEmpty()) {
                style.addSource(GeoJsonSource(PLACES_SOURCE, placesGeoJson(places)))
                style.addLayer(
                    FillLayer(PLACES_FILL_LAYER, PLACES_SOURCE).withProperties(
                        PropertyFactory.fillColor(placeColor),
                        PropertyFactory.fillOpacity(0.14f),
                    ),
                )
                style.addLayer(
                    LineLayer(PLACES_LINE_LAYER, PLACES_SOURCE).withProperties(
                        PropertyFactory.lineColor(placeColor),
                        PropertyFactory.lineWidth(1.5f),
                        PropertyFactory.lineOpacity(0.8f),
                    ),
                )
            }
            if (trail.size >= 2) {
                val coords = trail.joinToString(",") { "[${it.longitude},${it.latitude}]" }
                style.addSource(
                    GeoJsonSource(
                        TRAIL_SOURCE,
                        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}""",
                    ),
                )
                style.addLayer(
                    LineLayer(TRAIL_LAYER, TRAIL_SOURCE).withProperties(
                        PropertyFactory.lineColor(accent),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineOpacity(0.9f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
            }
            // Stops: where the phone stood still long enough to matter. Numbered in the list
            // under the map — on the map they stay dots so the route itself reads first.
            if (stops.isNotEmpty()) {
                val features =
                    stops.joinToString(",") {
                        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.longitude},${it.latitude}]},"properties":{}}"""
                    }
                style.addSource(GeoJsonSource(STOPS_SOURCE, """{"type":"FeatureCollection","features":[$features]}"""))
                style.addLayer(
                    CircleLayer(STOPS_LAYER, STOPS_SOURCE).withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor(accent),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                    ),
                )
            }
            if (selfLatitude != null && selfLongitude != null) {
                val selfPoint = """{"type":"Point","coordinates":[$selfLongitude,$selfLatitude]}"""
                style.addSource(GeoJsonSource(SELF_SOURCE, """{"type":"Feature","geometry":$selfPoint,"properties":{}}"""))
                style.addLayer(
                    CircleLayer(SELF_LAYER, SELF_SOURCE).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(SELF_COLOR),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                    ),
                )
            }
            // The child is always on the map, avatar or not: a missing bitmap used to leave
            // the coordinate marked by nothing at all.
            if (childPoint) {
                val point = """{"type":"Point","coordinates":[${target.longitude},${target.latitude}]}"""
                style.addSource(GeoJsonSource(MARKER_SOURCE, """{"type":"Feature","geometry":$point,"properties":{}}"""))
                // The dot is what actually marks the child: the avatar is a sprite, and a
                // sprite that fails to upload would leave the coordinate blank.
                style.addLayer(
                    CircleLayer(MARKER_DOT_LAYER, MARKER_SOURCE).withProperties(
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleColor(accent),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                    ),
                )
                if (marker != null) {
                    style.addImage(MARKER_IMAGE, marker)
                    style.addLayer(
                        SymbolLayer(MARKER_LAYER, MARKER_SOURCE).withProperties(
                            PropertyFactory.iconImage(MARKER_IMAGE),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        ),
                    )
                }
            }
        }
    }

    /** Each place as a 48-vertex polygon in metres — exact on the ground at every zoom. */
    private fun placesGeoJson(places: List<PlaceCircleUi>): String {
        val features =
            places.joinToString(",") { place ->
                val ring =
                    (0..48).joinToString(",") { i ->
                        val angle = 2 * PI * (i % 48) / 48
                        val dLat = place.radiusM * cos(angle) / METERS_PER_DEGREE
                        val dLon = place.radiusM * sin(angle) / (METERS_PER_DEGREE * cos(Math.toRadians(place.latitude)))
                        "[${place.longitude + dLon},${place.latitude + dLat}]"
                    }
                """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[[$ring]]},"properties":{}}"""
            }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_320.0
    }
}

@Composable
private fun MapPin(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(44.dp)) {
        val w = size.width
        val h = size.height * 0.86f
        // A soft ellipse on the ground gives the pin somewhere to stand.
        drawOval(
            color = Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(w * 0.30f, size.height * 0.87f),
            size = Size(w * 0.40f, size.height * 0.10f),
        )
        val path =
            Path().apply {
                moveTo(w * 0.5f, h)
                cubicTo(w * 0.5f, h, w * 0.08f, h * 0.52f, w * 0.08f, h * 0.37f)
                cubicTo(w * 0.08f, h * 0.15f, w * 0.29f, 0f, w * 0.5f, 0f)
                cubicTo(w * 0.71f, 0f, w * 0.92f, h * 0.15f, w * 0.92f, h * 0.37f)
                cubicTo(w * 0.92f, h * 0.52f, w * 0.5f, h, w * 0.5f, h)
                close()
            }
        drawPath(path, color)
        drawPath(path, Color.White, style = Stroke(width = w * 0.055f))
        drawCircle(color = Color.White, radius = w * 0.135f, center = Offset(w * 0.5f, h * 0.37f))
    }
}
