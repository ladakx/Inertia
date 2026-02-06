val joltVersion = "3.5.0"

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/") // paper
//        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Spigot API
//        maven("https://repo.codemc.io/repository/maven-public/") // CodeMC
//        maven("https://repo.panda-lang.org/releases") // Panda
//        maven("https://maven.enginehub.org/repo/") // WorldGuard
//        maven("https://repo.aikar.co/content/groups/aikar/") // aikar
//        maven("https://repo.maven.apache.org/maven2/") // apache
//        maven("https://repo.dmulloy2.net/repository/public/") // protocollib
//        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") // Adventure Snapshots
//        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
//        maven("https://jitpack.io") // Vault
        mavenCentral()
        maven("https://maven.enginehub.org/repo/")
    }

    dependencies {
        // Jolt-JNI
        "implementation"("com.github.stephengold:jolt-jni-Windows64:$joltVersion")

        // Adventure
        "implementation"("net.kyori:adventure-text-minimessage:4.25.0")

        // JOML
        "implementation"("org.joml:joml:1.10.8")
        //    // Нативні бібліотеки Jolt для різних платформ і режимів (Sp/Dp)
        //    // Float (Single Precision)
        //    runtimeOnly("com.github.stephengold:jolt-jni-Windows64:$joltVersion:SpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-Linux64:$joltVersion:SpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX64:$joltVersion:SpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX_ARM64:$joltVersion:SpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-Linux_ARM64:$joltVersion:SpRelease")
        //
        //    // Double (Double Precision)
        //    runtimeOnly("com.github.stephengold:jolt-jni-Windows64:$joltVersion:DpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-Linux64:$joltVersion:DpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX64:$joltVersion:DpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-MacOSX_ARM64:$joltVersion:DpRelease")
        //    runtimeOnly("com.github.stephengold:jolt-jni-Linux_ARM64:$joltVersion:DpRelease")
    }
}