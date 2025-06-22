plugins {
    `java-library`
}

dependencies {
    api(project(":inertia-api"))
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")

    if (name != "inertia-nms-abstraction") {
        implementation(project(":inertia-nms-abstraction"))
    }
}
