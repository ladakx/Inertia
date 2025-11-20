plugins {
    `java-library`
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.11"
}

dependencies {
    compileOnly(project(":inertia-core"))
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.19.4-R0.1-SNAPSHOT")

    if (name != "inertia-nms-abstraction") {
        implementation(project(":inertia-nms-abstraction"))
    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION

tasks.assemble {
    dependsOn(tasks.reobfJar)
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        options.release.set(17)
    }
}