plugins {
    `java-library`
    id("java")
}

dependencies {
    api(project(":inertia-api"))
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly(files(file("../../libs/spigot-1.16.5.jar")))

    if (name != "inertia-nms-abstraction") {
        implementation(project(":inertia-nms-abstraction"))
    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        options.release.set(16)
    }
}