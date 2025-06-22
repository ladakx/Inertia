plugins {
    `java-library`
}

dependencies {
    api(project(":inertia-api"))
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")

    if (name != "inertia-nms-abstraction") {
        implementation(project(":inertia-nms-abstraction"))
    }
}