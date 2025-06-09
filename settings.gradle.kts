
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include(":cloud")
include(":shared")
include(":proxy")

rootProject.name = "Kloud"