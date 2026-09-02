package app.kite.parent.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.tasks.ChildTask
import app.kite.parent.rules.DayChips
import app.kite.parent.rules.SubScreenHeader

/**
 * «Новое задание» (Kids360): title with suggestion chips, reward chips +5…+40 мин, and
 * «Повторять по дням недели» chips (none selected = one-time). Same screen edits a task.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    childName: String,
    initial: ChildTask?,
    onSave: (title: String, rewardMinutes: Int, repeatDays: Set<Int>) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var reward by remember { mutableIntStateOf(initial?.rewardMinutes ?: 10) }
    var days by remember { mutableStateOf(initial?.repeatDays?.toSet() ?: emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SubScreenHeader(title = if (initial == null) "Новое задание" else "Задание", onBack = onCancel)
        Spacer(Modifier.height(6.dp))
        Text(text = "Для: $childName", style = typography.subhead, color = colors.textSecondary)
        Spacer(Modifier.height(20.dp))

        InsetGroupedList {
            InsetGroup(header = "Название задания") {
                custom {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        AppTextField(
                            value = title,
                            onValueChange = {
                                title = it.take(ChildTask.MAX_TITLE)
                                error = null
                            },
                            placeholder = "Что нужно сделать",
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChildTask.TITLE_SUGGESTIONS.forEach { suggestion ->
                                Chip(text = suggestion, selected = title == suggestion, onClick = { title = suggestion })
                            }
                        }
                    }
                }
            }
            InsetGroup(header = "Награда за выполнение") {
                custom {
                    FlowRow(
                        Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChildTask.REWARD_OPTIONS.forEach { minutes ->
                            Chip(text = "+$minutes мин", selected = reward == minutes, onClick = { reward = minutes })
                        }
                    }
                }
            }
            InsetGroup(
                header = "Повторять по дням недели",
                footer =
                if (days.isEmpty()) {
                    "Ничего не выбрано — задание одноразовое."
                } else {
                    "После подтверждения задание появится снова в выбранные дни."
                },
            ) {
                custom {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        DayChips(selected = days, onToggle = { d -> days = if (d in days) days - d else days + d })
                    }
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = error!!, style = typography.subhead, color = colors.danger)
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Сохранить",
            onClick = {
                if (title.isBlank()) error = "Введите название" else onSave(title.trim(), reward, days)
            },
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.fillQuaternary)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else colors.textPrimary,
        )
    }
}
