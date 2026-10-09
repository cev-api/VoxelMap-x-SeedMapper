package com.mamiyaotaru.voxelmap.persistent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedPreviewTerrainSamplingTest {
    @Test void terrainWorkIsBoundedEvenWithMillionsOfBiomeTexels() {
        for (int span : new int[]{512, 4096, 65536, 1000000, 60000000}) {
            var grid = SeedPreviewTerrainSampling.layout(-span / 2, span / 2, -span / 2, span / 2);
            assertTrue((grid.width() + 1) * (grid.height() + 1) <= 131 * 131);
            assertTrue(grid.step() >= 16);
            assertTrue(grid.minX() <= -span / 2 && grid.maxX() >= span / 2);
        }
    }
    @Test void interpolationPreservesFlatTerrainAndContinuousSlopes() {
        var grid = SeedPreviewTerrainSampling.layout(-32, 32, -32, 32);
        int row = grid.width() + 1;
        int[] heights = new int[row * (grid.height() + 1)];
        java.util.Arrays.fill(heights, 128);
        assertEquals(128, SeedPreviewTerrainSampling.interpolate(grid, heights, -3, 7));
        for (int z = 0; z <= grid.height(); z++) for (int x = 0; x <= grid.width(); x++)
            heights[z * row + x] = 128 + grid.minX() + x * grid.step();
        assertEquals(125, SeedPreviewTerrainSampling.interpolate(grid, heights, -3, 7));
        assertEquals(160, SeedPreviewTerrainSampling.interpolate(grid, heights, grid.maxX(), grid.maxZ()));
    }
}
