// companion/app/build.gradle.kts
// AGP 9.0+ has built-in Kotlin support — org.jetbrains.kotlin.android must NOT be applied.
// Kotlin compiles automatically via com.android.application alone.

plugins {
    id("com.android.application")
}

android {
    namespace = "org.fossify.launchpad.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.fossify.launchpad.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.json:json:20240303")
}
