package app.kite.child.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.formatUsageMs
import app.kite.core.design.components.rowIcon
import kotlinx.coroutines.flow.Flow

@Composable
fun ChildStatusScreen(
    disableEnforcement: Flow<Boolean>,
    protectionGranted: Int,
    protectionTotal: Int,
    summary: TodaySummary,
    onOpenHealth: () -> Unit,
    onOpenRules: () -> Unit,
    onEnterParentCode: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val enforcementDisabled by disableEnforcement.collectAsStateWithLifecycle(initialValue = false)

    val protectionBroken = protectionGranted < protectionTotal

    var today by remember { mutableStateOf<TodaySummary.Today?>(null) }
    // Reread whenever the child comes back to the app: the service updates the numbers in the
    // background, and a screen that was composed an hour ago would show yesterday's total.
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshKey++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refreshKey) { today = summary.today() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Kite Jr",
            style = typography.largeTitle,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(14.dp))

        TimeHero(today = today, enforcementDisabled = enforcementDisabled)

        if (protectionBroken) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warning.copy(alpha = 0.15f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenHealth,
                    )
                    .padding(16.dp),
            ) {
                Column {
                    Text(text = "Защита настроена не полностью", style = typography.headline, color = colors.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Готово $protectionGranted из $protectionTotal. Нажми, чтобы исправить.",
                        style = typography.subhead,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        InsetGroupedList {
            InsetGroup(footer = "Код родителя работает без интернета — попроси родителя назвать его.") {
                row(
                    title = "Код родителя",
                    value = "+15 минут",
                    icon = rowIcon(KiteIcons.KeyRound, colors.accentDeep),
                    showChevron = true,
                    onClick = onEnterParentCode,
                )
                row(
                    title = "Настроенные правила",
                    value = "Что можно",
                    icon = rowIcon(KiteIcons.ListChecks, colors.accent),
                    showChevron = true,
                    onClick = onOpenRules,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * The one number the child looks for. Warm gradient, no chrome, same language as the block
 * screen so the two never contradict each other.
 */
@Composable
private fun TimeHero(today: TodaySummary.Today?, enforcementDisabled: Boolean) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val remaining = today?.remainingMinutes

    val title =
        when {
            enforcementDisabled -> "Ограничения выключены"
            today == null -> "Считаем…"
            remaining == null -> "Лимита на сегодня нет"
            remaining <= 0 -> "Время на сегодня закончилось"
            else -> formatUsageMs(remaining * 60_000L)
        }
    val caption =
        when {
            enforcementDisabled -> "Родитель временно снял все ограничения"
            today == null -> ""
            remaining == null -> "Использовано ${formatUsageMs(today.usedMs)}"
            remaining <= 0 -> "Выполни задание, чтобы получить ещё"
            else -> "Осталось из дневного лимита"
        }

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(colors.accentLight, colors.accent)))
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Text(
            text = caption.ifBlank { " " },
            style = typography.subhead,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = title, style = typography.largeTitle, color = Color.White)
        val limit = today?.limitMinutes
        if (today != null && limit != null) {
            val allowanceMinutes = (limit + today.bonusMinutes).coerceAtLeast(1)
            val usedFraction = (today.usedMs.toFloat() / (allowanceMinutes * 60_000L)).coerceIn(0f, 1f)
            val baseFraction = (limit.toFloat() / allowanceMinutes).coerceIn(0f, 1f)
            val tickColor = Color.White.copy(alpha = 0.55f)
            Spacer(Modifier.height(14.dp))
            // Deliberately quiet (owner: «посдержаннее, не вписывается в дизайн, как у
            // Apple»): a 5dp hairline capsule, not a progress bar — the numbers under it do
            // the talking. Canvas because the bar has to survive a 0% day and hold the tick.
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(color = Color.White.copy(alpha = 0.22f), cornerRadius = radius)
                if (usedFraction > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        size = Size(size.width * usedFraction, size.height),
                        cornerRadius = radius,
                    )
                }
                // Where the base limit ends: everything right of the tick was earned by
                // finishing tasks, so extra time reads as an extension, not a bigger limit.
                if (today.bonusMinutes > 0 && baseFraction > 0.04f && baseFraction < 0.96f) {
                    val x = size.width * baseFraction
                    drawLine(
                        color = tickColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text =
                buildString {
                    append("Использовано ").append(formatUsageMs(today.usedMs))
                    append(" из ").append(formatUsageMs(allowanceMinutes * 60_000L))
                    if (today.bonusMinutes > 0) append(" (+").append(today.bonusMinutes).append(" мин за задания)")
                },
                style = typography.footnote,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}
