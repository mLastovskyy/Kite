package app.kite.parent.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.family.FamilyMember
import app.kite.core.rules.AppRule
import app.kite.core.rules.ChildRules
import app.kite.core.rules.QuietInterval
import app.kite.core.rules.RulesRemote
import kotlinx.coroutines.launch

/**
 * Rules editor for one child (M5): daily limit, one quiet-hours interval, per-app
 * block/limit for the apps the child actually used this week. Saving uploads the whole
 * jsonb document; the child device picks it up and enforces offline.
 */
@Composable
fun RulesScreen(member: FamilyMember, knownApps: List<Pair<String, String>>, rulesRemote: RulesRemote, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf<ChildRules?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(member.id) {
        rulesRemote.fetch(member.id)
            .onSuccess { draft = it ?: ChildRules() }
            .onFailure { error = it.message ?: "Ошибка загрузки" }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Правила",
                style = typography.title1,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            AppButton(text = "Закрыть", style = AppButtonStyle.Plain, onClick = onClose)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = member.displayName.ifBlank { "Ребёнок" },
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))

        val rules = draft
        when {
            error != null && rules == null ->
                Text(
                    text = error!!,
                    style = typography.body,
                    color = colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )

            rules == null ->
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    AppSpinner(color = colors.accent, size = 28.dp)
                }

            else -> {
                DailyLimitSection(rules = rules, onChange = { draft = it })
                Spacer(Modifier.height(20.dp))
                QuietHoursSection(rules = rules, onChange = { draft = it })
                Spacer(Modifier.height(20.dp))
                AppRulesSection(rules = rules, knownApps = knownApps, onChange = { draft = it })

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        style = typography.subhead,
                        color = colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(24.dp))
                AppButton(
                    text = "Сохранить",
                    loading = saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            val payload = rules.copy(updatedAtEpochSeconds = System.currentTimeMillis() / 1000)
                            rulesRemote.upsert(member.id, member.familyId, payload)
                                .onSuccess {
                                    saving = false
                                    onClose()
                                }
                                .onFailure {
                                    saving = false
                                    error = it.message ?: "Не удалось сохранить"
                                }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Daily limit ─────────────────────────────────────────────────────────────
@Composable
private fun DailyLimitSection(rules: ChildRules, onChange: (ChildRules) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    SectionCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Дневной лимит",
                style = typography.body,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            AppSwitch(
                checked = rules.dailyLimitMinutes != null,
                onCheckedChange = { on -> onChange(rules.copy(dailyLimitMinutes = if (on) 120 else null)) },
            )
        }
        rules.dailyLimitMinutes?.let { limit ->
            Divider()
            StepperRow(
                value = formatMinutes(limit),
                onMinus = { onChange(rules.copy(dailyLimitMinutes = (limit - 15).coerceAtLeast(15))) },
                onPlus = { onChange(rules.copy(dailyLimitMinutes = (limit + 15).coerceAtMost(12 * 60))) },
            )
        }
    }
}

// ── Quiet hours ─────────────────────────────────────────────────────────────
@Composable
private fun QuietHoursSection(rules: ChildRules, onChange: (ChildRules) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val interval = rules.quietHours.firstOrNull()
    SectionCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Тихие часы",
                style = typography.body,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            AppSwitch(
                checked = interval != null,
                onCheckedChange = { on ->
                    onChange(rules.copy(quietHours = if (on) listOf(QuietInterval(22 * 60, 7 * 60)) else emptyList()))
                },
            )
        }
        if (interval != null) {
            Divider()
            StepperRow(
                label = "С",
                value = formatClock(interval.startMinutes),
                onMinus = { onChange(rules.withQuiet(interval.copy(startMinutes = shift(interval.startMinutes, -30)))) },
                onPlus = { onChange(rules.withQuiet(interval.copy(startMinutes = shift(interval.startMinutes, +30)))) },
            )
            Divider()
            StepperRow(
                label = "До",
                value = formatClock(interval.endMinutes),
                onMinus = { onChange(rules.withQuiet(interval.copy(endMinutes = shift(interval.endMinutes, -30)))) },
                onPlus = { onChange(rules.withQuiet(interval.copy(endMinutes = shift(interval.endMinutes, +30)))) },
            )
        }
    }
}

private fun ChildRules.withQuiet(interval: QuietInterval): ChildRules = copy(quietHours = listOf(interval))

private fun shift(minutes: Int, delta: Int): Int = ((minutes + delta) % (24 * 60) + 24 * 60) % (24 * 60)

// ── Per-app rules ───────────────────────────────────────────────────────────
@Composable
private fun AppRulesSection(rules: ChildRules, knownApps: List<Pair<String, String>>, onChange: (ChildRules) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    if (knownApps.isEmpty()) return
    Text(
        text = "Приложения",
        style = typography.footnote,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    SectionCard {
        knownApps.forEachIndexed { index, (packageName, label) ->
            val appRule = rules.appRules[packageName] ?: AppRule()

            fun put(updated: AppRule) {
                // An unrestricted rule (all defaults) is dropped to keep the document small.
                val cleaned =
                    if (!updated.blocked && updated.dailyLimitMinutes == null && !updated.alwaysAllowed) {
                        rules.appRules - packageName
                    } else {
                        rules.appRules + (packageName to updated)
                    }
                onChange(rules.copy(appRules = cleaned))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    // One tap cycles the app through its states — simple and offline.
                    .clickable { put(appRule.nextState()) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = label, style = typography.body, color = colors.textPrimary, maxLines = 1)
                    val (stateText, stateColor) = appRule.stateLabel(colors)
                    Text(text = stateText, style = typography.subhead, color = stateColor)
                }
                Text(text = "›", style = typography.title1, color = colors.textSecondary)
            }
            if (index < knownApps.lastIndex) Divider()
        }
    }
    Text(
        text = "Тап по приложению меняет режим: Обычно → Лимит → Заблокировано → Всегда доступно.",
        style = typography.caption,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp),
    )
}

/** Cycle: Обычно → Лимит 30м → 1ч → 2ч → 3ч → Заблокировано → Всегда доступно → Обычно. */
private fun AppRule.nextState(): AppRule = when {
    alwaysAllowed -> AppRule() // → Обычно
    blocked -> AppRule(alwaysAllowed = true) // → Всегда доступно
    dailyLimitMinutes == null -> AppRule(dailyLimitMinutes = 30)
    dailyLimitMinutes == 30 -> AppRule(dailyLimitMinutes = 60)
    dailyLimitMinutes == 60 -> AppRule(dailyLimitMinutes = 120)
    dailyLimitMinutes == 120 -> AppRule(dailyLimitMinutes = 180)
    else -> AppRule(blocked = true) // after 3ч → Заблокировано
}

private fun AppRule.stateLabel(colors: app.kite.core.design.AppColors): Pair<String, androidx.compose.ui.graphics.Color> {
    val limit = dailyLimitMinutes
    return when {
        alwaysAllowed -> "Всегда доступно" to colors.success
        blocked -> "Заблокировано" to colors.danger
        limit != null -> "Лимит: ${formatMinutes(limit)}" to colors.accent
        else -> "Обычно" to colors.textSecondary
    }
}

// ── Small shared pieces ─────────────────────────────────────────────────────
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase)) { content() }
}

@Composable
private fun Divider() {
    val colors = LocalAppColors.current
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(colors.separator))
}

@Composable
private fun StepperRow(value: String, onMinus: () -> Unit, onPlus: () -> Unit, label: String? = null) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(text = label, style = typography.body, color = colors.textSecondary)
            Spacer(Modifier.size(8.dp))
        }
        Text(text = value, style = typography.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
        StepButton(text = "−", onClick = onMinus)
        Spacer(Modifier.size(8.dp))
        StepButton(text = "+", onClick = onPlus)
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(colors.accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = typography.headline, color = colors.accent)
    }
}

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "$hours ч $rest мин"
        hours > 0 -> "$hours ч"
        else -> "$rest мин"
    }
}

private fun formatClock(minutesOfDay: Int): String = "%d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
