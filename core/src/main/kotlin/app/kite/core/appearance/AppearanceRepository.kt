package app.kite.core.appearance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/** How the app picks light or dark colours. [SYSTEM] follows the phone; the others pin it. */
enum class ThemeMode(val label: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

/**
 * Appearance settings, persisted locally (DataStore) — a device preference, never synced.
 * Read on every launch before the first frame, so there is no flash of the wrong theme.
 */
class AppearanceRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.appearanceDataStore,
) {
    val themeMode: Flow<ThemeMode> =
        dataStore.data
            .map { prefs -> prefs[KEY_THEME]?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } } ?: ThemeMode.SYSTEM }
            .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
    }
}
