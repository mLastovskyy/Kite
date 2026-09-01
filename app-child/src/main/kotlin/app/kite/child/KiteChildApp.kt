package app.kite.child

import android.app.Application
import app.kite.child.di.childModule
import app.kite.child.di.flavorModule
import app.kite.child.usage.UsageCollectScheduler
import app.kite.core.auth.SessionManager
import app.kite.core.di.coreModule
import app.kite.core.killswitch.KillSwitchScheduler
import app.kite.core.notifications.Channels
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KiteChildApp : Application() {
    private val sessionManager: SessionManager by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KiteChildApp)
            modules(coreModule(BuildConfig.VERSION_CODE), flavorModule, childModule)
        }
        Channels.create(this)
        // A previously paired child device keeps its (anonymous) session across launches.
        sessionManager.bootstrap()
        // The child device is the one that must obey the kill switch — check hourly.
        KillSwitchScheduler.schedule(this)
        // Screen-time events retention is ~a week; collect regularly into Room.
        UsageCollectScheduler.schedule(this)
    }
}
