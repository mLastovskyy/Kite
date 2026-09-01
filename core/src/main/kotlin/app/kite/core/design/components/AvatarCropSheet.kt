package app.kite.core.design.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import android.graphics.Canvas as AndroidCanvas

private const val OUT_PX = 512
private const val MAX_LOAD_PX = 1440

/**
 * Pick a photo and fit it into the circle: drag to move, pinch to zoom. Save renders the
 * visible square to a [OUT_PX] JPEG and hands back the bytes ([onCropped]); the caller
 * uploads. Circular masking is done at display time by KiteAvatar, so the output is a
 * plain square. Minimal chrome — the mask makes the intent obvious.
 */
@Composable
fun AvatarCropSheet(onCancel: () -> Unit, onCropped: (ByteArray) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxPx by remember { mutableStateOf(1) }
    var minScale by remember { mutableStateOf(1f) }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                pickedUri = uri
            } else if (bitmap == null) {
                onCancel()
            }
        }
    // Open the system photo picker straight away — one obvious action, no extra buttons.
    LaunchedEffect(Unit) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { decodeDownscaled(context, uri) }
        scale = 1f
        offset = Offset.Zero
    }

    fun recomputeMinScale(bmp: Bitmap) {
        minScale = max(boxPx.toFloat() / bmp.width, boxPx.toFloat() / bmp.height)
        if (scale < minScale) scale = minScale
    }

    Column(
        Modifier.fillMaxSize().background(colors.bgGrouped).safeContentPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Фото профиля", style = typography.title1, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Перетащите и масштабируйте",
            style = typography.subhead,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        val bmp = bitmap
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipToBounds()
                .background(Color.Black)
                .onSizeChanged {
                    boxPx = it.width.coerceAtLeast(1)
                    bmp?.let(::recomputeMinScale)
                }
                .pointerInput(bmp) {
                    if (bmp == null) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, minScale * 5f)
                        scale = newScale
                        val maxX = max(0f, (bmp.width * newScale - boxPx) / 2f)
                        val maxY = max(0f, (bmp.height * newScale - boxPx) / 2f)
                        offset =
                            Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                )
            }
            // Circular hint: dim the corners so the crop circle reads instantly.
            CircleMask()
        }

        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Сохранить",
            enabled = bmp != null,
            onClick = {
                val out = bmp?.let { crop(it, scale, offset, boxPx) } ?: return@AppButton
                onCropped(out)
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Отмена", style = AppButtonStyle.Plain, onClick = onCancel)
    }
}

@Composable
private fun CircleMask() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        val ring = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addOval(androidx.compose.ui.geometry.Rect(center.x - r, center.y - r, center.x + r, center.y + r))
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(ring, Color(0x99000000))
    }
}

private fun decodeDownscaled(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    val largest = max(bounds.outWidth, bounds.outHeight)
    while (largest / sample > MAX_LOAD_PX) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

/** Renders the visible square region (box-local [0,boxPx]) to an OUT_PX JPEG. */
private fun crop(src: Bitmap, scale: Float, offset: Offset, boxPx: Int): ByteArray {
    val out = Bitmap.createBitmap(OUT_PX, OUT_PX, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val k = OUT_PX.toFloat() / boxPx
    val m = Matrix()
    m.postTranslate(-src.width / 2f, -src.height / 2f)
    m.postScale(scale, scale)
    m.postTranslate(boxPx / 2f + offset.x, boxPx / 2f + offset.y)
    m.postScale(k, k)
    canvas.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    val stream = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 88, stream)
    return stream.toByteArray()
}
