subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/") // paper
        maven("https://repo.viaversion.com") // viaversion
        ivy {
            url = uri("https://github.com/ladakx/jolt-jni/releases/download/")

            patternLayout {
                artifact("[revision]/[artifact].[ext]")
            }

            metadataSources {
                artifact()
            }
        }
        maven("https://maven.enginehub.org/repo/")
    }
}

configure(subprojects.filter { it.name != "inertia-api" }) {
    dependencies {
        // Jolt-JNI
        "implementation"("com.github.ladakx:jolt-jni-api:3.6.1@jar")

        // Adventure
        "implementation"("net.kyori:adventure-text-minimessage:4.25.0")

        // JOML
        "implementation"("org.joml:joml:1.10.8")
    }
}
