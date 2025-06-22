plugins {
    id("java")
    id("io.github.goooler.shadow") version "8.1.8" apply false
}

subprojects {
    apply(plugin = "java")

    group = "com.inertia"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/groups/public/")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}
