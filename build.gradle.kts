import org.gradle.api.tasks.bundling.Jar

plugins {
    id("java")
    id("net.fabricmc.fabric-loom") version ("1.18-SNAPSHOT") apply (false)
    id("net.minecraftforge.gradle") version ("7.0.40") apply (false)
    id("net.neoforged.moddev") version ("2.0.147") apply (false)
    id("com.gradleup.shadow") version ("9.6.1") apply (false)
}

val minecraftVersion by extra { "26.3" }
val forgeVersion by extra { "66.0.3" }
val neoForgeVersion by extra { "26.3.0.16-beta" }
val fabricVersion by extra { "0.19.5" }
val fabricApiVersion by extra { "0.161.0+26.3" }
val modMenuVersion by extra { "21.0.0" }
val paperApiVersion by extra { "[26.3.build,)" }
val forkVersion by extra { providers.gradleProperty("forkVersion").orElse(providers.gradleProperty("forkversion")).orNull ?: "0.01" }
val modrinthId by extra { providers.gradleProperty("modrinth_id").orNull ?: "cVrDroCh" }
val voxelConfigVersion by extra { "1.0.2" }
val geckolibVersion by extra { "5.5.7" }
val voxelMapVersion by extra { "1.16.13" }

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
        mavenLocal()
        mavenCentral()
        maven {
            name = "VoxelConfig"
            url = uri("https://www.iani.de/nexus/content/repositories/releases/")
            content { includeGroup("de.voxelmap") }
        }
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "Geckolib Maven"
            url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        }
        maven {
            name = "Brokkonaut"
            url = uri("https://www.iani.de/nexus/content/groups/public/")
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
