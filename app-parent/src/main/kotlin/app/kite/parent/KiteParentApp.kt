package app.kite.parent

import android.app.Application
import app.kite.core.di.coreModule
import app.kite.core.killswitch.KillSwitchScheduler
import app.kite.parent.di.flavorModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KiteParentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KiteParentApp)
            modules(coreModule(BuildConfig.VERSION_CODE), flavorModule)
        }
        // The parent app polls update.json too, so «Проверить обновления» has fresh data.
        KillSwitchScheduler.schedule(this)
    }
}
