package app.kite.child.location

import android.content.Context
import app.kite.child.identity.MemberIdentity
import app.kite.core.family.FamilyRepository
import app.kite.core.location.Place
import app.kite.core.location.PlaceEvent
import app.kite.core.location.PlacesRemote
import app.kite.core.notifications.Channels
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** An enter/exit that has happened but has not reached the server yet (offline queue). */
@Serializable
data class PendingPlaceEvent(
    val placeId: String,
    val familyId: String,
    val placeName: String,
    val kind: String,
    val atMs: Long,
    val notify: Boolean,
)

/**
 * Great-circle distance in metres. Plain trigonometry rather than `Location.distanceBetween`
 * so the rule is unit-tested and identical on every flavor.
 */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Cached places and the inside/outside state per place. Both live in plain prefs: the child
 * may inspect its own places (transparency) and the state has to survive a reboot, or every
 * restart would re-fire «пришёл домой» for wherever the phone already is.
 */
class PlacesStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)

    fun places(): List<Place> = prefs.getString(KEY_PLACES, null)
        ?.let { raw -> runCatching { json.decodeFromString(ListSerializer(Place.serializer()), raw) }.getOrNull() }
        ?: emptyList()

    /** Replaces the cache and forgets the state of places the parent deleted. */
    fun save(places: List<Place>) {
        val ids = places.map { it.id }.toSet()
        val editor = prefs.edit().putString(KEY_PLACES, json.encodeToString(ListSerializer(Place.serializer()), places))
        prefs.all.keys.filter { it.startsWith(PREFIX_INSIDE) }
            .filterNot { it.removePrefix(PREFIX_INSIDE) in ids }
            .forEach(editor::remove)
        editor.apply()
    }

    /** null = never evaluated, so the first fix seeds the state instead of reporting a visit. */
    fun insideOrNull(placeId: String): Boolean? =
        if (prefs.contains(PREFIX_INSIDE + placeId)) prefs.getBoolean(PREFIX_INSIDE + placeId, false) else null

    fun setInside(placeId: String, inside: Boolean) {
        prefs.edit().putBoolean(PREFIX_INSIDE + placeId, inside).apply()
    }

    fun pending(): List<PendingPlaceEvent> = prefs.getString(KEY_PENDING, null)
        ?.let { raw -> runCatching { json.decodeFromString(ListSerializer(PendingPlaceEvent.serializer()), raw) }.getOrNull() }
        ?: emptyList()

    fun queue(event: PendingPlaceEvent) {
        savePending((pending() + event).takeLast(MAX_PENDING))
    }

    fun savePending(events: List<PendingPlaceEvent>) {
        prefs.edit().putString(KEY_PENDING, json.encodeToString(ListSerializer(PendingPlaceEvent.serializer()), events)).apply()
    }

    /** Cached so an offline-queued event can still name the child when it finally goes out. */
    fun childName(): String? = prefs.getString(KEY_CHILD_NAME, null)

    fun setChildName(name: String) {
        prefs.edit().putString(KEY_CHILD_NAME, name).apply()
    }

    private companion object {
        const val KEY_PLACES = "places_json"
        const val KEY_PENDING = "pending_events"
        const val KEY_CHILD_NAME = "child_name"
        const val PREFIX_INSIDE = "inside|"
        const val MAX_PENDING = 50
    }
}

/**
 * «Места»: our own radius check on every fix, on every flavor — no GeofencingClient, so the
 * behaviour is identical with and without GMS and keeps working offline (CLAUDE.md lists
 * exactly this as the AOSP fallback for geofences; we use it everywhere on purpose).
 *
 * Hysteresis matters more than the radius here: a fix that jitters around the boundary would
 * otherwise spam the parent with enter/exit pairs. Entering needs the fix to be inside the
 * radius; leaving needs it to be outside the radius PLUS the accuracy of that fix.
 */
class PlacesMonitor(
    private val store: PlacesStore,
    private val remote: PlacesRemote,
    private val identity: MemberIdentity,
    private val familyRepository: FamilyRepository,
) {
    /** Pulls the parent's current list; failures keep the cached copy. */
    suspend fun refresh() {
        val memberId = identity.memberId() ?: return
        remote.forChild(memberId).getOrNull()?.let(store::save)
    }

    /** Evaluates every place against one fix and reports the transitions it finds. */
    suspend fun onFix(latitude: Double, longitude: Double, accuracyM: Float?, online: Boolean) {
        val places = store.places()
        if (places.isEmpty()) return
        val now = System.currentTimeMillis()
        places.forEach { place ->
            val distance = haversineMeters(latitude, longitude, place.latitude, place.longitude)
            val exitMargin = maxOf(EXIT_MARGIN_M, (accuracyM ?: 0f).toDouble())
            val wasInside = store.insideOrNull(place.id)
            val inside =
                when {
                    distance < place.radiusM -> true
                    distance > place.radiusM + exitMargin -> false
                    // In the fuzzy ring between the two thresholds nothing changes.
                    else -> return@forEach
                }
            if (wasInside == inside) return@forEach
            store.setInside(place.id, inside)
            // The very first evaluation only seeds the state: the phone being at home when
            // the place is created is not an arrival.
            if (wasInside == null) return@forEach
            val kind = if (inside) PlaceEvent.KIND_ENTER else PlaceEvent.KIND_EXIT
            val notify = if (inside) place.notifyEnter else place.notifyExit
            val event = PendingPlaceEvent(place.id, place.familyId, place.name, kind, now, notify)
            if (!online || !send(event)) store.queue(event)
        }
    }

    /** Sends what was queued while offline, oldest first; whatever fails stays queued. */
    suspend fun flushQueue() {
        val queued = store.pending()
        if (queued.isEmpty()) return
        val failed = queued.filterNot { send(it) }
        store.savePending(failed)
    }

    private suspend fun send(event: PendingPlaceEvent): Boolean {
        val memberId = identity.memberId() ?: return false
        if (remote.reportEvent(event.familyId, memberId, event.placeId, event.kind).isFailure) return false
        if (event.notify) notifyParents(event)
        return true
    }

    /** One notification per parent device set, on the alerts channel. Best-effort. */
    private suspend fun notifyParents(event: PendingPlaceEvent) {
        val members = familyRepository.members(event.familyId).getOrNull() ?: return
        val myMemberId = identity.memberId()
        val childName =
            members.firstOrNull { it.id == myMemberId }?.displayName?.takeIf { it.isNotBlank() }
                ?: store.childName()
                ?: "Ребёнок"
        store.setChildName(childName)
        val time = TIME_FORMAT.format(Instant.ofEpochMilli(event.atMs).atZone(ZoneId.systemDefault()))
        val body = if (event.kind == PlaceEvent.KIND_ENTER) "Прибытие в $time" else "Уход в $time"
        members.filter { it.isParent }.forEach { parent ->
            remote.push(
                targetUserId = parent.userId,
                data = mapOf("action" to ACTION_PLACE_EVENT),
                title = "$childName: ${event.placeName}",
                body = body,
                channel = Channels.ALERTS,
            )
        }
    }

    private companion object {
        /** Extra metres a fix must be beyond the radius before we call it an exit. */
        const val EXIT_MARGIN_M = 50.0
        const val ACTION_PLACE_EVENT = "place_event"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
