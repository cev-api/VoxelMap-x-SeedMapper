plugins {
    id("java")
    id("idea")
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion: String by rootProject.extra
val fabricVersion: String by rootProject.extra
val fabricApiVersion: String by rootProject.extra
val modMenuVersion: String by rootProject.extra
val voxelConfigVersion: String by rootProject.extra

val fullVersion: String by rootProject.extra
val forkVersion: String by rootProject.extra
val modrinthId: String by rootProject.extra

base {
    archivesName.set("voxelmap-fabric")
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")

    implementation("net.fabricmc:fabric-loader:${fabricVersion}")
    implementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion}")
    compileOnly("maven.modrinth:modmenu:${modMenuVersion}")



    implementation(project.project(":server-common").sourceSets.getByName("main").output)
    implementation(project.project(":common").sourceSets.getByName("main").output)
}

tasks.named("compileTestJava").configure {
    enabled = false
}

tasks.named("test").configure {
    enabled = false
}

tasks.named("validateAccessWidener") {
    mustRunAfter(project(":common").tasks.named("genSourcesWithVineflower"))
}

loom {
    if (project(":common").file("src/main/resources/voxelmap.accesswidener").exists())
        accessWidenerPath.set(project(":common").file("src/main/resources/voxelmap.accesswidener"))

    // Register the shared source sets as part of the Fabric mod in development
    // runs as well as in the assembled jar. Without this, Fabric's mixin loader
    // can see the shared mixin config but not its classes on a multi-project run.
    mods {
        create("voxelmap-cevapi") {
            sourceSet(sourceSets.main.get())
            sourceSet(project(":common").sourceSets.main.get())
            sourceSet(project(":server-common").sourceSets.main.get())
        }
    }

    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir(providers.gradleProperty("smokeRunDir").orElse("run").get())
        }
    }
}

tasks {
    processResources {
        from(project.project(":common").sourceSets.main.get().resources)
        inputs.property("version", fullVersion)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to fullVersion))
        }
        filesMatching("voxelmap-build.properties") {
            expand(
                mapOf(
                    "forkVersion" to forkVersion,
                    "modrinthId" to modrinthId
                )
            )
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(zipTree(project.project(":common").tasks.named("shadowJar").map { (it as org.gradle.jvm.tasks.Jar).archiveFile }))
        from(zipTree(project.project(":server-common").tasks.jar.get().archiveFile))
    }

    jar.get().destinationDirectory = rootDir.resolve("build").resolve("libs")
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }

    repositories {
        maven("file://${System.getenv("local_maven")}")
    }
}
