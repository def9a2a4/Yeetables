plugins {
    `java`
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "anon.def9a2a4"
version = "0.1.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.1.0")
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName.set("Yeetables")
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }

    shadowJar {
        archiveBaseName.set("Yeetables")
        archiveClassifier.set("")
        relocate("org.bstats", "${project.group}.bstats")
        mergeServiceFiles()
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }
}
