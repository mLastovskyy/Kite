package app.kite.core.di

import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import app.kite.core.platform.PlatformServicesFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Core bindings shared by both apps. `PlatformServicesFactory` is resolved per flavor at
 * compile time: the gms build wires GoogleApiAvailability, the hms build wires
 * HuaweiApiAvailability, and both fall back to plain AOSP.
 *
 * [currentAppVersionCode] comes from the app's BuildConfig — the library cannot know it,
 * and the update check compares it against update.json.
 */
fun coreModule(currentAppVersionCode: Int): Module = module {
    single<PlatformServices> { PlatformServicesFactory.create(androidContext()) }
    single { Json { ignoreUnknownKeys = true } }
    single { HttpClient(OkHttp) }
    single { KillSwitchRepository(androidContext(), get(), get(), currentAppVersionCode) }
}
