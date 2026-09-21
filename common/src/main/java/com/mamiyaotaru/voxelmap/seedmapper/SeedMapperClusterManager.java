package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the explicitly requested buried-treasure cluster results for map
 * rendering. Cluster generation is intentionally command-triggered because
 * searching the complete world border is much more expensive than a normal
 * viewport marker query.
 */
public final class SeedMapperClusterManager {
    private static volatile ClusterState state = ClusterState.EMPTY;

    private SeedMapperClusterManager() {
    }

    public static List<SeedMapperMarker> buildMarkers(List<SeedMapperBuriedTreasureClusterService.ClusterResult> clusters) {
        ArrayList<SeedMapperMarker> markers = new ArrayList<>();
        if (clusters == null) {
            return markers;
        }
        for (SeedMapperBuriedTreasureClusterService.ClusterResult cluster : clusters) {
            if (cluster == null || cluster.origin() == null) {
                continue;
            }
            ChunkPos origin = cluster.origin();
            markers.add(new SeedMapperMarker(
                    SeedMapperFeature.TREASURE_CLUSTER,
                    origin.getMiddleBlockX(),
                    origin.getMiddleBlockZ(),
                    cluster.treasureCount() + (cluster.treasureCount() == 1 ? " treasure" : " treasures")));
        }
        return markers;
    }

    public static void set(String worldKey, long seed, int mcVersion, int generatorFlags,
                           int saltHash, List<SeedMapperBuriedTreasureClusterService.ClusterResult> clusters) {
        state = new ClusterState(worldIdentity(worldKey), seed, mcVersion,
                generatorFlags, saltHash, List.copyOf(buildMarkers(clusters)));
    }

    public static void clear() {
        state = ClusterState.EMPTY;
        pending = null;
    }

    public static boolean hasStateFor(String worldKey, long seed, int mcVersion, int generatorFlags, int saltHash) {
        return state.matches(worldIdentity(worldKey), seed, mcVersion, generatorFlags, saltHash);
    }

    public static boolean isPending(String worldKey, long seed, int mcVersion, int generatorFlags, int saltHash) {
        PendingRequest current = pending;
        return current != null && current.matches(worldIdentity(worldKey), seed, mcVersion, generatorFlags, saltHash);
    }

    /** Returns true when this call claimed the request and the caller should compute it. */
    public static boolean beginPending(String worldKey, long seed, int mcVersion, int generatorFlags, int saltHash) {
        String identity = worldIdentity(worldKey);
        PendingRequest current = pending;
        if (current != null && current.matches(identity, seed, mcVersion, generatorFlags, saltHash)) {
            return false;
        }
        if (state.matches(identity, seed, mcVersion, generatorFlags, saltHash)) {
            return false;
        }
        pending = new PendingRequest(identity, seed, mcVersion, generatorFlags, saltHash);
        return true;
    }

    public static void finishPending(String worldKey, long seed, int mcVersion, int generatorFlags, int saltHash,
                                     List<SeedMapperBuriedTreasureClusterService.ClusterResult> clusters) {
        String identity = worldIdentity(worldKey);
        PendingRequest current = pending;
        if (current == null || !current.matches(identity, seed, mcVersion, generatorFlags, saltHash)) {
            state = new ClusterState(identity, seed, mcVersion, generatorFlags, saltHash, List.copyOf(buildMarkers(clusters)));
            return;
        }
        pending = null;
        state = new ClusterState(identity, seed, mcVersion, generatorFlags, saltHash, List.copyOf(buildMarkers(clusters)));
    }

    /**
     * The stored world key intentionally drops the dimension suffix: buried
     * treasure clusters only generate in the overworld, and the world map can
     * be open on a different dimension than the one the player currently
     * stands in.
     */
    private static String worldIdentity(String worldKey) {
        if (worldKey == null) {
            return "unknown";
        }
        int separator = worldKey.lastIndexOf('|');
        return separator < 0 ? worldKey : worldKey.substring(0, separator);
    }

    public static List<SeedMapperMarker> getMarkersInBounds(String worldKey, long seed, int mcVersion,
                                                             int generatorFlags, int saltHash,
                                                             int dimension, int minX, int maxX,
                                                             int minZ, int maxZ) {
        if (dimension != Cubiomes.DIM_OVERWORLD()) {
            return List.of();
        }
        ClusterState current = state;
        if (!current.matches(worldIdentity(worldKey), seed, mcVersion, generatorFlags, saltHash)) {
            return List.of();
        }
        return current.markers.stream()
                .filter(marker -> marker.blockX() >= minX && marker.blockX() <= maxX
                        && marker.blockZ() >= minZ && marker.blockZ() <= maxZ)
                .toList();
    }

    private static volatile PendingRequest pending;

    private record PendingRequest(String worldKey, long seed, int mcVersion, int generatorFlags, int saltHash) {
        private boolean matches(String worldKey, long seed, int mcVersion, int generatorFlags, int saltHash) {
            return this.seed == seed
                    && this.mcVersion == mcVersion
                    && this.generatorFlags == generatorFlags
                    && this.saltHash == saltHash
                    && java.util.Objects.equals(this.worldKey, worldKey);
        }
    }

    private record ClusterState(String worldKey, long seed, int mcVersion, int generatorFlags,
                                int saltHash, List<SeedMapperMarker> markers) {
        private static final ClusterState EMPTY = new ClusterState("", Long.MIN_VALUE,
                Integer.MIN_VALUE, 0, 0, List.of());

        private boolean matches(String worldKey, long seed, int mcVersion, int generatorFlags, int saltHash) {
            return this.seed == seed
                    && this.mcVersion == mcVersion
                    && this.generatorFlags == generatorFlags
                    && this.saltHash == saltHash
                    && java.util.Objects.equals(this.worldKey, worldKey);
        }
    }
}
