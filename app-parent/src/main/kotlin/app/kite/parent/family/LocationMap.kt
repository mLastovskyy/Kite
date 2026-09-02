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
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val SOURCE_ID = "kite-child"
private const val LAYER_ID = "kite-child-layer"
private const val IMAGE_ID = "kite-child-avatar"

/**
 * MapLibre map on OpenFreeMap tiles (no key, commercial use allowed — CLAUDE.md pin), GMS-free.
 * With a [marker] bitmap the child's avatar sits on the coordinate as a symbol layer
 * (bottom-anchored, so the pointer tip marks the spot); without one a Compose pin is drawn
 * at the camera target. [styleUrl] switches the map look («Стандарт / Яркая / Светлая»).
 * Tiles need internet; offline it degrades to the attribution background. NEEDS_DEVICE_TEST.
 */
@Composable
fun LocationMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    styleUrl: String = MapStyle.LIBERTY.url,
    marker: Bitmap? = null,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapLibre must be initialised before a MapView is created.
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val target = remember(latitude, longitude) { LatLng(latitude, longitude) }

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
                        map.setStyle(Style.Builder().fromUri(styleUrl)) { style -> placeMarker(style, marker, target) }
                    }
                }
            },
            update = {
                it.getMapAsync { map ->
                    map.animateCamera(CameraUpdateFactory.newLatLng(target))
                    val loaded = map.style
                    // A new style URL reloads the style (and the marker with it); otherwise refresh in place.
                    if (loaded == null || loaded.uri != styleUrl) {
                        map.setStyle(Style.Builder().fromUri(styleUrl)) { style -> placeMarker(style, marker, target) }
                    } else if (loaded.isFullyLoaded) {
                        placeMarker(loaded, marker, target)
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        )
        if (marker == null) {
            // Fallback pin (drawn in Compose): points at the camera target = the child.
            MapPin(color = colors.accent, modifier = Modifier.offset(y = (-14).dp))
        }
    }
}

/** (Re)places the avatar symbol; removing first keeps this idempotent across updates. */
private fun placeMarker(style: Style, marker: Bitmap?, target: LatLng) {
    runCatching {
        style.removeLayer(LAYER_ID)
        style.removeSource(SOURCE_ID)
        if (marker == null) return
        style.addImage(IMAGE_ID, marker)
        val point = """{"type":"Point","coordinates":[${target.longitude},${target.latitude}]}"""
        val feature = """{"type":"Feature","geometry":$point,"properties":{}}"""
        style.addSource(GeoJsonSource(SOURCE_ID, feature))
        style.addLayer(
            SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                PropertyFactory.iconImage(IMAGE_ID),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            ),
        )
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
