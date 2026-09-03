package app.kite.parent.location

/**
 * In-app rendering is always MapLibre + OpenFreeMap (no key, works on Huawei). One calm
 * style, not a switch: the owner asked for a quiet map with the child on it, and for the
 * choice of *map app* to live behind one button, Kids360-style — see [ExternalMap].
 */
enum class MapStyle(val id: String, val url: String) {
    LIBERTY("liberty", "https://tiles.openfreemap.org/styles/liberty"),
    POSITRON("positron", "https://tiles.openfreemap.org/styles/positron"),
    ;

    companion object {
        /** The look used everywhere: light grey base, muted colours, roads and labels only. */
        val DEFAULT = POSITRON
    }
}

/**
 * The three map apps offered from the map's «Открыть в» button: Google, Яндекс and whatever
 * the phone treats as its maps app (`geo:` — on Huawei that is Petal Maps). https links open
 * the app when installed and the site otherwise. [satellite] switches Google and Яндекс to
 * imagery (Maps URLs `basemap=satellite`, Яндекс `l=sat`); the `geo:` scheme has no such flag.
 */
enum class ExternalMap(val label: String) {
    GOOGLE("Google Карты"),
    YANDEX("Яндекс Карты"),
    SYSTEM("Карты телефона"),
    ;

    fun uri(latitude: Double, longitude: Double, label: String, satellite: Boolean = false): String = when (this) {
        GOOGLE ->
            if (satellite) {
                "https://www.google.com/maps/@?api=1&map_action=map&center=$latitude,$longitude&zoom=17&basemap=satellite"
            } else {
                "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
            }
        YANDEX -> "https://yandex.ru/maps/?pt=$longitude,$latitude&z=16&l=${if (satellite) "sat" else "map"}"
        SYSTEM -> "geo:$latitude,$longitude?q=$latitude,$longitude(${label.replace("(", "").replace(")", "")})"
    }
}
