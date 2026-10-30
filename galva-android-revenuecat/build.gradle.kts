import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.DeploymentValidation
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
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

// io.galva.sdk:galva-android-revenuecat:<VERSION_NAME>, signed, as galva-android publishes galva-sdk:
// `publishToMavenCentral` uploads it to Maven Central (a -SNAPSHOT to Central's snapshot repository),
// `publishToMavenLocal` puts it in ~/.m2. Credentials and the signing key come from the
// mavenCentralUsername / mavenCentralPassword / signingInMemoryKey* Gradle properties.
mavenPublishing {
    configure(AndroidSingleVariantLibrary(variant = "release"))
    publishToMavenCentral(validateDeployment = DeploymentValidation.PUBLISHED)
    signAllPublications()
    // Only Maven Central needs signed artifacts: publishToMavenLocal also works on a machine
    // without the signing key, and still signs where the key is set.
    extensions.configure<SigningExtension> {
        isRequired = gradle.startParameter.taskNames.any { it.contains("MavenCentral", ignoreCase = true) }
    }
    coordinates(property("GROUP") as String, "galva-android-revenuecat", property("VERSION_NAME") as String)

    pom {
        name.set("Galva RevenueCat")
        description.set("Connects the Galva Android SDK to RevenueCat.")
        url.set("https://github.com/Galva-io/galva-android-revenuecat")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("linhttbk97")
                name.set("LinhTT")
                email.set("tailinh@unstatic.co")
            }
        }
        scm {
            url.set("https://github.com/Galva-io/galva-android-revenuecat")
            connection.set("scm:git:git://github.com/Galva-io/galva-android-revenuecat.git")
            developerConnection.set("scm:git:ssh://git@github.com/Galva-io/galva-android-revenuecat.git")
        }
    }
}
