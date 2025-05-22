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
        mavenCentral() // JCenter repository is required for Picasso, check if it's already added
        jcenter()
        }
    }


rootProject.name = "GuardianTrack"
include(":app")

 