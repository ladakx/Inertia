subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/") // paper
        ivy {
            url = uri("https://github.com/ladakx/jolt-jni/releases/download/")

            patternLayout {
                artifact("[revision]/[artifact].[ext]")
            }

            metadataSources {
                artifact()
            }
        }
        mavenCentral()
        maven("https://maven.enginehub.org/repo/")
    }

    dependencies {
        // Jolt-JNI
        "implementation"("com.github.ladakx:jolt-jni-windows-dp:3.6.0@jar")

        // Adventure
        "implementation"("net.kyori:adventure-text-minimessage:4.25.0")

        // JOML
        "implementation"("org.joml:joml:1.10.8")
    }
}