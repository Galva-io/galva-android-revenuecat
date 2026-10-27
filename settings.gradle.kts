pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// An unreleased galva-android, published with its `./gradlew publishToMavenLocal`. Off unless
// -Pgalva.mavenLocal is passed or local.properties has galva.mavenLocal=true (per machine and
// git-ignored, so Android Studio's sync picks it up too). A stale local build never stands in for
// the published Galva by accident.
val localProperties = java.util.Properties().apply {
    file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val useLocalGalva = providers.gradleProperty("galva.mavenLocal").isPresent ||
    localProperties.getProperty("galva.mavenLocal") == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useLocalGalva) {
            mavenLocal { content { includeGroup("io.galva.sdk") } }
        }
        google()
        mavenCentral()
        // Galva's artifacts, as in galva-android's docs/installation.md.
        maven {
            name = "GalvaGitHubPackages"
            url = uri("https://maven.pkg.github.com/Galva-io/galva-android")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GPR_USER")).orNull
                // gpr.key as in the installation guide; gpr.token as galva-android's publishing uses.
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.gradleProperty("gpr.token"))
                    .orElse(providers.environmentVariable("GPR_KEY")).orNull
            }
            content { includeGroup("io.galva.sdk") }
        }
    }
}

rootProject.name = "galva-android-revenuecat"

include(":galva-android-revenuecat")
// Demo app: RevenueCat + Galva wired together (see CONTRIBUTING.md).
include(":app")
