//import java.util.Properties
//import java.util.*

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta17"
    id("maven-publish")
}

group = "com.ladakx"
version = "1.0-DEV"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.panda-lang.org/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")

    api(project(":inertia-api"))
    implementation(project(":inertia-core"))
    implementation(project(":inertia-nms-abstraction"))
    implementation(project(":inertia-nms-v1_21_R1"))

    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.3")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")

    implementation("de.tr7zw:item-nbt-api:2.12.3")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(project.properties)
        }
    }

    shadowJar {
        archiveBaseName.set("Inertia")
        archiveClassifier.set("")
        archiveVersion.set("1.0-DEV")

        relocate("net.kyori.adventure", "com.ladakx.inertia.libs.adventure")
        relocate("de.tr7zw.changeme.nbtapi", "com.ladakx.inertia.libs.nbtapi")

        finalizedBy("copyJarToDesktop")
    }

    build {
        dependsOn(shadowJar)
    }
}

tasks.register<Copy>("copyJarToDesktop") {
    group = "deployment"
    description = "Copy the built JAR file to desktop dev folder"
    dependsOn(tasks.shadowJar)

    from(tasks.shadowJar.get().archiveFile)
    into("/Users/vladislav/Desktop/dev/plugins")

    doLast {
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        println("JAR file copied to: /Users/vladislav/Desktop/dev/plugins/${jarFile.name}")
    }
}

tasks.register("deployToDesktop") {
    group = "deployment"
    description = "Build and copy JAR to desktop dev folder"
    dependsOn(tasks.build)
    finalizedBy("copyJarToDesktop")
}