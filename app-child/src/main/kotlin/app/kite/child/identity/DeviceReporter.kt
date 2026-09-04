package app.kite.child.identity

import android.content.Context
import android.location.LocationManager
import android.os.Build
import app.kite.child.permissions.ProtectionInspector
import app.kite.child.permissions.ProtectionRequirement
import app.kite.child.permissions.WizardStateStore
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
                lastSeenAt = Instant.now().toString(),
            )
        return remote.report(device)
    }

    private suspend fun missingRequirements(): List<String> {
        val autostartConfirmed = runCatching { wizardState.vendorAutostartConfirmed.first() }.getOrDefault(false)
        val missing =
            inspector.requirements
                .filterNot { inspector.isSatisfied(it, autostartConfirmed) }
                .map { it.name }
        return if (locationServicesOff()) missing + LOCATION_SERVICES_OFF else missing
    }

    private fun locationServicesOff(): Boolean = runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

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
        const val LOCATION_SERVICES_OFF = "LOCATION_SERVICES_OFF"

        fun deviceModel(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        }

        fun requirementTitle(name: String): String = runCatching { ProtectionRequirement.valueOf(name).title }.getOrDefault(name)
    }
}
