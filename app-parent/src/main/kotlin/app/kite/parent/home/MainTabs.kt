package app.kite.parent.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.kite.core.appearance.AppearanceRepository
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.family.Family
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.update.ApkInstaller
import app.kite.core.usage.UsageRemote
import app.kite.parent.auth.PinLock
import app.kite.parent.family.ApprovalsScreen
import app.kite.parent.family.FamilyMapScreen
import app.kite.parent.family.FamilyScreen
import app.kite.parent.settings.SettingsScreen

enum class ParentTab(val label: String) {
    Family("Семья"),
    Map("Карта"),
    Requests("Запросы"),
    Settings("Настройки"),
}

/**
 * Signed-in home: an iOS-style tab bar over four sections. Members are loaded once here and
 * shared by every tab; the Family and Settings tabs ask for a reload after they change them.
 */
@Composable
fun MainTabs(
    family: Family,
    familyRepository: FamilyRepository,
    sessionManager: SessionManager,
    secureStore: SecureStore,
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    commandsRemote: CommandsRemote,
    locationRemote: DeviceLocationRemote,
    approvalsRemote: ApprovalsRemote,
    avatarRemote: AvatarRemote,
    pinLock: PinLock,
    appearance: AppearanceRepository,
    apkInstaller: ApkInstaller,
    killSwitch: KillSwitchRepository,
    versionName: String,
    onSignOut: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ParentTab.Family) }
    var members by remember { mutableStateOf<List<FamilyMember>>(emptyList()) }
    var membersKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(family.id, membersKey) {
        familyRepository.members(family.id).onSuccess { members = it }
    }

    val session = (sessionManager.authState.value as? AuthState.SignedIn)?.session
    val me = members.firstOrNull { it.userId == session?.userId }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The tab bar owns the bottom inset; screens must not pad for it twice.
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            when (tab) {
                ParentTab.Family ->
                    FamilyScreen(
                        family = family,
                        members = members,
                        onMembersChanged = { membersKey++ },
                        familyRepository = familyRepository,
                        secureStore = secureStore,
                        usageRemote = usageRemote,
                        rulesRemote = rulesRemote,
                        commandsRemote = commandsRemote,
                        locationRemote = locationRemote,
                    )
                ParentTab.Map -> FamilyMapScreen(members = members, locationRemote = locationRemote)
                ParentTab.Requests ->
                    ApprovalsScreen(
                        familyId = family.id,
                        members = members,
                        approvalsRemote = approvalsRemote,
                        commandsRemote = commandsRemote,
                    )
                ParentTab.Settings ->
                    SettingsScreen(
                        me = me,
                        email = session?.email,
                        familyRepository = familyRepository,
                        avatarRemote = avatarRemote,
                        pinLock = pinLock,
                        appearance = appearance,
                        apkInstaller = apkInstaller,
                        killSwitch = killSwitch,
                        versionName = versionName,
                        onProfileChanged = { membersKey++ },
                        onSignOut = onSignOut,
                    )
            }
        }
        TabBar(selected = tab, onSelect = { tab = it })
    }
}

@Composable
private fun TabBar(selected: ParentTab, onSelect: (ParentTab) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(Modifier.fillMaxWidth().background(colors.bgBase)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            ParentTab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) colors.accent else colors.textTertiary
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TabIcon(tab = tab, tint = tint, filled = active)
                    Spacer(Modifier.height(3.dp))
                    Text(text = tab.label, style = typography.caption, color = tint)
                }
            }
        }
    }
}

/** Tab glyphs drawn in Compose — no icon font, no asset pipeline, identical on Huawei. */
@Composable
private fun TabIcon(tab: ParentTab, tint: Color, filled: Boolean) {
    Canvas(Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            ParentTab.Family -> {
                // Two people: a taller one behind, a smaller one in front.
                person(cx = w * 0.62f, headY = h * 0.26f, headR = w * 0.13f, shoulderW = w * 0.5f, baseY = h * 0.78f, tint, filled, stroke)
                person(cx = w * 0.36f, headY = h * 0.36f, headR = w * 0.11f, shoulderW = w * 0.42f, baseY = h * 0.84f, tint, filled, stroke)
            }
            ParentTab.Map -> {
                val pin =
                    Path().apply {
                        moveTo(w * 0.5f, h * 0.94f)
                        cubicTo(w * 0.5f, h * 0.94f, w * 0.14f, h * 0.55f, w * 0.14f, h * 0.38f)
                        cubicTo(w * 0.14f, h * 0.18f, w * 0.3f, h * 0.06f, w * 0.5f, h * 0.06f)
                        cubicTo(w * 0.7f, h * 0.06f, w * 0.86f, h * 0.18f, w * 0.86f, h * 0.38f)
                        cubicTo(w * 0.86f, h * 0.55f, w * 0.5f, h * 0.94f, w * 0.5f, h * 0.94f)
                        close()
                    }
                if (filled) {
                    drawPath(pin, tint)
                    drawCircle(Color.White, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.38f))
                } else {
                    drawPath(pin, tint, style = stroke)
                    drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.5f, h * 0.38f), style = stroke)
                }
            }
            ParentTab.Requests -> {
                // Bell.
                val bell =
                    Path().apply {
                        moveTo(w * 0.2f, h * 0.7f)
                        lineTo(w * 0.8f, h * 0.7f)
                        lineTo(w * 0.73f, h * 0.6f)
                        lineTo(w * 0.73f, h * 0.4f)
                        cubicTo(w * 0.73f, h * 0.22f, w * 0.62f, h * 0.12f, w * 0.5f, h * 0.12f)
                        cubicTo(w * 0.38f, h * 0.12f, w * 0.27f, h * 0.22f, w * 0.27f, h * 0.4f)
                        lineTo(w * 0.27f, h * 0.6f)
                        close()
                    }
                if (filled) drawPath(bell, tint) else drawPath(bell, tint, style = stroke)
                drawLine(tint, Offset(w * 0.42f, h * 0.84f), Offset(w * 0.58f, h * 0.84f), stroke.width, StrokeCap.Round)
            }
            ParentTab.Settings -> {
                // Gear: ring plus eight teeth.
                val c = Offset(w * 0.5f, h * 0.5f)
                repeat(8) { i ->
                    rotate(degrees = i * 45f, pivot = c) {
                        drawLine(tint, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.2f), stroke.width * 1.3f, StrokeCap.Round)
                    }
                }
                drawCircle(tint, radius = w * 0.28f, center = c, style = Stroke(width = stroke.width * (if (filled) 2.2f else 1f)))
                drawCircle(tint, radius = w * 0.1f, center = c, style = stroke)
            }
        }
    }
}

private fun DrawScope.person(
    cx: Float,
    headY: Float,
    headR: Float,
    shoulderW: Float,
    baseY: Float,
    tint: Color,
    filled: Boolean,
    stroke: Stroke,
) {
    val shoulders =
        Path().apply {
            val top = baseY - shoulderW * 0.62f
            moveTo(cx - shoulderW / 2, baseY)
            cubicTo(cx - shoulderW / 2, top, cx + shoulderW / 2, top, cx + shoulderW / 2, baseY)
            close()
        }
    if (filled) {
        drawCircle(tint, radius = headR, center = Offset(cx, headY))
        drawPath(shoulders, tint)
    } else {
        drawCircle(tint, radius = headR, center = Offset(cx, headY), style = stroke)
        drawPath(shoulders, tint, style = stroke)
    }
    // Keep the Size import meaningful for future glyphs sharing this file.
    @Suppress("UNUSED_VARIABLE")
    val unused = Size.Zero
}
