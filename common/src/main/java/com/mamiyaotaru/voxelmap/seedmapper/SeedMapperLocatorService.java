package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;
import com.github.cubiomes.Piece;
import com.github.cubiomes.Pos;
import com.github.cubiomes.Pos3;
import com.github.cubiomes.StrongholdIter;
import com.github.cubiomes.StructureConfig;
import com.github.cubiomes.SurfaceNoise;
import com.github.cubiomes.OreVeinParameters;
import net.minecraft.util.Mth;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SeedMapperLocatorService {
    private static final SeedMapperLocatorService INSTANCE = new SeedMapperLocatorService();
    private static final int MAX_CACHE_ENTRIES = 48;

    private volatile long lastComputeMs = 0L;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VoxelMap-SeedMapper-Locator");
        thread.setDaemon(true);
        return thread;
    });
    private final Object requestLock = new Object();
    private final LinkedHashMap<QueryKey, List<SeedMapperMarker>> queryCache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<QueryKey, List<SeedMapperMarker>> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };
    private volatile QueryKey lastCompletedQuery;
    private volatile QueryKey runningQuery;
    private volatile QueryKey queuedQuery;

    private SeedMapperLocatorService() {
    }

    public static SeedMapperLocatorService get() {
        return INSTANCE;
    }

    public QueryResult queryWithStatus(long seed, int dimension, int mcVersion, int generatorFlags, int minX, int maxX, int minZ, int maxZ, SeedMapperSettingsManager settings) {
        return queryWithStatus(seed, dimension, mcVersion, generatorFlags, minX, maxX, minZ, maxZ, settings, null);
    }

    public QueryResult queryWithStatus(long seed, int dimension, int mcVersion, int generatorFlags, int minX, int maxX, int minZ, int maxZ, SeedMapperSettingsManager settings, String datapackWorldKey) {
        QueryKey key = buildQueryKey(seed, dimension, mcVersion, generatorFlags, minX, maxX, minZ, maxZ, settings, datapackWorldKey);
        synchronized (requestLock) {
            List<SeedMapperMarker> exact = queryCache.get(key);
            if (exact != null) {
                return new QueryResult(exact, true);
            }

            queueQueryLocked(key);
            return new QueryResult(findFallbackLocked(key), false);
        }
    }

    public List<SeedMapperMarker> query(long seed, int dimension, int mcVersion, int generatorFlags, int minX, int maxX, int minZ, int maxZ, SeedMapperSettingsManager settings) {
        return queryWithStatus(seed, dimension, mcVersion, generatorFlags, minX, maxX, minZ, maxZ, settings, null).markers();
    }

    public List<SeedMapperMarker> queryBlocking(long seed, int dimension, int mcVersion, int generatorFlags, int minX, int maxX, int minZ, int maxZ, SeedMapperSettingsManager settings) {
        return queryBlocking(seed, dimension, mcVersion, generatorFlags, minX, maxX, minZ, maxZ, settings, null);
    }

    public List<SeedMapperMarker> queryBlocking(long seed, int dimension, int mcVersion, int generatorFlags, int minX, int maxX, int minZ, int maxZ, SeedMapperSettingsManager settings, String datapackWorldKey) {
        QueryKey key = buildQueryKey(seed, dimension, mcVersion, generatorFlags, minX, maxX, minZ, maxZ, settings, datapackWorldKey);
        List<SeedMapperMarker> result;
        try {
            result = computeMarkers(key, false);
        } catch (RuntimeException ignored) {
            result = List.of();
        }
        synchronized (requestLock) {
            cacheResultLocked(key, result);
        }
        lastComputeMs = System.currentTimeMillis();
        return result;
    }

    private void queueQuery(QueryKey key) {
        synchronized (requestLock) {
            queueQueryLocked(key);
        }
    }

    private void queueQueryLocked(QueryKey key) {
        if (queryCache.containsKey(key) || key.equals(runningQuery) || key.equals(queuedQuery)) {
            return;
        }
        queuedQuery = key;
        if (runningQuery == null) {
            scheduleNextLocked();
        }
    }

    private void scheduleNextLocked() {
        QueryKey next = queuedQuery;
        if (next == null) {
            return;
        }
        queuedQuery = null;
        runningQuery = next;
        worker.execute(() -> {
            List<SeedMapperMarker> result;
            try {
                result = computeMarkers(next, true);
            } catch (RuntimeException ignored) {
                result = List.of();
            }
            lastComputeMs = System.currentTimeMillis();
            synchronized (requestLock) {
                cacheResultLocked(next, result);
                runningQuery = null;
                if (queuedQuery != null && !queryCache.containsKey(queuedQuery)) {
                    scheduleNextLocked();
                }
            }
        });
    }

    private void cacheResultLocked(QueryKey key, List<SeedMapperMarker> result) {
        queryCache.put(key, result);
        lastCompletedQuery = key;
    }

    private List<SeedMapperMarker> findFallbackLocked(QueryKey key) {
        long requestArea = area(key);
        List<SeedMapperMarker> bestMarkers = List.of();
        double bestScore = -1.0D;

        for (Map.Entry<QueryKey, List<SeedMapperMarker>> entry : queryCache.entrySet()) {
            QueryKey candidate = entry.getKey();
            if (!candidate.compatibleForFallback(key)) {
                continue;
            }

            long overlap = overlapArea(candidate, key);
            if (overlap <= 0L) {
                continue;
            }

            long candidateArea = area(candidate);
            if (candidateArea > requestArea * 6L) {
                continue;
            }

            double score = (double) overlap / (double) Math.max(1L, candidateArea);
            if (score > bestScore) {
                bestScore = score;
                bestMarkers = entry.getValue();
            }
        }

        if (!bestMarkers.isEmpty()) {
            return bestMarkers;
        }

        if (lastCompletedQuery != null && lastCompletedQuery.compatibleForFallback(key)) {
            List<SeedMapperMarker> latest = queryCache.get(lastCompletedQuery);
            if (latest != null && area(lastCompletedQuery) <= requestArea * 2L) {
                return latest;
            }
        }

        return List.of();
    }

    private static long area(QueryKey key) {
        long width = Math.max(1L, (long) key.maxX - key.minX + 1L);
        long height = Math.max(1L, (long) key.maxZ - key.minZ + 1L);
        return width * height;
    }

    private static long overlapArea(QueryKey a, QueryKey b) {
        int oxMin = Math.max(a.minX, b.minX);
        int oxMax = Math.min(a.maxX, b.maxX);
        int ozMin = Math.max(a.minZ, b.minZ);
        int ozMax = Math.min(a.maxZ, b.maxZ);
        if (oxMax < oxMin || ozMax < ozMin) {
            return 0L;
        }

        long width = (long) oxMax - oxMin + 1L;
        long height = (long) ozMax - ozMin + 1L;
        return width * height;
    }

    private QueryKey buildQueryKey(long seed, int dimension, int mcVersion, int generatorFlags, int minX, int maxX, int minZ, int maxZ, SeedMapperSettingsManager settings, String datapackWorldKey) {
        // Use coarser snapping so viewport panning does not invalidate cache every pixel.
        int spanX = Math.max(1, Math.abs(maxX - minX));
        int spanZ = Math.max(1, Math.abs(maxZ - minZ));
        int span = Math.max(spanX, spanZ);
        int snap = Math.max(128, Math.min(1024, span / 8));
        minX = floorDiv(minX, snap) * snap;
        maxX = floorDiv(maxX + snap - 1, snap) * snap;
        minZ = floorDiv(minZ, snap) * snap;
        maxZ = floorDiv(maxZ + snap - 1, snap) * snap;

        long featureMask = 0L;
        for (SeedMapperFeature feature : SeedMapperFeature.values()) {
            if (settings.isFeatureEnabled(feature)) {
                featureMask |= (1L << feature.ordinal());
            }
        }
        return new QueryKey(seed, dimension, mcVersion, generatorFlags, minX, maxX, minZ, maxZ, settings.showLootableOnly, featureMask, settings.getDatapackMarkerHash(), settings, datapackWorldKey);
    }

    private List<SeedMapperMarker> computeMarkers(QueryKey key, boolean fastMode) {
        SeedMapperNative.ensureLoaded();
        synchronized (SeedMapperNative.cubiomesLock()) {
            try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, key.mcVersion, key.generatorFlags);
            Cubiomes.applySeed(generator, key.dimension, key.seed);

            MemorySegment surfaceNoise = SurfaceNoise.allocate(arena);
            Cubiomes.initSurfaceNoise(surfaceNoise, key.dimension, key.seed);

            MemorySegment structureConfig = StructureConfig.allocate(arena);
            MemorySegment structurePos = Pos.allocate(arena);

            ArrayList<SeedMapperMarker> markers = new ArrayList<>();

            if ((key.featureMask & (1L << SeedMapperFeature.IRON_ORE_VEIN.ordinal())) != 0L) {
                addOreVeinSamples(arena, key, markers, false, fastMode);
            }
            if ((key.featureMask & (1L << SeedMapperFeature.COPPER_ORE_VEIN.ordinal())) != 0L) {
                addOreVeinSamples(arena, key, markers, true, fastMode);
            }
            if ((key.featureMask & (1L << SeedMapperFeature.SLIME_CHUNK.ordinal())) != 0L) {
                addSlimeChunkSamples(key, markers, fastMode);
            }
            if ((key.featureMask & (1L << SeedMapperFeature.DATAPACK_STRUCTURE.ordinal())) != 0L) {
                if (key.settings.datapackEnabled) {
                    for (SeedMapperMarker marker : SeedMapperImportedDatapackManager.queryImportedMarkers(
                            key.settings.datapackCachePath,
                            key.seed,
                            key.minX,
                            key.maxX,
                            key.minZ,
                            key.maxZ
                    )) {
                        if (isDatapackStructureVisible(key, marker.label())) {
                            markers.add(marker);
                        }
                    }
                }
                for (SeedMapperSettingsManager.DatapackMarker marker : key.settings.getDatapackLocatedMarkers(key.dimension, key.minX, key.maxX, key.minZ, key.maxZ)) {
                    if (isDatapackStructureVisible(key, marker.structureId())) {
                        markers.add(new SeedMapperMarker(SeedMapperFeature.DATAPACK_STRUCTURE, marker.x(), marker.z(), marker.structureId()));
                    }
                }
            }

            for (SeedMapperFeature feature : SeedMapperFeature.values()) {
                if ((key.featureMask & (1L << feature.ordinal())) == 0L) {
                    continue;
                }
                if (feature == SeedMapperFeature.ELYTRA) {
                    continue;
                }
                if (feature.structureId() < 0 || !feature.availableInDimension(key.dimension)) {
                    continue;
                }
                if (key.showLootableOnly && !feature.lootable()) {
                    continue;
                }

                if (Cubiomes.getStructureConfig(feature.structureId(), key.mcVersion, structureConfig) == 0) {
                    continue;
                }

                int regionSize = StructureConfig.regionSize(structureConfig) << 4;
                if (regionSize <= 0) {
                    continue;
                }

                int minRegionX = Mth.floor((double) key.minX / (double) regionSize) - 1;
                int maxRegionX = Mth.floor((double) key.maxX / (double) regionSize) + 1;
                int minRegionZ = Mth.floor((double) key.minZ / (double) regionSize) - 1;
                int maxRegionZ = Mth.floor((double) key.maxZ / (double) regionSize) + 1;

                for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                    for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                        if (Cubiomes.getStructurePos(feature.structureId(), key.mcVersion, key.seed, regionX, regionZ, structurePos) == 0) {
                            continue;
                        }

                        int blockX = Pos.x(structurePos);
                        int blockZ = Pos.z(structurePos);
                        if (blockX < key.minX || blockX > key.maxX || blockZ < key.minZ || blockZ > key.maxZ) {
                            continue;
                        }

                        if (Cubiomes.isViableStructurePos(feature.structureId(), generator, blockX, blockZ, 0) == 0) {
                            continue;
                        }

                        if (Cubiomes.isViableStructureTerrain(feature.structureId(), generator, blockX, blockZ) == 0) {
                            continue;
                        }

                        if (feature == SeedMapperFeature.END_CITY && Cubiomes.isViableEndCityTerrain(generator, surfaceNoise, blockX, blockZ) == 0) {
                            continue;
                        }

                        markers.add(new SeedMapperMarker(feature, blockX, blockZ));
                        if (feature == SeedMapperFeature.END_CITY && (key.featureMask & (1L << SeedMapperFeature.ELYTRA.ordinal())) != 0L) {
                            addElytraShipMarkers(arena, key, blockX, blockZ, markers);
                        }
                    }
                }
            }

        if (key.dimension == Cubiomes.DIM_OVERWORLD()) {
            addStrongholds(arena, key, generator, markers);
            addWorldSpawn(arena, key, generator, markers);
            }

                return Collections.unmodifiableList(markers);
            }
        }
    }

    private static boolean isDatapackStructureVisible(QueryKey key, String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return true;
        }
        String worldKey = key.datapackWorldKey();
        return worldKey == null || worldKey.isBlank() || key.settings.isDatapackStructureEnabled(worldKey, structureId);
    }

    private static void addStrongholds(Arena arena, QueryKey key, MemorySegment generator, List<SeedMapperMarker> out) {
        if (key.showLootableOnly) {
            return;
        }

        MemorySegment strongholdIter = StrongholdIter.allocate(arena);
        Cubiomes.initFirstStronghold(arena, strongholdIter, key.mcVersion, key.seed);

        int count = key.mcVersion <= Cubiomes.MC_1_8() ? 3 : 128;
        for (int i = 0; i < count; i++) {
            if (Cubiomes.nextStronghold(strongholdIter, generator) == 0) {
                break;
            }

            MemorySegment pos = StrongholdIter.pos(strongholdIter);
            int blockX = Pos.x(pos);
            int blockZ = Pos.z(pos);
            if (blockX < key.minX || blockX > key.maxX || blockZ < key.minZ || blockZ > key.maxZ) {
                continue;
            }
            out.add(new SeedMapperMarker(SeedMapperFeature.STRONGHOLD, blockX, blockZ));
        }
    }

    private static void addWorldSpawn(Arena arena, QueryKey key, MemorySegment generator, List<SeedMapperMarker> out) {
        if (key.showLootableOnly) {
            return;
        }

        MemorySegment spawnPos = Cubiomes.getSpawn(arena, generator);
        int blockX = Pos.x(spawnPos);
        int blockZ = Pos.z(spawnPos);
        if (blockX >= key.minX && blockX <= key.maxX && blockZ >= key.minZ && blockZ <= key.maxZ) {
            out.add(new SeedMapperMarker(SeedMapperFeature.WORLD_SPAWN, blockX, blockZ));
        }
    }

    private static void addElytraShipMarkers(Arena arena, QueryKey key, int cityX, int cityZ, List<SeedMapperMarker> out) {
        MemorySegment pieces = Piece.allocateArray(512, arena);
        int numPieces = Cubiomes.getEndCityPieces(pieces, key.seed, cityX >> 4, cityZ >> 4);
        for (int i = 0; i < numPieces; i++) {
            MemorySegment piece = Piece.asSlice(pieces, i);
            if (Piece.type(piece) != Cubiomes.END_SHIP()) {
                continue;
            }
            MemorySegment shipPos = Piece.pos(piece);
            int sx = Pos3.x(shipPos);
            int sz = Pos3.z(shipPos);
            if (sx >= key.minX && sx <= key.maxX && sz >= key.minZ && sz <= key.maxZ) {
                out.add(new SeedMapperMarker(SeedMapperFeature.ELYTRA, sx, sz));
            }
        }
    }

    private static void addOreVeinSamples(Arena arena, QueryKey key, List<SeedMapperMarker> out, boolean copper, boolean fastMode) {
        if (key.dimension != Cubiomes.DIM_OVERWORLD() || key.showLootableOnly) {
            return;
        }
        MemorySegment params = OreVeinParameters.allocate(arena);
        if (Cubiomes.initOreVeinNoise(params, key.seed, key.mcVersion) == 0) {
            return;
        }
        int sampleY = copper ? 48 : -8;
        int span = Math.max(Math.abs(key.maxX - key.minX), Math.abs(key.maxZ - key.minZ));
        int step = span > 8192 ? 512 : (span > 4096 ? 256 : (span > 2048 ? 128 : 64));
        if (fastMode) {
            step = Math.max(step, span > 4096 ? 512 : 256);
        }
        int samples = 0;
        int maxSamples = fastMode ? 4000 : 25000;
        int maxMarkers = fastMode ? 900 : Integer.MAX_VALUE;
        int found = 0;
        for (int x = (key.minX / step) * step; x <= key.maxX; x += step) {
            for (int z = (key.minZ / step) * step; z <= key.maxZ; z += step) {
                if (++samples > maxSamples) {
                    return;
                }
                int block = Cubiomes.getOreVeinBlockAt(x, sampleY, z, params);
                if (copper) {
                    if (block == Cubiomes.COPPER_ORE() || block == Cubiomes.RAW_COPPER_BLOCK()) {
                        out.add(new SeedMapperMarker(SeedMapperFeature.COPPER_ORE_VEIN, x, z));
                        if (++found >= maxMarkers) {
                            return;
                        }
                    }
                } else if (block == Cubiomes.IRON_ORE() || block == Cubiomes.RAW_IRON_BLOCK()) {
                    out.add(new SeedMapperMarker(SeedMapperFeature.IRON_ORE_VEIN, x, z));
                    if (++found >= maxMarkers) {
                        return;
                    }
                }
            }
        }
    }

    private static void addSlimeChunkSamples(QueryKey key, List<SeedMapperMarker> out, boolean fastMode) {
        if (key.dimension != Cubiomes.DIM_OVERWORLD() || key.showLootableOnly) {
            return;
        }
        int span = Math.max(Math.abs(key.maxX - key.minX), Math.abs(key.maxZ - key.minZ));
        int chunkStep = span > 8192 ? 8 : (span > 4096 ? 4 : (span > 2048 ? 2 : 1));
        if (fastMode) {
            chunkStep = Math.max(chunkStep, span > 4096 ? 8 : 4);
        }
        int minChunkX = floorDiv(key.minX, 16);
        int maxChunkX = floorDiv(key.maxX, 16);
        int minChunkZ = floorDiv(key.minZ, 16);
        int maxChunkZ = floorDiv(key.maxZ, 16);
        int samples = 0;
        int maxSamples = fastMode ? 12000 : 50000;
        int maxMarkers = fastMode ? 1400 : Integer.MAX_VALUE;
        int found = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx += chunkStep) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz += chunkStep) {
                if (++samples > maxSamples) {
                    return;
                }
                long mixed = key.seed
                        + (long) (cx * cx * 4987142)
                        + (long) (cx * 5947611)
                        + (long) (cz * cz) * 4392871L
                        + (long) (cz * 389711)
                        ^ 987234911L;
                if (new Random(mixed).nextInt(10) == 0) {
                    out.add(new SeedMapperMarker(SeedMapperFeature.SLIME_CHUNK, cx * 16 + 8, cz * 16 + 8));
                    if (++found >= maxMarkers) {
                        return;
                    }
                }
            }
        }
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) {
            r--;
        }
        return r;
    }

    public record QueryResult(List<SeedMapperMarker> markers, boolean exact) {
    }

    private record QueryKey(long seed, int dimension, int mcVersion, int generatorFlags, int minX, int maxX, int minZ, int maxZ, boolean showLootableOnly, long featureMask, int datapackMarkerHash, SeedMapperSettingsManager settings, String datapackWorldKey) {
        private boolean compatibleForFallback(QueryKey other) {
            return seed == other.seed
                    && dimension == other.dimension
                    && mcVersion == other.mcVersion
                    && generatorFlags == other.generatorFlags
                    && showLootableOnly == other.showLootableOnly
                    && featureMask == other.featureMask
                    && datapackMarkerHash == other.datapackMarkerHash
                    && settings == other.settings
                    && java.util.Objects.equals(datapackWorldKey, other.datapackWorldKey);
        }
    }
}
