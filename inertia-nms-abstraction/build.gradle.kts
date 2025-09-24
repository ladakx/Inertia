// inertia-nms-abstraction/build.gradle.kts

plugins {
    `java-library`
}

// --> ДОДАЙТЕ ЦЕЙ БЛОК <--
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16)) // Використовуйте ту ж версію, що й в API
    }
}

dependencies {
    api(project(":inertia-api"))
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")

    if (name != "inertia-nms-abstraction") {
        implementation(project(":inertia-nms-abstraction"))
    }
}