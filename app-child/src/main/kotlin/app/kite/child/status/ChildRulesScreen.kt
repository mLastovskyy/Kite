package app.kite.child.status

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.kite.child.enforce.RulesStore
import app.kite.child.enforce.RulesSyncer
import app.kite.child.identity.ParentsStore
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppIconImage
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.EmptyState
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupScope
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.RowIcon
import app.kite.core.design.components.rowIcon
import app.kite.core.rules.QuietInterval
import app.kite.core.tasks.ChildTask
import kotlinx.coroutines.launch

/**
 * «Настроенные правила» on the child device: everything the parent set, as it is, plus who
 * set it. Every group opens the actual apps behind it — icons and names, the same way the
 * time statistics show them (CLAUDE.md: the child sees what is monitored, no hidden mode).
 */
@Composable
fun ChildRulesScreen(rulesStore: RulesStore, rulesSyncer: RulesSyncer, parentsStore: ParentsStore, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf(rulesStore.rules()) }
    var authorId by remember { mutableStateOf(rulesStore.author()) }
    val author = authorId?.let { id -> parentsStore.parents().firstOrNull { it.userId == id } }
    var refreshing by remember { mutableStateOf(false) }
    var refreshNote by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<RulesDetail?>(null) }

    detail?.let { open ->
        BackHandler { detail = null }
        AppListScreen(title = open.title, apps = open.apps, onBack = { detail = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(
            title = "Настроенные правила",
            onBack = onBack,
            // The parent changes rules while the child is looking at them, and the child is
            // no longer told about every change — so pulling the fresh copy is a tap here.
            trailing = {
                RefreshButton(refreshing = refreshing) {
                    refreshing = true
                    refreshNote = null
                    scope.launch {
                        val ok = runCatching { rulesSyncer.refresh() }.getOrDefault(false)
                        rules = rulesStore.rules()
                        authorId = rulesStore.author()
                        refreshing = false
                        refreshNote = if (ok) "Обновлено" else "Нет связи — правила из памяти"
                    }
                }
            },
        )
        refreshNote?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(text = note, style = LocalAppTypography.current.footnote, color = colors.textSecondary)
        }
        Spacer(Modifier.height(20.dp))

        val blocked = rules.appRules.filterValues { it.blocked }.keys.map { RuleApp(it, "закрыто") }
        val limited =
            rules.appRules.mapNotNull { (pkg, rule) -> rule.dailyLimitMinutes?.let { RuleApp(pkg, formatMinutes(it)) } }
        val always = rules.appRules.filterValues { it.alwaysAllowed }.keys.map { RuleApp(it, "всегда") }
        val schedules = rules.quietHours.filter { it.enabled }

        InsetGroupedList {
            if (author != null) {
                InsetGroup {
                    row(
                        title = author.name,
                        value = "настроил(а) правила",
                        icon = RowIcon(background = Color.Transparent) {
                            KiteAvatar(preset = AvatarPreset.byId(author.avatarKind), size = 29.dp, avatarUrl = author.avatarUrl)
                        },
                    )
                }
            }

            InsetGroup(header = "Лимит на день", footer = "Минуты за задания добавляются к этому лимиту.") {
                (1..7).forEach { day ->
                    row(title = dayName(day), value = rules.limitFor(day)?.let(::formatMinutes) ?: "без лимита")
                }
            }

            InsetGroup(header = "Расписание") {
                if (schedules.isEmpty()) {
                    row(title = "Расписаний нет")
                } else {
                    schedules.forEach { interval ->
                        val apps = interval.packages.map { RuleApp(it, null) }
                        row(
                            title = interval.name.ifBlank { "Расписание" },
                            value = "${clock(interval.startMinutes)}–${clock(interval.endMinutes)}",
                            subtitle = scheduleSubtitle(interval),
                            showChevron = apps.isNotEmpty(),
                            onClick = { if (apps.isNotEmpty()) detail = RulesDetail(interval.name.ifBlank { "Расписание" }, apps) },
                        )
                    }
                }
            }

            InsetGroup(header = "Приложения", footer = "Звонки, сообщения, камера и файлы работают всегда.") {
                appGroupRow("Закрыты совсем", blocked, KiteIcons.Ban) { detail = it }
                appGroupRow("Со своим лимитом", limited, KiteIcons.Hourglass) { detail = it }
                appGroupRow("Доступны всегда", always, KiteIcons.ShieldCheck) { detail = it }
                if (blocked.isEmpty() && limited.isEmpty() && always.isEmpty()) row(title = "Отдельных правил нет")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !refreshing,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            icon = KiteIcons.Refresh,
            tint = if (refreshing) colors.textTertiary else colors.accent,
            size = 20.dp,
        )
    }
}

private data class RuleApp(val packageName: String, val value: String?)

private data class RulesDetail(val title: String, val apps: List<RuleApp>)

private fun InsetGroupScope.appGroupRow(title: String, apps: List<RuleApp>, icon: Int, onOpen: (RulesDetail) -> Unit) {
    if (apps.isEmpty()) return
    row(
        title = title,
        value = "${apps.size}",
        icon = rowIcon(icon, RULES_TINT),
        showChevron = true,
        onClick = { onOpen(RulesDetail(title, apps)) },
    )
}

private val RULES_TINT = Color(0xFF5856D6)

@Composable
private fun AppListScreen(title: String, apps: List<RuleApp>, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = title, onBack = onBack)
        Spacer(Modifier.height(20.dp))
        if (apps.isEmpty()) {
            EmptyState(icon = KiteIcons.ListChecks, text = "Приложений здесь нет.")
            return@Column
        }
        InsetGroupedList {
            InsetGroup {
                apps.sortedBy { appLabel(context, it.packageName).lowercase() }.forEach { app ->
                    val label = appLabel(context, app.packageName)
                    row(
                        title = label,
                        value = app.value,
                        icon = RowIcon(background = Color.Transparent) {
                            AppIconImage(packageName = app.packageName, label = label, size = 29.dp)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun appLabel(context: Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

private fun scheduleSubtitle(interval: QuietInterval): String {
    val days = daysSummary(interval.days)
    val apps = interval.packages.size
    return if (apps == 0) "$days · приложения не выбраны" else "$days · приложений: $apps"
}

private fun daysSummary(days: List<Int>): String = when {
    days.isEmpty() || days.size == 7 -> "Каждый день"
    days.sorted() == listOf(1, 2, 3, 4, 5) -> "Будни"
    days.sorted() == listOf(6, 7) -> "Выходные"
    else -> days.sorted().joinToString(", ") { ChildTask.WEEKDAY_SHORT.getOrElse(it - 1) { "" } }
}

private fun dayName(isoDay: Int): String = DAY_NAMES.getOrElse(isoDay - 1) { "" }

private val DAY_NAMES = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

private fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0 -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}
