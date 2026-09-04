package app.kite.parent.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.KiteLoader
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.family.PairingInvite
import app.kite.core.family.PairingKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AddStep { Install, Code, Waiting }

/**
 * «Настроить телефон ребёнка» in stages, the way Kids360 does it: 1 — install Kite Jr on the
 * child's phone (no store listing yet, so no «send a link» — the parent is simply told which
 * app to install); 2 — the code and the QR (QR stays: CLAUDE.md pairing rule); 3 — waiting
 * for the device, which advances by itself when the new child appears in the family.
 */
@Composable
fun AddChildFlow(
    familyId: String,
    knownMemberIds: Set<String>,
    familyRepository: FamilyRepository,
    onDone: (FamilyMember?) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(AddStep.Install) }
    var invite by remember { mutableStateOf<PairingInvite?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun ensureInvite(then: () -> Unit) {
        if (invite != null && secondsLeft(invite!!.expiresAt) > 0) {
            then()
            return
        }
        scope.launch {
            creating = true
            error = null
            familyRepository.createInvite(familyId, PairingKind.PAIR_CHILD)
                .onSuccess {
                    invite = it
                    then()
                }
                .onFailure { error = it.message ?: "Не удалось создать код" }
            creating = false
        }
    }

    // Stage 3: poll the family until a member we did not know appears.
    var joined by remember { mutableStateOf<FamilyMember?>(null) }
    LaunchedEffect(step) {
        if (step != AddStep.Waiting) return@LaunchedEffect
        while (joined == null) {
            familyRepository.members(familyId).getOrNull()
                ?.firstOrNull { !it.isParent && it.id !in knownMemberIds }
                ?.let { joined = it }
            if (joined == null) delay(3_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Телефон ребёнка", style = typography.title1, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AppButton(text = if (step == AddStep.Waiting) "Позже" else "Отмена", style = AppButtonStyle.Plain, onClick = onCancel)
        }
        Spacer(Modifier.height(16.dp))
        Steps(current = step.ordinal)
        Spacer(Modifier.height(28.dp))

        when (step) {
            AddStep.Install -> {
                // The child app's real launcher icon (black disc, white kite) with its name, so
                // the parent knows exactly what to look for on the child's phone.
                ChildAppIcon(size = 88.dp)
                Spacer(Modifier.height(10.dp))
                Text(text = "Kite Jr", style = typography.headline, color = colors.textPrimary)
                Spacer(Modifier.height(16.dp))
                Text(text = "Установите Kite Jr", style = typography.title2, color = colors.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Скачайте на телефон ребёнка приложение Kite Jr с такой иконкой и откройте его. Дальше — код с этого экрана.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.weight(1f))
                AppButton(text = "Установлено, дальше", loading = creating, onClick = { ensureInvite { step = AddStep.Code } })
                Spacer(Modifier.height(24.dp))
            }

            AddStep.Code -> {
                val active = invite
                if (active == null) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        KiteLoader(size = 64.dp)
                    }
                } else {
                    val remaining by produceState(initialValue = secondsLeft(active.expiresAt), active.expiresAt) {
                        while (value > 0) {
                            delay(1000)
                            value = secondsLeft(active.expiresAt)
                        }
                    }
                    Text(
                        text = "Настройте приложение ребёнка",
                        style = typography.title2,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Введите этот код в Kite Jr или отсканируйте QR.",
                        style = typography.subhead,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(
                            RoundedCornerShape(12.dp),
                        ).background(colors.bgBase).padding(horizontal = 20.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Same type as the rest of the app (largeTitle), just tracked wider —
                        // no separate 36sp face for the code.
                        Text(
                            text = groupedCode(active.code),
                            style = typography.largeTitle.copy(letterSpacing = 6.sp),
                            maxLines = 1,
                            color = colors.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    QrCode(content = active.deepLink, size = 200.dp, logo = { KiteAvatar(preset = AvatarPreset.KITE, size = 40.dp) })
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (remaining > 0) "Действует ещё ${formatMmSs(remaining)}" else "Код истёк",
                        style = typography.footnote,
                        color = if (remaining > 0) colors.textSecondary else colors.danger,
                    )
                    Spacer(Modifier.weight(1f))
                    if (remaining <= 0) {
                        AppButton(text = "Новый код", loading = creating, onClick = {
                            invite = null
                            ensureInvite { }
                        })
                    } else {
                        AppButton(text = "Код введён", onClick = { step = AddStep.Waiting })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            AddStep.Waiting -> {
                val done = joined
                if (done == null) {
                    Spacer(Modifier.height(24.dp))
                    KiteLoader(size = 64.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(text = "Ждём подключения…", style = typography.title2, color = colors.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text =
                        "Ребёнок вводит код, соглашается и выдаёт разрешения — это займёт пару минут. " +
                            "Можно закрыть: телефон появится сам.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    AppButton(text = "Показать код снова", style = AppButtonStyle.Plain, onClick = { step = AddStep.Code })
                    Spacer(Modifier.height(24.dp))
                } else {
                    Spacer(Modifier.height(12.dp))
                    KiteAvatar(preset = AvatarPreset.byId(done.avatarKind), size = 88.dp, avatarUrl = done.avatarUrl)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "${done.displayName.ifBlank {
                            "Ребёнок"
                        }} подключён",
                        style = typography.title2,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Лимиты, расписание и задания — на Главной. Пока ребёнок выдаёт разрешения, защита включается по частям.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    AppButton(text = "Готово", onClick = { onDone(done) })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Three numbered dots with a caption — the staging is visible from the first screen. */
@Composable
private fun Steps(current: Int) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val labels = listOf("Установить", "Код", "Подключение")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val done = index < current
            val active = index == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (done || active) colors.accent else colors.fillQuaternary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        AppIcon(icon = KiteIcons.Check, tint = Color.White, size = 16.dp)
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = typography.subhead.copy(fontWeight = FontWeight.Bold),
                            color = if (active) Color.White else colors.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(text = label, style = typography.caption, color = if (active) colors.textPrimary else colors.textSecondary)
            }
            if (index < labels.lastIndex) {
                Box(Modifier.width(24.dp).height(2.dp).background(if (done) colors.accent else colors.separator))
            }
        }
    }
}

private fun secondsLeft(expiresAtSeconds: Long): Long = (expiresAtSeconds - System.currentTimeMillis() / 1000).coerceAtLeast(0)

internal fun groupedCode(code: String): String = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

private fun formatMmSs(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Unused-import guard for the tile helper referenced from a sibling file. */
@Suppress("unused")
private val tileRef = ::IconTile

/** Kite Jr launcher icon as drawn in the app: black disc, white outlined kite with a tail. */
@Composable
private fun ChildAppIcon(size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.09f
            val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val body =
                Path().apply {
                    moveTo(w * 0.5f, 0f)
                    lineTo(w * 0.85f, h * 0.4f)
                    lineTo(w * 0.5f, h * 0.75f)
                    lineTo(w * 0.15f, h * 0.4f)
                    close()
                }
            drawPath(body, Color.White, style = line)
            drawLine(Color.White, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h * 0.75f), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.15f, h * 0.4f), Offset(w * 0.85f, h * 0.4f), stroke, StrokeCap.Round)
            val tail =
                Path().apply {
                    moveTo(w * 0.5f, h * 0.75f)
                    cubicTo(w * 0.4f, h * 0.86f, w * 0.62f, h * 0.9f, w * 0.5f, h)
                }
            drawPath(tail, Color.White, style = Stroke(width = stroke * 0.8f, cap = StrokeCap.Round))
        }
    }
}
