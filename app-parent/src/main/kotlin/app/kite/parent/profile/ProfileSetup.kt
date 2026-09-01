package app.kite.parent.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar

/**
 * Reusable profile picker: pick a preset avatar and type a nickname. Custom-photo upload
 * (to Supabase Storage) is a later addition; the preset path needs no network or storage.
 */
@Composable
fun ProfileSetup(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    selected: AvatarPreset,
    onSelect: (AvatarPreset) -> Unit,
    nicknamePlaceholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KiteAvatar(preset = selected, size = 96.dp)
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
                    val ring =
                        if (preset == selected) {
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        } else {
                            Modifier.size(64.dp)
                        }
                    val interaction = remember { MutableInteractionSource() }
                    Box(ring, contentAlignment = Alignment.Center) {
                        KiteAvatar(
                            preset = preset,
                            size = if (preset == selected) 58.dp else 64.dp,
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
