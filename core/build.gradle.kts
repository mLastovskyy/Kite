import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.kotlinx.serialization.json)
    // TOTP secret + Supabase tokens live in EncryptedSharedPreferences, never plain prefs.
    implementation(libs.androidx.security.crypto)

    "gmsImplementation"(libs.play.services.base)
    "hmsImplementation"(libs.hms.base)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
