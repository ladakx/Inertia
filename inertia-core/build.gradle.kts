plugins {
    `java-library`
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}

dependencies {
    api(project(":inertia-api"))

    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")

    compileOnly("com.mojang:authlib:3.13.56")

    implementation("org.incendo:cloud-paper:2.0.0-beta.14")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.14")

    implementation("it.unimi.dsi:fastutil:8.5.13")

    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.9")
    compileOnly("com.sk89q.worldedit:worldedit-core:7.2.0-SNAPSHOT")
}
