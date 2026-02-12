plugins {
    `java-library`
    id("java")
}

dependencies {
    compileOnly(project(":inertia-core"))
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly(files(file("../libs/spigot-1.16.5.jar")))
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}