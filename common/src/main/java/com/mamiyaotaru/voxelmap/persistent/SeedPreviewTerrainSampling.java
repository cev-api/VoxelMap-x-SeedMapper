package com.mamiyaotaru.voxelmap.persistent;

/** A bounded, world-aligned height field independent of the biome texture resolution. */
final class SeedPreviewTerrainSampling {
    private SeedPreviewTerrainSampling() { }

    static SeedPreviewSampling.Layout layout(int minX, int maxX, int minZ, int maxZ) {
        double span = Math.max((long) maxX - minX, (long) maxZ - minZ);
        return SeedPreviewSampling.layout(minX, maxX, minZ, maxZ, Math.max(16, span / 128), 130);
    }

    static int interpolate(SeedPreviewSampling.Layout grid, int[] heights, double blockX, double blockZ) {
        double gx = Math.max(0, Math.min(grid.width(), (blockX - grid.minX()) / grid.step()));
        double gz = Math.max(0, Math.min(grid.height(), (blockZ - grid.minZ()) / grid.step()));
        int x = Math.min(grid.width() - 1, (int) gx), z = Math.min(grid.height() - 1, (int) gz);
        double fx = gx - x, fz = gz - z;
        int row = grid.width() + 1, index = z * row + x;
        double upper = heights[index] * (1 - fx) + heights[index + 1] * fx;
        double lower = heights[index + row] * (1 - fx) + heights[index + row + 1] * fx;
        return (int) Math.round(upper * (1 - fz) + lower * fz);
    }
}
