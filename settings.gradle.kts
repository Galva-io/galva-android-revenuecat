pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "galva-android-revenuecat"

// Local sibling checkout while galva-android's integrations API is unreleased: Gradle builds
// `io.galva.sdk:galva-sdk` from ../galva-android instead of downloading it. Before publishing,
// remove this line and add the GitHub Packages repository above.
includeBuild("../galva-android")

include(":galva-revenuecat")
