plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.sunshine"
version = "1.3.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API 1.21.4 targets Java 21 -> widest server compatibility.
    // (Paper 26.x jars require Java 25 and would lock out most servers.)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")

    // Bundled into the jar (relocated below to avoid conflicts).
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // Needed at test runtime: tests load GroupResolver which references Bukkit types.
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

// --- Shadow jar (what you upload to Modrinth/Hangar/Spigot) ---------------

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    archiveFileName.set("SunshineCommandGuard-1.3.0.jar")
    relocate("org.bstats", "com.sunshine.cmdguard.bstats")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
