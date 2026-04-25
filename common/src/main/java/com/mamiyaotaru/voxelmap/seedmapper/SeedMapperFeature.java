package com.mamiyaotaru.voxelmap.seedmapper;

import net.minecraft.resources.Identifier;

public enum SeedMapperFeature {
    DESERT_PYRAMID("desert_pyramid", "seedmapper.feature.desert_pyramid", com.github.cubiomes.Cubiomes.Desert_Pyramid(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/desert_pyramid.png", true),
    JUNGLE_PYRAMID("jungle_pyramid", "seedmapper.feature.jungle_pyramid", com.github.cubiomes.Cubiomes.Jungle_Pyramid(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/jungle_pyramid.png", true),
    SWAMP_HUT("swamp_hut", "seedmapper.feature.swamp_hut", com.github.cubiomes.Cubiomes.Swamp_Hut(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/swamp_hut.png", false),
    STRONGHOLD("stronghold", "seedmapper.feature.stronghold", -1, com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/stronghold.png", false),
    IGLOO("igloo", "seedmapper.feature.igloo", com.github.cubiomes.Cubiomes.Igloo(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/igloo.png", true),
    VILLAGE("village", "seedmapper.feature.village", com.github.cubiomes.Cubiomes.Village(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/village.png", false),
    OCEAN_RUIN("ocean_ruin", "seedmapper.feature.ocean_ruin", com.github.cubiomes.Cubiomes.Ocean_Ruin(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/ocean_ruin.png", false),
    SHIPWRECK("shipwreck", "seedmapper.feature.shipwreck", com.github.cubiomes.Cubiomes.Shipwreck(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/shipwreck.png", true),
    MONUMENT("monument", "seedmapper.feature.monument", com.github.cubiomes.Cubiomes.Monument(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/monument.png", false),
    MANSION("mansion", "seedmapper.feature.mansion", com.github.cubiomes.Cubiomes.Mansion(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/mansion.png", false),
    OUTPOST("pillager_outpost", "seedmapper.feature.outpost", com.github.cubiomes.Cubiomes.Outpost(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/pillager_outpost.png", true),
    RUINED_PORTAL("ruined_portal", "seedmapper.feature.ruined_portal", com.github.cubiomes.Cubiomes.Ruined_Portal(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/ruined_portal.png", true),
    ANCIENT_CITY("ancient_city", "seedmapper.feature.ancient_city", com.github.cubiomes.Cubiomes.Ancient_City(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/ancient_city.png", false),
    TREASURE("buried_treasure", "seedmapper.feature.buried_treasure", com.github.cubiomes.Cubiomes.Treasure(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/buried_treasure.png", true),
    FORTRESS("fortress", "seedmapper.feature.fortress", com.github.cubiomes.Cubiomes.Fortress(), com.github.cubiomes.Cubiomes.DIM_NETHER(), "images/seedmapper/cubiomes_viewer_icons/fortress.png", true),
    BASTION("bastion_remnant", "seedmapper.feature.bastion", com.github.cubiomes.Cubiomes.Bastion(), com.github.cubiomes.Cubiomes.DIM_NETHER(), "images/seedmapper/cubiomes_viewer_icons/bastion_remnant.png", true),
    END_CITY("end_city", "seedmapper.feature.end_city", com.github.cubiomes.Cubiomes.End_City(), com.github.cubiomes.Cubiomes.DIM_END(), "images/seedmapper/cubiomes_viewer_icons/end_city.png", true),
    ELYTRA("end_city_ship", "seedmapper.feature.elytra", com.github.cubiomes.Cubiomes.End_City(), com.github.cubiomes.Cubiomes.DIM_END(), "images/seedmapper/cubiomes_viewer_icons/elytra.png", true),
    END_GATEWAY("end_gateway", "seedmapper.feature.end_gateway", com.github.cubiomes.Cubiomes.End_Gateway(), com.github.cubiomes.Cubiomes.DIM_END(), "images/seedmapper/cubiomes_viewer_icons/end_gateway.png", false),
    TRAIL_RUINS("trail_ruins", "seedmapper.feature.trail_ruins", com.github.cubiomes.Cubiomes.Trail_Ruins(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/trail_ruins.png", false),
    TRIAL_CHAMBERS("trial_chambers", "seedmapper.feature.trial_chambers", com.github.cubiomes.Cubiomes.Trial_Chambers(), com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/trial_chambers.png", false),
    IRON_ORE_VEIN("iron_ore_vein", "seedmapper.feature.iron_ore_vein", -1, com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/feature_icons/iron_ore_vein.png", false),
    COPPER_ORE_VEIN("copper_ore_vein", "seedmapper.feature.copper_ore_vein", -1, com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/feature_icons/copper_ore_vein.png", false),
    SLIME_CHUNK("slime_chunk", "seedmapper.feature.slime_chunk", -1, com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/feature_icons/slime_chunk.png", false),
    DATAPACK_STRUCTURE("datapack_structure", "seedmapper.feature.datapack_structure", -1, Integer.MIN_VALUE, "images/seedmapper/feature_icons/waypoint.png", false),
    WORLD_SPAWN("world_spawn", "seedmapper.feature.world_spawn", -1, com.github.cubiomes.Cubiomes.DIM_OVERWORLD(), "images/seedmapper/cubiomes_viewer_icons/world_spawn.png", false),
    NETHER_PORTAL("nether_portal", "seedmapper.feature.nether_portal", -1, Integer.MIN_VALUE, "minecraft:textures/block/nether_portal.png", false),
    END_PORTAL("end_portal", "seedmapper.feature.end_portal", -1, Integer.MIN_VALUE, "minecraft:textures/block/end_portal_frame_top.png", false),
    END_BEACON("end_beacon", "seedmapper.feature.end_beacon", -1, Integer.MIN_VALUE, "minecraft:textures/block/bedrock.png", false);

    private final String id;
    private final String translationKey;
    private final int structureId;
    private final int dimension;
    private final Identifier icon;
    private final boolean lootable;

    SeedMapperFeature(String id, String translationKey, int structureId, int dimension, String iconPath, boolean lootable) {
        this.id = id;
        this.translationKey = translationKey;
        this.structureId = structureId;
        this.dimension = dimension;
        this.icon = parseIcon(iconPath);
        this.lootable = lootable;
    }

    private static Identifier parseIcon(String iconPath) {
        if (iconPath.contains(":")) {
            Identifier parsed = Identifier.tryParse(iconPath);
            if (parsed != null) {
                return parsed;
            }
        }
        return Identifier.fromNamespaceAndPath("voxelmap", iconPath);
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public int structureId() {
        return structureId;
    }

    public int dimension() {
        return dimension;
    }

    public boolean availableInDimension(int currentDimension) {
        if (currentDimension == Integer.MIN_VALUE) {
            return true;
        }
        if (this == DATAPACK_STRUCTURE) {
            return true;
        }
        if (this == NETHER_PORTAL || this == END_PORTAL || this == END_BEACON) {
            return true;
        }
        if (this == RUINED_PORTAL) {
            return currentDimension == com.github.cubiomes.Cubiomes.DIM_OVERWORLD()
                    || currentDimension == com.github.cubiomes.Cubiomes.DIM_NETHER();
        }
        return this.dimension == currentDimension;
    }

    public Identifier icon() {
        return icon;
    }

    public boolean lootable() {
        return lootable;
    }
}
