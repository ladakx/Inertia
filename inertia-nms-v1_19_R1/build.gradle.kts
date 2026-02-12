plugins {
    `java-library`
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

dependencies {
    compileOnly(project(":inertia-core"))
    compileOnly("io.papermc.paper:paper-api:1.19-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.19-R0.1-SNAPSHOT")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION

tasks.assemble {
    dependsOn(tasks.reobfJar)
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}