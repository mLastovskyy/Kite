package app.kite.parent.family

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
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
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
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
private const val MARKER_IMAGE = "kite-child-avatar"
private const val TRAIL_SOURCE = "kite-trail"
private const val TRAIL_LAYER = "kite-trail-layer"
private const val PLACES_SOURCE = "kite-places"
private const val PLACES_FILL_LAYER = "kite-places-fill"
private const val PLACES_LINE_LAYER = "kite-places-line"

/**
 * MapLibre map on OpenFreeMap tiles (no key, commercial use allowed — CLAUDE.md pin), GMS-free.
 * Draws, bottom to top: the saved [places] as translucent circles, the day's [trail] as a
 * polyline, and the child's avatar [marker] on the coordinate (bottom-anchored). Without a
 * marker bitmap a Compose pin marks the camera target. [styleUrl] switches the map look.
 * With a trail the camera fits the whole route; otherwise it follows the child.
 * Tiles need internet; offline it degrades to the attribution background. NEEDS_DEVICE_TEST.
 */
@Composable
fun LocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    styleUrl: String = MapStyle.LIBERTY.url,
    marker: Bitmap? = null,
    trail: List<GeoPointUi> = emptyList(),
    places: List<PlaceCircleUi> = emptyList(),
    showFallbackPin: Boolean = true,
    onCameraIdle: ((latitude: Double, longitude: Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = colors.accent.toArgb()
    val placeColor = colors.info.toArgb()

    // MapLibre must be initialised before a MapView is created.
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val target = remember(latitude, longitude) { LatLng(latitude, longitude) }
    val overlays = remember(marker, trail, places, accent, placeColor) { Overlays(marker, trail, places, accent, placeColor) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
                        // Place picker: the parent pans, the centre is the pick.
                        if (onCameraIdle != null) {
                            map.addOnCameraIdleListener {
                                val c = map.cameraPosition.target ?: return@addOnCameraIdleListener
                                onCameraIdle(c.latitude, c.longitude)
                            }
                        }
                        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                            overlays.apply(style, target)
                            frame(map, target, overlays.trail, animate = false)
                        }
                    }
                }
            },
            update = {
                it.getMapAsync { map ->
                    val loaded = map.style
                    // A new style URL reloads the style (and the overlays with it); otherwise refresh in place.
                    if (loaded == null || loaded.uri != styleUrl) {
                        map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                            overlays.apply(style, target)
                            frame(map, target, overlays.trail, animate = false)
                        }
                    } else if (loaded.isFullyLoaded) {
                        overlays.apply(loaded, target)
                        if (onCameraIdle == null) frame(map, target, overlays.trail, animate = true)
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        )
        if (showFallbackPin && marker == null && trail.isEmpty()) {
            // Fallback pin (drawn in Compose): points at the camera target = the child.
            MapPin(color = colors.accent, modifier = Modifier.offset(y = (-14).dp))
        }
    }
}

/** Fit the route when there is one, else follow the child. */
private fun frame(map: MapLibreMap, target: LatLng, trail: List<GeoPointUi>, animate: Boolean) {
    runCatching {
        val update =
            if (trail.size >= 2) {
                val bounds = LatLngBounds.Builder().also { b -> trail.forEach { p -> b.include(LatLng(p.latitude, p.longitude)) } }.build()
                CameraUpdateFactory.newLatLngBounds(bounds, 64)
            } else {
                CameraUpdateFactory.newLatLng(target)
            }
        if (animate) map.animateCamera(update) else map.moveCamera(update)
    }
}

/** The three overlays as GeoJSON sources + layers; (re)applied idempotently. */
private class Overlays(
    private val marker: Bitmap?,
    val trail: List<GeoPointUi>,
    private val places: List<PlaceCircleUi>,
    private val accent: Int,
    private val placeColor: Int,
) {
    fun apply(style: Style, target: LatLng) {
        runCatching {
            listOf(MARKER_LAYER, TRAIL_LAYER, PLACES_LINE_LAYER, PLACES_FILL_LAYER).forEach { style.removeLayer(it) }
            listOf(MARKER_SOURCE, TRAIL_SOURCE, PLACES_SOURCE).forEach { style.removeSource(it) }

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
            if (marker != null) {
                style.addImage(MARKER_IMAGE, marker)
                val point = """{"type":"Point","coordinates":[${target.longitude},${target.latitude}]}"""
                style.addSource(GeoJsonSource(MARKER_SOURCE, """{"type":"Feature","geometry":$point,"properties":{}}"""))
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
    Canvas(modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        // Teardrop pin: rounded head tapering to the bottom point.
        val path =
            Path().apply {
                moveTo(w * 0.5f, h)
                cubicTo(w * 0.5f, h, w * 0.06f, h * 0.5f, w * 0.06f, h * 0.36f)
                cubicTo(w * 0.06f, h * 0.14f, w * 0.28f, 0f, w * 0.5f, 0f)
                cubicTo(w * 0.72f, 0f, w * 0.94f, h * 0.14f, w * 0.94f, h * 0.36f)
                cubicTo(w * 0.94f, h * 0.5f, w * 0.5f, h, w * 0.5f, h)
                close()
            }
        drawPath(path, color)
        drawCircle(color = Color.White, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.36f))
    }
}
