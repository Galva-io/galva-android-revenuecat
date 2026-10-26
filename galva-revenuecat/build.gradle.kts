plugins {
    alias(libs.plugins.android.library)
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String

android {
    namespace = "io.galva.revenuecat"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        buildConfigField("String", "VERSION", "\"${property("VERSION_NAME")}\"")
    }
    buildFeatures.buildConfig = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.unitTests.isReturnDefaultValues = true
}

kotlin {
    // Every public declaration is deliberate API.
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // This package is an integration: it builds on Galva's integration API.
        optIn.add("io.galva.sdk.GalvaIntegrationApi")
    }
}

dependencies {
    // Apps get Galva and RevenueCat (with its Play Billing Library) by adding this artifact.
    api(libs.galva.sdk)
    api(libs.revenuecat)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.kotlin)
}
