package app.kite.parent.family

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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// OpenFreeMap raster/vector style — no API key, commercial use allowed (CLAUDE.md pin).
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * MapLibre map centered on the child's latest position, with a Compose pin drawn at the
 * center (the camera tracks the point, so the center marks the child — no annotation
 * plugin or icon asset needed). GMS-free; tiles need internet, so it degrades to the
 * attribution background offline. NEEDS_DEVICE_TEST for real tile rendering.
 */
@Composable
fun LocationMap(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
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
                        map.setStyle(Style.Builder().fromUri(STYLE_URL))
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
                    }
                }
            },
            update = { it.getMapAsync { map -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0)) } },
            modifier = Modifier.matchParentSize(),
        )
        // Center pin (drawn in Compose): points at the camera target = the child.
        MapPin(color = colors.accent, modifier = Modifier.offset(y = (-14).dp))
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
