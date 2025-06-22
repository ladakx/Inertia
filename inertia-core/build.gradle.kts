plugins {
    id("java-library")
}

dependencies {
    implementation(project(":inertia-api"))
    implementation(project(":inertia-nms-abstraction"))

    implementation("net.kyori:adventure-platform-bukkit:4.3.2")
    implementation("dev.rollczi:litecommands-bukkit:3.2.1")
    implementation("de.tr7zw:nbt-api:2.12.3")

    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
}
