package app.kite.child.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import app.kite.child.identity.ChildParent
import app.kite.core.design.components.AppChoiceDialog
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.DialogChoice
import app.kite.core.design.components.KiteAvatar

/**
 * «Кого попросить?» — the child picks the parent a request goes to. Shown only when there is
 * more than one parent in the family; with a single parent [AskParentDialog] answers for the
 * child immediately, so callers never have to special-case family size.
 */
@Composable
fun AskParentDialog(sender: ChildRequestSender, message: String? = null, onPick: (ChildParent?) -> Unit, onDismiss: () -> Unit) {
    var parents by remember { mutableStateOf(sender.parents()) }
    LaunchedEffect(Unit) {
        sender.refreshParents()
        parents = sender.parents()
        if (parents.size <= 1) onPick(parents.firstOrNull())
    }
    if (parents.size <= 1) return

    AppChoiceDialog(
        title = "Кого попросить?",
        message = message,
        choices = parents.map { parent ->
            DialogChoice(
                label = parent.name,
                leading = { KiteAvatar(preset = AvatarPreset.byId(parent.avatarKind), size = 26.dp, avatarUrl = parent.avatarUrl) },
                onClick = { onPick(parent) },
            )
        },
        onDismiss = onDismiss,
    )
}
