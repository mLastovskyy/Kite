package app.kite.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

/**
 * Preset avatars for profile setup — a shape in white on a colored circle. Vector-drawn so
 * they scale and recolor; no image assets. [AvatarPreset.id] is what gets stored as
 * `avatar_kind`; a custom photo (uploaded later) uses a different path.
 */
enum class AvatarPreset(val id: String, val background: Color, val icon: Int? = null) {
    KITE("kite", Color(0xFF007AFF)),
    STAR("star", Color(0xFF5856D6)),
    MOON("moon", Color(0xFF30B0C7)),
    BOLT("bolt", Color(0xFF34C759)),
    HEART("heart", Color(0xFFFF9500)),
    WAVE("wave", Color(0xFFFF2D55)),
    CLOUD("cloud", Color(0xFF8E8E93)),

    // Lucide glyphs on a disc — more choice without more hand-drawn paths.
    SPARKLES("sparkles", Color(0xFFAF52DE), KiteIcons.Sparkles),
    GIFT("gift", Color(0xFFFF375F), KiteIcons.Gift),
    BOOK("book", Color(0xFF32ADE6), KiteIcons.BookOpen),
    SUN("sun", Color(0xFFFFB800), KiteIcons.SunMoon),
    CAMERA("camera", Color(0xFF5E5CE6), KiteIcons.Camera),
    PALETTE("palette", Color(0xFFFF6482), KiteIcons.Palette),
    SHIELD("shield", Color(0xFF30D158), KiteIcons.ShieldCheck),
    BELL("bell", Color(0xFFFF9F0A), KiteIcons.Bell),
    ;

    companion object {
        fun byId(id: String?): AvatarPreset = entries.firstOrNull { it.id == id } ?: KITE
    }
}

@Composable
fun KiteAvatar(preset: AvatarPreset, size: Dp, modifier: Modifier = Modifier, avatarUrl: String? = null) {
    // A custom uploaded photo wins over the preset vector.
    if (!avatarUrl.isNullOrBlank()) {
        coil.compose.AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).background(preset.background),
        )
        return
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(preset.background),
        contentAlignment = Alignment.Center,
    ) {
        if (preset.icon != null) {
            AppIcon(icon = preset.icon, tint = Color.White, size = size * 0.5f)
            return@Box
        }
        Canvas(Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.09f
            val white = Color.White
            val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (preset) {
                AvatarPreset.KITE -> {
                    val p =
                        Path().apply {
                            moveTo(w * 0.5f, 0f)
                            lineTo(w * 0.85f, h * 0.4f)
                            lineTo(w * 0.5f, h * 0.75f)
                            lineTo(w * 0.15f, h * 0.4f)
                            close()
                        }
                    drawPath(p, white, style = line)
                    drawLine(white, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h * 0.75f), stroke, StrokeCap.Round)
                    drawLine(white, Offset(w * 0.15f, h * 0.4f), Offset(w * 0.85f, h * 0.4f), stroke, StrokeCap.Round)
                    val tail =
                        Path().apply {
                            moveTo(w * 0.5f, h * 0.75f)
                            cubicTo(w * 0.4f, h * 0.86f, w * 0.62f, h * 0.9f, w * 0.5f, h)
                        }
                    drawPath(tail, white, style = Stroke(width = stroke * 0.8f, cap = StrokeCap.Round))
                }

                AvatarPreset.STAR -> {
                    val p =
                        Path().apply {
                            val cx = w / 2
                            val cy = h / 2
                            val outer = w * 0.5f
                            val inner = w * 0.22f
                            for (i in 0 until 10) {
                                val r = if (i % 2 == 0) outer else inner
                                val a = Math.toRadians((i * 36 - 90).toDouble())
                                val x = cx + (r * kotlin.math.cos(a)).toFloat()
                                val y = cy + (r * kotlin.math.sin(a)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                    drawPath(p, white, style = line)
                }

                AvatarPreset.MOON -> {
                    val p =
                        Path().apply {
                            moveTo(w * 0.8f, h * 0.55f)
                            cubicTo(w * 0.8f, h * 0.9f, w * 0.35f, h * 0.95f, w * 0.2f, h * 0.62f)
                            cubicTo(w * 0.1f, h * 0.35f, w * 0.35f, h * 0.05f, w * 0.62f, h * 0.1f)
                            cubicTo(w * 0.45f, h * 0.28f, w * 0.55f, h * 0.55f, w * 0.8f, h * 0.55f)
                            close()
                        }
                    drawPath(p, white, style = line)
                }

                AvatarPreset.BOLT -> {
                    val p =
                        Path().apply {
                            moveTo(w * 0.58f, 0f)
                            lineTo(w * 0.2f, h * 0.55f)
                            lineTo(w * 0.48f, h * 0.55f)
                            lineTo(w * 0.42f, h)
                            lineTo(w * 0.8f, h * 0.42f)
                            lineTo(w * 0.52f, h * 0.42f)
                            close()
                        }
                    drawPath(p, white, style = line)
                }

                AvatarPreset.HEART -> {
                    val p =
                        Path().apply {
                            moveTo(w * 0.5f, h)
                            cubicTo(w * 0.05f, h * 0.62f, w * 0.15f, h * 0.1f, w * 0.5f, h * 0.32f)
                            cubicTo(w * 0.85f, h * 0.1f, w * 0.95f, h * 0.62f, w * 0.5f, h)
                            close()
                        }
                    drawPath(p, white, style = line)
                }

                AvatarPreset.WAVE -> {
                    for (row in 0..1) {
                        val y = if (row == 0) h * 0.38f else h * 0.66f
                        val p =
                            Path().apply {
                                moveTo(0f, y)
                                cubicTo(w * 0.25f, y - h * 0.22f, w * 0.35f, y + h * 0.22f, w * 0.5f, y)
                                cubicTo(w * 0.65f, y - h * 0.22f, w * 0.75f, y + h * 0.22f, w, y)
                            }
                        drawPath(p, white, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    }
                }

                AvatarPreset.CLOUD -> {
                    val p =
                        Path().apply {
                            moveTo(w * 0.28f, h * 0.78f)
                            cubicTo(w * 0.02f, h * 0.78f, w * 0.05f, h * 0.4f, w * 0.32f, h * 0.42f)
                            cubicTo(w * 0.38f, h * 0.12f, w * 0.82f, h * 0.15f, w * 0.78f, h * 0.5f)
                            cubicTo(w * 0.98f, h * 0.5f, w * 0.98f, h * 0.78f, w * 0.75f, h * 0.78f)
                            close()
                        }
                    drawPath(p, white, style = line)
                }
                else -> Unit
            }
        }
    }
}
