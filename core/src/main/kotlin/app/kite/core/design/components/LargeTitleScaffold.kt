package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

private val BarHeight = 44.dp

/**
 * iOS-like collapsing header (DESIGN_SYSTEM.md): the title starts as a left-aligned
 * 34sp large title inside the list and collapses into a centred 17sp/600 inline bar title
 * with a hairline separator. The collapse fraction is driven directly by the scroll offset
 * of the large-title item, so it tracks the finger with no lag and reverses cleanly.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var largeTitleHeight by remember { mutableIntStateOf(0) }
    val collapsed by remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                largeTitleHeight == 0 -> 0f
                else -> (listState.firstVisibleItemScrollOffset / largeTitleHeight.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize().background(colors.bgGrouped)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
            PaddingValues(
                top = statusBarTop + BarHeight,
                bottom = navigationBottom + 24.dp,
            ),
        ) {
            item(key = "kite-large-title") {
                Text(
                    text = title,
                    style = typography.largeTitle,
                    color = colors.textPrimary,
                    modifier =
                    Modifier
                        .onSizeChanged { largeTitleHeight = it.height }
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp)
                        .graphicsLayer { alpha = 1f - collapsed },
                )
            }
            content()
        }
        // Inline bar overlays the list; its background is transparent while the large
        // title is visible, but actions stay fully visible the whole time.
        val barColor = colors.bgGrouped
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(color = barColor, alpha = collapsed) }
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(BarHeight),
            ) {
                Text(
                    text = title,
                    style = typography.headline,
                    color = colors.textPrimary,
                    modifier =
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = ((collapsed - 0.6f) / 0.4f).coerceIn(0f, 1f) },
                )
                if (actions != null) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
            Box(Modifier.graphicsLayer { alpha = collapsed }) {
                HairlineSeparator()
            }
        }
    }
}
