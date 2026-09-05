package app.kite.child.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kite.child.identity.ChildParent
import app.kite.child.identity.ParentsStore
import app.kite.core.design.LocalAppColors
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.RowIcon

@Composable
fun ChildParentsScreen(parentsStore: ParentsStore, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    var parents by remember { mutableStateOf(parentsStore.parents()) }
    var preferred by remember { mutableStateOf(parentsStore.preferredId()) }

    LaunchedEffect(Unit) {
        parentsStore.refresh()
        parents = parentsStore.parents()
        preferred = parentsStore.preferredId()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "Мои родители", onBack = onBack)
        Spacer(Modifier.height(20.dp))

        InsetGroupedList {
            InsetGroup(
                header = "Кого просить",
                footer = "Запрос на время придёт этому родителю. «Всем» — увидят все взрослые в семье.",
            ) {
                parents.forEach { parent ->
                    row(
                        title = parent.name,
                        icon = avatarIcon(parent),
                        onClick = {
                            parentsStore.choose(parent.memberId)
                            preferred = parent.memberId
                        },
                        trailing = {
                            if (parent.memberId == preferred) AppIcon(icon = KiteIcons.Check, tint = colors.accent, size = 20.dp)
                        },
                    )
                }
                row(
                    title = "Всем родителям",
                    onClick = {
                        parentsStore.choose(null)
                        preferred = null
                    },
                    trailing = { if (preferred == null) AppIcon(icon = KiteIcons.Check, tint = colors.accent, size = 20.dp) },
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun avatarIcon(parent: ChildParent): RowIcon = RowIcon(background = Color.Transparent) {
    KiteAvatar(preset = AvatarPreset.byId(parent.avatarKind), size = 29.dp, avatarUrl = parent.avatarUrl)
}
