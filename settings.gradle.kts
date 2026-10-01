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
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            content { includeGroup("org.spigotmc") }
        }
    }
}

rootProject.name = "CraftConnect"
include(":app")
include(":bridge-protocol", ":server-bridge")
