package app.kite.core.di

import app.kite.core.auth.SessionManager
import app.kite.core.auth.SupabaseAuthClient
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import app.kite.core.platform.PlatformServicesFactory
import app.kite.core.secure.SecureStore
import app.kite.core.usage.UsageRemote
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Core bindings shared by both apps. `PlatformServicesFactory` is resolved per flavor at
 * compile time. [currentAppVersionCode] comes from the app's BuildConfig for the update check.
 */
fun coreModule(currentAppVersionCode: Int): Module = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(get()) }
        }
    }
    single<PlatformServices> { PlatformServicesFactory.create(androidContext()) }
    single { KillSwitchRepository(androidContext(), get(), get(), currentAppVersionCode) }

    single { SecureStore(androidContext()) }
    single { SupabaseAuthClient(get(), get()) }
    single { SessionManager(get(), get(), get()) }
    single { FamilyRepository(get(), get(), get()) }
    single { UsageRemote(get(), get(), get()) }
}
