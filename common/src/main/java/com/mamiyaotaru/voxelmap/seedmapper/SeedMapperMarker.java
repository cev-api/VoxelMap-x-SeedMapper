package com.mamiyaotaru.voxelmap.seedmapper;

public record SeedMapperMarker(SeedMapperFeature feature, int blockX, int blockZ, String label) {
    public SeedMapperMarker(SeedMapperFeature feature, int blockX, int blockZ) {
        this(feature, blockX, blockZ, "");
    }
}
