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

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")

    api("org.joml:joml:1.10.8")
    api("com.github.ladakx:jolt-jni-api:3.6.1@jar")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "inertia-api"
        }
    }
}

tasks.named("build").configure {
    if (providers.gradleProperty("inertia.publishLocalOnBuild").orElse("true").get().equals("true", ignoreCase = true)) {
        finalizedBy("publishToMavenLocal")
    }
}
