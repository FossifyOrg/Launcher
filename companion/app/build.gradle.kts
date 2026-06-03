import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Standalone project: versions must be explicit (no shared root classpath).
    // AGP 9.0+ has built-in Kotlin support — org.jetbrains.kotlin.android must NOT be applied.
    id("com.android.application") version "9.2.1"
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

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    // The companion's Kotlin sources live in src/main/kotlin. AGP's built-in Kotlin does not
    // add that directory automatically, so register it (mirrors the launcher's app module).
    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Emit JVM 17 bytecode WITHOUT pinning a specific JDK toolchain. The previous
// `kotlin { jvmToolchain(17) }` forced Gradle to locate/provision an exact JDK 17 and failed
// the build ("Cannot find a Java installation matching {languageVersion=17}") on any machine
// running a different JDK. This mirrors the launcher's app module, which builds on any JDK 17+.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.json:json:20240303")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
