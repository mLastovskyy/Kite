package app.kite.core.killswitch

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.killSwitchDataStore by preferencesDataStore(name = "kill_switch")

/**
 * Client side of the kill switch. Fetches `update.json`, persists the last successfully
 * parsed manifest and exposes it as a flow.
 *
 * Failure semantics matter here: a failed fetch NEVER clears stored state, so offline
 * devices keep the last known manifest, and a fresh install behaves as "enforcement on"
 * until the first successful fetch.
 */
class KillSwitchRepository(
    context: Context,
    private val httpClient: HttpClient,
    private val json: Json,
    private val updateUrl: String = DEFAULT_UPDATE_URL,
) {
    private val dataStore: DataStore<Preferences> = context.applicationContext.killSwitchDataStore

    val manifest: Flow<UpdateManifest> =
        dataStore.data.map { prefs ->
            prefs[KEY_MANIFEST]
                ?.let { raw -> runCatching { json.decodeFromString<UpdateManifest>(raw) }.getOrNull() }
                ?: UpdateManifest()
        }

    /** True → the child app must stop blocking apps and lift all locks, keeping reporting alive. */
    val disableEnforcement: Flow<Boolean> = manifest.map { it.disableEnforcement }.distinctUntilChanged()

    suspend fun refresh(): Result<UpdateManifest> = runCatching {
        val body = httpClient.get(updateUrl).bodyAsText()
        val parsed = json.decodeFromString<UpdateManifest>(body)
        val normalized = json.encodeToString(UpdateManifest.serializer(), parsed)
        dataStore.edit { prefs -> prefs[KEY_MANIFEST] = normalized }
        parsed
    }.onFailure { Log.w(TAG, "update.json refresh failed, keeping last known manifest", it) }

    companion object {
        /**
         * Placeholder until the release repository exists (M1 constraint: no real backend).
         * The shape is final: a GitHub Release asset, editable without shipping a build.
         */
        const val DEFAULT_UPDATE_URL = "https://github.com/kite-parental/releases/releases/latest/download/update.json"

        private val KEY_MANIFEST = stringPreferencesKey("manifest_json")
        private const val TAG = "KillSwitch"
    }
}
