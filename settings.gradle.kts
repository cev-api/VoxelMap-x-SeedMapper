rootProject.name = "voxelmap"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        gradlePluginPortal()
    }
}

include("common")
include("server-common")

when (providers.gradleProperty("targetLoader").orNull) {
    "fabric" -> include("fabric")
    "forge" -> error("Forge has no Minecraft 26.3 release. Use Fabric or NeoForge; the Forge sources remain for a future port.")
    "neoforge" -> include("neoforge")
    "paper" -> include("paper")
    else -> {
        include("fabric")
        include("paper")
        include("neoforge")
    }
}
