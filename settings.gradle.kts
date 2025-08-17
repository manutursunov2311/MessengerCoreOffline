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

rootProject.name = "MessengerCoreOffline"
include(":app")
include(":core")
include(":core:common")
include(":core:connectivity")
include(":domain")
include(":domain:chat")
include(":data")
include(":data:chat")
