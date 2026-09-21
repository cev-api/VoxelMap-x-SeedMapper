package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedMapperMapBiomeSamplerTest {
    @Test
    void samplesARealBiomePaletteOffTheRenderThread() throws Exception {
        SeedMapperMapBiomeSampler.CacheKey key = new SeedMapperMapBiomeSampler.CacheKey(
                12345L, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_26_3(), 0,
                -512, 512, -384, 384, 64);
        SeedMapperMapBiomeSampler.Sample sample = SeedMapperMapBiomeSampler.request(key)
                .get(15, TimeUnit.SECONDS);

        assertTrue(sample.available());
        assertNotEquals(0, sample.colorAt(0, 0));
        assertNotEquals(0, sample.colorAt(SeedMapperMapBiomeSampler.SAMPLE_WIDTH - 1,
                SeedMapperMapBiomeSampler.SAMPLE_HEIGHT - 1));
    }
}
