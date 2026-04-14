package com.mamiyaotaru.voxelmap.seedmapper;

import java.util.Locale;

public enum SeedMapperEspTarget {
    BLOCK_HIGHLIGHT("blockhighlightesp", "Block Highlight ESP"),
    ORE_VEIN("oreveinesp", "Ore Vein ESP"),
    TERRAIN("terrainesp", "Terrain ESP"),
    CANYON("canyonesp", "Canyon ESP"),
    CAVE("caveesp", "Cave ESP");

    private final String id;
    private final String displayName;

    SeedMapperEspTarget(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public String configPrefix() {
        return "SeedMapper ESP " + this.id;
    }

    public static SeedMapperEspTarget fromId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalize(value);
        for (SeedMapperEspTarget target : values()) {
            if (normalize(target.id).equals(normalized) || normalize(target.displayName).equals(normalized)) {
                return target;
            }
        }
        return null;
    }

    public static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }
}
