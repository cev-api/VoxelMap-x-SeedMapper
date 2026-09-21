package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;
import com.github.cubiomes.StructureConfig;
import com.github.cubiomes.TerrainNoise;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCompatibilityTest {
    @Test
    void bundledLibrarySupports263AndKeepsOlderBiomePredictions() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, Cubiomes.MC_1_16_1(), 0);
            Cubiomes.applySeed(generator, Cubiomes.DIM_OVERWORLD(), 12345L);
            assertEquals(Cubiomes.taiga_mountains(), Cubiomes.getBiomeAt(generator, 1, 0, 0, 0));
            Cubiomes.setupGenerator(generator, Cubiomes.MC_1_18_2(), 0);
            Cubiomes.applySeed(generator, Cubiomes.DIM_NETHER(), 12345L);
            assertEquals(Cubiomes.nether_wastes(), Cubiomes.getBiomeAt(generator, 1, 0, 0, 0));
            Cubiomes.setupGenerator(generator, Cubiomes.MC_26_3(), 0);
            Cubiomes.applySeed(generator, Cubiomes.DIM_OVERWORLD(), 12345L);
            assertTrue(Cubiomes.getBiomeAt(generator, 4, 0, 16, 0) >= 0);
            assertEquals(Cubiomes.MC_26_3(), Cubiomes.MC_NEWEST());
        }
    }

    @Test
    void terrainAbiReturnsWorldCoordinatesWithoutABlockBuffer() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment terrain = TerrainNoise.allocate(arena);
            Cubiomes.setupTerrainNoise(terrain, Cubiomes.MC_26_3(), 0);
            Cubiomes.initTerrainNoise(terrain, 12345L, Cubiomes.DIM_OVERWORLD());
            MemorySegment heights = arena.allocate(Cubiomes.C_INT, 256);
            Cubiomes.generateRegion(terrain, 0, 0, 1, 1, MemorySegment.NULL, 0, 48, heights, 1);
            for (int i = 0; i < 256; i++) {
                int height = heights.getAtIndex(Cubiomes.C_INT, i);
                assertTrue(height >= -64 && height <= 320, "height=" + height);
            }
        }
    }

    @Test
    void terrainAbiSupportsNetherAndEndHeightOnlySampling() {
        for (int dimension : new int[]{Cubiomes.DIM_NETHER(), Cubiomes.DIM_END()}) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment terrain = TerrainNoise.allocate(arena);
                MemorySegment heights = arena.allocate(Cubiomes.C_INT, 256);
                Cubiomes.setupTerrainNoise(terrain, Cubiomes.MC_26_3(), 0);
                Cubiomes.initTerrainNoise(terrain, 12345L, dimension);
                Cubiomes.generateRegion(terrain, 0, 0, 1, 1,
                        MemorySegment.NULL, 0, dimension == Cubiomes.DIM_END() ? 32 : 16, heights, 1);
                for (int i = 0; i < 256; i++) {
                    int height = heights.getAtIndex(Cubiomes.C_INT, i);
                    assertTrue(height >= 0 && height <= 128, "dimension=" + dimension + ", height=" + height);
                }
            }
        }
    }

    @Test
    void customStructureProviderOverridesOnlyTheActiveSalt() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment config = StructureConfig.allocate(arena);
            assertNotEquals(0, Cubiomes.getStructureConfig(
                    Cubiomes.Trial_Chambers(), Cubiomes.MC_26_3(), config));
            int defaultSalt = StructureConfig.salt(config);
            int customSalt = defaultSalt ^ 0x13579BDF;

            SeedMapperNative.withStructureSalts(
                    Map.of(Cubiomes.Trial_Chambers(), customSalt),
                    () -> {
                        assertNotEquals(0, Cubiomes.getStructureConfig(
                                Cubiomes.Trial_Chambers(), Cubiomes.MC_26_3(), config));
                        assertEquals(customSalt, StructureConfig.salt(config));
                    });

            assertNotEquals(0, Cubiomes.getStructureConfig(
                    Cubiomes.Trial_Chambers(), Cubiomes.MC_26_3(), config));
            assertEquals(defaultSalt, StructureConfig.salt(config));
        }
    }

    @Test
    void only26_3FeaturesStayHiddenOn26_2() {
        assertFalse(SeedMapperFeature.ABANDONED_CAMP.availableInVersion(Cubiomes.MC_26_2()));
        assertTrue(SeedMapperFeature.ABANDONED_CAMP.availableInVersion(Cubiomes.MC_26_3()));
    }
}
