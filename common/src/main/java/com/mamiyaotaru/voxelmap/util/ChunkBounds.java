package com.mamiyaotaru.voxelmap.util;

/** Inclusive rectangular chunk bounds. Negative coordinates use floor division. */
public record ChunkBounds(int minX, int maxX, int minZ, int maxZ) {
    public static ChunkBounds around(int x, int z, int radius) {
        return new ChunkBounds(x - radius, x + radius, z - radius, z + radius);
    }

    public ChunkBounds align(int size, int padding) {
        return new ChunkBounds(Math.floorDiv(minX - padding, size) * size,
                Math.floorDiv(maxX + padding, size) * size + size - 1,
                Math.floorDiv(minZ - padding, size) * size,
                Math.floorDiv(maxZ + padding, size) * size + size - 1);
    }

    public boolean isEmpty() { return maxX < minX || maxZ < minZ; }
}
