package app.kite.child.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * Pairing stages that precede the permission wizard: the code, the child's name and avatar,
 * the consent screen. Setting up the child phone is ONE sequence in the child's eyes, so the
 * wizard continues this numbering instead of restarting at «Шаг 1».
 */
const val PAIRING_STAGES = 3

/** Seconds a single wizard step realistically takes, for the «≈ N мин осталось» estimate. */
const val SECONDS_PER_STEP = 30

/** Rounded-up minutes still ahead, given how many steps are left (never below one minute). */
fun minutesLeft(stepsLeft: Int): Int = ((stepsLeft.coerceAtLeast(1) * SECONDS_PER_STEP) + 59) / 60

/**
 * Segmented progress for the whole setup: one thin segment per step, filled up to [step],
 * with «Шаг N из M» and an optional [note] («≈ 4 мин осталось») under it. Segments beat a
 * single bar here because the child can see how many screens are actually left.
 */
@Composable
fun SetupProgress(step: Int, total: Int, note: String? = null, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total.coerceAtLeast(1)) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (index < step) colors.accent else colors.fillQuaternary,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            if (note != null) {
                Text(text = note, style = typography.footnote, color = colors.textSecondary, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = "Шаг $step из $total",
                style = typography.footnote,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
}
