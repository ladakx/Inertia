plugins {
    `java-library`
}

dependencies {
    api(project(":inertia-api"))
    implementation(project(":inertia-nms-abstraction"))
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")
}
