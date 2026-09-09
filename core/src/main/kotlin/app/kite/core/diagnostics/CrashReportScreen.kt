package app.kite.core.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.rowIcon
import app.kite.core.family.FamilyMember
import app.kite.core.util.Timestamps
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The last crash, in full, with a copy button — the only way to get a stack trace off a
 * sideloaded build on a phone that has no GMS and no cable attached.
 *
 * Given [familyId] and [reportsRemote] it also lists what the other phones in the family filed,
 * each line naming its source — the parent's nickname and photo, or the device (owner,
 * 08.09.2026: a crash on one phone has to be readable on the other). Without them the screen is
 * exactly what it was: this phone only.
 */
@Composable
fun CrashReportScreen(
    crashLog: CrashLog,
    onBack: () -> Unit,
    familyId: String? = null,
    reportsRemote: CrashReportsRemote? = null,
    members: List<FamilyMember> = emptyList(),
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    var report by remember { mutableStateOf(crashLog.last()) }
    var copied by remember { mutableStateOf(false) }
    var family by remember(familyId) { mutableStateOf<List<CrashReport>?>(null) }
    var opened by remember { mutableStateOf<CrashReport?>(null) }
    BackHandler(enabled = opened != null) { opened = null }

    LaunchedEffect(familyId) {
        val remote = reportsRemote ?: return@LaunchedEffect
        val id = familyId ?: return@LaunchedEffect
        family = remote.list(id).getOrDefault(emptyList())
    }

    opened?.let { chosen ->
        OneReportScreen(
            title = chosen.source,
            caption = listOfNotNull(chosen.versionName?.let { "Kite $it" }, chosen.osVersion, crashDate(chosen.happenedAt))
                .joinToString(" · "),
            text = chosen.report,
            onBack = { opened = null },
        )
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
        BackHeader(title = "Отчёт о сбое", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        val text = report
        val others = family.orEmpty()
        if (text == null && others.isEmpty()) {
            Text(
                text = "Сбоев не было.",
                style = typography.body,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }

        if (text != null) {
            Text(
                text = "Покажите этот текст разработчику — в нём видно, что именно упало.",
                style = typography.subhead,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgBase)
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(text = text, style = typography.footnote.copy(fontFamily = FontFamily.Monospace), color = colors.textPrimary)
            }
            Spacer(Modifier.height(16.dp))
            AppButton(
                text = if (copied) "Скопировано" else "Скопировать",
                onClick = {
                    copyToClipboard(context, text)
                    copied = true
                },
            )
            Spacer(Modifier.height(8.dp))
            AppButton(
                text = "Удалить отчёт",
                style = AppButtonStyle.Plain,
                onClick = {
                    crashLog.clear()
                    report = null
                },
            )
        }

        if (others.isNotEmpty()) {
            Spacer(Modifier.height(if (text == null) 0.dp else 28.dp))
            val byMember = members.associateBy { it.id }
            InsetGroupedList {
                InsetGroup(
                    header = "Отчёты семьи",
                    footer = "С каждого телефона приходит его последний сбой. Хранятся 30 дней.",
                ) {
                    others.forEach { item ->
                        val author = item.memberId?.let(byMember::get)
                        row(
                            title = item.source,
                            subtitle = listOfNotNull(item.versionName?.let { "Kite $it" }, item.osVersion).joinToString(" · "),
                            value = crashDate(item.happenedAt),
                            icon = rowIcon(if (item.isChild) KiteIcons.Smartphone else KiteIcons.User, colors.textTertiary),
                            showChevron = true,
                            onClick = { opened = item },
                            trailing =
                            author?.let { member ->
                                {
                                    KiteAvatar(
                                        preset = AvatarPreset.byId(member.avatarKind),
                                        size = 24.dp,
                                        avatarUrl = member.avatarUrl,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** One report from another phone: the same monospace block, with a copy button. */
@Composable
private fun OneReportScreen(title: String, caption: String, text: String, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    var copied by remember(text) { mutableStateOf(false) }
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
        if (caption.isNotBlank()) {
            Text(text = caption, style = typography.footnote, color = colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bgBase)
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(text = text, style = typography.footnote.copy(fontFamily = FontFamily.Monospace), color = colors.textPrimary)
        }
        Spacer(Modifier.height(16.dp))
        AppButton(
            text = if (copied) "Скопировано" else "Скопировать",
            onClick = {
                copyToClipboard(context, text)
                copied = true
            },
        )
        Spacer(Modifier.height(32.dp))
    }
}

/** «8 сент, 14:03» — enough to line a crash up with what the phone was doing. */
private fun crashDate(iso: String): String = Timestamps.zonedOrNull(iso)?.let(CRASH_DATE::format).orEmpty()

private val CRASH_DATE = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("Kite crash", text))
}
