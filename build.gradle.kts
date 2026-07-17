import org.gradle.api.tasks.bundling.Jar

plugins {
    id("java")
    id("net.fabricmc.fabric-loom") version ("1.17-SNAPSHOT") apply (false)
    id("net.minecraftforge.gradle") version ("7.0.17") apply (false)
    id("net.neoforged.moddev") version ("2.0.141") apply (false)
}

val minecraftVersion by extra { "26.2" }
val forgeVersion by extra { "65.0.0" }
val neoForgeVersion by extra { "26.2.0.0-beta" }
val fabricVersion by extra { "0.19.3" }
val fabricApiVersion by extra { "0.152.1+26.2" }
val modMenuVersion by extra { "20.0.1" }
val paperApiVersion by extra { "[26.2.build,)" }
val voxelMapVersion by extra { "1.16.8" }
val forkVersion by extra { providers.gradleProperty("forkVersion").orElse(providers.gradleProperty("forkversion")).orNull ?: "0.01" }
val modrinthId by extra { providers.gradleProperty("modrinth_id").orNull ?: "cVrDroCh" }

val fullVersion by extra { "${minecraftVersion}-${voxelMapVersion}" }

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    enabled = false
}

subprojects {
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven { url = uri("https://api.modrinth.com/maven") }
    }

    java.toolchain.languageVersion = JavaLanguageVersion.of(25)

    tasks.processResources {
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(mapOf("version" to fullVersion))
        }
    }

    tasks.withType<Jar>().configureEach {
        archiveFileName.set(provider {
            val classifier = archiveClassifier.orNull?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""
            val forkSuffix = if (forkVersion.startsWith("v")) forkVersion else "v$forkVersion"
            val moduleSuffix = archiveBaseName.get()
                .substringAfter("voxelmap-", archiveBaseName.get())
            "voxelmap-x-seedmapper_${minecraftVersion}_${moduleSuffix}${classifier}_${forkSuffix}.jar"
        })
    }

    version = fullVersion
    group = "com.mamiyaotaru"

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }
}
