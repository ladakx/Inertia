rootProject.name = "Inertia"
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Spigot API
        maven("https://repo.papermc.io/repository/maven-public/") // paper
        maven("https://repo.codemc.io/repository/maven-public/") // CodeMC
        maven("https://repo.panda-lang.org/releases") // Panda
        maven("https://maven.enginehub.org/repo/") // WorldGuard
        maven(url = "https://repo.aikar.co/content/groups/aikar/") // aikar
        maven(url = "https://repo.maven.apache.org/maven2/") // apache
        maven(url = "https://repo.dmulloy2.net/repository/public/") // protocollib
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/") // Adventure Snapshots
        maven(url = "https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
        maven(url = "https://jitpack.io") // Vault
    }
}

include("inertia-api")
include("inertia-core")
include("inertia-plugin")
include("inertia-nms-abstraction")
include("inertia-nms-v1_16_R3")
include("inertia-nms-v1_17_R1")
include("inertia-nms-v1_18_R2")
include("inertia-nms-v1_19_R3")
include("inertia-nms-v1_20_R1")
include("inertia-nms-v1_20_R2")
include("inertia-nms-v1_20_R3")
include("inertia-nms-v1_20_R4")
include("inertia-nms-v1_21_R1")
include("inertia-nms-v1_21_R2")
include("inertia-nms-v1_21_R3")