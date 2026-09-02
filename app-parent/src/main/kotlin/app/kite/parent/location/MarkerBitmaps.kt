package app.kite.parent.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.drawable.toBitmap
import app.kite.core.design.components.AvatarPreset
import app.kite.core.family.FamilyMember
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * The child's avatar as a map marker: a circle (photo, or the preset colour with the
 * initial) with a white ring and a small pointer underneath, anchored at the bottom so the
 * tip sits on the coordinate. Plain android.graphics — MapLibre wants a Bitmap.
 */
object MarkerBitmaps {
    suspend fun forMember(context: Context, member: FamilyMember, sizePx: Int): Bitmap {
        val photo = member.avatarUrl?.takeIf { it.isNotBlank() }?.let { loadPhoto(context, it) }
        return draw(member, photo, sizePx)
    }

    private suspend fun loadPhoto(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        (ImageLoader(context).execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }.getOrNull()

    private fun draw(member: FamilyMember, photo: Bitmap?, size: Int): Bitmap {
        val ring = size * 0.06f
        val pointer = size * 0.22f
        val bitmap = Bitmap.createBitmap(size, (size + pointer).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f - ring / 2f
        val color = AvatarPreset.byId(member.avatarKind).background.let { c ->
            Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
        }

        // Pointer first, so the disc overlaps its base.
        val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
        canvas.drawPath(
            Path().apply {
                moveTo(cx - pointer * 0.6f, cy + radius * 0.75f)
                lineTo(cx + pointer * 0.6f, cy + radius * 0.75f)
                lineTo(cx, size + pointer - ring)
                close()
            },
            pointerPaint,
        )
        // White ring.
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        val inner = radius - ring
        if (photo != null) {
            val scale = (inner * 2) / minOf(photo.width, photo.height)
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(
                Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(cx - photo.width * scale / 2f, cy - photo.height * scale / 2f)
                },
            )
            canvas.drawCircle(cx, cy, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            canvas.drawCircle(cx, cy, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            val initial = member.displayName.trim().take(1).uppercase().ifBlank { "•" }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = inner * 1.1f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val baseline = cy - (text.descent() + text.ascent()) / 2f
            canvas.drawText(initial, cx, baseline, text)
        }
        return bitmap
    }
}
