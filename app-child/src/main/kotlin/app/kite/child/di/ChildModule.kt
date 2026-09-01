package app.kite.child.di

import androidx.room.Room
import app.kite.child.enforce.BlockOverlay
import app.kite.child.enforce.EnforcementController
import app.kite.child.enforce.GuardOverlay
import app.kite.child.enforce.RemoteLock
import app.kite.child.enforce.RulesStore
import app.kite.child.enforce.RulesSyncer
import app.kite.child.enforce.UninstallGuard
import app.kite.child.enforce.WarningTracker
import app.kite.child.identity.MemberIdentity
import app.kite.child.usage.UsageCollector
import app.kite.child.usage.UsageSyncer
import app.kite.core.usage.UsageDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Bindings that exist only on the child device: usage telemetry and enforcement.
 *  UsageRemote/RulesRemote are bound in coreModule (both apps use them). */
val childModule =
    module {
        single { Room.databaseBuilder(androidContext(), UsageDatabase::class.java, "usage.db").build() }
        single { get<UsageDatabase>().usageDao() }
        single { UsageCollector(androidContext(), get()) }
        single { MemberIdentity(androidContext(), get(), get(), get()) }
        single { UsageSyncer(androidContext(), get(), get(), get()) }
        single { RulesStore(androidContext(), get()) }
        single { RulesSyncer(get(), get(), get()) }
        single { BlockOverlay(androidContext()) }
        single { WarningTracker(androidContext()) }
        single { RemoteLock(androidContext(), get(), get()) }
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
            )
        }
    }
