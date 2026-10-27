plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
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
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
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

// io.galva.sdk:galva-android-revenuecat:<VERSION_NAME>: `publishToMavenLocal` puts it in ~/.m2,
// `publishReleasePublicationToGitHubPackagesRepository` on this repository's GitHub Packages.
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Galva-io/galva-android-revenuecat")
            // Same credentials as galva-android's publishing: a token with write:packages.
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
    publications {
        register<MavenPublication>("release") {
            artifactId = "galva-android-revenuecat"
            // AGP creates the "release" component once the project is evaluated.
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("Galva RevenueCat")
                description.set("Connects the Galva Android SDK to RevenueCat.")
                url.set("https://github.com/Galva-io/galva-android-revenuecat")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/Galva-io/galva-android-revenuecat/blob/main/LICENSE")
                    }
                }
                scm {
                    url.set("https://github.com/Galva-io/galva-android-revenuecat")
                    connection.set("scm:git:https://github.com/Galva-io/galva-android-revenuecat.git")
                }
            }
        }
    }
}
