package app.kite.core.appearance

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/** How the app picks light or dark colours. [SYSTEM] follows the phone; the others pin it. */
enum class ThemeMode(val label: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

/** Light or dark for this mode right now. [SYSTEM] asks the phone; the others ignore it. */
fun ThemeMode.isDark(context: Context): Boolean = when (this) {
    ThemeMode.SYSTEM ->
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Appearance settings, persisted locally (DataStore) — a device preference, never synced.
 * Read on every launch before the first frame, so there is no flash of the wrong theme.
 *
 * The choice is mirrored into plain prefs as well: the block screen is a raw system window
 * built outside Compose, off the main thread, and it cannot wait for a DataStore read.
 */
class AppearanceRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.appearanceDataStore,
) {
    private val mirror = context.applicationContext.getSharedPreferences("appearance_mirror", Context.MODE_PRIVATE)

    val themeMode: Flow<ThemeMode> =
        dataStore.data
            .map { prefs -> prefs[KEY_THEME]?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.SYSTEM }
            .distinctUntilChanged()
            .onEach { mode -> mirror.edit().putString(KEY_MIRROR, mode.name).apply() }

    /** Last known choice, without a coroutine. */
    fun currentMode(): ThemeMode =
        mirror.getString(KEY_MIRROR, null)?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.SYSTEM

    suspend fun setThemeMode(mode: ThemeMode) {
        mirror.edit().putString(KEY_MIRROR, mode.name).apply()
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        const val KEY_MIRROR = "theme_mode"
    }
}

/**
 * The child's own choice, for every window the app opens — a dialog or an Activity started
 * from the enforcement service must look like the app the child themselves set up, not like
 * whatever the system theme happens to be.
 */
@Composable
fun AppearanceRepository.isDarkTheme(): Boolean {
    val context = LocalContext.current
    val initial = remember { currentMode() }
    val mode by themeMode.collectAsState(initial = initial)
    return if (mode == ThemeMode.SYSTEM) isSystemInDarkTheme() else mode.isDark(context)
}
