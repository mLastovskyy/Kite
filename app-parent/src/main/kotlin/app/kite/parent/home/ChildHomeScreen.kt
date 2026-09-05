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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.sp
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.TimeGrantsRemote
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.CircleIconButton
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
import app.kite.parent.family.freshnessShort
import app.kite.parent.requests.GrantsScreen
import app.kite.parent.requests.RequestCard
import app.kite.parent.requests.RequestsController
import app.kite.parent.requests.askedForLabel
import app.kite.parent.rules.AppListKind
import app.kite.parent.rules.AppListsScreen
import app.kite.parent.rules.LimitsScreen
import app.kite.parent.rules.RulesController
import app.kite.parent.rules.SchedulesScreen
import app.kite.parent.stats.UsageWeek
import app.kite.parent.stats.loadUsageWeek
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class HomeSub { Limits, Apps, Schedules, Code, Grants }

/** One refresh per child per two minutes, however often the parent switches back and forth. */
private const val REFRESH_THROTTLE_MS = 2L * 60 * 1000

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
    onOpenRequests: () -> Unit,
    openAppPackage: String? = null,
    onOpenedApp: () -> Unit = {},
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    commandsRemote: CommandsRemote,
    requestsController: RequestsController,
    grantsRemote: TimeGrantsRemote,
    parents: List<FamilyMember>,
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
    var note by remember { mutableStateOf<String?>(null) }

    var device by remember(child.id) { mutableStateOf<ChildDevice?>(null) }

    LaunchedEffect(child.id, reloadKey) {
        rulesController.load()
        launch { device = childDeviceRemote.forChild(child.id).getOrNull() }
        launch { loadUsageWeek(usageRemote, child.id, today).onSuccess { week = it } }
        launch { location = locationRemote.latest(child.id).getOrNull() }
    }

    // Opening the app is the only moment the parent actually reads these numbers, so that is
    // where the child is asked for fresh ones — nothing runs on the child in between.
    var lastRefreshSent by remember(child.id) { mutableStateOf(0L) }
    OnResumeEffect(child.id) {
        reloadKey++
        val now = System.currentTimeMillis()
        if (now - lastRefreshSent > REFRESH_THROTTLE_MS) {
            lastRefreshSent = now
            scope.launch { commandsRemote.send(child.id, familyId, DeviceCommand.REFRESH) }
        }
    }

    LaunchedEffect(child.id) {
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
        HomeSub.Grants -> {
            GrantsScreen(child = child, parents = parents, grantsRemote = grantsRemote, onBack = { sub = null })
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
    var showDevice by remember { mutableStateOf(false) }
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
            message = "Громкий сигнал ~10 секунд, даже в тихом режиме.",
            confirmText = "Подать сигнал",
            onConfirm = {
                confirmRing = false
                send(DeviceCommand.RING, done = "Сигнал отправлен")
            },
            onDismiss = { confirmRing = false },
        )
    }

    if (showDevice) {
        ChildDeviceSheet(
            device = device,
            onRelease = {
                showDevice = false
                confirmRelease = true
            },
            onDismiss = { showDevice = false },
        )
    }

    fun resolveRequest(request: ApprovalRequest, approve: Boolean, minutes: Int = 15, scopeToApp: Boolean = false) {
        if (approve && request.type == ApprovalRequest.TYPE_UNLOCK) locked = false
        requestsController.resolve(request, approve, minutes, scopeToApp) { done ->
            note = done
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Главная", style = typography.largeTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            RequestsButton(count = requestsController.count, onClick = onOpenRequests)
        }
        Spacer(Modifier.height(12.dp))
        ChildSwitcher(
            children = children,
            selected = child,
            onSelect = onSelectChild,
            badgeFor = { requestsController.forChild(it.id).size },
        )
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
        Spacer(Modifier.height(16.dp))

        InsetGroupedList {
            val requests = requestsController.forChild(child.id)
            if (requests.isNotEmpty()) {
                InsetGroup(header = "Просит ${child.displayName.ifBlank { "ребёнок" }}") {
                    requests.forEach { request ->
                        custom {
                            RequestCard(
                                request = request,
                                busy = requestsController.busy == request.id,
                                askedFor = askedForLabel(request.targetMemberId, parents, requestsController.myMemberId),
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

            if (device?.isHealthy == false) {
                InsetGroup {
                    row(
                        title = "Защита настроена не полностью",
                        value = "${device?.protectionMissing?.size ?: 0}",
                        icon = rowIcon(KiteIcons.Shield, colors.warning),
                        showChevron = true,
                        onClick = { showDevice = true },
                    )
                }
            }

            val limitedCount = rules?.appRules?.count { it.value.inPool && it.value.dailyLimitMinutes != null } ?: 0
            val blockedCount = rules?.appRules?.count { it.value.blocked } ?: 0
            val schedules = rules?.quietHours?.count { it.enabled } ?: 0
            InsetGroup(header = "Ограничения") {
                row(
                    title = "Приложения",
                    value = (blockedCount + limitedCount).takeIf { it > 0 }?.toString() ?: "Все",
                    icon = rowIcon(KiteIcons.Smartphone, AppListKind.Pool.color),
                    showChevron = true,
                    onClick = { sub = HomeSub.Apps },
                )
                row(
                    title = "Расписание",
                    value = if (schedules == 0) "Выкл" else "$schedules",
                    icon = rowIcon(KiteIcons.CalendarClock, Color(0xFF5856D6)),
                    showChevron = true,
                    onClick = { sub = HomeSub.Schedules },
                )
                row(
                    title = "Дополнительное время",
                    icon = rowIcon(KiteIcons.Clock, Color(0xFF34C759)),
                    showChevron = true,
                    onClick = { sub = HomeSub.Grants },
                )
            }

            InsetGroup(header = "Телефон") {
                row(
                    title = "Где ребёнок",
                    value = location?.let { freshnessShort(it.recordedAt) } ?: "Нет",
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

/** Bell with the open-request count: the way into «Запросы» from anywhere on Главная. */
@Composable
private fun RequestsButton(count: Int, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box {
        CircleIconButton(icon = KiteIcons.Bell, size = 38.dp, onClick = onClick)
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(colors.danger)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    style = typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildDeviceSheet(device: ChildDevice?, onRelease: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgGrouped, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = device?.model ?: "Телефон ребёнка",
                style = typography.title3,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                text = listOfNotNull(device?.osVersion, device?.appVersionCode?.let { "сборка $it" }).joinToString(" · "),
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(16.dp))
            InsetGroupedList {
                if (device?.protectionMissing.orEmpty().isNotEmpty()) {
                    InsetGroup(
                        header = "Не настроено у ребёнка",
                        footer = "Попросите ребёнка открыть Kite Jr и нажать «Здоровье защиты».",
                    ) {
                        device?.protectionMissing.orEmpty().forEach { requirement ->
                            row(title = protectionTitle(requirement))
                        }
                    }
                }
                InsetGroup(footer = "Ограничения выключатся, Kite Jr можно будет удалить.") {
                    row(
                        title = "Снять защиту с телефона",
                        icon = rowIcon(KiteIcons.LockOpen, colors.danger),
                        onClick = onRelease,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
