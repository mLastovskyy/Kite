package app.kite.parent

import android.app.Application
import app.kite.core.auth.SessionManager
import app.kite.core.di.coreModule
import app.kite.core.killswitch.KillSwitchScheduler
import app.kite.core.notifications.Channels
import app.kite.parent.di.flavorModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KiteParentApp : Application() {
    private val sessionManager: SessionManager by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KiteParentApp)
            modules(coreModule(BuildConfig.VERSION_CODE), flavorModule)
        }
        Channels.create(this)
        // Load any persisted session without a network round-trip (offline-first).
        sessionManager.bootstrap()
        // The parent app polls update.json too, so «Проверить обновления» has fresh data.
        KillSwitchScheduler.schedule(this)
    }
}
