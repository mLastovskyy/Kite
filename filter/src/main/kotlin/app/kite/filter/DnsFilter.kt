package app.kite.filter

/**
 * DNS filtering module placeholder — implementation arrives in M8.
 *
 * The plan of record (CLAUDE.md): our own VpnService intercepting DNS on UDP/TCP port 53
 * only — deliberately NOT a full TUN proxy with TCP parsing — with Bloom-filter blocklists,
 * an upstream family DNS, custom allow/block lists and forced Safe Search.
 *
 * Kept as a separate module from day one so enforcement code in :app-child never links
 * against VPN internals.
 */
object DnsFilter {
    /** Flips to true in M8 when the VpnService lands. */
    const val IMPLEMENTED = false
}
