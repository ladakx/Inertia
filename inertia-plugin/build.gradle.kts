import java.util.Properties
import java.util.*

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
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")

    api(project(":inertia-api"))
    implementation(project(":inertia-core"))
    implementation(project(":inertia-nms-abstraction"))
    implementation(project(":inertia-nms-v1_12_R1"))
    implementation(project(":inertia-nms-v1_16_R3"))
    implementation(project(":inertia-nms-v1_21_R1"))

    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.3")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")

    implementation("dev.rollczi:litecommands-bukkit:3.9.7")

    implementation("de.tr7zw:item-nbt-api:2.12.3")
}

tasks.register<Exec>("cmake") {
    group = "native"
    description = "Run CMake for native code"
    workingDir = file("../native")

    val javaHome = "/Users/vladislav/Library/Java/JavaVirtualMachines/openjdk-24.0.1/Contents/Home"
    val jniIncludeDir = file("$javaHome/include").absolutePath
    val jniPlatformIncludeDir = file("$javaHome/include/darwin").absolutePath

    commandLine(
        "/usr/local/bin/cmake",
        "-DJNI_INCLUDE_DIR=$jniIncludeDir",
        "-DJNI_INCLUDE_DIR2=$jniPlatformIncludeDir",
        "."
    )
}

tasks.register<Exec>("make") {
    group = "native"
    description = "Compile native code"
    dependsOn("cmake")
    workingDir = file("../native")
    val os = System.getProperty("os.name").lowercase()
    if (os.contains("win")) { commandLine("nmake") } else { commandLine("make") }
}

tasks {
    // Your existing processResources and shadowJar tasks remain here
    processResources {
        from(project(":inertia-core").tasks.named("processResources"))
        filesMatching("plugin.yml") {
            expand(project.properties)
        }
    }

    shadowJar {
        archiveBaseName.set("Inertia")
        archiveClassifier.set("")
        archiveVersion.set("1.0-DEV")

        relocate("net.kyori.adventure", "com.ladakx.inertia.libs.adventure")
        relocate("dev.rollczi.litecommands", "com.ladakx.inertia.libs.litecommands")
        relocate("de.tr7zw.changeme.nbtapi", "com.ladakx.inertia.libs.nbtapi")

        // --- FINAL CORRECTED NATIVE LIBRARY PACKAGING ---
        val os = System.getProperty("os.name").toLowerCase(Locale.ROOT)
        val osFolder = when {
            os.contains("mac") -> "darwin"
            os.contains("win") -> "windows"
            else -> "linux"
        }

        // THIS IS THE FIX: We are now looking for the library in the correct build directory
        // and NOT in a 'libs' subfolder.
        from(project(":native").layout.buildDirectory) {
            // Include only the library file based on the OS.
            val libName = when (osFolder) {
                "windows" -> "inertia_native.dll"
                "darwin" -> "libinertia_native.dylib"
                else -> "libinertia_native.so"
            }
            include(libName)
            into("native/$osFolder")
        }
    }

    build {
        dependsOn(shadowJar)
    }
}

val properties = Properties().apply {
    val propertiesFile = rootProject.file("deployment.properties")
    if (propertiesFile.exists()) {
        load(propertiesFile.inputStream())
    } else {
        println("WARNING: deployment.properties not found. Skipping deployment setup.")
    }
}

val deployPlugin by tasks.registering {
    group = "deployment"
    description = "Deploys the plugin to the test server."

    dependsOn(tasks.shadowJar)

    val pluginFile = tasks.shadowJar.get().archiveFile.get().asFile
    val serverIp = properties["serverIp"] as String?
    val remotePath = properties["remotePath"] as String?
    val username = properties["username"] as String?
    val privateKeyPath = rootProject.file(properties["privateKeyPath"] as String).absolutePath

    enabled = serverIp != null

    doLast {
        if (pluginFile.exists()) {
            println("Deploying ${pluginFile.name} to $username@$serverIp:$remotePath")

            exec {
                commandLine("scp", "-i", privateKeyPath, pluginFile.absolutePath, "$username@$serverIp:$remotePath")
            }

            exec {
                commandLine("ssh", "-i", privateKeyPath, "$username@$serverIp", "screen -S dev -X stuff 'Inertia reload confirm\n'")
            }

            println("Plugin successfully deployed and server reloaded.")
        } else {
            println("ERROR: Plugin file not found at ${pluginFile.absolutePath}")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.shadowJar)

            groupId = project.group.toString()
            artifactId = "Inertia"
            version = project.version.toString()

            pom {
                name.set("Inertia")
                description.set("A Minecraft plugin that integrates the Jolt Physics engine.")
                url.set("https://github.com/ladakx/Inertia")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("ladakx")
                        name.set("Ladakx")
                        email.set("vlad1256a@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/ladakx/Inertia.git")
                    developerConnection.set("scm:git:ssh://github.com/ladakx/Inertia.git")
                    url.set("https://github.com/ladakx/Inertia")
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.register("buildAndDeploy") {
    group = "deployment"
    description = "Builds the project and deploys the plugin."
    dependsOn(tasks.build)
    finalizedBy(deployPlugin)
}

tasks.named("build") {
    finalizedBy(deployPlugin)
    finalizedBy("publishToMavenLocal")
}