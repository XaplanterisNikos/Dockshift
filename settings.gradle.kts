/**
 * Gradle settings: where plugins and libraries are downloaded from,
 * and which modules make up the project.
 */
pluginManagement {
    repositories {
        // Google's Maven repository hosts the Android Gradle Plugin and AndroidX
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Modules may not declare their own repositories; everything is resolved from here
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Dockshift"
// :app    Android app: Compose UI, ViewModels, data sources (DataStore, Room), DI
// :domain plain Kotlin: models and business rules, no Android dependency allowed
include(":app")
include(":domain")
