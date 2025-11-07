plugins {
    `java-library`
}

// Define versions in one place for consistency
val joltVersion = "3.5.0" // Sticking with a known stable version

dependencies {
    // Project Modules
    api(project(":inertia-api"))
    implementation(project(":inertia-nms-abstraction"))

    // Temporary dependency on Bullet for core functionality
    implementation("org.jmonkeyengine:jme3-core:3.7.0-stable")
    implementation("com.github.stephengold:Libbulletjme:21.2.1")

    // Spigot API
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    implementation("org.jetbrains:annotations:24.0.1")

    // WorldEdit for region selection and manipulation
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.12")

    // Adventure API for rich text handling
    implementation("net.kyori:adventure-platform-bukkit:4.3.4")
    implementation("net.kyori:adventure-text-minimessage:4.18.0")
    implementation("net.kyori:adventure-text-serializer-gson:4.18.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.18.0")

    // Other Libraries
    implementation("de.tr7zw:item-nbt-api:2.14.1")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("org.joml:joml:1.10.8")
    implementation("xyz.jpenilla:reflection-remapper:0.1.1")

    // We only need the main Jolt JNI library now
    implementation("com.github.stephengold:jolt-jni-Windows64:$joltVersion")

    // The native libraries are still needed at runtime to be packaged into the JAR
    runtimeOnly("com.github.stephengold:jolt-jni-Windows64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-Linux64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX_ARM64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-Linux_ARM64:$joltVersion:ReleaseSp") // For Linux on ARM64
}


