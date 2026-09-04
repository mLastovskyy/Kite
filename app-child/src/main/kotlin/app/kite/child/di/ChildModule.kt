package app.kite.child.di

import androidx.room.Room
import app.kite.child.apps.InstalledAppsPublisher
import app.kite.child.enforce.BlockOverlay
import app.kite.child.enforce.BonusStore
import app.kite.child.enforce.EnforcementController
import app.kite.child.enforce.GuardOverlay
import app.kite.child.enforce.OfflineTimeGrant
import app.kite.child.enforce.ProtectionState
import app.kite.child.enforce.RemoteLock
import app.kite.child.enforce.RulesStore
import app.kite.child.enforce.RulesSyncer
import app.kite.child.enforce.UninstallGuard
import app.kite.child.enforce.WarningTracker
import app.kite.child.findphone.FindPhoneRinger
import app.kite.child.identity.DeviceReporter
import app.kite.child.identity.MemberIdentity
import app.kite.child.location.PlacesMonitor
import app.kite.child.location.PlacesStore
import app.kite.child.location.TrailUploader
import app.kite.child.status.TodaySummary
import app.kite.child.tasks.TasksStore
import app.kite.child.tasks.TasksSyncer
import app.kite.child.usage.UsageCollector
import app.kite.child.usage.UsageSyncer
import app.kite.core.usage.UsageDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Bindings that exist only on the child device: usage telemetry and enforcement.
 *  UsageRemote/RulesRemote are bound in coreModule (both apps use them). */
val childModule =
    module {
        single {
            Room.databaseBuilder(androidContext(), UsageDatabase::class.java, "usage.db")
                // Raw local telemetry; dropping it on a schema change is acceptable pre-release.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
        single { get<UsageDatabase>().usageDao() }
        single { get<UsageDatabase>().locationDao() }
        single { UsageCollector(androidContext(), get()) }
        single { MemberIdentity(androidContext(), get(), get(), get()) }
        single { UsageSyncer(androidContext(), get(), get(), get()) }
        single { InstalledAppsPublisher(androidContext(), get(), get(), get()) }
        single { RulesStore(androidContext(), get()) }
        single { RulesSyncer(get(), get(), get()) }
        single { TasksStore(androidContext(), get()) }
        single { TasksSyncer(get(), get(), get()) }
        single { TodaySummary(androidContext(), get(), get(), get(), get()) }
        single { PlacesStore(androidContext(), get()) }
        single { PlacesMonitor(get(), get(), get(), get()) }
        single { TrailUploader(androidContext(), get(), get(), get()) }
        single { BlockOverlay(androidContext()) }
        single { WarningTracker(androidContext()) }
        single { FindPhoneRinger(androidContext()) }
        single { BonusStore(androidContext()) }
        single { ProtectionState(androidContext()) }
        single { OfflineTimeGrant(androidContext(), get()) }
        single { DeviceReporter(androidContext(), get(), get(), get()) }
        single { RemoteLock(androidContext(), get(), get(), get(), get(), get(), get()) }
        single { UninstallGuard(androidContext()) }
        single { GuardOverlay(androidContext()) }
        single {
            EnforcementController(
                context = androidContext(),
                collector = get(),
                dao = get(),
                rulesStore = get(),
                rulesSyncer = get(),
                killSwitch = get(),
                overlay = get(),
                warnings = get(),
                remoteLock = get(),
                realtime = get(),
                identity = get(),
                bonusStore = get(),
                approvalsRemote = get(),
                tasksStore = get(),
                tasksSyncer = get(),
                protectionState = get(),
                deviceReporter = get(),
            )
        }
    }
