// inertia-api/build.gradle.kts

plugins {
    `java-library`
}

group = "com.inertia"
version = "DEV"

// --> ДОДАЙТЕ ЦЕЙ БЛОК <--
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16)) // Або 8, або 16
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    implementation("org.jetbrains:annotations:24.0.1")
}