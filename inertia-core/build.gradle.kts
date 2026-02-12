plugins {
    `java-library`
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    testCompileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.mojang:authlib:3.13.56")
    implementation("org.incendo:cloud-paper:2.0.0-beta.10")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.10")
    implementation("it.unimi.dsi:fastutil:8.5.13")

    // Bukkit API (если нужен BukkitAdapter)
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.9")
    // Platform-independent core
    compileOnly("com.sk89q.worldedit:worldedit-core:7.2.0-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
