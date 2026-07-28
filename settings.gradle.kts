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

rootProject.name = "HadronAndroid"

// :hadronkit is the port of hadron-ios' HadronKit Swift package — the shared
// client stack (OAuth, secure storage, GraphQL). Kept a separate module so the
// UI can never reach around it into raw HTTP.
include(":hadronkit")
include(":app")
