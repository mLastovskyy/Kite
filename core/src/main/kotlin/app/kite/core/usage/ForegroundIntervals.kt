package app.kite.core.usage

/**
 * Turns a usage-event stream into foreground intervals. The model is single-foreground:
 * one app is "current" at a time — split-screen mis-attributes the second app, which is an
 * accepted trade-off (same as Digital Wellbeing's headline numbers).
 *
 * Pure Kotlin so the pairing rules are unit-tested; the Android collector only converts
 * `UsageEvents.Event` into [Ev] values.
 */
object ForegroundIntervals {
    enum class Kind { Resumed, Paused, ScreenOff }

    data class Ev(val kind: Kind, val packageName: String?, val timestamp: Long)

    data class Interval(val packageName: String, val startMs: Long, val endMs: Long)

    /** An app still in the foreground when collection ran; carried into the next run. */
    data class Carry(val packageName: String, val since: Long)

    data class Outcome(val intervals: List<Interval>, val carry: Carry?)

    /**
     * [events] must be time-ordered (UsageStatsManager returns them that way). [initial]
     * is the carry from the previous run. An interval still open at [endAt] is closed
     * there and returned as the new carry, so no time is lost between runs.
     */
    fun reduce(events: List<Ev>, initial: Carry?, endAt: Long): Outcome {
        val intervals = mutableListOf<Interval>()
        var current = initial

        fun close(at: Long) {
            val open = current ?: return
            if (at > open.since) intervals += Interval(open.packageName, open.since, at)
            current = null
        }

        for (event in events) {
            when (event.kind) {
                Kind.Resumed -> {
                    val pkg = event.packageName ?: continue
                    if (current?.packageName == pkg) continue // duplicate RESUMED, keep the start
                    close(event.timestamp)
                    current = Carry(pkg, event.timestamp)
                }
                Kind.Paused -> if (current?.packageName == event.packageName) close(event.timestamp)
                Kind.ScreenOff -> close(event.timestamp)
            }
        }

        val carry = current?.let { Carry(it.packageName, endAt) }
        close(endAt)
        return Outcome(intervals, carry)
    }
}
