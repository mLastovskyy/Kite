package app.kite.parent.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.formatUsageMs
import app.kite.core.design.components.rowIcon
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import app.kite.core.rules.ChildRules
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.usage.UsageRemote
import app.kite.parent.family.ApprovalCodeScreen
import app.kite.parent.family.ChildLocationScreen
import app.kite.parent.family.freshness
import app.kite.parent.rules.AppListKind
import app.kite.parent.rules.AppListsScreen
import app.kite.parent.rules.LimitsScreen
import app.kite.parent.rules.RulesController
import app.kite.parent.rules.SchedulesScreen
import app.kite.parent.rules.daysSummary
import app.kite.parent.rules.formatClock
import app.kite.parent.stats.UsageWeek
import app.kite.parent.stats.loadUsageWeek
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class HomeSub { Limits, Apps, Schedules, Location, Code }

/**
 * Главная for one child, in Kids360's card order: the hero limit card («Изменить лимит»,
 * «Заблокировать сейчас»), the child's pending requests, then «Лимит на приложение»,
 * «Доступны всегда», «Всегда заблокированы», «Расписание», «Где ребёнок», and the small
 * actions «Найти телефон» / «Код подтверждения». Every card opens its own screen; nothing
 * here needs a «Сохранить».
 */
@Composable
fun ChildHomeScreen(
    familyId: String,
    children: List<FamilyMember>,
    child: FamilyMember,
    onSelectChild: (FamilyMember) -> Unit,
    anonymousAccount: Boolean,
    onLinkEmail: () -> Unit,
    onOpenTasks: () -> Unit,
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    commandsRemote: CommandsRemote,
    approvalsRemote: ApprovalsRemote,
    locationRemote: DeviceLocationRemote,
    familyRepository: FamilyRepository,
    secureStore: SecureStore,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    val rulesController = remember(child.id) { RulesController(child, rulesRemote, scope) }
    var week by remember(child.id) { mutableStateOf<UsageWeek?>(null) }
    var requests by remember(child.id) { mutableStateOf<List<ApprovalRequest>>(emptyList()) }
    var location by remember(child.id) { mutableStateOf<DeviceLocationRow?>(null) }
    var reloadKey by remember(child.id) { mutableIntStateOf(0) }
    var sub by remember(child.id) { mutableStateOf<HomeSub?>(null) }
    // The server keeps no lock state; remember what this parent last sent for this child.
    var locked by remember(child.id) { mutableStateOf(false) }
    var busyRequest by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(child.id, reloadKey) {
        rulesController.load()
        launch { loadUsageWeek(usageRemote, child.id, today).onSuccess { week = it } }
        launch { requests = approvalsRemote.pending(familyId).getOrNull().orEmpty().filter { it.childMemberId == child.id } }
        launch { location = locationRemote.latest(child.id).getOrNull() }
    }

    val rules = rulesController.rules
    val appsToday = week?.apps(today).orEmpty()
    val appsWeek = week?.apps(null).orEmpty()

    when (sub) {
        HomeSub.Limits -> {
            LimitsScreen(controller = rulesController, onBack = { sub = null })
            return
        }
        HomeSub.Apps -> {
            // Labels from the week (an app used yesterday still needs a rule), today's minutes for the subtitle.
            val todayByPkg = appsToday.associateBy { it.packageName }
            val merged = appsWeek.map { it.copy(totalMs = todayByPkg[it.packageName]?.totalMs ?: 0L) }
            AppListsScreen(controller = rulesController, apps = merged, initialKind = appsKind, onBack = { sub = null })
            return
        }
        HomeSub.Schedules -> {
            SchedulesScreen(controller = rulesController, onBack = { sub = null })
            return
        }
        HomeSub.Location -> {
            ChildLocationScreen(member = child, locationRemote = locationRemote, onClose = { sub = null })
            return
        }
        HomeSub.Code -> {
            ApprovalCodeScreen(member = child, familyRepository = familyRepository, secureStore = secureStore, onClose = { sub = null })
            return
        }
        null -> Unit
    }

    fun send(command: String, payload: String? = null, done: String) {
        scope.launch {
            commandsRemote.send(child.id, familyId, command, payloadJson = payload)
                .onSuccess { note = done }
                .onFailure { note = it.message ?: "Не удалось отправить" }
        }
    }

    var confirmLock by remember { mutableStateOf(false) }
    var confirmRing by remember { mutableStateOf(false) }
    if (confirmLock) {
        AppDialog(
            title = "Заблокировать сейчас?",
            message = "Игры и другие приложения закроются. Звонки, сообщения и список «Доступны всегда» продолжат работать.",
            confirmText = "Заблокировать",
            destructive = true,
            onConfirm = {
                confirmLock = false
                locked = true
                send(DeviceCommand.LOCK, done = "Телефон блокируется")
            },
            onDismiss = { confirmLock = false },
        )
    }
    if (confirmRing) {
        AppDialog(
            title = "Найти телефон",
            message = "Громкий сигнал ~5 секунд, даже в тихом режиме.",
            confirmText = "Подать сигнал",
            onConfirm = {
                confirmRing = false
                send(DeviceCommand.RING, done = "Сигнал отправлен")
            },
            onDismiss = { confirmRing = false },
        )
    }

    fun resolveRequest(request: ApprovalRequest, approve: Boolean, minutes: Int = 15, scopeToApp: Boolean = false) {
        scope.launch {
            busyRequest = request.id
            if (approve) {
                when (request.type) {
                    ApprovalRequest.TYPE_UNLOCK -> {
                        locked = false
                        commandsRemote.send(child.id, familyId, DeviceCommand.UNLOCK)
                    }
                    ApprovalRequest.TYPE_EXTRA_TIME -> {
                        val pkg = request.packageName?.takeIf { scopeToApp }
                        val payload = if (pkg != null) """{"minutes":$minutes,"package":"$pkg"}""" else """{"minutes":$minutes}"""
                        commandsRemote.send(child.id, familyId, DeviceCommand.GRANT_TIME, payloadJson = payload)
                    }
                    ApprovalRequest.TYPE_REMOVAL -> commandsRemote.send(child.id, familyId, DeviceCommand.ALLOW_REMOVAL)
                }
            }
            approvalsRemote.resolve(request.id, if (approve) ApprovalRequest.STATUS_APPROVED else ApprovalRequest.STATUS_REJECTED)
            busyRequest = null
            reloadKey++
        }
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
        Text(text = "Главная", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        ChildSwitcher(children = children, selected = child, onSelect = onSelectChild)
        Spacer(Modifier.height(16.dp))

        HeroCard(
            rules = rules,
            usedTodayMs = week?.dayTotal(today) ?: 0L,
            topApps = appsToday.take(3).map { it.label to it.totalMs },
            moreApps = (appsToday.size - 3).coerceAtLeast(0),
            locked = locked,
            onEditLimit = { sub = HomeSub.Limits },
            onLock = { confirmLock = true },
            onUnlock = {
                locked = false
                send(DeviceCommand.UNLOCK, done = "Блокировка снимается")
            },
        )
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = typography.footnote,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))

        InsetGroupedList {
            if (anonymousAccount) {
                InsetGroup {
                    row(
                        title = "Привяжите email",
                        value = "Для входа с другого телефона",
                        icon = rowIcon(KiteIcons.Mail, colors.accent),
                        showChevron = true,
                        onClick = onLinkEmail,
                    )
                }
            }
            if (requests.isNotEmpty()) {
                InsetGroup(header = "Просит ${child.displayName.ifBlank { "ребёнок" }}") {
                    requests.forEach { request ->
                        custom {
                            RequestCard(
                                request = request,
                                busy = busyRequest == request.id,
                                onApprove = { minutes, scoped ->
                                    resolveRequest(request, approve = true, minutes = minutes, scopeToApp = scoped)
                                },
                                onDeny = { resolveRequest(request, approve = false) },
                                onOpenTasks = onOpenTasks,
                            )
                        }
                    }
                }
            }

            val limitedCount = rules?.appRules?.count { it.value.inPool && it.value.dailyLimitMinutes != null } ?: 0
            val alwaysCount = rules?.appRules?.count { it.value.alwaysAllowed } ?: 0
            val blockedCount = rules?.appRules?.count { it.value.blocked } ?: 0
            InsetGroup(header = "Приложения") {
                row(
                    title = "Лимит на приложение",
                    value = if (limitedCount > 0) "$limitedCount" else "Добавить",
                    icon = rowIcon(KiteIcons.Hourglass, AppListKind.Pool.color),
                    showChevron = true,
                    onClick = {
                        appsKind = AppListKind.Pool
                        sub = HomeSub.Apps
                    },
                )
                row(
                    title = "Доступны всегда",
                    value = if (alwaysCount > 0) "$alwaysCount" else "",
                    icon = rowIcon(KiteIcons.LockOpen, AppListKind.Always.color),
                    showChevron = true,
                    onClick = {
                        appsKind = AppListKind.Always
                        sub = HomeSub.Apps
                    },
                )
                row(
                    title = "Всегда заблокированы",
                    value = if (blockedCount > 0) "$blockedCount" else "Добавить",
                    icon = rowIcon(KiteIcons.Ban, AppListKind.Blocked.color),
                    showChevron = true,
                    onClick = {
                        appsKind = AppListKind.Blocked
                        sub = HomeSub.Apps
                    },
                )
            }

            InsetGroup(
                header = "Расписание",
                footer = if (rules?.quietHours.isNullOrEmpty()) "Например, «Сон» 21:00–07:00 и «Учёба» 08:00–16:00 по будням." else null,
            ) {
                val active = rules?.quietHours?.filter { it.enabled }.orEmpty()
                row(
                    title = "Блокировать по расписанию",
                    value = if (active.isEmpty()) "Выкл" else "${active.size}",
                    icon = rowIcon(KiteIcons.CalendarClock, Color(0xFF5856D6)),
                    showChevron = true,
                    onClick = { sub = HomeSub.Schedules },
                )
                active.take(3).forEach { q ->
                    row(
                        title = q.name.ifBlank { "Без названия" },
                        value = "${formatClock(q.startMinutes)}–${formatClock(q.endMinutes)} · ${daysSummary(q.days)}",
                        onClick = { sub = HomeSub.Schedules },
                    )
                }
            }

            InsetGroup(header = "Телефон") {
                row(
                    title = "Где ребёнок",
                    value = location?.let { freshness(it.recordedAt) } ?: "Нет данных",
                    icon = rowIcon(KiteIcons.MapPin, Color(0xFF34C759)),
                    showChevron = true,
                    onClick = { sub = HomeSub.Location },
                )
                row(
                    title = "Найти телефон",
                    icon = rowIcon(KiteIcons.BellRing, Color(0xFFFF9500)),
                    showChevron = true,
                    onClick = { confirmRing = true },
                )
                row(
                    title = "Код подтверждения",
                    value = "Офлайн",
                    icon = rowIcon(KiteIcons.KeyRound, Color(0xFF8E8E93)),
                    showChevron = true,
                    onClick = { sub = HomeSub.Code },
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// Which list the «Приложения» screen opens on; a plain var is enough for a one-shot handoff.
private var appsKind: AppListKind = AppListKind.Pool

/** Kids360's violet hero, in our accent: today's usage against the limit, top apps, two actions. */
@Composable
private fun HeroCard(
    rules: ChildRules?,
    usedTodayMs: Long,
    topApps: List<Pair<String, Long>>,
    moreApps: Int,
    locked: Boolean,
    onEditLimit: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val limit = rules?.limitFor(LocalDate.now().dayOfWeek.value)
    val white = Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accent)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Экранное время сегодня", style = typography.headline, color = white, modifier = Modifier.weight(1f))
            AppIcon(icon = if (locked) KiteIcons.Lock else KiteIcons.Clock, tint = white.copy(alpha = 0.9f), size = 20.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text =
            buildString {
                append(formatUsageMs(usedTodayMs))
                if (limit != null) append(" из ${formatUsageMs(limit * 60_000L)}")
            },
            style = typography.title1.copy(fontWeight = FontWeight.Bold),
            color = white,
        )
        if (limit == null) {
            Text(
                text = if (rules ==
                    null
                ) {
                    "Загрузка правил…"
                } else {
                    "Без лимита сегодня"
                },
                style = typography.subhead,
                color = white.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(10.dp))
        val fraction = if (limit != null && limit > 0) (usedTodayMs / (limit * 60_000f)).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(white.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(white))
        }
        if (topApps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            topApps.forEach { (label, ms) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = label,
                        style = typography.subhead,
                        color = white.copy(alpha = 0.92f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = formatUsageMs(ms), style = typography.subhead, color = white.copy(alpha = 0.92f))
                }
            }
            if (moreApps > 0) {
                Text(text = "Ещё $moreApps приложений", style = typography.footnote, color = white.copy(alpha = 0.75f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroButton(text = "Изменить лимит", filled = false, modifier = Modifier.weight(1f), onClick = onEditLimit)
            HeroButton(
                text = if (locked) "Разблокировать" else "Заблокировать сейчас",
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = if (locked) onUnlock else onLock,
            )
        }
    }
}

@Composable
private fun HeroButton(text: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.22f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (filled) colors.accent else Color.White,
            maxLines = 1,
        )
    }
}

/** One pending request from the child, with the actions Kids360 puts on its cards. */
@Composable
private fun RequestCard(
    request: ApprovalRequest,
    busy: Boolean,
    onApprove: (minutes: Int, scopedToApp: Boolean) -> Unit,
    onDeny: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var minutes by remember(request.id) { mutableIntStateOf(request.minutes ?: 15) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text =
            when (request.type) {
                ApprovalRequest.TYPE_UNLOCK -> "Просит снять блокировку"
                ApprovalRequest.TYPE_EXTRA_TIME -> request.appLabel?.let { "Просит ещё время для «$it»" } ?: "Просит ещё время"
                ApprovalRequest.TYPE_REMOVAL -> "Просит разрешить удаление Kite Jr"
                ApprovalRequest.TYPE_TASK_REQUEST -> "Просит задание, чтобы заработать время"
                else -> "Запрос"
            },
            style = typography.headline,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        when (request.type) {
            ApprovalRequest.TYPE_EXTRA_TIME -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        val on = minutes == m
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) colors.accent else colors.fillQuaternary)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { minutes = m },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$m мин",
                                style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = if (on) Color.White else colors.textPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        text = "Отклонить",
                        style = AppButtonStyle.Tinted,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = onDeny,
                    )
                    AppButton(
                        text = if (request.packageName != null) "Дать приложению" else "Дать",
                        loading = busy,
                        modifier = Modifier.weight(1f),
                        onClick = { onApprove(minutes, request.packageName != null) },
                    )
                }
                if (request.packageName != null) {
                    Spacer(Modifier.height(4.dp))
                    AppButton(text = "Дать на все приложения", style = AppButtonStyle.Plain, enabled = !busy, onClick = {
                        onApprove(minutes, false)
                    })
                }
            }
            ApprovalRequest.TYPE_TASK_REQUEST ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        text = "Отклонить",
                        style = AppButtonStyle.Tinted,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = onDeny,
                    )
                    AppButton(text = "К заданиям", modifier = Modifier.weight(1f), onClick = onOpenTasks)
                }
            else ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        text = "Отклонить",
                        style = AppButtonStyle.Tinted,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = onDeny,
                    )
                    AppButton(
                        text = if (request.type == ApprovalRequest.TYPE_REMOVAL) "Разрешить" else "Разблокировать",
                        loading = busy,
                        modifier = Modifier.weight(1f),
                        onClick = { onApprove(0, false) },
                    )
                }
        }
        Spacer(Modifier.height(2.dp))
        Text(text = "Ребёнок увидит ответ сразу", style = typography.caption, color = colors.textTertiary)
        Spacer(Modifier.width(0.dp))
    }
}
