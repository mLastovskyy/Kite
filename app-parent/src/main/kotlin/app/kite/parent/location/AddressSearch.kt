package app.kite.parent.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.cos

/** One address suggestion for the place picker. */
data class AddressSuggestion(val title: String, val subtitle: String, val latitude: Double, val longitude: Double)

/**
 * Address → coordinates for «Добавить место»: Nominatim (OpenStreetMap) search, free, no key.
 * Its usage policy asks for low volume and an identified User-Agent, so callers debounce
 * (≥ 1 s between requests) and we never fire more than one request at a time. Never throws:
 * an empty list means «ничего не нашли» or offline.
 *
 * Results are LOCAL first (owner, 04.09.2026): «школа 33» typed by a family in Minsk must mean
 * the school in Minsk, not one in Moscow. With a reference point the first request is
 * restricted to a ~50 km box around it; only when that yields fewer than [MIN_LOCAL] hits is
 * a second, world-wide request made (still ranked towards the box), and its distinct results
 * are appended after the local ones.
 */
class AddressSearch(private val versionName: String, private val countryCode: String? = null) {
    suspend fun suggest(query: String, nearLatitude: Double? = null, nearLongitude: Double? = null): List<AddressSuggestion> {
        val q = query.trim()
        if (q.length < 3) return emptyList()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val viewbox = viewbox(nearLatitude, nearLongitude)
        return withContext(Dispatchers.IO) {
            val country = countryCode?.lowercase()?.takeIf { it.length == 2 }?.let { "&countrycodes=$it" }.orEmpty()
            if (viewbox == null) return@withContext fetch("$BASE&q=$encoded&limit=$LIMIT$country")
            val local = fetch("$BASE&q=$encoded&limit=$LIMIT$country&viewbox=$viewbox&bounded=1")
            if (local.size >= MIN_LOCAL) return@withContext local
            val nearby = fetch("$BASE&q=$encoded&limit=$LIMIT$country&viewbox=$viewbox")
            val merged = local + nearby.filter { candidate -> local.none { it.samePlace(candidate) } }
            if (merged.size >= MIN_LOCAL || country.isEmpty()) return@withContext merged.take(LIMIT)
            val wide = fetch("$BASE&q=$encoded&limit=$LIMIT&viewbox=$viewbox")
            (merged + wide.filter { candidate -> merged.none { it.samePlace(candidate) } }).take(LIMIT)
        }
    }

    /** Nominatim `viewbox` = lon1,lat1,lon2,lat2 — about ±50 km around the reference point. */
    private fun viewbox(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        val dLat = HALF_SPAN_DEG
        val dLon = HALF_SPAN_DEG / cos(Math.toRadians(latitude)).coerceAtLeast(0.2)
        return "${longitude - dLon},${latitude + dLat},${longitude + dLon},${latitude - dLat}"
    }

    private fun fetch(url: String): List<AddressSuggestion> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Kite/$versionName (parental control; Android)")
        }
        try {
            if (connection.responseCode != 200) return@runCatching emptyList()
            parse(JSONArray(connection.inputStream.bufferedReader().readText()))
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())

    private fun parse(array: JSONArray): List<AddressSuggestion> = (0 until array.length()).mapNotNull { i ->
        val item = array.getJSONObject(i)
        val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
        val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
        val display = item.optString("display_name")
        val address = item.optJSONObject("address")
        val street =
            listOfNotNull(
                address?.optString("road")?.ifBlank { null },
                address?.optString("house_number")?.ifBlank { null },
            ).joinToString(" ")
        val city =
            address?.let { a ->
                listOf("city", "town", "village", "municipality", "state").firstNotNullOfOrNull { k ->
                    a.optString(k).ifBlank { null }
                }
            }
        val title = item.optString("name").ifBlank { null } ?: street.ifBlank { null } ?: display.substringBefore(",")
        val subtitle = listOfNotNull(
            street.ifBlank {
                null
            }?.takeIf { it != title },
            city,
        ).joinToString(", ").ifBlank { display.take(80) }
        AddressSuggestion(title = title, subtitle = subtitle, latitude = lat, longitude = lon)
    }

    private fun AddressSuggestion.samePlace(other: AddressSuggestion): Boolean =
        abs(latitude - other.latitude) < SAME_PLACE_DEG && abs(longitude - other.longitude) < SAME_PLACE_DEG

    private companion object {
        const val BASE = "https://nominatim.openstreetmap.org/search?format=jsonv2&accept-language=ru&addressdetails=1"
        const val LIMIT = 6
        const val MIN_LOCAL = 3

        /** Half the side of the local box, degrees of latitude (~50 km). */
        const val HALF_SPAN_DEG = 0.45

        /** Two hits closer than this (~10 m) are the same place. */
        const val SAME_PLACE_DEG = 0.0001
    }
}
