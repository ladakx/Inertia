plugins {
    `java-library`
}

// Define versions in one place for consistency
val joltVersion = "3.4.0" // Sticking with a known stable version

dependencies {
    // Project Modules
    api(project(":inertia-api"))
    implementation(project(":inertia-nms-abstraction"))

    // Spigot API
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    implementation("org.jetbrains:annotations:24.0.1")

    // We only need the main Jolt JNI library now
    implementation("com.github.stephengold:jolt-jni-Windows64:$joltVersion")

    // The native libraries are still needed at runtime to be packaged into the JAR
    runtimeOnly("com.github.stephengold:jolt-jni-Windows64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-Linux64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX_ARM64:$joltVersion:ReleaseSp")
    runtimeOnly("com.github.stephengold:jolt-jni-Linux_ARM64:$joltVersion:ReleaseSp") // For Linux on ARM64
}


