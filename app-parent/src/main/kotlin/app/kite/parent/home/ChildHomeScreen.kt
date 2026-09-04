package app.kite.parent.home

import androidx.activity.compose.BackHandler
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
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.FitText
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.RollingText
import app.kite.core.design.components.formatUsageMs
import app.kite.core.design.components.rowIcon
import app.kite.core.family.ChildDevice
import app.kite.core.family.ChildDeviceRemote
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import app.kite.core.realtime.RealtimeTable
import app.kite.core.rules.ChildRules
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.usage.UsageRemote
import app.kite.parent.family.ApprovalCodeScreen
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

private enum class HomeSub { Limits, Apps, Schedules, Code }

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
    onOpenMap: () -> Unit,
    openAppPackage: String? = null,
    onOpenedApp: () -> Unit = {},
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    commandsRemote: CommandsRemote,
    approvalsRemote: ApprovalsRemote,
    locationRemote: DeviceLocationRemote,
    childAppsRemote: ChildAppsRemote,
    childDeviceRemote: ChildDeviceRemote,
    realtime: RealtimeTable,
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
    // Sub-screens are swapped in place, so the system back gesture must close them — otherwise
    // it leaves the app from «Лимиты времени».
    BackHandler(enabled = sub != null) { sub = null }
    var appsFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openAppPackage) {
        openAppPackage?.let {
            appsFocus = it
            sub = HomeSub.Apps
            onOpenedApp()
        }
    }
    // The server keeps no lock state; remember what this parent last sent for this child.
    var locked by remember(child.id) { mutableStateOf(false) }
    var busyRequest by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    var device by remember(child.id) { mutableStateOf<ChildDevice?>(null) }

    suspend fun reloadRequests() {
        requests = approvalsRemote.pending(familyId).getOrNull().orEmpty().filter { it.childMemberId == child.id }
    }

    LaunchedEffect(child.id, reloadKey) {
        rulesController.load()
        launch { device = childDeviceRemote.forChild(child.id).getOrNull() }
        launch { loadUsageWeek(usageRemote, child.id, today).onSuccess { week = it } }
        launch { reloadRequests() }
        launch { location = locationRemote.latest(child.id).getOrNull() }
    }

    LaunchedEffect(child.id) {
        realtime.subscribe(
            scope = this,
            table = "approval_requests",
            filter = "family_id=eq.$familyId",
            events = listOf(RealtimeTable.EVENT_INSERT, RealtimeTable.EVENT_UPDATE),
        ) { scope.launch { reloadRequests() } }
        realtime.subscribe(
            scope = this,
            table = "device_location",
            filter = "member_id=eq.${child.id}",
            events = listOf(RealtimeTable.EVENT_INSERT, RealtimeTable.EVENT_UPDATE),
        ) { scope.launch { location = locationRemote.latest(child.id).getOrNull() } }
        realtime.subscribe(
            scope = this,
            table = "devices",
            filter = "member_id=eq.${child.id}",
            events = listOf(RealtimeTable.EVENT_INSERT, RealtimeTable.EVENT_UPDATE),
        ) { scope.launch { device = childDeviceRemote.forChild(child.id).getOrNull() } }
    }

    val rules = rulesController.rules
    val appsToday = week?.apps(today).orEmpty()
    val appsWeek = week?.apps(null).orEmpty()

    // Labels from the week (an app used yesterday still needs a rule), today's minutes for the subtitle.
    val todayByPkg = appsToday.associateBy { it.packageName }
    val mergedApps = appsWeek.map { it.copy(totalMs = todayByPkg[it.packageName]?.totalMs ?: 0L) }

    when (sub) {
        HomeSub.Limits -> {
            LimitsScreen(controller = rulesController, onBack = { sub = null })
            return
        }
        HomeSub.Apps -> {
            AppListsScreen(
                controller = rulesController,
                apps = mergedApps,
                childAppsRemote = childAppsRemote,
                memberId = child.id,
                initialPackage = appsFocus,
                onBack = {
                    appsFocus = null
                    sub = null
                },
            )
            return
        }
        HomeSub.Schedules -> {
            SchedulesScreen(
                controller = rulesController,
                apps = mergedApps,
                childAppsRemote = childAppsRemote,
                memberId = child.id,
                onBack = { sub = null },
            )
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
    var confirmRelease by remember { mutableStateOf(false) }
    if (confirmRelease) {
        AppDialog(
            title = "Снять защиту?",
            message =
            "Все ограничения на телефоне ребёнка отключатся, и Kite Jr можно будет удалить. " +
                "Включить обратно можно только с телефона ребёнка.",
            confirmText = "Снять защиту",
            destructive = true,
            onConfirm = {
                confirmRelease = false
                send(DeviceCommand.RELEASE, done = "Защита снимается")
            },
            onDismiss = { confirmRelease = false },
        )
    }
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
            val blockedCount = rules?.appRules?.count { it.value.blocked } ?: 0
            InsetGroup(
                header = "Приложения",
                footer =
                listOfNotNull(
                    if (limitedCount > 0) "С лимитом: $limitedCount" else null,
                    if (blockedCount > 0) "Запрещено: $blockedCount" else null,
                ).joinToString(" · ").ifEmpty { null },
            ) {
                row(
                    title = "Приложения на телефоне",
                    icon = rowIcon(KiteIcons.Smartphone, AppListKind.Pool.color),
                    showChevron = true,
                    onClick = { sub = HomeSub.Apps },
                )
            }

            InsetGroup(
                header = "Расписание",
                footer = null,
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
                    // Days go into the title (it may wrap), the time range stays a short value.
                    row(
                        title = "${q.name.ifBlank { "Без названия" }} · ${daysSummary(q.days)}",
                        value = "${formatClock(q.startMinutes)}–${formatClock(q.endMinutes)}",
                        onClick = { sub = HomeSub.Schedules },
                    )
                }
            }

            InsetGroup(header = "Телефон ребёнка", footer = deviceFooter(device)) {
                row(
                    title = device?.model ?: "Телефон не отвечает",
                    value = device?.osVersion,
                    icon = rowIcon(KiteIcons.Smartphone, if (device?.isHealthy == false) colors.warning else colors.textTertiary),
                )
                device?.protectionMissing.orEmpty().forEach { requirement ->
                    row(title = protectionTitle(requirement), value = "Не настроено")
                }
                row(
                    title = "Снять защиту с телефона",
                    value = "Удалит ограничения",
                    icon = rowIcon(KiteIcons.LockOpen, colors.danger),
                    onClick = { confirmRelease = true },
                )
            }

            InsetGroup(header = "Телефон") {
                row(
                    title = "Где ребёнок",
                    value = location?.let { freshness(it.recordedAt) } ?: "Нет данных",
                    icon = rowIcon(KiteIcons.MapPin, Color(0xFF34C759)),
                    showChevron = true,
                    onClick = onOpenMap,
                )
                row(
                    title = "Найти телефон",
                    icon = rowIcon(KiteIcons.BellRing, Color(0xFFFF9500)),
                    showChevron = true,
                    onClick = { confirmRing = true },
                )
                row(
                    title = "Код для ребёнка",
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

/** Kids360's violet hero, in our accent: today's usage against the limit, top apps, two actions. */
@Composable
private fun HeroCard(
    rules: ChildRules?,
    usedTodayMs: Long,
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
        // Rolls when the number changes (new sync, granted minutes) instead of blinking.
        RollingText(
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
        // A quiet hairline capsule, not a progress bar: the numbers above carry the meaning.
        val fraction = if (limit != null && limit > 0) (usedTodayMs / (limit * 60_000f)).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(white.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(white.copy(alpha = 0.85f)))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroButton(text = "Изменить лимит", filled = false, modifier = Modifier.weight(1f), onClick = onEditLimit)
            HeroButton(
                text = if (locked) "Разблокировать" else "Заблокировать",
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitText(
            text = text,
            style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (filled) colors.accent else Color.White,
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
                // Primary action full width, «Отклонить» as a plain text button below: half-width
                // buttons clipped every Russian label on a 360dp phone.
                AppButton(
                    text = if (request.packageName != null) "Дать приложению" else "Дать время",
                    loading = busy,
                    onClick = { onApprove(minutes, request.packageName != null) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (request.packageName != null) {
                        AppButton(text = "Дать на все приложения", style = AppButtonStyle.Plain, enabled = !busy, onClick = {
                            onApprove(minutes, false)
                        })
                    }
                    AppButton(text = "Отклонить", style = AppButtonStyle.Plain, enabled = !busy, onClick = onDeny)
                }
            }
            ApprovalRequest.TYPE_TASK_REQUEST -> {
                AppButton(text = "К заданиям", onClick = onOpenTasks)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppButton(text = "Отклонить", style = AppButtonStyle.Plain, enabled = !busy, onClick = onDeny)
                }
            }
            else -> {
                AppButton(
                    text = if (request.type == ApprovalRequest.TYPE_REMOVAL) "Разрешить" else "Разблокировать",
                    loading = busy,
                    onClick = { onApprove(0, false) },
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppButton(text = "Отклонить", style = AppButtonStyle.Plain, enabled = !busy, onClick = onDeny)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(text = "Ребёнок увидит ответ сразу", style = typography.caption, color = colors.textTertiary)
        Spacer(Modifier.width(0.dp))
    }
}

private fun deviceFooter(device: ChildDevice?): String? = when {
    device == null -> "Телефон ребёнка ещё не выходил на связь."
    device.protectionMissing.isEmpty() -> null
    else -> "Ребёнку нужно доделать настройку — попросите его открыть Kite Jr."
}

private fun protectionTitle(requirement: String): String = when (requirement) {
    "NOTIFICATIONS" -> "Уведомления"
    "USAGE_ACCESS" -> "Доступ к статистике"
    "OVERLAY" -> "Показ поверх окон"
    "LOCATION_FOREGROUND" -> "Геолокация"
    "LOCATION_BACKGROUND" -> "Геолокация всегда"
    "ACCESSIBILITY" -> "Спец. возможности"
    "BATTERY" -> "Без энергосбережения"
    "VENDOR_AUTOSTART" -> "Автозапуск"
    "DEVICE_ADMIN" -> "Администратор устройства"
    "LOCATION_SERVICES_OFF" -> "Геолокация выключена на телефоне"
    else -> requirement
}
