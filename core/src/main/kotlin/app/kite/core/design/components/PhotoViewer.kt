package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kite.core.design.LocalAppColors
import coil.compose.AsyncImage

/**
 * One photo, full screen on black: the task proof the child sent, big enough to decide by.
 * [model] is whatever Coil takes — a URL for a photo already uploaded, a `File` for one still
 * waiting on the child's phone. A tap anywhere closes, the way a photo viewer always does.
 */
@Composable
fun PhotoViewer(model: Any, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().safeContentPadding().padding(8.dp),
            )
            Box(Modifier.align(Alignment.TopEnd).safeContentPadding().padding(12.dp)) {
                CircleIconButton(icon = KiteIcons.X, size = 36.dp, onClick = onDismiss)
            }
        }
    }
}

/** The same photo as a rounded thumbnail: the tap target that opens [PhotoViewer]. */
@Composable
fun PhotoThumbnail(model: Any, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    val tap =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(colors.fillQuaternary).then(tap),
    )
}
