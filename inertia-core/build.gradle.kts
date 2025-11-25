plugins {
    `java-library`
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}

dependencies {
    compileOnly(project(":inertia-api"))
    compileOnly(project(":inertia-common"))
    compileOnly(project(":inertia-nms-abstraction"))
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
}