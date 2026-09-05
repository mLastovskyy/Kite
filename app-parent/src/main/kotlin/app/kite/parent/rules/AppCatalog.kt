package app.kite.parent.rules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.kite.core.apps.ChildApp
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.design.components.UsageAppItem
import app.kite.core.rules.ChildRules

/** One app as the parent sees it: from the phone's inventory, from usage, or from an old rule. */
internal data class AppEntry(
    val packageName: String,
    val label: String,
    val todayMs: Long,
    val isSystem: Boolean,
    val used: Boolean = true,
)

/**
 * Everything the parent can point a rule at, for one child. Inventory (`child_apps`, every
 * launchable app on the phone) first, then apps only known from usage, then apps that only
 * carry a rule. [failed] is set when the inventory could not be fetched — the list then
 * holds only what was ever opened, and the screen says so.
 */
internal class AppCatalog(val installed: List<ChildApp>?, val failed: Boolean) {
    val loading: Boolean get() = installed == null && !failed

    fun entries(usage: List<UsageAppItem>, rules: ChildRules): List<AppEntry> = mergeApps(installed.orEmpty(), usage, rules)
}

/** Fetches the child's inventory once per [memberId] and keeps it for the screen's lifetime. */
@Composable
internal fun rememberAppCatalog(memberId: String, childAppsRemote: ChildAppsRemote): AppCatalog {
    var installed by remember(memberId) { mutableStateOf<List<ChildApp>?>(null) }
    var failed by remember(memberId) { mutableStateOf(false) }
    LaunchedEffect(memberId) {
        childAppsRemote.forChild(memberId)
            .onSuccess { installed = it }
            .onFailure { failed = true }
    }
    return remember(installed, failed) { AppCatalog(installed, failed) }
}

internal fun mergeApps(installed: List<ChildApp>, usage: List<UsageAppItem>, rules: ChildRules): List<AppEntry> {
    val today = usage.associateBy { it.packageName }
    val ruled = rules.appRules.keys + rules.quietHours.flatMap { it.packages }
    val byPackage = linkedMapOf<String, AppEntry>()
    installed.forEach { app ->
        byPackage[app.packageName] =
            AppEntry(
                packageName = app.packageName,
                label = app.label,
                todayMs = today[app.packageName]?.totalMs ?: 0L,
                isSystem = app.isSystem,
                used = app.packageName in today || app.packageName in ruled,
            )
    }
    usage.forEach { app ->
        if (app.packageName !in byPackage) {
            byPackage[app.packageName] = AppEntry(app.packageName, app.label, app.totalMs, isSystem = false)
        }
    }
    ruled.forEach { pkg ->
        if (pkg !in byPackage) byPackage[pkg] = AppEntry(pkg, fallbackLabel(pkg), 0L, isSystem = false)
    }
    return byPackage.values.toList()
}

/** «com.example.game» → «Game» while the phone has not reported a label yet. */
internal fun fallbackLabel(packageName: String): String = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

/** «1 приложение», «3 приложения», «5 приложений». */
internal fun pluralApps(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word =
        when {
            mod100 in 11..14 -> "приложений"
            mod10 == 1 -> "приложение"
            mod10 in 2..4 -> "приложения"
            else -> "приложений"
        }
    return "$count $word"
}
