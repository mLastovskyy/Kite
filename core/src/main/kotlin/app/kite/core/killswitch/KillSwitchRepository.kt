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
    private val currentVersionCode: Int = 0,
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

    /** In-app update check; both apps surface this in their UI. */
    val updateStatus: Flow<UpdateStatus> =
        manifest.map { UpdateStatus(currentVersionCode, it.latestVersionCode, it.message) }.distinctUntilChanged()

    suspend fun refresh(): Result<UpdateManifest> = runCatching {
        val body = httpClient.get(updateUrl).bodyAsText()
        val parsed = json.decodeFromString<UpdateManifest>(body)
        val normalized = json.encodeToString(UpdateManifest.serializer(), parsed)
        dataStore.edit { prefs -> prefs[KEY_MANIFEST] = normalized }
        parsed
    }.onFailure { Log.w(TAG, "update.json refresh failed, keeping last known manifest", it) }

    companion object {
        /**
         * update.json is attached to every GitHub Release by the release workflow;
         * "latest/download" always resolves to the newest release. Editing the asset of an
         * existing release is enough to disarm every client without shipping a build.
         */
        const val DEFAULT_UPDATE_URL = "https://github.com/mLastovskyy/Kite/releases/latest/download/update.json"

        /** Where «Скачать обновление» sends the user until the in-app installer (hms) / Play In-App Updates (gms) land. */
        const val RELEASES_PAGE_URL = "https://github.com/mLastovskyy/Kite/releases/latest"

        private val KEY_MANIFEST = stringPreferencesKey("manifest_json")
        private const val TAG = "KillSwitch"
    }
}
