package app.kite.core.design.components

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
import androidx.compose.ui.graphics.Color
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
    // Shuffled once per screen so the same four icons are not always the ones on offer; the
    // current pick leads, so it stays visible without scrolling.
    val presets = remember { listOf(selected) + AvatarPreset.entries.filterNot { it == selected }.shuffled() }
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
            items(presets, key = { it.id }) { preset ->
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

@Composable
private fun PhotoGlyph(background: Color, tint: Color) {
    Box(Modifier.size(CELL).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        AppIcon(icon = KiteIcons.Camera, tint = tint, size = CELL * 0.44f)
    }
}

private val CELL = 56.dp
private val RING = 66.dp
