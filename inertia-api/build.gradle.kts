plugins {
    `java-library`
}

group = "com.inertia"
version = "1.0-DEV"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains:annotations:24.0.1")
}