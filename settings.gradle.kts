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

rootProject.name = "house-calendar"

include(":contracts")
include(":schedule")
include(":intake")
include(":sync")
include(":data")
include(":lan")
include(":nearby")
include(":app")
