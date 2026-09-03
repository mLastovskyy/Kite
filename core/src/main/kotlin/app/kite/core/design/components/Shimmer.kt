package app.kite.core.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors

/**
 * iOS-style loading placeholders: grey shapes with a soft highlight sweeping across, in the
 * layout of the content that is about to appear. A screen that is loading looks like the
 * screen it will become, not like a spinner in a void.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
        label = "shimmerShift",
    )
    val highlight = LocalAppColors.current.bgBase
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val width = size.width
            val brush =
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, highlight.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(width * shift, 0f),
                    end = Offset(width * (shift + 0.6f), size.height),
                )
            drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
        }
}

/** A single placeholder block (a line of text, an avatar, a card). */
@Composable
fun SkeletonBlock(width: Dp, height: Dp, corner: Dp = 6.dp, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Spacer(
        modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(corner))
            .background(colors.fillQuaternary),
    )
}

/** Placeholder for an inset-grouped card with [rows] rows of icon + two text lines. */
@Composable
fun SkeletonCard(rows: Int = 3, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgBase)
            .shimmer(),
    ) {
        repeat(rows) { index ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(width = 29.dp, height = 29.dp, corner = 7.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    SkeletonBlock(width = if (index % 2 == 0) 150.dp else 110.dp, height = 12.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(width = 70.dp, height = 10.dp)
                }
            }
            if (index < rows - 1) HairlineSeparator(startInset = 57.dp)
        }
    }
}

/** Placeholder for the big hero card (title, number, three app rows). */
@Composable
fun SkeletonHero(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.fillQuaternary)
            .shimmer()
            .padding(18.dp),
    ) {
        SkeletonBlock(width = 180.dp, height = 14.dp)
        Spacer(Modifier.height(14.dp))
        SkeletonBlock(width = 220.dp, height = 28.dp, corner = 8.dp)
        Spacer(Modifier.height(16.dp))
        SkeletonBlock(width = 1000.dp, height = 4.dp, corner = 2.dp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SkeletonBlock(width = 90.dp, height = 12.dp)
                Spacer(Modifier.weight(1f))
                SkeletonBlock(width = 50.dp, height = 12.dp)
            }
        }
    }
}
