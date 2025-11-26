import org.gradle.api.tasks.Copy
import java.util.Properties

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta17"
}

group = "com.ladakx"
version = "1.0-DEV"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(16))
}

dependencies {
    implementation(project(":inertia-api"))
    implementation(project(":inertia-core"))
    implementation(project(":inertia-common"))
    implementation(project(":inertia-nms-abstraction"))
    implementation(project(":inertia-nms-v1_16_R3"))
    implementation(project(":inertia-nms-v1_17_R1"))
    implementation(project(":inertia-nms-v1_18_R2"))
    implementation(project(":inertia-nms-v1_19_R3"))
    implementation(project(":inertia-nms-v1_20_R1"))
    implementation(project(":inertia-nms-v1_20_R2"))
    implementation(project(":inertia-nms-v1_20_R3"))
    implementation(project(":inertia-nms-v1_20_R4"))
    implementation(project(":inertia-nms-v1_21_R1"))
    implementation(project(":inertia-nms-v1_21_R2"))
    implementation(project(":inertia-nms-v1_21_R3"))
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
        relocate("org.joml", "com.ladakx.inertia.libs.joml")

        mergeServiceFiles()

        exclude("META-INF/LICENSE*")
        exclude("META-INF/AL2.0")
        exclude("META-INF/LGPL2.1")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

        finalizedBy("copyJarToDesktop")
    }

    build {
        dependsOn(shadowJar)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}

val properties = Properties().apply {
    load(file("deployment.properties").inputStream())
}

tasks.register<Copy>("copyJarToDesktop") {
    group = "deployment"
    description = "Copy the built JAR file to the desktop dev folder"
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into("/Users/vladislav/Desktop/dev/plugins")
    doLast {
        val jarFile = tasks.shadowJar.get().archiveFile.get().asFile
        println("JAR file copied to: /Users/vladislav/Desktop/dev/plugins/${jarFile.name}")
    }

    val pluginFile = file("build/libs/Inertia-${version}.jar")
    val serverIp = properties["serverIp"] as String
    val remotePath = properties["remotePath"] as String
    val username = properties["username"] as String
    val privateKeyPath = properties["privateKeyPath"] as String

    doLast {
        if (pluginFile.exists()) {
            exec {
                commandLine("scp", "-i", privateKeyPath, pluginFile.absolutePath, "$username@$serverIp:$remotePath")
            }
            exec {
                commandLine("ssh", "-i", privateKeyPath, "$username@$serverIp", "screen -S dev -X stuff '\n'")
            }
            exec {
                commandLine("ssh", "-i", privateKeyPath, "$username@$serverIp", "screen -S dev -X stuff 'say Inertia deploy success\n'")
            }

            println("Plugin loaded.")
        } else {
            println("File plugin not found: ${pluginFile.absolutePath}")
        }
    }
}

tasks.register("deployToDesktop") {
    group = "deployment"
    description = "Build and copy JAR to the desktop dev folder"
    dependsOn(tasks.build)
    finalizedBy("copyJarToDesktop")
}