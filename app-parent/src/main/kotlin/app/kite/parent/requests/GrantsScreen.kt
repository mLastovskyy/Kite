package app.kite.parent.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.approval.TimeGrant
import app.kite.core.approval.TimeGrantsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.ScreenLoading
import app.kite.core.design.components.rowIcon
import app.kite.core.family.FamilyMember
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «Дополнительное время»: minutes handed out on the spot, and every minute this child was
 * given before — how much, when, and which parent said yes. With two parents in a family the
 * log is the only way to see that a request denied by one was granted by the other ten
 * minutes later.
 *
 * Giving time needs no request and no task (owner, 07.09.2026): the parent taps an amount and
 * it is added to today's limit right away.
 */
@Composable
fun GrantsScreen(
    child: FamilyMember,
    parents: List<FamilyMember>,
    grantsRemote: TimeGrantsRemote,
    familyId: String,
    commandsRemote: CommandsRemote,
    myMemberId: String?,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    var grants by remember(child.id) { mutableStateOf<List<TimeGrant>?>(null) }
    var reloadKey by remember(child.id) { mutableStateOf(0) }
    var granting by remember { mutableStateOf(false) }
    var note by remember(child.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(child.id, reloadKey) {
        grants = grantsRemote.forChild(child.id).getOrNull().orEmpty()
    }

    fun grant(minutes: Int) {
        granting = true
        note = null
        scope.launch {
            commandsRemote.send(child.id, familyId, DeviceCommand.GRANT_TIME, payloadJson = """{"minutes":$minutes}""")
                .onSuccess {
                    // The log must not fail the grant: the minutes are already on their way.
                    grantsRemote.record(
                        familyId = familyId,
                        childMemberId = child.id,
                        minutes = minutes,
                        grantedBy = myMemberId,
                        source = TimeGrant.SOURCE_MANUAL,
                    )
                    note = "Добавлено $minutes мин"
                    reloadKey++
                }
                .onFailure { note = "Нет связи — время не отправлено" }
            granting = false
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
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "Дополнительное время", onBack = onBack)
        Spacer(Modifier.height(20.dp))

        InsetGroupedList {
            InsetGroup(
                header = "Добавить сейчас",
                footer = note ?: "Минуты прибавятся к сегодняшнему лимиту. Ребёнок увидит уведомление.",
            ) {
                EXTRA_MINUTES.forEach { minutes ->
                    row(
                        title = "+$minutes мин",
                        icon = rowIcon(KiteIcons.Clock, colors.success),
                        enabled = !granting,
                        onClick = { grant(minutes) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val list = grants
        when {
            list == null -> ScreenLoading(caption = "Загружаем историю…", height = 160.dp)
            list.isEmpty() ->
                Text(
                    text = "Дополнительное время ещё не выдавалось.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            else -> {
                val zone = remember { ZoneId.systemDefault() }
                InsetGroupedList {
                    list.groupBy { dayOf(it.createdAt, zone) }.forEach { (day, dayGrants) ->
                        InsetGroup(header = dayLabel(day), footer = totalLabel(dayGrants)) {
                            dayGrants.forEach { grant ->
                                row(
                                    title = "+${grant.minutes} мин",
                                    value = timeOf(grant.createdAt, zone),
                                    subtitle = sourceLabel(grant, parents),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** The amounts a parent actually gives; anything finer belongs to a task. */
private val EXTRA_MINUTES = listOf(15, 30, 60)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM")

private fun parse(iso: String?): OffsetDateTime? = iso?.let {
    runCatching { OffsetDateTime.parse(it) }.getOrNull()
        ?: runCatching { Instant.parse(it).atOffset(OffsetDateTime.now().offset) }.getOrNull()
}

private fun dayOf(iso: String?, zone: ZoneId): LocalDate? = parse(iso)?.atZoneSameInstant(zone)?.toLocalDate()

private fun timeOf(iso: String?, zone: ZoneId): String = parse(iso)?.atZoneSameInstant(zone)?.format(TIME_FORMAT).orEmpty()

private fun dayLabel(day: LocalDate?): String {
    day ?: return "Ранее"
    val today = LocalDate.now()
    return when (day) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> day.format(DAY_FORMAT)
    }
}

private fun totalLabel(grants: List<TimeGrant>): String = "Всего ${grants.sumOf { it.minutes }} мин"

private fun sourceLabel(grant: TimeGrant, parents: List<FamilyMember>): String {
    val who = parents.firstOrNull { it.id == grant.grantedBy }?.displayName?.takeIf { it.isNotBlank() }
    val reason =
        when (grant.source) {
            TimeGrant.SOURCE_TASK -> "за задание"
            TimeGrant.SOURCE_MANUAL -> "вручную"
            TimeGrant.SOURCE_OFFLINE_CODE -> "по офлайн-коду"
            else -> "по запросу"
        }
    return listOfNotNull(who, reason).joinToString(" · ")
}
