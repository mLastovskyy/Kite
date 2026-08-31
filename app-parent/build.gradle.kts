import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: credentials come from the environment (CI secrets) or from
// keystore.properties (local, gitignored). Without them the release build stays
// unsigned — checks still pass, the artifact just cannot be installed.
val signingProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(prop)

// versionName comes from gradle.properties (bumped manually per release); versionCode is
// the git commit count, so it grows monotonically with every commit — update.json
// comparisons (latestVersionCode / minSupportedVersionCode) rely on that. Both apps share
// the same pair. Fallback 1 covers builds from a source archive without git.
val kiteVersionName: String = providers.gradleProperty("kite.versionName").get()
val kiteVersionCode: Int =
    runCatching {
        providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
            .standardOutput.asText.get().trim().toInt()
    }.getOrDefault(1)

android {
    namespace = "app.kite.parent"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.kite.parent"
        minSdk = 26
        targetSdk = 36
        versionCode = kiteVersionCode
        versionName = kiteVersionName
    }

    flavorDimensions += "services"
    productFlavors {
        create("gms") {
            dimension = "services"
            versionNameSuffix = "-gms"
        }
        create("hms") {
            dimension = "services"
            versionNameSuffix = "-hms"
        }
    }

    val releaseStore = signingValue("KITE_KEYSTORE_FILE", "storeFile")
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = rootProject.file(releaseStore)
                storePassword = signingValue("KITE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KITE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KITE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.koin.android)
}
