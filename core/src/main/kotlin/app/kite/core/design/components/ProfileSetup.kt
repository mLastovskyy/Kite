package app.kite.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * Reusable profile picker: a large preview, one horizontally scrolling row of choices, and
 * the nickname. The row starts with a «photo» cell (when [onPickPhoto] is given) followed by
 * the vector presets — one line, no grid with holes, no separate text link. Selecting a
 * preset is the caller's cue to drop a custom photo ([customAvatarUrl]); the photo cell reads
 * as selected while a custom photo is set.
 */
@Composable
fun ProfileSetup(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    selected: AvatarPreset,
    onSelect: (AvatarPreset) -> Unit,
    nicknamePlaceholder: String,
    modifier: Modifier = Modifier,
    customAvatarUrl: String? = null,
    onPickPhoto: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val hasPhoto = !customAvatarUrl.isNullOrBlank()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KiteAvatar(preset = selected, size = 96.dp, avatarUrl = customAvatarUrl)
        Spacer(Modifier.height(20.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onPickPhoto != null) {
                item("photo") {
                    ChoiceCell(selected = hasPhoto, onClick = onPickPhoto) {
                        PhotoGlyph(background = colors.fillQuaternary, tint = colors.textSecondary)
                    }
                }
            }
            items(AvatarPreset.entries, key = { it.id }) { preset ->
                ChoiceCell(selected = !hasPhoto && preset == selected, onClick = { onSelect(preset) }) {
                    KiteAvatar(preset = preset, size = CELL)
                }
            }
        }
        if (onPickPhoto != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasPhoto) "Фото выбрано" else "Фото или значок",
                style = typography.footnote,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            placeholder = nicknamePlaceholder,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
    }
}

/**
 * One choice in the row. Selected: accent ring with a background-coloured gap, so the ring
 * reads on every preset colour (including the one equal to the accent).
 */
@Composable
private fun ChoiceCell(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    val ring =
        if (selected) {
            Modifier
                .size(RING)
                .clip(CircleShape)
                .background(colors.accent)
                .padding(2.5.dp)
                .clip(CircleShape)
                .background(colors.bgGrouped)
                .padding(2.5.dp)
        } else {
            Modifier.size(RING).padding(5.dp)
        }
    val interaction = remember { MutableInteractionSource() }
    Box(
        ring.clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Camera outline on a neutral disc — the «use my own photo» choice. */
@Composable
private fun PhotoGlyph(background: Color, tint: Color) {
    Box(Modifier.size(CELL).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(CELL * 0.46f)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // Body with a raised hump for the viewfinder, then the lens.
            drawRoundRect(
                color = tint,
                topLeft = Offset(0f, h * 0.24f),
                size = Size(w, h * 0.66f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
                style = stroke,
            )
            drawLine(tint, Offset(w * 0.32f, h * 0.24f), Offset(w * 0.4f, h * 0.1f), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.4f, h * 0.1f), Offset(w * 0.6f, h * 0.1f), stroke.width, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.6f, h * 0.1f), Offset(w * 0.68f, h * 0.24f), stroke.width, StrokeCap.Round)
            drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.57f), style = stroke)
        }
    }
}

private val CELL = 56.dp
private val RING = 66.dp
