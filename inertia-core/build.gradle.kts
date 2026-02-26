plugins {
    `java-library`
    `maven-publish`
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))
java {
    withSourcesJar()
    withJavadocJar()
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}
tasks.withType<Javadoc>().configureEach {
    // Some internal sources contain javadoc text that doclint treats as errors.
    // Publishing should not be blocked by javadoc strictness.
    isFailOnError = false
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

dependencies {
    api(project(":inertia-api"))

    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.viaversion:viaversion-api:5.7.1")

    compileOnly("com.mojang:authlib:3.13.56")

    implementation("org.incendo:cloud-paper:2.0.0-beta.14")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.14")

    implementation("it.unimi.dsi:fastutil:8.5.13")

    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.9")
    compileOnly("com.sk89q.worldedit:worldedit-core:7.2.0-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "inertia-core"
        }
    }
}

tasks.named("build").configure {
    if (providers.gradleProperty("inertia.publishLocalOnBuild").orElse("true").get().equals("true", ignoreCase = true)) {
        finalizedBy("publishToMavenLocal")
    }
}
