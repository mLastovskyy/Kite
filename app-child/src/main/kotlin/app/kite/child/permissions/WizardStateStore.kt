package app.kite.child.permissions

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wizardDataStore by preferencesDataStore(name = "onboarding_wizard")

/**
 * Wizard progress that cannot be derived from system state: the manual vendor-autostart
 * confirmation and the «настроить позже» postponement. Everything else is re-checked live
 * so the wizard resumes exactly from the first unsatisfied step.
 */
class WizardStateStore(context: Context) {
    private val dataStore: DataStore<Preferences> = context.applicationContext.wizardDataStore

    val vendorAutostartConfirmed: Flow<Boolean> = dataStore.data.map { it[KEY_VENDOR_CONFIRMED] ?: false }
    val wizardPostponed: Flow<Boolean> = dataStore.data.map { it[KEY_POSTPONED] ?: false }
    val wizardSeen: Flow<Boolean> = dataStore.data.map { it[KEY_SEEN] ?: false }

    suspend fun markWizardSeen() {
        dataStore.edit { it[KEY_SEEN] = true }
    }

    suspend fun confirmVendorAutostart() {
        dataStore.edit { it[KEY_VENDOR_CONFIRMED] = true }
    }

    suspend fun setPostponed(postponed: Boolean) {
        dataStore.edit { it[KEY_POSTPONED] = postponed }
    }

    private companion object {
        val KEY_VENDOR_CONFIRMED = booleanPreferencesKey("vendor_autostart_confirmed")
        val KEY_POSTPONED = booleanPreferencesKey("wizard_postponed")
        val KEY_SEEN = booleanPreferencesKey("wizard_seen")
    }
}
