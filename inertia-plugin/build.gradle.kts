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
    implementation("dev.rollczi:litecommands-bukkit:3.9.7")
    implementation("de.tr7zw:item-nbt-api:2.12.3")
}

tasks.register<Exec>("cmake") {
    group = "native"
    description = "Run CMake for native code"
    workingDir = file("../native")

    val javaHome = "/Users/vladislav/Library/Java/JavaVirtualMachines/ms-21.0.7/Contents/Home"

    println("Using JAVA_HOME: $javaHome")

    val jniIncludeDir = file("$javaHome/include").absolutePath
    val jniPlatformIncludeDir = file("$javaHome/include/darwin").absolutePath

    if (!file(jniIncludeDir).exists()) {
        throw GradleException("JNI include directory not found: $jniIncludeDir")
    }
    if (!file(jniPlatformIncludeDir).exists()) {
        throw GradleException("JNI platform include directory not found: $jniPlatformIncludeDir")
    }

    environment("JAVA_HOME", javaHome)

    commandLine(
        "/usr/local/bin/cmake",
        "-S", ".",
        "-B", "build",
        "-DJAVA_HOME=$javaHome",
        "-DJAVA_INCLUDE_PATH=$jniIncludeDir",
        "-DJAVA_INCLUDE_PATH2=$jniPlatformIncludeDir"
    )
}

tasks.register<Exec>("make") {
    group = "native"
    description = "Compile native code"
    dependsOn("cmake")
    workingDir = file("../native/build")
    commandLine("make")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(project.properties)
        }
    }

    shadowJar {
        dependsOn("make")

        archiveBaseName.set("Inertia")
        archiveClassifier.set("")
        archiveVersion.set("1.0-DEV")

        relocate("net.kyori.adventure", "com.ladakx.inertia.libs.adventure")
        relocate("dev.rollczi.litecommands", "com.ladakx.inertia.libs.litecommands")
        relocate("de.tr7zw.changeme.nbtapi", "com.ladakx.inertia.libs.nbtapi")

        from(file("../native/build/libinertia_native.dylib")) {
            into("native/darwin")
        }

        finalizedBy("copyJarToDesktop")
    }

    build {
        // Головний таск 'build' залежить від 'shadowJar', що створює правильний ланцюжок
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

//val properties = Properties().apply {
//    val propertiesFile = rootProject.file("deployment.properties")
//    if (propertiesFile.exists()) {
//        load(propertiesFile.inputStream())
//    } else {
//        println("WARNING: deployment.properties not found. Skipping deployment setup.")
//    }
//}
//
//val deployPlugin by tasks.registering {
//    group = "deployment"
//    description = "Deploys the plugin to the test server."
//
//    dependsOn(tasks.shadowJar)
//
//    val pluginFile = tasks.shadowJar.get().archiveFile.get().asFile
//    val serverIp = properties["serverIp"] as String?
//    val remotePath = properties["remotePath"] as String?
//    val username = properties["username"] as String?
//    val privateKeyPath = rootProject.file(properties["privateKeyPath"] as String).absolutePath
//
//    enabled = serverIp != null
//
//    doLast {
//        if (pluginFile.exists()) {
//            println("Deploying ${pluginFile.name} to $username@$serverIp:$remotePath")
//
//            exec {
//                commandLine("scp", "-i", privateKeyPath, pluginFile.absolutePath, "$username@$serverIp:$remotePath")
//            }
//
//            exec {
//                commandLine("ssh", "-i", privateKeyPath, "$username@$serverIp", "screen -S dev -X stuff 'Inertia reload confirm\n'")
//            }
//
//            println("Plugin successfully deployed and server reloaded.")
//        } else {
//            println("ERROR: Plugin file not found at ${pluginFile.absolutePath}")
//        }
//    }
//}
//
//publishing {
//    publications {
//        create<MavenPublication>("mavenJava") {
//            artifact(tasks.shadowJar)
//
//            groupId = project.group.toString()
//            artifactId = "Inertia"
//            version = project.version.toString()
//
//            pom {
//                name.set("Inertia")
//                description.set("A Minecraft plugin that integrates the Jolt Physics engine.")
//                url.set("https://github.com/ladakx/Inertia")
//
//                licenses {
//                    license {
//                        name.set("The Apache License, Version 2.0")
//                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
//                    }
//                }
//                developers {
//                    developer {
//                        id.set("ladakx")
//                        name.set("Ladakx")
//                        email.set("vlad1256a@gmail.com")
//                    }
//                }
//                scm {
//                    connection.set("scm:git:git://github.com/ladakx/Inertia.git")
//                    developerConnection.set("scm:git:ssh://github.com/ladakx/Inertia.git")
//                    url.set("https://github.com/ladakx/Inertia")
//                }
//            }
//        }
//    }
//    repositories {
//        mavenLocal()
//    }
//}
//
//tasks.register("buildAndDeploy") {
//    group = "deployment"
//    description = "Builds the project and deploys the plugin."
//    dependsOn(tasks.build)
//    finalizedBy(deployPlugin)
//}
//
//tasks.named("build") {
//    finalizedBy(deployPlugin)
//    finalizedBy("publishToMavenLocal")
//}