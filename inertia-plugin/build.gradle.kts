import java.util.Properties

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta17"
    id("maven-publish")
}

group = "com.inertia"
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

tasks.register<Copy>("copyNativeLib") {
    group = "native"
    description = "Copy native library to resources"
    dependsOn("make")
    val os = System.getProperty("os.name").lowercase()
    val libName = when {
        os.contains("win") -> "inertia_native.dll"
        os.contains("mac") -> "libinertia_native.dylib"
        else -> "libinertia_native.so"
    }
    from(file("../native/$libName"))
    into(file("src/main/resources"))
}

tasks.processResources { dependsOn("copyNativeLib") }

tasks.shadowJar {
    archiveBaseName.set("Inertia")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    relocate("net.kyori.adventure", "com.inertia.libs.adventure")
    relocate("dev.rollczi.litecommands", "com.inertia.libs.litecommands")
    relocate("de.tr7zw.changeme.nbtapi", "com.inertia.libs.nbtapi")

    minimize()
}

tasks.build { dependsOn(tasks.shadowJar) }

dependencies {
    api(project(":inertia-api"))
    implementation(project(":inertia-core"))
    implementation(project(":inertia-nms-abstraction"))
    implementation(project(":inertia-nms-v1_12_R1"))
    implementation(project(":inertia-nms-v1_16_R3"))
    implementation(project(":inertia-nms-v1_21_R1"))

    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.3")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("dev.rollczi:litecommands-bukkit:3.2.1")
    implementation("de.tr7zw:item-nbt-api:2.12.3")
}

tasks.withType<Test> {
    enabled = false
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