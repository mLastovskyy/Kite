package app.kite.core.killswitch

/**
 * Snapshot for the in-app update check. Version codes are git commit counts (see the app
 * build scripts), so a plain greater-than comparison is safe.
 */
data class UpdateStatus(
    val currentVersionCode: Int,
    val latestVersionCode: Int,
    val message: String? = null,
    val latestVersionName: String? = null,
    /** Direct APK for this app + flavor, when the release publishes one. */
    val apkUrl: String? = null,
) {
    val updateAvailable: Boolean get() = latestVersionCode > currentVersionCode
}
