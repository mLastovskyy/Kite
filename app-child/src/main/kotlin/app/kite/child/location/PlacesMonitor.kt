package app.kite.child.location

import android.content.Context
import app.kite.child.identity.MemberIdentity
import app.kite.core.family.FamilyRepository
import app.kite.core.location.Place
import app.kite.core.location.PlaceEvent
import app.kite.core.location.PlacesRemote
import app.kite.core.notifications.Channels
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

@Serializable
data class PendingPlaceEvent(
    val placeId: String,
    val familyId: String,
    val placeName: String,
    val kind: String,
    val atMs: Long,
    val notify: Boolean,
)

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

class PlacesStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)

    fun places(): List<Place> = prefs.getString(KEY_PLACES, null)
        ?.let { raw -> runCatching { json.decodeFromString(ListSerializer(Place.serializer()), raw) }.getOrNull() }
        ?: emptyList()

    fun save(places: List<Place>) {
        val ids = places.map { it.id }.toSet()
        val editor = prefs.edit().putString(KEY_PLACES, json.encodeToString(ListSerializer(Place.serializer()), places))
        prefs.all.keys
            .filter { key -> isStaleStateKey(key, ids) }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun isStaleStateKey(key: String, ids: Set<String>): Boolean =
        key.startsWith(PREFIX_LEGACY_INSIDE) || key.startsWith(PREFIX_STATE) && key.removePrefix(PREFIX_STATE) !in ids

    fun state(placeId: String): PlaceState = prefs.getString(PREFIX_STATE + placeId, null)
        ?.let { raw -> runCatching { json.decodeFromString(PlaceState.serializer(), raw) }.getOrNull() }
        ?: PlaceState()

    fun setState(placeId: String, state: PlaceState) {
        prefs.edit().putString(PREFIX_STATE + placeId, json.encodeToString(PlaceState.serializer(), state)).apply()
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

    fun childName(): String? = prefs.getString(KEY_CHILD_NAME, null)

    fun setChildName(name: String) {
        prefs.edit().putString(KEY_CHILD_NAME, name).apply()
    }

    private companion object {
        const val KEY_PLACES = "places_json"
        const val KEY_PENDING = "pending_events"
        const val KEY_CHILD_NAME = "child_name"
        const val PREFIX_STATE = "state|"
        const val PREFIX_LEGACY_INSIDE = "inside|"
        const val MAX_PENDING = 50
    }
}

class PlacesMonitor(
    private val store: PlacesStore,
    private val remote: PlacesRemote,
    private val identity: MemberIdentity,
    private val familyRepository: FamilyRepository,
) {
    private val stateLock = Mutex()
    private val queueLock = Mutex()

    fun metersToNearestBoundary(latitude: Double, longitude: Double): Double? = store.places()
        .map { place -> haversineMeters(latitude, longitude, place.latitude, place.longitude) - place.radiusM }
        .minOrNull()

    suspend fun refresh() {
        val memberId = identity.memberId() ?: return
        remote.forChild(memberId).getOrNull()?.let(store::save)
    }

    suspend fun onFix(fix: AcceptedFix, online: Boolean) = stateLock.withLock {
        val now = System.currentTimeMillis()
        store.places().forEach { place ->
            val before = store.state(place.id)
            apply(place, before, PlaceTransitions.onFix(before, place, fix, now), online)
        }
    }

    suspend fun settle(online: Boolean) = stateLock.withLock {
        val now = System.currentTimeMillis()
        store.places().forEach { place ->
            val before = store.state(place.id)
            apply(place, before, PlaceTransitions.settle(before, now), online)
        }
    }

    fun nextDeadline(): Long? = store.places().mapNotNull { store.state(it.id).deadline }.minOrNull()

    private suspend fun apply(place: Place, before: PlaceState, step: PlaceStep, online: Boolean) {
        if (step.state != before) store.setState(place.id, step.state)
        val entered = step.entered ?: return
        val event =
            PendingPlaceEvent(
                placeId = place.id,
                familyId = place.familyId,
                placeName = place.name,
                kind = if (entered) PlaceEvent.KIND_ENTER else PlaceEvent.KIND_EXIT,
                atMs = step.at,
                notify = if (entered) place.notifyEnter else place.notifyExit,
            )
        queueLock.withLock { if (!online || !send(event)) store.queue(event) }
    }

    suspend fun flushQueue() = queueLock.withLock {
        val queued = store.pending()
        if (queued.isNotEmpty()) store.savePending(queued.filterNot { send(it) })
    }

    private suspend fun send(event: PendingPlaceEvent): Boolean {
        val memberId = identity.memberId() ?: return false
        val at = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(event.atMs))
        if (remote.reportEvent(event.familyId, memberId, event.placeId, event.kind, at).isFailure) return false
        if (event.notify) notifyParents(event)
        return true
    }

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
        val data = buildMap {
            put("action", ACTION_PLACE_EVENT)
            myMemberId?.let { put("child_member_id", it) }
        }
        members.filter { it.isParent }.forEach { parent ->
            remote.push(
                targetUserId = parent.userId,
                data = data,
                title = "$childName: ${event.placeName}",
                body = body,
                channel = Channels.ALERTS,
            )
        }
    }

    private companion object {
        const val ACTION_PLACE_EVENT = "place_event"
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
