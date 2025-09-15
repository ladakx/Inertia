pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Spigot API
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://repo.panda-lang.org/releases")
    }
}

rootProject.name = "Inertia"

include("inertia-api")
include("inertia-core")
include("inertia-plugin")
include("inertia-nms-abstraction")
include("inertia-nms-v1_12_R1")
include("inertia-nms-v1_16_R3")
include("inertia-nms-v1_21_R1")