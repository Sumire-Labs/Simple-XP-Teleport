plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "com.example.sxt"
version = "1.0.0"

val pluginVersion = version.toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.16")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.15")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("me.clip:placeholderapi:2.12.2")
    testImplementation("com.sk89q.worldguard:worldguard-bukkit:7.0.16")
    testImplementation("com.sk89q.worldedit:worldedit-bukkit:7.2.15")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.8")
    }

    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("org.sqlite", "com.example.sxt.libs.org.sqlite")
        mergeServiceFiles()
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }
}
