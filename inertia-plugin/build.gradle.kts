plugins {
    id("io.github.goooler.shadow") version "8.1.8"
}

dependencies {
    // Включаємо всю нашу логіку та NMS-реалізації
    implementation(project(":inertia-core"))
    implementation(project(":inertia-nms-v1_12_R1"))
    implementation(project(":inertia-nms-v1_16_R3"))
    implementation(project(":inertia-nms-v1_21_R1"))
}

tasks.shadowJar {
    archiveBaseName.set("Inertia")
    archiveClassifier.set("") // Щоб назва файлу була Inertia-1.0.0-SNAPSHOT.jar

    // Релокація пакетів для уникнення конфліктів
    relocate("net.kyori.adventure", "com.inertia.core.lib.adventure")
    relocate("dev.rollczi.litecommands", "com.inertia.core.lib.litecommands")
    relocate("de.tr7zw.nbtapi", "com.inertia.core.lib.nbtapi")
}

// Завдання, яке збирає фінальний JAR
tasks.getByName("build") {
    dependsOn(tasks.getByName("shadowJar"))
}
