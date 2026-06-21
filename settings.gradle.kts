rootProject.name = "voxelmap"

pluginManagement {
    repositories {
        mavenLocal()
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
    "forge" -> include("forge")
    "neoforge" -> include("neoforge")
    "paper" -> include("paper")
    else -> {
        include("fabric")
        include("paper")
        include("forge")
        include("neoforge")
    }
}
