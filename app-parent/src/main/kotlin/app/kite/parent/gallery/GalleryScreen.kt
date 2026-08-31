package app.kite.parent.gallery

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.design.KiteTheme
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.LargeTitleScaffold
import app.kite.core.design.components.RowIcon
import app.kite.core.platform.PlatformVariant
import kotlinx.coroutines.flow.Flow

private enum class ThemeMode { System, Light, Dark }

/**
 * M1 deliverable: every design-system component rendered in one place, switchable between
 * light and dark without touching the system setting.
 */
@Composable
fun GalleryScreen(platformVariant: PlatformVariant, servicesFlavor: String, disableEnforcement: Flow<Boolean>) {
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.System) }
    val darkTheme =
        when (themeMode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

    // Keep status-bar icon contrast in sync with the in-app theme override.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    KiteTheme(darkTheme = darkTheme) {
        val enforcementDisabled by disableEnforcement.collectAsStateWithLifecycle(initialValue = false)
        LargeTitleScaffold(title = "Компоненты") {
            item { ThemeGroup(themeMode) { themeMode = it } }
            item { SwitchGroup() }
            item { ButtonGroup() }
            item { ListDemoGroup() }
            item { TypographyGroup() }
            item { ColorGroup() }
            item { PlatformGroup(platformVariant, servicesFlavor, enforcementDisabled) }
        }
    }
}

private val GroupSpacing = Modifier.padding(bottom = 24.dp)

@Composable
private fun CheckMark() {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Text(text = "✓", style = typography.headline, color = colors.accent)
}

@Composable
private fun ThemeGroup(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    InsetGroup(modifier = GroupSpacing, header = "Тема") {
        row(
            title = "Как в системе",
            onClick = { onSelect(ThemeMode.System) },
            trailing = if (selected == ThemeMode.System) ({ CheckMark() }) else null,
        )
        row(
            title = "Светлая",
            onClick = { onSelect(ThemeMode.Light) },
            trailing = if (selected == ThemeMode.Light) ({ CheckMark() }) else null,
        )
        row(
            title = "Тёмная",
            onClick = { onSelect(ThemeMode.Dark) },
            trailing = if (selected == ThemeMode.Dark) ({ CheckMark() }) else null,
        )
    }
}

@Composable
private fun SwitchGroup() {
    var notifications by rememberSaveable { mutableStateOf(true) }
    var quietHours by rememberSaveable { mutableStateOf(false) }
    InsetGroup(
        modifier = GroupSpacing,
        header = "Переключатели",
        footer = "Подпись под группой поясняет, что делает переключатель.",
    ) {
        row(title = "Уведомления", trailing = { AppSwitch(notifications, { notifications = it }) })
        row(title = "Тихие часы", trailing = { AppSwitch(quietHours, { quietHours = it }) })
        row(title = "Недоступно", trailing = { AppSwitch(checked = true, onCheckedChange = {}, enabled = false) })
    }
}

@Composable
private fun ButtonGroup() {
    InsetGroup(modifier = GroupSpacing, header = "Кнопки") {
        custom {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppButton(text = "Основное действие", onClick = {}, style = AppButtonStyle.Filled)
                AppButton(text = "Дополнительное действие", onClick = {}, style = AppButtonStyle.Tinted)
                AppButton(text = "Текстовая кнопка", onClick = {}, style = AppButtonStyle.Plain)
                AppButton(text = "Удалить устройство", onClick = {}, style = AppButtonStyle.Destructive)
                AppButton(text = "Недоступно", onClick = {}, enabled = false)
            }
        }
    }
}

@Composable
private fun ListDemoGroup() {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    InsetGroup(modifier = GroupSpacing, header = "Списки") {
        row(
            title = "Экранное время",
            value = "2 ч 15 мин",
            icon =
            RowIcon(colors.accent) {
                Text(text = "Э", style = typography.subhead, color = Color.White)
            },
            showChevron = true,
            onClick = {},
        )
        row(
            title = "Правила",
            icon =
            RowIcon(colors.success) {
                Text(text = "П", style = typography.subhead, color = Color.White)
            },
            showChevron = true,
            onClick = {},
        )
        row(title = "Без иконки", value = "значение")
        row(title = "Недоступная строка", enabled = false, onClick = {})
    }
}

@Composable
private fun TypographyGroup() {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val samples =
        listOf(
            "Large Title · 34" to typography.largeTitle,
            "Title 1 · 28" to typography.title1,
            "Title 2 · 22" to typography.title2,
            "Title 3 · 20" to typography.title3,
            "Headline · 17" to typography.headline,
            "Body · 17" to typography.body,
            "Callout · 16" to typography.callout,
            "Subhead · 15" to typography.subhead,
            "Footnote · 13" to typography.footnote,
            "Caption · 12" to typography.caption,
        )
    InsetGroup(modifier = GroupSpacing, header = "Типографика") {
        custom {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                samples.forEach { (label, style) ->
                    Text(text = label, style = style, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun ColorGroup() {
    val colors = LocalAppColors.current
    val swatches =
        listOf(
            colors.accent,
            colors.accentLight,
            colors.accentDeep,
            colors.success,
            colors.warning,
            colors.danger,
            colors.info,
        )
    InsetGroup(modifier = GroupSpacing, header = "Цвета") {
        custom {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                swatches.forEach { color ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformGroup(platformVariant: PlatformVariant, servicesFlavor: String, enforcementDisabled: Boolean) {
    InsetGroup(
        header = "Платформа",
        footer = "Реализация выбирается на старте: сервисы вендора, иначе чистый AOSP.",
    ) {
        row(title = "Сборка", value = servicesFlavor)
        row(title = "Сервисы в рантайме", value = platformVariant.name)
        row(title = "Kill switch", value = if (enforcementDisabled) "активирован" else "неактивен")
    }
}
