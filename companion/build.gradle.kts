plugins {
    // Versions omitted: both plugins are already on the classpath via the root project
    // (AGP 9.2.1 + Kotlin 2.3.10 applied by the root build.gradle.kts). Declaring a
    // version here causes "already on classpath with unknown version" conflict.
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.json:json:20240303")
}
