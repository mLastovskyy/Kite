import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Room's processor runs through kapt: KSP has no Kotlin 2.4.10 build yet
    // (checked 2026-09-01) — swap to KSP when it catches up.
    alias(libs.plugins.kotlin.kapt)
}

// Supabase project coordinates. The publishable key is public by design (it ships inside
// the APK); real protection is Postgres RLS. Values come from CI env or local.properties —
// never hardcoded, and the app must keep working offline when they are blank.
val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun configValue(env: String, prop: String): String = System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop) ?: ""

android {
    namespace = "app.kite.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "SUPABASE_URL", "\"${configValue("KITE_SUPABASE_URL", "kite.supabase.url")}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${configValue("KITE_SUPABASE_PUBLISHABLE_KEY", "kite.supabase.publishableKey")}\"",
        )

        // Firebase/FCM config — kept out of source (GitHub secret scanning) and injected
        // from local.properties / CI env. Empty in CI is fine: builds compile, push just
        // no-ops there.
        buildConfigField("String", "FCM_API_KEY", "\"${configValue("KITE_FCM_API_KEY", "kite.fcm.apiKey")}\"")
        buildConfigField("String", "FCM_SENDER_ID", "\"${configValue("KITE_FCM_SENDER_ID", "kite.fcm.senderId")}\"")
        buildConfigField("String", "FCM_PROJECT_ID", "\"${configValue("KITE_FCM_PROJECT_ID", "kite.fcm.projectId")}\"")
        buildConfigField("String", "FCM_APP_ID_PARENT", "\"${configValue("KITE_FCM_APP_ID_PARENT", "kite.fcm.appIdParent")}\"")
        buildConfigField("String", "FCM_APP_ID_CHILD", "\"${configValue("KITE_FCM_APP_ID_CHILD", "kite.fcm.appIdChild")}\"")
    }

    // Platform code (GMS/HMS) is separated by flavor so that com.google.android.gms.*
    // never leaks outside src/gms and com.huawei.* never leaks outside src/hms.
    flavorDimensions += "services"
    productFlavors {
        create("gms") { dimension = "services" }
        create("hms") { dimension = "services" }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Design system exposes Compose types in its public API, so consumers inherit them.
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // Supabase Realtime (instant remote lock) rides a plain WebSocket — no Supabase SDK.
    implementation(libs.ktor.client.websockets)
    implementation(libs.kotlinx.serialization.json)
    // TOTP secret + Supabase tokens live in EncryptedSharedPreferences, never plain prefs.
    implementation(libs.androidx.security.crypto)

    // Raw usage telemetry stays in Room ON THE DEVICE (CLAUDE.md) — only daily
    // aggregates ever reach the server.
    api(libs.room.runtime)
    "kapt"(libs.room.compiler)
    // Room 2.8.x reads Kotlin metadata with this library; the version must be >= the
    // Kotlin compiler's metadata version (2.4), or kapt fails with "maximum supported".
    "kapt"(libs.kotlin.metadata.jvm)

    "gmsImplementation"(libs.play.services.base)
    // FCM lives only in the gms source set; hms/AOSP use their own wake-up paths.
    "gmsImplementation"(libs.firebase.messaging)
    "hmsImplementation"(libs.hms.base)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
