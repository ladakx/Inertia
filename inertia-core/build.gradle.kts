plugins {
    `java-library`
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(16))
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(16)
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.mojang:authlib:3.13.56")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
}