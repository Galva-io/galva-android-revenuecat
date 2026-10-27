import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Keys live in local.properties (git-ignored). A missing key doesn't fail the build: the demo shows
// which SDK isn't configured instead.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name).orEmpty()

android {
    namespace = "io.galva.revenuecat.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.galva.revenuecat.demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GALVA_API_KEY", "\"${localProperty("GALVA_API_KEY")}\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"${localProperty("REVENUECAT_API_KEY")}\"")
    }
    buildFeatures.buildConfig = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // All an app needs for Galva + RevenueCat: this brings in both SDKs.
    implementation(project(":galva-android-revenuecat"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
