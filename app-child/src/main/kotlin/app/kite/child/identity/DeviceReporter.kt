package app.kite.child.identity

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import app.kite.child.enforce.RemoteLock
import app.kite.child.permissions.ProtectionInspector
import app.kite.child.permissions.ProtectionRequirement
import app.kite.child.permissions.WizardStateStore
import app.kite.core.diagnostics.CrashReport
import app.kite.core.diagnostics.CrashReportSync
import app.kite.core.family.ChildDevice
import app.kite.core.family.ChildDeviceRemote
import app.kite.core.platform.PlatformServices
import kotlinx.coroutines.flow.first
import java.time.Instant

class DeviceReporter(
    private val context: Context,
    private val identity: MemberIdentity,
    private val remote: ChildDeviceRemote,
    private val platformServices: PlatformServices,
    private val remoteLock: RemoteLock,
    private val crashSync: CrashReportSync,
) {
    private val inspector by lazy { ProtectionInspector(context) }
    private val wizardState by lazy { WizardStateStore(context) }

    suspend fun report(): Result<Unit> {
        val familyId = identity.familyId() ?: return Result.success(Unit)
        val memberId = identity.memberId() ?: return Result.success(Unit)
        val device =
            ChildDevice(
                memberId = memberId,
                familyId = familyId,
                platform = "android",
                services = platformServices.variant.name.lowercase(),
                model = deviceModel(),
                osVersion = "Android ${Build.VERSION.RELEASE}",
                appVersionCode = versionCode(),
                protectionMissing = missingRequirements(),
                batteryPct = batteryPercent(),
                locked = remoteLock.locked,
                lastSeenAt = Instant.now().toString(),
            )
        // A crash on this phone is part of what the parent needs to know about it.
        runCatching { crashSync.push(familyId, memberId, CrashReport.APP_CHILD, identity.displayName()) }
        return remote.report(device)
    }

    /**
     * Exactly what «Здоровье защиты» shows the child — the same inspector, the same list. It used
     * to add an item of its own for the location switch, which the child's screen knew nothing
     * about, so the parent read «не защищено» while the child read «всё готово» (owner,
     * 09.09.2026). LOCATION_SERVICES is a requirement like any other now.
     */
    private suspend fun missingRequirements(): List<String> {
        val autostartConfirmed = runCatching { wizardState.vendorAutostartConfirmed.first() }.getOrDefault(false)
        return inspector.requirements
            .filterNot { inspector.isSatisfied(it, autostartConfirmed) }
            .map { it.name }
    }

    /** Same reading the location service sends, but this one goes up without a fix. */
    private fun batteryPercent(): Int? = (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }

    private fun versionCode(): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    companion object {

        fun deviceModel(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        }

        fun requirementTitle(name: String): String = runCatching { ProtectionRequirement.valueOf(name).title }.getOrDefault(name)
    }
}
