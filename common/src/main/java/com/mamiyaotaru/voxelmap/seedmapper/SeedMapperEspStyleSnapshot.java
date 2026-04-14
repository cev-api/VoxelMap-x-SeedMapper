package com.mamiyaotaru.voxelmap.seedmapper;

public record SeedMapperEspStyleSnapshot(
        int outlineColor,
        float outlineAlpha,
        boolean fillEnabled,
        int fillColor,
        float fillAlpha,
        boolean rainbow,
        float rainbowSpeed
) {
}
