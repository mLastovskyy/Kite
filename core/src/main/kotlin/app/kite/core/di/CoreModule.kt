package app.kite.core.di

import app.kite.core.appearance.AppearanceRepository
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.apps.AppIconsRemote
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.auth.SessionManager
import app.kite.core.auth.SupabaseAuthClient
import app.kite.core.avatar.AvatarRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.RealtimeCommands
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.PlacesRemote
import app.kite.core.location.TrailRemote
import app.kite.core.net.ConnectivityObserver
import app.kite.core.platform.PlatformServices
import app.kite.core.platform.PlatformServicesFactory
import app.kite.core.push.PushRegistrar
import app.kite.core.push.PushTokenRemote
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.tasks.TasksRemote
import app.kite.core.update.ApkInstaller
import app.kite.core.usage.UsageRemote
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Core bindings shared by both apps. `PlatformServicesFactory` is resolved per flavor at
 * compile time. [currentAppVersionCode] comes from the app's BuildConfig for the update check.
 */
fun coreModule(currentAppVersionCode: Int, apkKey: String = ""): Module = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(get()) }
            // Supabase Realtime (instant remote lock) rides a raw WebSocket.
            install(WebSockets)
        }
    }
    single<PlatformServices> { PlatformServicesFactory.create(androidContext()) }
    single { KillSwitchRepository(androidContext(), get(), get(), currentAppVersionCode, apkKey) }
    single { AppearanceRepository(androidContext()) }
    single { ApkInstaller(androidContext(), get()) }

    single { SecureStore(androidContext()) }
    single { SupabaseAuthClient(get(), get()) }
    single { SessionManager(get(), get(), get()) }
    single { FamilyRepository(get(), get(), get()) }
    single { UsageRemote(get(), get(), get()) }
    single { RulesRemote(get(), get(), get()) }
    single { CommandsRemote(get(), get(), get()) }
    single { RealtimeCommands(get(), get(), get()) }
    single { ApprovalsRemote(get(), get(), get()) }
    single { TasksRemote(get(), get(), get()) }
    single { AvatarRemote(get(), get()) }
    single { DeviceLocationRemote(get(), get(), get()) }
    single { PlacesRemote(get(), get(), get()) }
    single { TrailRemote(get(), get(), get()) }
    single { ChildAppsRemote(get(), get(), get()) }
    single { AppIconsRemote(get(), get()) }
    single { ConnectivityObserver(androidContext()) }
    single { PushTokenRemote(get(), get()) }
    single { PushRegistrar(get(), get(), get()) }
}
