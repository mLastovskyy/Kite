package app.kite.child

import android.app.Application
import app.kite.child.di.flavorModule
import app.kite.core.di.coreModule
import app.kite.core.killswitch.KillSwitchScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KiteChildApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KiteChildApp)
            modules(coreModule(BuildConfig.VERSION_CODE), flavorModule)
        }
        // The child device is the one that must obey the kill switch — check hourly.
        KillSwitchScheduler.schedule(this)
    }
}
