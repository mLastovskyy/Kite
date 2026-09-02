package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * Reusable profile picker: a preset avatar (or an uploaded photo) and a nickname. When
 * [onPickPhoto] is provided, a link lets the user upload a custom picture; the caller
 * handles the crop + upload and passes the resulting [customAvatarUrl] back.
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
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KiteAvatar(preset = selected, size = 96.dp, avatarUrl = customAvatarUrl)
        if (onPickPhoto != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (customAvatarUrl != null) "Изменить фото" else "Загрузить фото",
                style = typography.subhead,
                color = colors.accent,
                modifier = Modifier.clickable(onClick = onPickPhoto),
            )
        }
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(AvatarPreset.entries.size) { index ->
                val preset = AvatarPreset.entries[index]
                Box(contentAlignment = Alignment.Center) {
                    // Selected: accent ring with a background-coloured gap, so the ring reads
                    // on every preset colour (including the one equal to the accent).
                    val ring =
                        if (preset == selected) {
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(colors.bgGrouped)
                                .padding(2.5.dp)
                        } else {
                            Modifier.size(64.dp).padding(5.dp)
                        }
                    val interaction = remember { MutableInteractionSource() }
                    Box(ring, contentAlignment = Alignment.Center) {
                        KiteAvatar(
                            preset = preset,
                            size = 54.dp,
                            modifier =
                            Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) { onSelect(preset) },
                        )
                    }
                }
            }
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
