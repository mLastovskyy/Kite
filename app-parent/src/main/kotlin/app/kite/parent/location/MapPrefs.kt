package app.kite.parent.location

import android.content.Context

/**
 * Which map the parent looks at (Kids360 lets you switch providers). In-app rendering is
 * always MapLibre + OpenFreeMap (no key, works on Huawei); the choice is the style. External
 * apps are offered separately from «Открыть в…» and need no setting — links work whether or
 * not the app is installed (they fall back to the web).
 */
enum class MapStyle(val id: String, val label: String, val url: String) {
    LIBERTY("liberty", "Стандарт", "https://tiles.openfreemap.org/styles/liberty"),
    BRIGHT("bright", "Яркая", "https://tiles.openfreemap.org/styles/bright"),
    POSITRON("positron", "Светлая", "https://tiles.openfreemap.org/styles/positron"),
    ;

    companion object {
        fun byId(id: String?): MapStyle = entries.firstOrNull { it.id == id } ?: LIBERTY
    }
}

class MapPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("map", Context.MODE_PRIVATE)

    fun style(): MapStyle = MapStyle.byId(prefs.getString(KEY_STYLE, null))

    fun setStyle(style: MapStyle) {
        prefs.edit().putString(KEY_STYLE, style.id).apply()
    }

    private companion object {
        const val KEY_STYLE = "style"
    }
}

/** External map apps for «Открыть в…»: https links open the app when installed, the site otherwise. */
enum class ExternalMap(val label: String) {
    GOOGLE("Google Карты"),
    YANDEX("Яндекс Карты"),
    DGIS("2ГИС"),
    SYSTEM("Другое приложение"),
    ;

    fun uri(latitude: Double, longitude: Double, label: String): String = when (this) {
        GOOGLE -> "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        YANDEX -> "https://yandex.ru/maps/?pt=$longitude,$latitude&z=16&l=map"
        DGIS -> "https://2gis.ru/geo/$longitude,$latitude"
        SYSTEM -> "geo:$latitude,$longitude?q=$latitude,$longitude(${label.replace("(", "").replace(")", "")})"
    }
}
