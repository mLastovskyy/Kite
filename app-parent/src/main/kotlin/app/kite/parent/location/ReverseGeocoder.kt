package app.kite.parent.location

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Coordinates → a short human address for the map card. Two backends, both free:
 * the platform [Geocoder] (Google-backed on GMS phones; usually empty on Huawei), then
 * Nominatim (OpenStreetMap; low volume, one request at a time, identified User-Agent, as its
 * usage policy asks). Results are cached per ~10 m cell so re-opening the tab costs nothing.
 * Never throws: null means «адрес не определён».
 */
class ReverseGeocoder(private val context: Context, private val versionName: String) {
    private val cache = HashMap<String, String?>()

    suspend fun address(latitude: Double, longitude: Double): String? {
        val key = "${(latitude * 10_000).roundToInt()}:${(longitude * 10_000).roundToInt()}"
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null
        val result = withContext(Dispatchers.IO) { platform(latitude, longitude) ?: nominatim(latitude, longitude) }
        cache[key] = result
        return result
    }

    /**
     * Just the city/town for a point («Минск») — shown next to the address field so the parent
     * sees which area the suggestions are drawn from. Same two backends, its own cache cell.
     */
    suspend fun city(latitude: Double, longitude: Double): String? {
        val key = "city:${(latitude * 100).roundToInt()}:${(longitude * 100).roundToInt()}"
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null
        val result = withContext(Dispatchers.IO) { platformCity(latitude, longitude) ?: nominatimCity(latitude, longitude) }
        cache[key] = result
        return result
    }

    @Suppress("DEPRECATION")
    private fun platformCity(latitude: Double, longitude: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(context, Locale("ru")).getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()?.let { a ->
            a.locality?.ifBlank { null } ?: a.subAdminArea?.ifBlank { null } ?: a.adminArea?.ifBlank { null }
        }
    }.getOrNull()

    private fun nominatimCity(latitude: Double, longitude: Double): String? = runCatching {
        // zoom=10 answers at city level; no street noise to strip.
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$latitude&lon=$longitude&zoom=10&accept-language=ru")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Kite/$versionName (parental control; Android)")
        }
        try {
            if (connection.responseCode != 200) return null
            val address = JSONObject(connection.inputStream.bufferedReader().readText()).optJSONObject("address")
            address?.let { a ->
                listOf("city", "town", "village", "municipality", "county", "state")
                    .firstNotNullOfOrNull { k -> a.optString(k).ifBlank { null } }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun platform(latitude: Double, longitude: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        // The blocking overload is deprecated on 33+ but still functional; we are on an IO thread.
        val found = Geocoder(context, Locale("ru")).getFromLocation(latitude, longitude, 1).orEmpty()
        found.firstOrNull()?.let { a ->
            listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ").ifBlank { null }
                ?.let { street -> listOfNotNull(street, a.locality).joinToString(", ") }
                ?: a.getAddressLine(0)
        }
    }.getOrNull()

    private fun nominatim(latitude: Double, longitude: Double): String? = runCatching {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$latitude&lon=$longitude&zoom=18&accept-language=ru")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Kite/$versionName (parental control; Android)")
        }
        try {
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val address = json.optJSONObject("address")
            val street = listOfNotNull(
                address?.optString("road")?.ifBlank {
                    null
                },
                address?.optString("house_number")?.ifBlank { null },
            ).joinToString(" ")
            val city = address?.let { a ->
                listOf("city", "town", "village", "municipality").firstNotNullOfOrNull { k -> a.optString(k).ifBlank { null } }
            }
            listOfNotNull(street.ifBlank { null }, city).joinToString(", ").ifBlank { null }
                ?: json.optString("display_name").ifBlank { null }?.substringBefore(", Россия")?.take(80)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
