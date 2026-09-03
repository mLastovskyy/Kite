package app.kite.parent.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One address suggestion for the place picker. */
data class AddressSuggestion(val title: String, val subtitle: String, val latitude: Double, val longitude: Double)

/**
 * Address → coordinates for «Добавить место»: Nominatim (OpenStreetMap) search, free, no key.
 * Its usage policy asks for low volume and an identified User-Agent, so callers debounce
 * (≥ 1 s between requests) and we never fire more than one request at a time. Never throws:
 * an empty list means «ничего не нашли» or offline.
 */
class AddressSearch(private val versionName: String) {
    suspend fun suggest(query: String): List<AddressSuggestion> {
        val q = query.trim()
        if (q.length < 3) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url =
                    URL("https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=6&accept-language=ru&addressdetails=1")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", "Kite/$versionName (parental control; Android)")
                }
                try {
                    if (connection.responseCode != 200) return@runCatching emptyList()
                    val array = JSONArray(connection.inputStream.bufferedReader().readText())
                    (0 until array.length()).mapNotNull { i ->
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
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }
    }
}
