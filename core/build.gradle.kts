import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kite.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    implementation(libs.kotlinx.serialization.json)

    "gmsImplementation"(libs.play.services.base)
    "hmsImplementation"(libs.hms.base)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
