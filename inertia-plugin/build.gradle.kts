plugins {
    id("io.github.goooler.shadow") version "8.1.8"
}

dependencies {
    implementation(project(":inertia-core"))
    implementation(project(":inertia-nms-v1_12_R1"))
    implementation(project(":inertia-nms-v1_16_R3"))
    implementation(project(":inertia-nms-v1_21_R1"))
}

val nativeProjectDir = file("../../native")
val nativeBuildDir = file("$buildDir/native")

tasks.register<Exec>("cmake") {
    workingDir(nativeBuildDir)
    commandLine("cmake", nativeProjectDir.path)

    doFirst {
        mkdir(nativeBuildDir)
    }
}


tasks.register<Exec>("make") {
    dependsOn("cmake")
    workingDir(nativeBuildDir)
    commandLine("make")
}

sourceSets.main.get().resources {
    srcDir(nativeBuildDir)
    // Включаємо тільки саму бібліотеку
    include("libinertia.dylib")
    // Вказуємо, куди саме в JAR її покласти
    into("native/darwin")
}

tasks.getByName("processResources") {
    dependsOn("make")
}

tasks.shadowJar {
    archiveBaseName.set("Inertia")
    archiveClassifier.set("")

    relocate("net.kyori.adventure", "com.inertia.core.lib.adventure")
    relocate("dev.rollczi.litecommands", "com.inertia.core.lib.litecommands")
    relocate("de.tr7zw.nbtapi", "com.inertia.core.lib.nbtapi")
}

tasks.getByName("build") {
    dependsOn(tasks.getByName("shadowJar"))
}
