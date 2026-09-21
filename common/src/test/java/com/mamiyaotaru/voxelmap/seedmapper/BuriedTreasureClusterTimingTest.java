package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the whole-world-border buried-treasure cluster search. The world map
 * runs this in the background, so it has to terminate in reasonable time and be
 * cached per seed.
 */
class BuriedTreasureClusterTimingTest {
    @Test
    void clusterSearchIsBoundedAndCached() {
        long start = System.nanoTime();
        List<SeedMapperBuriedTreasureClusterService.ClusterResult> clusters =
                SeedMapperBuriedTreasureClusterService.find(12345L, Cubiomes.MC_26_3(), 0, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertNotNull(clusters);
        System.out.println("BURIED_TREASURE_CLUSTER_MS=" + elapsedMs + " clusters=" + clusters.size());
        assertTrue(elapsedMs < 30_000L, "cluster search took " + elapsedMs + "ms");

        long cachedStart = System.nanoTime();
        List<SeedMapperBuriedTreasureClusterService.ClusterResult> cached =
                SeedMapperBuriedTreasureClusterService.find(12345L, Cubiomes.MC_26_3(), 0, null);
        long cachedMs = (System.nanoTime() - cachedStart) / 1_000_000L;
        System.out.println("BURIED_TREASURE_CLUSTER_CACHED_MS=" + cachedMs);
        assertSame(clusters, cached, "repeat queries must be served from cache");
    }

    @Test
    void clusterMarkersCarryTreasureCounts() {
        List<SeedMapperBuriedTreasureClusterService.ClusterResult> clusters =
                SeedMapperBuriedTreasureClusterService.find(12345L, Cubiomes.MC_26_3(), 0, null);
        List<SeedMapperMarker> markers = SeedMapperClusterManager.buildMarkers(clusters);
        assertEquals(clusters.size(), markers.size());
        for (SeedMapperMarker marker : markers) {
            assertEquals(SeedMapperFeature.TREASURE_CLUSTER, marker.feature());
            assertTrue(marker.label() != null && marker.label().contains("treasure"), marker.label());
        }
    }
}
