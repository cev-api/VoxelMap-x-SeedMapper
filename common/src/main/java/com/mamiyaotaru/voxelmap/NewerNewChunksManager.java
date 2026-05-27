package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.persistent.ThreadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class NewerNewChunksManager {
    public enum DetectMode {
        NORMAL,
        IGNORE_BLOCK_EXPLOIT,
        BLOCK_EXPLOIT_MODE
    }

    private static final Direction[] SEARCH_DIRS =
            new Direction[] {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.UP};

    private static final Set<Block> DEEPSLATE_BLOCKS = Set.of(Blocks.DEEPSLATE,
            Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE);

    private static final Set<Block> ORE_BLOCKS = Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);

    private static final Set<Block> NEW_OVERWORLD_BLOCKS = Set.of(Blocks.DEEPSLATE, Blocks.AMETHYST_BLOCK,
            Blocks.BUDDING_AMETHYST, Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.BIG_DRIPLEAF,
            Blocks.BIG_DRIPLEAF_STEM, Blocks.SMALL_DRIPLEAF, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT,
            Blocks.SPORE_BLOSSOM, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.GLOW_LICHEN, Blocks.RAW_COPPER_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.DRIPSTONE_BLOCK,
            Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.POINTED_DRIPSTONE, Blocks.SMOOTH_BASALT, Blocks.TUFF,
            Blocks.CALCITE, Blocks.HANGING_ROOTS, Blocks.ROOTED_DIRT, Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES, Blocks.POWDER_SNOW);

    private static final Set<Block> NEW_NETHER_BLOCKS = createNewNetherBlocks();

    private static final Field PAL_CONTAINER_DATA_FIELD = getField(PalettedContainer.class, "data");
    private static final Field PAL_DATA_PALETTE_FIELD = getPaletteField();

    private static final Path NEW_CHUNK_DATA = Path.of("NewChunkData.txt");
    private static final Path OLD_CHUNK_DATA = Path.of("OldChunkData.txt");
    private static final Path BLOCK_EXPLOIT_CHUNK_DATA = Path.of("BlockExploitChunkData.txt");
    private static final Path BEING_UPDATED_CHUNK_DATA = Path.of("BeingUpdatedChunkData.txt");
    private static final Path OLD_GENERATION_CHUNK_DATA = Path.of("OldGenerationChunkData.txt");
    private static final Set<Path> DATA_FILES = Set.of(NEW_CHUNK_DATA, OLD_CHUNK_DATA, BLOCK_EXPLOIT_CHUNK_DATA,
            BEING_UPDATED_CHUNK_DATA, OLD_GENERATION_CHUNK_DATA);
    private static final int REGION_SHIFT = 5; // 32x32 chunk buckets for spatial indexing
    private static final int MAX_CACHED_CHUNKS = 50000; // Allow full window to be cached seamlessly
    private static final int CACHE_EVICTION_RADIUS = 4096; // Keep a large safety margin (256 chunk diameter)
    private static final int EVICTION_CHECK_INTERVAL = 200; // Check every 200 ticks (10 seconds)
    // Restore original scan cadence; we only limit IO/loading radius, not scanning speed
    private static final int RESCAN_INTERVAL_TICKS = 40; // every 2s (vanilla behavior here)
    // Windowed loading of persisted data around player to avoid loading entire history
    private static final int WINDOW_LOAD_RADIUS_CHUNKS = 64; // ~1024 blocks, comfortably above minimap at max zoom
    private static final int WINDOW_REFRESH_DISTANCE_CHUNKS = 32; // reload window after moving this far
    private static final int WINDOW_REFRESH_INTERVAL_TICKS = 400; // at most every 20s
    private static final int V2_FORMAT_VERSION = 2;
    private static final int REGION_SIZE = 1 << REGION_SHIFT;
    private static final int REGION_CHUNK_COUNT = REGION_SIZE * REGION_SIZE;
    private static final int REGION_BYTES = REGION_CHUNK_COUNT / 8;
    private static final int V2_MAGIC = 0x4E4E4332; // NNC2
    private static final int MAX_SYNC_REGION_LOADS = 2048;

    private final Set<ChunkPos> newChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> oldChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> tickExploitChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> beingUpdatedOldChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> oldGenerationChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> pendingChunkPackets = ConcurrentHashMap.newKeySet();
    
    // Region-indexed maps for fast range queries (REGION_SHIFT = 5 means 32x32 chunk regions)
    private final Map<Long, Set<ChunkPos>> newChunksByRegion = new HashMap<>();
    private final Map<Long, Set<ChunkPos>> oldChunksByRegion = new HashMap<>();
    private final Map<Long, Set<ChunkPos>> tickExploitChunksByRegion = new HashMap<>();
    private final Map<Long, Set<ChunkPos>> beingUpdatedChunksByRegion = new HashMap<>();
    private final Map<Long, Set<ChunkPos>> oldGenerationChunksByRegion = new HashMap<>();
    private final Map<OverlayType, Set<Long>> loadedV2Regions = new EnumMap<>(OverlayType.class);
    private final Map<OverlayType, Set<Long>> dirtyV2Regions = new EnumMap<>(OverlayType.class);
    
    private final Object chunkSetLock = new Object();

    private String loadedWorldKey = "";
    private boolean v2StorageReady = false;
    private boolean migrationQueued = false;
    private boolean migrationRunning = false;
    private volatile boolean legacyWritesEnabled = true;
    private long dataVersion = 0L;
    private long lastRescanGameTime = Long.MIN_VALUE;
    private long lastEvictionTime = 0;
    private boolean lastKnownFeatureState = false;
    private long lastDebugLogTick = Long.MIN_VALUE;
    private long lastStorageDebugMs = 0L;
    private int windowCenterChunkX = Integer.MIN_VALUE;
    private int windowCenterChunkZ = Integer.MIN_VALUE;
    private long lastWindowLoadTick = Long.MIN_VALUE;
    private long lastStorageLoadTimeMs = 0L;
    private int lastLoadedRegionFiles = 0;
    private int lastDecodedChunks = 0;
    private int lastSkippedMissingRegionFiles = 0;
    private long lastMigrationTimeMs = 0L;
    private boolean storageLoadIncomplete = false;

    private record ChunkClassification(boolean isNewChunk, boolean isOldGeneration, boolean chunkIsBeingUpdated) {
    }

    public record NewOldChunksSnapshot(Set<ChunkPos> oldChunks, Set<ChunkPos> newChunks) {
    }

    public record NewOldCellsSnapshot(Set<Long> oldCells, Set<Long> newCells, int oldChunks, int newChunks) {
    }

    private enum OverlayType {
        NEW("new", NEW_CHUNK_DATA),
        OLD("old", OLD_CHUNK_DATA),
        BLOCK_EXPLOIT("block_exploit", BLOCK_EXPLOIT_CHUNK_DATA),
        BEING_UPDATED("being_updated", BEING_UPDATED_CHUNK_DATA),
        OLD_GENERATION("old_generation", OLD_GENERATION_CHUNK_DATA);

        private final String directoryName;
        private final Path legacyFile;

        OverlayType(String directoryName, Path legacyFile) {
            this.directoryName = directoryName;
            this.legacyFile = legacyFile;
        }
    }

    public NewerNewChunksManager() {
        for (OverlayType type : OverlayType.values()) {
            loadedV2Regions.put(type, ConcurrentHashMap.newKeySet());
            dirtyV2Regions.put(type, ConcurrentHashMap.newKeySet());
        }
    }

    public void onTick() {
        ensureTrackingWorld();
        processPendingChunkPackets();
        rescanLoadedChunksAroundPlayer();
        evictDistantChunksIfNeeded();
        refreshWindowIfNeeded();
        flushDirtyV2Regions(2);
    }

    public void processChunk(LevelChunk chunk) {
        // Intentionally no-op.
        // MapChunk callbacks can arrive while chunk data is still in flux, which
        // causes transient false "old" classifications while moving. We classify
        // from chunk packets and periodic loaded-chunk rescans instead.
    }

    private void classifyChunkInternal(LevelChunk chunk, boolean allowReclassify) {
        if (chunk == null) {
            return;
        }
        ensureTrackingWorld();

        if (chunk.isEmpty()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        if (!allowReclassify && containsAny(chunkPos)) {
            return;
        }
        if (allowReclassify) {
            removeFromAllSets(chunkPos);
        }

        ChunkClassification classification = classifyChunk(chunk);
        boolean isNewChunk = classification.isNewChunk();
        boolean isOldGeneration = classification.isOldGeneration();
        boolean chunkIsBeingUpdated = classification.chunkIsBeingUpdated();
        boolean allowNew = isEnd() ? isNewChunk : !isOldGeneration;

        if (isNewChunk && !chunkIsBeingUpdated && allowNew) {
            markNew(chunkPos);
            return;
        }

        if (!isNewChunk && !chunkIsBeingUpdated && isOldGeneration) {
            markOldGeneration(chunkPos);
            return;
        }

        if (chunkIsBeingUpdated) {
            markBeingUpdated(chunkPos);
            return;
        }

        if (!isNewChunk) {
            markOld(chunkPos);
            return;
        }

        RadarSettingsManager radarSettings = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        if (radarSettings != null && radarSettings.newerNewChunksLiquidExploit && hasFlowingFluid(chunk)) {
            markOld(chunkPos);
        }
    }

    public void onBlockUpdated(BlockPos pos, boolean blockUpdateExploit, boolean liquidExploit) {
        if (pos == null) {
            return;
        }
        ensureTrackingWorld();

        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }

        handleBlockLikeUpdate(pos, level.getBlockState(pos), blockUpdateExploit, liquidExploit);
    }

    public void onChunkDeltaUpdated(BlockPos pos, BlockState state, boolean liquidExploit) {
        if (pos == null || state == null) {
            return;
        }
        ensureTrackingWorld();
        if (GameVariableAccessShim.getWorld() == null) {
            return;
        }

        // Matches Trouser's afterChunkDeltaUpdate: no block-update exploit mark.
        handleBlockLikeUpdate(pos, state, false, liquidExploit);
    }

    public void onChunkDataPacket(int chunkX, int chunkZ) {
        // Defer packet-driven classification to tick time and require FULL status.
        // This avoids transient false classifications from partially realized chunks.
        pendingChunkPackets.add(new ChunkPos(chunkX, chunkZ));
    }

    private void handleBlockLikeUpdate(BlockPos pos, BlockState state, boolean blockUpdateExploit, boolean liquidExploit) {
        ChunkPos chunkPos = ChunkPos.containing(pos);

        if (blockUpdateExploit && !containsAny(chunkPos)) {
            markTickExploit(chunkPos);
        }

        FluidState fluid = state.getFluidState();
        if (!liquidExploit || fluid.isEmpty() || fluid.isSource() || containsFinalChunkType(chunkPos)) {
            return;
        }

        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }

        for (Direction dir : SEARCH_DIRS) {
            BlockPos neighborPos = pos.relative(dir);
            FluidState neighborFluid = level.getBlockState(neighborPos).getFluidState();
            if (!neighborFluid.isEmpty() && neighborFluid.isSource()) {
                synchronized (chunkSetLock) {
                    tickExploitChunks.remove(chunkPos);
                }
                markNew(chunkPos);
                return;
            }
        }
    }

    public Set<ChunkPos> getNewChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return getChunksInRange(OverlayType.NEW, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE);
    }

    public Set<ChunkPos> getNewChunksInRange(int centerChunkX, int centerChunkZ, int radius, int maxResults) {
        ensureTrackingWorld();
        return getChunksInRange(OverlayType.NEW, centerChunkX, centerChunkZ, radius, maxResults);
    }

    public Set<ChunkPos> getOldChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return getChunksInRange(OverlayType.OLD, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE);
    }

    public Set<ChunkPos> getOldChunksInRange(int centerChunkX, int centerChunkZ, int radius, int maxResults) {
        ensureTrackingWorld();
        return getChunksInRange(OverlayType.OLD, centerChunkX, centerChunkZ, radius, maxResults);
    }

    public Set<ChunkPos> getBlockUpdateChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        Set<ChunkPos> chunks = getChunksInRange(OverlayType.BLOCK_EXPLOIT, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE);
        // Important: avoid removeAll against whole-world sets (O(n) over all tracked chunks).
        // Instead, only filter the small, visible set with O(1) contains checks.
        chunks.removeIf(chunk ->
                newChunks.contains(chunk)
                        || oldChunks.contains(chunk)
                        || beingUpdatedOldChunks.contains(chunk)
                        || oldGenerationChunks.contains(chunk));
        return chunks;
    }

    public Set<ChunkPos> getBeingUpdatedChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return getChunksInRange(OverlayType.BEING_UPDATED, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE);
    }

    public Set<ChunkPos> getOldGenerationChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return getChunksInRange(OverlayType.OLD_GENERATION, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE);
    }

    public NewOldChunksSnapshot getNewOldChunksInRange(int centerChunkX, int centerChunkZ, int radius, int maxResults) {
        ensureTrackingWorld();
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;

        loadV2RegionsForBounds(OverlayType.NEW, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        loadV2RegionsForBounds(OverlayType.OLD, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        loadV2RegionsForBounds(OverlayType.BEING_UPDATED, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        loadV2RegionsForBounds(OverlayType.OLD_GENERATION, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        reconcileLoadedData();

        int budget = maxResults <= 0 ? Integer.MAX_VALUE : maxResults;
        Set<ChunkPos> newResult = collectChunksInBounds(newChunksByRegion, minChunkX, maxChunkX, minChunkZ, maxChunkZ, budget, null);
        int remainingBudget = budget == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, budget - newResult.size());
        Set<ChunkPos> oldResult = collectChunksInBounds(oldChunksByRegion, minChunkX, maxChunkX, minChunkZ, maxChunkZ, remainingBudget, chunk ->
                !newChunks.contains(chunk)
                        && !beingUpdatedOldChunks.contains(chunk)
                        && !oldGenerationChunks.contains(chunk));
        return new NewOldChunksSnapshot(oldResult, newResult);
    }

    public NewOldCellsSnapshot getNewOldCellsInRange(int centerChunkX, int centerChunkZ, int radius, int cellChunkSize) {
        ensureTrackingWorld();
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;

        loadV2RegionsForBounds(OverlayType.NEW, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        loadV2RegionsForBounds(OverlayType.OLD, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        loadV2RegionsForBounds(OverlayType.BEING_UPDATED, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        loadV2RegionsForBounds(OverlayType.OLD_GENERATION, minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);
        reconcileLoadedData();

        int safeCellChunkSize = Math.max(1, cellChunkSize);
        HashSet<Long> newCells = new HashSet<>();
        HashSet<Long> oldCells = new HashSet<>();
        int[] newCount = new int[1];
        int[] oldCount = new int[1];

        collectCellsInBounds(newChunksByRegion, minChunkX, maxChunkX, minChunkZ, maxChunkZ, safeCellChunkSize, newCells, newCount, null);
        collectCellsInBounds(oldChunksByRegion, minChunkX, maxChunkX, minChunkZ, maxChunkZ, safeCellChunkSize, oldCells, oldCount, chunk ->
                !newChunks.contains(chunk)
                        && !beingUpdatedOldChunks.contains(chunk)
                        && !oldGenerationChunks.contains(chunk));
        oldCells.removeAll(newCells);

        return new NewOldCellsSnapshot(oldCells, newCells, oldCount[0], newCount[0]);
    }

    public long getDataVersion() {
        ensureTrackingWorld();
        return dataVersion;
    }

    public String getLoadedWorldKey() {
        ensureTrackingWorld();
        return loadedWorldKey;
    }

    public long getLastStorageLoadTimeMs() {
        return lastStorageLoadTimeMs;
    }

    public int getLastLoadedRegionFiles() {
        return lastLoadedRegionFiles;
    }

    public int getLastDecodedChunks() {
        return lastDecodedChunks;
    }

    public int getLastSkippedMissingRegionFiles() {
        return lastSkippedMissingRegionFiles;
    }

    public long getLastMigrationTimeMs() {
        return lastMigrationTimeMs;
    }

    public boolean consumeStorageLoadIncomplete() {
        boolean incomplete = storageLoadIncomplete;
        storageLoadIncomplete = false;
        return incomplete;
    }

    private Set<ChunkPos> getChunksInRange(OverlayType type, int centerChunkX, int centerChunkZ, int radius, int maxResults) {
        // Use region-based lookup for O(1) query performance
        Set<ChunkPos> result = new HashSet<>();
        if (maxResults <= 0) {
            return result;
        }
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        int maxRegionLoads = maxResults == Integer.MAX_VALUE
                ? MAX_SYNC_REGION_LOADS
                : Math.max(64, Math.min(MAX_SYNC_REGION_LOADS, (maxResults / REGION_CHUNK_COUNT) + 16));
        loadV2RegionsForBounds(type, minChunkX, maxChunkX, minChunkZ, maxChunkZ, maxRegionLoads);

        Map<Long, Set<ChunkPos>> regionMap = getRegionMapForType(type);
        if (regionMap == null || regionMap.isEmpty()) {
            return result;
        }

        int minRegionX = minChunkX >> REGION_SHIFT;
        int maxRegionX = maxChunkX >> REGION_SHIFT;
        int minRegionZ = minChunkZ >> REGION_SHIFT;
        int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        synchronized (chunkSetLock) {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    Set<ChunkPos> bucket = regionMap.get(regionKey(regionX, regionZ));
                    if (bucket == null || bucket.isEmpty()) {
                        continue;
                    }
                    for (ChunkPos chunk : bucket) {
                        if (chunk.x() >= minChunkX && chunk.x() <= maxChunkX
                                && chunk.z() >= minChunkZ && chunk.z() <= maxChunkZ) {
                            result.add(chunk);
                            if (result.size() >= maxResults) {
                                return result;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private Set<ChunkPos> collectChunksInBounds(Map<Long, Set<ChunkPos>> regionMap, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
            int maxResults, java.util.function.Predicate<ChunkPos> filter) {
        Set<ChunkPos> result = new HashSet<>();
        if (maxResults <= 0 || regionMap == null || regionMap.isEmpty()) {
            return result;
        }

        int minRegionX = minChunkX >> REGION_SHIFT;
        int maxRegionX = maxChunkX >> REGION_SHIFT;
        int minRegionZ = minChunkZ >> REGION_SHIFT;
        int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        synchronized (chunkSetLock) {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    Set<ChunkPos> bucket = regionMap.get(regionKey(regionX, regionZ));
                    if (bucket == null || bucket.isEmpty()) {
                        continue;
                    }
                    for (ChunkPos chunk : bucket) {
                        if (chunk.x() < minChunkX || chunk.x() > maxChunkX || chunk.z() < minChunkZ || chunk.z() > maxChunkZ) {
                            continue;
                        }
                        if (filter != null && !filter.test(chunk)) {
                            continue;
                        }
                        result.add(chunk);
                        if (result.size() >= maxResults) {
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    private void collectCellsInBounds(Map<Long, Set<ChunkPos>> regionMap, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
            int cellChunkSize, Set<Long> result, int[] visibleChunks, java.util.function.Predicate<ChunkPos> filter) {
        if (regionMap == null || regionMap.isEmpty()) {
            return;
        }

        int minRegionX = minChunkX >> REGION_SHIFT;
        int maxRegionX = maxChunkX >> REGION_SHIFT;
        int minRegionZ = minChunkZ >> REGION_SHIFT;
        int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        synchronized (chunkSetLock) {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    Set<ChunkPos> bucket = regionMap.get(regionKey(regionX, regionZ));
                    if (bucket == null || bucket.isEmpty()) {
                        continue;
                    }
                    for (ChunkPos chunk : bucket) {
                        int chunkX = chunk.x();
                        int chunkZ = chunk.z();
                        if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                            continue;
                        }
                        if (filter != null && !filter.test(chunk)) {
                            continue;
                        }
                        visibleChunks[0]++;
                        result.add(cellKey(Math.floorDiv(chunkX, cellChunkSize), Math.floorDiv(chunkZ, cellChunkSize)));
                    }
                }
            }
        }
    }

    private void markNew(ChunkPos chunkPos) {
        boolean added;
        synchronized (chunkSetLock) {
            added = newChunks.add(chunkPos);
            if (added) {
                removeFromAllSetsExcept(chunkPos, ChunkSet.NEW);
                indexChunk(chunkPos, newChunksByRegion);
            }
        }
        if (added) {
            markV2Dirty(OverlayType.NEW, chunkPos);
            markDataChanged();
            if (legacyWritesEnabled) {
                appendChunk(getDataPath(NEW_CHUNK_DATA), chunkPos);
            }
        }
    }

    private void markOld(ChunkPos chunkPos) {
        boolean added;
        synchronized (chunkSetLock) {
            if (newChunks.contains(chunkPos) || beingUpdatedOldChunks.contains(chunkPos) || oldGenerationChunks.contains(chunkPos)) {
                return;
            }
            added = oldChunks.add(chunkPos);
            if (added) {
                removeFromAllSetsExcept(chunkPos, ChunkSet.OLD);
                indexChunk(chunkPos, oldChunksByRegion);
            }
        }
        if (added) {
            markV2Dirty(OverlayType.OLD, chunkPos);
            markDataChanged();
            if (legacyWritesEnabled) {
                appendChunk(getDataPath(OLD_CHUNK_DATA), chunkPos);
            }
        }
    }

    private void markTickExploit(ChunkPos chunkPos) {
        boolean added;
        synchronized (chunkSetLock) {
            if (newChunks.contains(chunkPos) || oldChunks.contains(chunkPos) || beingUpdatedOldChunks.contains(chunkPos)
                    || oldGenerationChunks.contains(chunkPos)) {
                return;
            }
            added = tickExploitChunks.add(chunkPos);
            if (added) {
                indexChunk(chunkPos, tickExploitChunksByRegion);
            }
        }
        if (added) {
            markV2Dirty(OverlayType.BLOCK_EXPLOIT, chunkPos);
            markDataChanged();
            if (legacyWritesEnabled) {
                appendChunk(getDataPath(BLOCK_EXPLOIT_CHUNK_DATA), chunkPos);
            }
        }
    }

    private void markBeingUpdated(ChunkPos chunkPos) {
        boolean added;
        synchronized (chunkSetLock) {
            added = beingUpdatedOldChunks.add(chunkPos);
            if (added) {
                removeFromAllSetsExcept(chunkPos, ChunkSet.BEING_UPDATED);
                indexChunk(chunkPos, beingUpdatedChunksByRegion);
            }
        }
        if (added) {
            markV2Dirty(OverlayType.BEING_UPDATED, chunkPos);
            markDataChanged();
            if (legacyWritesEnabled) {
                appendChunk(getDataPath(BEING_UPDATED_CHUNK_DATA), chunkPos);
            }
        }
    }

    private void markOldGeneration(ChunkPos chunkPos) {
        boolean added;
        synchronized (chunkSetLock) {
            added = oldGenerationChunks.add(chunkPos);
            if (added) {
                removeFromAllSetsExcept(chunkPos, ChunkSet.OLD_GENERATION);
                indexChunk(chunkPos, oldGenerationChunksByRegion);
            }
        }
        if (added) {
            markV2Dirty(OverlayType.OLD_GENERATION, chunkPos);
            markDataChanged();
            if (legacyWritesEnabled) {
                appendChunk(getDataPath(OLD_GENERATION_CHUNK_DATA), chunkPos);
            }
        }
    }

    private enum ChunkSet {
        NEW,
        OLD,
        TICK_EXPLOIT,
        BEING_UPDATED,
        OLD_GENERATION
    }

    private void removeFromAllSets(ChunkPos chunkPos) {
        boolean changed = false;
        synchronized (chunkSetLock) {
            changed |= removeChunkFromType(chunkPos, OverlayType.NEW);
            changed |= removeChunkFromType(chunkPos, OverlayType.OLD);
            changed |= removeChunkFromType(chunkPos, OverlayType.BLOCK_EXPLOIT);
            changed |= removeChunkFromType(chunkPos, OverlayType.BEING_UPDATED);
            changed |= removeChunkFromType(chunkPos, OverlayType.OLD_GENERATION);
        }
        if (changed) {
            markDataChanged();
        }
    }

    private void removeFromAllSetsExcept(ChunkPos chunkPos, ChunkSet keep) {
        boolean changed = false;
        synchronized (chunkSetLock) {
            if (keep != ChunkSet.NEW) {
                changed |= removeChunkFromType(chunkPos, OverlayType.NEW);
            }
            if (keep != ChunkSet.OLD) {
                changed |= removeChunkFromType(chunkPos, OverlayType.OLD);
            }
            if (keep != ChunkSet.TICK_EXPLOIT) {
                changed |= removeChunkFromType(chunkPos, OverlayType.BLOCK_EXPLOIT);
            }
            if (keep != ChunkSet.BEING_UPDATED) {
                changed |= removeChunkFromType(chunkPos, OverlayType.BEING_UPDATED);
            }
            if (keep != ChunkSet.OLD_GENERATION) {
                changed |= removeChunkFromType(chunkPos, OverlayType.OLD_GENERATION);
            }
        }
        if (changed) {
            markDataChanged();
        }
    }

    private boolean containsAny(ChunkPos chunkPos) {
        synchronized (chunkSetLock) {
            return newChunks.contains(chunkPos) || oldChunks.contains(chunkPos) || tickExploitChunks.contains(chunkPos)
                    || beingUpdatedOldChunks.contains(chunkPos) || oldGenerationChunks.contains(chunkPos);
        }
    }

    private boolean containsFinalChunkType(ChunkPos chunkPos) {
        synchronized (chunkSetLock) {
            return newChunks.contains(chunkPos) || oldChunks.contains(chunkPos) || beingUpdatedOldChunks.contains(chunkPos)
                    || oldGenerationChunks.contains(chunkPos);
        }
    }

    private ChunkClassification classifyChunk(LevelChunk chunk) {
        boolean isNewChunk = false;
        boolean isOldGeneration = false;
        boolean chunkIsBeingUpdated = false;
        LevelChunkSection[] sections = chunk.getSections();

        if (isOverworld()) {
            isOldGeneration = isOverworldOldGeneration(chunk);
        } else if (isNether()) {
            isOldGeneration = isNetherOldGeneration(chunk);
        } else if (isEnd()) {
            isOldGeneration = hasEndBiomeFromPalette(sections);
        }

        boolean firstChunkAppearsNew = false;
        int loops = 0;
        int newChunkQuantifier = 0;
        int oldChunkQuantifier = 0;

        for (LevelChunkSection section : sections) {
            if (section == null) {
                continue;
            }

            int isNewSection = 0;
            int isBeingUpdatedSection = 0;

            if (!section.hasOnlyAir()) {
                PalettedContainer<BlockState> blockStates = section.getStates();
                List<BlockState> paletteEntries = getRawPaletteEntries(blockStates);
                int blockPaletteLength = paletteEntries.size();

                if (isHashMapPalette(blockStates)) {
                    int bstatesSize = countDistinctSectionStates(section);
                    if (bstatesSize <= 1) {
                        bstatesSize = blockPaletteLength;
                    }
                    if (bstatesSize < blockPaletteLength) {
                        isNewSection = 2;
                    }
                }

                for (int i = 0; i < blockPaletteLength; i++) {
                    Block block = paletteEntries.get(i).getBlock();
                    if (i == 0 && loops == 0 && block == Blocks.AIR && !isEnd()) {
                        firstChunkAppearsNew = true;
                    }

                    if (i == 0 && block == Blocks.AIR && !isNether() && !isEnd()) {
                        isNewSection++;
                    }

                    if (i == 1 && (block == Blocks.WATER || block == Blocks.STONE || block == Blocks.GRASS_BLOCK
                            || block == Blocks.SNOW_BLOCK) && !isNether() && !isEnd()) {
                        isNewSection++;
                    }

                    if (i == 2 && (block == Blocks.SNOW_BLOCK || block == Blocks.DIRT || block == Blocks.POWDER_SNOW)
                            && !isNether() && !isEnd()) {
                        isNewSection++;
                    }

                    if (loops == 4 && block == Blocks.BEDROCK && !isNether() && !isEnd()) {
                        chunkIsBeingUpdated = true;
                    }

                    if (block == Blocks.AIR && (isNether() || isEnd())) {
                        isBeingUpdatedSection++;
                    }
                }

                if (isBeingUpdatedSection >= 2) {
                    oldChunkQuantifier++;
                }
                if (isNewSection >= 2) {
                    newChunkQuantifier++;
                }
            }

            if (isEnd() && isEndNewChunkByPalette(section)) {
                isNewChunk = true;
            }

            if (!section.hasOnlyAir()) {
                loops++;
            }
        }

        if (loops > 0) {
            if (isNether() || isEnd()) {
                double oldPercentage = ((double) oldChunkQuantifier / loops) * 100.0D;
                if (oldPercentage >= 25.0D) {
                    chunkIsBeingUpdated = true;
                }
            } else {
                double percentage = ((double) newChunkQuantifier / loops) * 100.0D;
                if (percentage >= 51.0D) {
                    isNewChunk = true;
                }
            }
        }

        if (firstChunkAppearsNew) {
            isNewChunk = true;
        }

        return new ChunkClassification(isNewChunk, isOldGeneration, chunkIsBeingUpdated);
    }

    private static Field getField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Field getPaletteField() {
        try {
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data");
            Field field = dataClass.getDeclaredField("palette");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getRawPaletteEntries(Object paletteContainer) {
        if (paletteContainer == null || PAL_CONTAINER_DATA_FIELD == null || PAL_DATA_PALETTE_FIELD == null) {
            return List.of();
        }

        try {
            Object data = PAL_CONTAINER_DATA_FIELD.get(paletteContainer);
            Object palette = PAL_DATA_PALETTE_FIELD.get(data);
            if (palette == null) {
                return List.of();
            }

            int size = getPaletteSize(palette);
            if (size <= 0) {
                return List.of();
            }

            ArrayList<T> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                T entry = (T) getPaletteEntry(palette, i);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    private static int getPaletteSize(Object palette) {
        try {
            return ((Number) palette.getClass().getMethod("getSize").invoke(palette)).intValue();
        } catch (ReflectiveOperationException ignored) {
            try {
                return ((Number) palette.getClass().getMethod("size").invoke(palette)).intValue();
            } catch (ReflectiveOperationException ignoredToo) {
                return 0;
            }
        }
    }

    private static Object getPaletteEntry(Object palette, int index) {
        try {
            return palette.getClass().getMethod("get", int.class).invoke(palette, index);
        } catch (ReflectiveOperationException ignored) {
            try {
                return palette.getClass().getMethod("valueFor", int.class).invoke(palette, index);
            } catch (ReflectiveOperationException ignoredToo) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean isHashMapPalette(PalettedContainer<BlockState> states) {
        if (PAL_CONTAINER_DATA_FIELD == null || PAL_DATA_PALETTE_FIELD == null) {
            return false;
        }

        try {
            Object data = PAL_CONTAINER_DATA_FIELD.get(states);
            Object palette = PAL_DATA_PALETTE_FIELD.get(data);
            return palette instanceof HashMapPalette<?>;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private int countDistinctSectionStates(LevelChunkSection section) {
        HashSet<BlockState> distinct = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    distinct.add(section.getBlockState(x, y, z));
                }
            }
        }
        return distinct.size();
    }

    private boolean hasFlowingFluid(LevelChunk chunk) {
        int minY = chunk.getMinY();
        int maxY = chunk.getMaxY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = 0; cx < 16; cx++) {
            for (int y = minY; y <= maxY; y++) {
                for (int cz = 0; cz < 16; cz++) {
                    pos.set(chunk.getPos().getMinBlockX() + cx, y, chunk.getPos().getMinBlockZ() + cz);
                    FluidState fluid = chunk.getFluidState(pos);
                    if (!fluid.isEmpty() && !fluid.isSource()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasEndBiomeFromPalette(LevelChunkSection[] sections) {
        if (sections.length == 0 || sections[0] == null) {
            return false;
        }

        List<Holder<Biome>> paletteEntries = getRawPaletteEntries(sections[0].getBiomes());
        return !paletteEntries.isEmpty() && paletteEntries.getFirst().is(Biomes.THE_END);
    }

    private boolean isEndNewChunkByPalette(LevelChunkSection section) {
        List<Holder<Biome>> paletteEntries = getRawPaletteEntries(section.getBiomes());
        return !paletteEntries.isEmpty() && paletteEntries.getFirst().is(Biomes.PLAINS);
    }

    private boolean isOverworldOldGeneration(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int safeSections = Math.min(17, sections.length);
        boolean foundAnyOre = false;
        boolean hasNewOverworldGeneration = false;

        for (int i = 0; i < safeSections; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        if (!foundAnyOre && ORE_BLOCKS.contains(block)) {
                            foundAnyOre = true;
                        }

                        if (hasNewOverworldGeneration) {
                            continue;
                        }

                        boolean inModernRange = (i == 4 && y >= 5) || i > 4;
                        if (inModernRange && (NEW_OVERWORLD_BLOCKS.contains(block) || DEEPSLATE_BLOCKS.contains(block))) {
                            hasNewOverworldGeneration = true;
                        }
                    }
                }
            }
        }

        return foundAnyOre && !hasNewOverworldGeneration;
    }

    private boolean isNetherOldGeneration(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int safeSections = Math.min(8, sections.length);

        for (int i = 0; i < safeSections; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        if (NEW_NETHER_BLOCKS.contains(block)) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private static Set<Block> createNewNetherBlocks() {
        HashSet<Block> blocks = new HashSet<>(Set.of(Blocks.ANCIENT_DEBRIS, Blocks.BASALT, Blocks.BLACKSTONE,
                Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRIMSON_STEM,
                Blocks.CRIMSON_NYLIUM, Blocks.NETHER_GOLD_ORE, Blocks.WARPED_NYLIUM, Blocks.WARPED_STEM,
                Blocks.TWISTING_VINES, Blocks.WEEPING_VINES, Blocks.BONE_BLOCK, Blocks.OBSIDIAN,
                Blocks.CRYING_OBSIDIAN, Blocks.SOUL_SOIL, Blocks.SOUL_FIRE));

        Block chain = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:chain"));
        if (chain != null && chain != Blocks.AIR) {
            blocks.add(chain);
        }

        return Collections.unmodifiableSet(blocks);
    }

    private boolean isOverworld() {
        Level level = GameVariableAccessShim.getWorld();
        return level != null && "minecraft:overworld".equals(level.dimension().identifier().toString());
    }

    private boolean isNether() {
        Level level = GameVariableAccessShim.getWorld();
        return level != null && "minecraft:the_nether".equals(level.dimension().identifier().toString());
    }

    private boolean isEnd() {
        Level level = GameVariableAccessShim.getWorld();
        return level != null && "minecraft:the_end".equals(level.dimension().identifier().toString());
    }

    private void ensureTrackingWorld() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            flushDirtyV2Regions(Integer.MAX_VALUE);
            clearChunkData();
            loadedWorldKey = "";
            lastRescanGameTime = Long.MIN_VALUE;
            lastKnownFeatureState = false;
            return;
        }

        // Check if the new chunks feature is enabled
        RadarSettingsManager radarSettings = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        boolean featureEnabled = radarSettings != null && radarSettings.showNewerNewChunks;

        String worldKey = getWorldKey();
        
        // Handle world change
        if (!worldKey.equals(loadedWorldKey)) {
            flushDirtyV2Regions(Integer.MAX_VALUE);
            if (featureEnabled) {
                loadWorld(worldKey);
            } else {
                clearChunkData();
                loadedWorldKey = worldKey;
            }
        }
        
        // Handle feature toggled on/off
        if (featureEnabled != lastKnownFeatureState) {
            if (featureEnabled) {
                // Feature was just enabled - load chunks for current world
                loadWorld(worldKey);
            } else {
                // Feature was just disabled - clear chunks to free memory
                flushDirtyV2Regions(Integer.MAX_VALUE);
                clearChunkData();
                loadedWorldKey = "";
            }
            lastKnownFeatureState = featureEnabled;
        }
    }

    private void clearChunkData() {
        boolean changed;
        synchronized (chunkSetLock) {
            changed = !newChunks.isEmpty() || !oldChunks.isEmpty() || !tickExploitChunks.isEmpty()
                    || !beingUpdatedOldChunks.isEmpty() || !oldGenerationChunks.isEmpty();
            newChunks.clear();
            oldChunks.clear();
            tickExploitChunks.clear();
            beingUpdatedOldChunks.clear();
            oldGenerationChunks.clear();
            
            // Clear region-indexed maps
            newChunksByRegion.clear();
            oldChunksByRegion.clear();
            tickExploitChunksByRegion.clear();
            beingUpdatedChunksByRegion.clear();
            oldGenerationChunksByRegion.clear();
            for (Set<Long> loadedRegions : loadedV2Regions.values()) {
                loadedRegions.clear();
            }
        }
        pendingChunkPackets.clear();
        v2StorageReady = false;
        if (changed) {
            markDataChanged();
        }
    }

    private void processPendingChunkPackets() {
        // Skip if feature is disabled to avoid unnecessary processing
        RadarSettingsManager radarSettings = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        if (radarSettings == null || !radarSettings.showNewerNewChunks) {
            return;
        }
        
        Level level = GameVariableAccessShim.getWorld();
        if (level == null || pendingChunkPackets.isEmpty()) {
            return;
        }
        long tick = level.getGameTime();
        for (ChunkPos chunkPos : new ArrayList<>(pendingChunkPackets)) {
            ChunkAccess chunkAccess = level.getChunkSource().getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.FULL, false);
            if (!(chunkAccess instanceof LevelChunk chunk)) {
                continue;
            }
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }

            classifyChunkInternal(chunk, false);
            pendingChunkPackets.remove(chunkPos);
        }
        // no debug logging
    }

    private void rescanLoadedChunksAroundPlayer() {
        // Skip if feature is disabled to avoid expensive chunk classification
        RadarSettingsManager radarSettings = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        if (radarSettings == null || !radarSettings.showNewerNewChunks) {
            return;
        }
        
        Level level = GameVariableAccessShim.getWorld();
        if (level == null || Minecraft.getInstance().player == null) {
            return;
        }

        long tick = level.getGameTime();
        if (tick == lastRescanGameTime || tick % RESCAN_INTERVAL_TICKS != 0L) {
            return;
        }
        lastRescanGameTime = tick;

        int px = Minecraft.getInstance().player.chunkPosition().x();
        int pz = Minecraft.getInstance().player.chunkPosition().z();
        int radius = Math.max(2, Minecraft.getInstance().options.getEffectiveRenderDistance()) + 1;

        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                if (!level.hasChunk(x, z)) {
                    continue;
                }

                ChunkAccess chunkAccess = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (!(chunkAccess instanceof LevelChunk chunk) || chunk.isEmpty()) {
                    continue;
                }

                classifyChunkInternal(chunk, false);
            }
        }
        // no debug logging
    }

    private void refreshWindowIfNeeded() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null || Minecraft.getInstance().player == null) return;
        long tick = level.getGameTime();
        int px = Minecraft.getInstance().player.chunkPosition().x();
        int pz = Minecraft.getInstance().player.chunkPosition().z();

        if (windowCenterChunkX == Integer.MIN_VALUE || windowCenterChunkZ == Integer.MIN_VALUE) {
            // First-time initialization after load
            return;
        }
        if (tick - lastWindowLoadTick < WINDOW_REFRESH_INTERVAL_TICKS) return;

        int dx = Math.abs(px - windowCenterChunkX);
        int dz = Math.abs(pz - windowCenterChunkZ);
        if (Math.max(dx, dz) >= getRefreshDistanceChunks()) {
            loadWindow(px, pz, tick);
        }
    }

    public void reloadWindowNow() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null || Minecraft.getInstance().player == null) return;
        loadWindow(Minecraft.getInstance().player.chunkPosition().x(), Minecraft.getInstance().player.chunkPosition().z(), level.getGameTime());
    }

    public void flushStorage() {
        flushDirtyV2Regions(Integer.MAX_VALUE);
    }

    private int getWindowRadiusChunks() {
        RadarSettingsManager rs = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        int v = rs != null ? rs.newerNewChunksWindowRadiusChunks : WINDOW_LOAD_RADIUS_CHUNKS;
        return Math.max(16, Math.min(256, v));
    }

    private int getRefreshDistanceChunks() {
        RadarSettingsManager rs = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        int v = rs != null ? rs.newerNewChunksRefreshDistanceChunks : WINDOW_REFRESH_DISTANCE_CHUNKS;
        return Math.max(8, Math.min(128, v));
    }

    /**
     * Periodically evicts chunks from memory that are far from the player.
     * This prevents memory bloat from accumulated chunk data on heavily-explored servers.
     */
    private void evictDistantChunksIfNeeded() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null || Minecraft.getInstance().player == null) {
            return;
        }

        long tick = level.getGameTime();
        // Only check every EVICTION_CHECK_INTERVAL ticks to avoid overhead
        if (tick - lastEvictionTime < EVICTION_CHECK_INTERVAL) {
            return;
        }
        lastEvictionTime = tick;

        // Get current player position in chunks
        int playerChunkX = Minecraft.getInstance().player.chunkPosition().x();
        int playerChunkZ = Minecraft.getInstance().player.chunkPosition().z();
        int evictionRadiusChunks = CACHE_EVICTION_RADIUS >> 4; // Convert blocks to chunks

        // Calculate current cache size
        int totalChunks = newChunks.size() + oldChunks.size() + tickExploitChunks.size() 
                + beingUpdatedOldChunks.size() + oldGenerationChunks.size();

        // Only evict if we're exceeding max cache size
        if (totalChunks <= MAX_CACHED_CHUNKS) {
            return;
        }

        // Evict chunks outside the viewport radius
        boolean changed = false;
        synchronized (chunkSetLock) {
            changed |= evictChunksFromSet(OverlayType.NEW, playerChunkX, playerChunkZ, evictionRadiusChunks);
            changed |= evictChunksFromSet(OverlayType.OLD, playerChunkX, playerChunkZ, evictionRadiusChunks);
            changed |= evictChunksFromSet(OverlayType.BLOCK_EXPLOIT, playerChunkX, playerChunkZ, evictionRadiusChunks);
            changed |= evictChunksFromSet(OverlayType.BEING_UPDATED, playerChunkX, playerChunkZ, evictionRadiusChunks);
            changed |= evictChunksFromSet(OverlayType.OLD_GENERATION, playerChunkX, playerChunkZ, evictionRadiusChunks);
        }
        if (changed) {
            markDataChanged();
        }
    }

    /**
     * Removes chunks from a set if they're outside the eviction radius from the player
     */
    private boolean evictChunksFromSet(OverlayType type, int playerChunkX, int playerChunkZ, int evictionRadiusChunks) {
        Set<ChunkPos> chunkSet = getChunkSetForType(type);
        Map<Long, Set<ChunkPos>> regionMap = getRegionMapForType(type);
        if (chunkSet == null || regionMap == null) {
            return false;
        }
        List<ChunkPos> toRemove = new ArrayList<>();
        for (ChunkPos chunk : chunkSet) {
            int dx = Math.abs(chunk.x() - playerChunkX);
            int dz = Math.abs(chunk.z() - playerChunkZ);
            if (Math.max(dx, dz) > evictionRadiusChunks) {
                toRemove.add(chunk);
            }
        }

        for (ChunkPos chunk : toRemove) {
            chunkSet.remove(chunk);
            deindexChunk(chunk, regionMap);
            loadedV2Regions.get(type).remove(regionKey(chunk.x() >> REGION_SHIFT, chunk.z() >> REGION_SHIFT));
        }
        return !toRemove.isEmpty();
    }

    public void clearCurrentWorldData() {
        ensureTrackingWorld();
        clearChunkData();
        for (Set<Long> dirtyRegions : dirtyV2Regions.values()) {
            dirtyRegions.clear();
        }

        Path baseDir = getBaseDir();
        try {
            Files.deleteIfExists(baseDir.resolve(NEW_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(OLD_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(BLOCK_EXPLOIT_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(BEING_UPDATED_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(OLD_GENERATION_CHUNK_DATA));
            deleteDirectoryIfExists(getV2Dir());
        } catch (IOException ignored) {
        }

        ensureDataFiles();
        ensureV2Storage();
        markDataChanged();
    }

    private void loadWorld(String worldKey) {
        flushDirtyV2Regions(Integer.MAX_VALUE);
        clearChunkData();
        loadedWorldKey = worldKey;
        ensureV2Storage();
        if (legacyWritesEnabled) {
            ensureDataFiles();
        }
        Level level = GameVariableAccessShim.getWorld();
        int px = level != null && Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.chunkPosition().x() : 0;
        int pz = level != null && Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.chunkPosition().z() : 0;
        long tick = level != null ? level.getGameTime() : 0L;
        loadWindow(px, pz, tick);
    }

    private void loadWindow(int centerChunkX, int centerChunkZ, long tick) {
        windowCenterChunkX = centerChunkX;
        windowCenterChunkZ = centerChunkZ;
        lastWindowLoadTick = tick;
        int radius = getWindowRadiusChunks();
        int minX = centerChunkX - radius;
        int maxX = centerChunkX + radius;
        int minZ = centerChunkZ - radius;
        int maxZ = centerChunkZ + radius;

        for (OverlayType type : OverlayType.values()) {
            loadV2RegionsForBounds(type, minX, maxX, minZ, maxZ, MAX_SYNC_REGION_LOADS);
        }
        reconcileLoadedData();
    }

    private void reconcileLoadedData() {
        boolean changed = false;
        synchronized (chunkSetLock) {
            // Precedence: old-generation > being-updated > new > old > block-exploit
            changed |= removeAllIndexed(oldChunks, newChunks);
            changed |= removeAllIndexed(tickExploitChunks, newChunks);

            changed |= removeAllIndexed(newChunks, beingUpdatedOldChunks);
            changed |= removeAllIndexed(oldChunks, beingUpdatedOldChunks);
            changed |= removeAllIndexed(tickExploitChunks, beingUpdatedOldChunks);

            changed |= removeAllIndexed(newChunks, oldGenerationChunks);
            changed |= removeAllIndexed(oldChunks, oldGenerationChunks);
            changed |= removeAllIndexed(beingUpdatedOldChunks, oldGenerationChunks);
            changed |= removeAllIndexed(tickExploitChunks, oldGenerationChunks);

            changed |= removeAllIndexed(tickExploitChunks, oldChunks);
        }
        if (changed) {
            rebuildRegionalIndices();
            markDataChanged();
        }
    }

    private void ensureDataFiles() {
        Path baseDir = getBaseDir();
        try {
            Files.createDirectories(baseDir);
            for (Path fileName : DATA_FILES) {
                Path file = baseDir.resolve(fileName);
                if (Files.notExists(file)) {
                    Files.createFile(file);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private Set<ChunkPos> loadPath(Path fileName) {
        Set<ChunkPos> loaded = new HashSet<>();
        Path path = getDataPath(fileName);
        if (Files.notExists(path)) {
            return loaded;
        }

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                ChunkPos chunkPos = parseChunk(line);
                if (chunkPos != null) {
                    loaded.add(chunkPos);
                }
            }
        } catch (IOException ignored) {
        }
        return loaded;
    }

    private void ensureV2Storage() {
        if (v2StorageReady) {
            return;
        }
        try {
            Path v2Dir = getV2Dir();
            Files.createDirectories(v2Dir);
            for (OverlayType type : OverlayType.values()) {
                Files.createDirectories(getV2OverlayDir(type));
            }
            if (isV2MigrationComplete()) {
                legacyWritesEnabled = false;
                moveLegacyTextFilesToBackup(getStorageWorldKey());
            } else {
                legacyWritesEnabled = true;
                queueLegacyMigration();
            }
            v2StorageReady = true;
        } catch (IOException ignored) {
        }
    }

    private void queueLegacyMigration() {
        if (migrationQueued || migrationRunning) {
            return;
        }
        migrationQueued = true;
        String migrationWorldKey = getStorageWorldKey();
        ThreadManager.executorService.execute(() -> {
            migrationRunning = true;
            try {
                migrateLegacyTextFilesToV2(migrationWorldKey);
            } finally {
                migrationRunning = false;
                migrationQueued = false;
            }
        });
    }

    private boolean isV2MigrationComplete() {
        Path manifest = getV2ManifestPath();
        if (Files.notExists(manifest)) {
            return false;
        }
        try {
            String text = Files.readString(manifest, StandardCharsets.UTF_8);
            String compact = text.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
            return compact.contains("\"formatVersion\":2")
                    && compact.contains("\"regionShift\":" + REGION_SHIFT)
                    && compact.contains("\"migrationComplete\":true")
                    && compact.contains("\"legacyImportVerified\":true");
        } catch (IOException ignored) {
            return false;
        }
    }

    private void migrateLegacyTextFilesToV2(String worldKey) {
        long start = System.nanoTime();
        int migratedChunks = 0;
        int writtenRegions = 0;
        int expectedRegions = 0;
        int failedRegions = 0;
        for (OverlayType type : OverlayType.values()) {
            Map<Long, BitSet> regionBits = new HashMap<>();
            Path legacyPath = getDataPath(worldKey, type.legacyFile);
            if (Files.exists(legacyPath)) {
                try (Stream<String> lines = Files.lines(legacyPath, StandardCharsets.UTF_8)) {
                    Iterator<String> iterator = lines.iterator();
                    while (iterator.hasNext()) {
                        ChunkPos chunkPos = parseChunk(iterator.next());
                        if (chunkPos == null) {
                            continue;
                        }
                        int regionX = chunkPos.x() >> REGION_SHIFT;
                        int regionZ = chunkPos.z() >> REGION_SHIFT;
                        long regionKey = regionKey(regionX, regionZ);
                        BitSet bits = regionBits.computeIfAbsent(regionKey, ignored -> new BitSet(REGION_CHUNK_COUNT));
                        bits.set(regionBitIndex(chunkPos.x(), chunkPos.z()));
                        migratedChunks++;
                    }
                } catch (IOException ignored) {
                }
            }

            expectedRegions += regionBits.size();
            for (Map.Entry<Long, BitSet> entry : regionBits.entrySet()) {
                int regionX = (int) (entry.getKey() >> 32);
                int regionZ = (int) (long) entry.getKey();
                BitSet existingBits = readRegionBits(getV2RegionPath(worldKey, type, regionX, regionZ));
                if (existingBits != null && !existingBits.isEmpty()) {
                    entry.getValue().or(existingBits);
                }
                if (writeRegionBits(worldKey, type, regionX, regionZ, entry.getValue())) {
                    writtenRegions++;
                } else {
                    failedRegions++;
                }
            }
        }
        lastMigrationTimeMs = (System.nanoTime() - start) / 1_000_000L;
        boolean complete = failedRegions == 0;
        writeV2Manifest(worldKey, complete, migratedChunks, writtenRegions, expectedRegions, failedRegions);
        if (complete) {
            moveLegacyTextFilesToBackup(worldKey);
        }
        if (worldKey.equals(loadedWorldKey)) {
            for (Set<Long> loadedRegions : loadedV2Regions.values()) {
                loadedRegions.clear();
            }
            legacyWritesEnabled = !complete;
            markDataChanged();
        }
        if (VoxelConstants.DEBUG) {
            VoxelConstants.getLogger().info("NewerNewChunks v2 migration: world={} chunks={} regions={}/{} failedRegions={} timeMs={}",
                    worldKey, migratedChunks, writtenRegions, expectedRegions, failedRegions, lastMigrationTimeMs);
        }
    }

    private void loadV2RegionsForBounds(OverlayType type, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, int maxRegionLoads) {
        ensureV2Storage();
        int minRegionX = minChunkX >> REGION_SHIFT;
        int maxRegionX = maxChunkX >> REGION_SHIFT;
        int minRegionZ = minChunkZ >> REGION_SHIFT;
        int maxRegionZ = maxChunkZ >> REGION_SHIFT;
        int loaded = 0;
        int decoded = 0;
        int skipped = 0;
        long start = System.nanoTime();
        Set<Long> loadedRegions = loadedV2Regions.get(type);

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                if (loaded >= maxRegionLoads) {
                    storageLoadIncomplete = true;
                    recordStorageLoadStats(start, loaded, decoded, skipped);
                    return;
                }
                long key = regionKey(regionX, regionZ);
                if (loadedRegions.contains(key)) {
                    continue;
                }
                Path path = getV2RegionPath(type, regionX, regionZ);
                if (Files.notExists(path)) {
                    loadedRegions.add(key);
                    skipped++;
                    continue;
                }
                BitSet bits = readRegionBits(path);
                loadedRegions.add(key);
                loaded++;
                if (bits == null || bits.isEmpty()) {
                    continue;
                }
                decoded += addRegionBitsToMemory(type, regionX, regionZ, bits);
            }
        }
        recordStorageLoadStats(start, loaded, decoded, skipped);
    }

    private void recordStorageLoadStats(long startNanos, int loaded, int decoded, int skipped) {
        if (loaded == 0 && decoded == 0 && skipped == 0) {
            return;
        }
        lastStorageLoadTimeMs = (System.nanoTime() - startNanos) / 1_000_000L;
        lastLoadedRegionFiles = loaded;
        lastDecodedChunks = decoded;
        lastSkippedMissingRegionFiles = skipped;
        if (decoded > 0) {
            markDataChanged();
        }
        long now = System.currentTimeMillis();
        if (VoxelConstants.DEBUG && now - lastStorageDebugMs > 5000L) {
            lastStorageDebugMs = now;
            VoxelConstants.getLogger().info("NewerNewChunks v2 load: world={} files={} decoded={} missing={} timeMs={} version={}",
                    loadedWorldKey, loaded, decoded, skipped, lastStorageLoadTimeMs, dataVersion);
        }
    }

    private int addRegionBitsToMemory(OverlayType type, int regionX, int regionZ, BitSet bits) {
        int decoded = 0;
        int baseChunkX = regionX << REGION_SHIFT;
        int baseChunkZ = regionZ << REGION_SHIFT;
        Set<ChunkPos> chunkSet = getChunkSetForType(type);
        Map<Long, Set<ChunkPos>> regionMap = getRegionMapForType(type);
        if (chunkSet == null || regionMap == null) {
            return 0;
        }
        synchronized (chunkSetLock) {
            for (int bit = bits.nextSetBit(0); bit >= 0 && bit < REGION_CHUNK_COUNT; bit = bits.nextSetBit(bit + 1)) {
                int localX = bit & (REGION_SIZE - 1);
                int localZ = bit >> REGION_SHIFT;
                ChunkPos chunkPos = new ChunkPos(baseChunkX + localX, baseChunkZ + localZ);
                if (chunkSet.add(chunkPos)) {
                    indexChunk(chunkPos, regionMap);
                    decoded++;
                }
            }
        }
        return decoded;
    }

    private void flushDirtyV2Regions(int maxRegions) {
        if (loadedWorldKey == null || loadedWorldKey.isEmpty()) {
            return;
        }
        int flushed = 0;
        for (OverlayType type : OverlayType.values()) {
            Set<Long> dirtyRegions = dirtyV2Regions.get(type);
            Iterator<Long> iterator = dirtyRegions.iterator();
            while (iterator.hasNext() && flushed < maxRegions) {
                long key = iterator.next();
                iterator.remove();
                int regionX = (int) (key >> 32);
                int regionZ = (int) key;
                loadV2RegionIfNeeded(type, regionX, regionZ);
                writeRegionBits(getStorageWorldKey(), type, regionX, regionZ, buildRegionBits(type, regionX, regionZ));
                flushed++;
            }
            if (flushed >= maxRegions) {
                break;
            }
        }
    }

    private void loadV2RegionIfNeeded(OverlayType type, int regionX, int regionZ) {
        Set<Long> loadedRegions = loadedV2Regions.get(type);
        long key = regionKey(regionX, regionZ);
        if (loadedRegions.contains(key)) {
            return;
        }
        Path path = getV2RegionPath(type, regionX, regionZ);
        loadedRegions.add(key);
        if (Files.notExists(path)) {
            return;
        }
        BitSet bits = readRegionBits(path);
        if (bits != null && !bits.isEmpty()) {
            addRegionBitsToMemory(type, regionX, regionZ, bits);
        }
    }

    private BitSet buildRegionBits(OverlayType type, int regionX, int regionZ) {
        BitSet bits = new BitSet(REGION_CHUNK_COUNT);
        Map<Long, Set<ChunkPos>> regionMap = getRegionMapForType(type);
        if (regionMap == null) {
            return bits;
        }
        Set<ChunkPos> bucket = regionMap.get(regionKey(regionX, regionZ));
        if (bucket == null || bucket.isEmpty()) {
            return bits;
        }
        synchronized (chunkSetLock) {
            for (ChunkPos chunkPos : bucket) {
                bits.set(regionBitIndex(chunkPos.x(), chunkPos.z()));
            }
        }
        return bits;
    }

    private boolean writeRegionBits(String worldKey, OverlayType type, int regionX, int regionZ, BitSet bits) {
        Path path = getV2RegionPath(worldKey, type, regionX, regionZ);
        try {
            Files.createDirectories(path.getParent());
            if (bits == null || bits.isEmpty()) {
                Files.deleteIfExists(path);
                return true;
            }
            byte[] data = new byte[REGION_BYTES];
            byte[] bitBytes = bits.toByteArray();
            System.arraycopy(bitBytes, 0, data, 0, Math.min(bitBytes.length, data.length));
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writeInt(output, V2_MAGIC);
                writeInt(output, V2_FORMAT_VERSION);
                writeInt(output, REGION_SHIFT);
                writeInt(output, regionX);
                writeInt(output, regionZ);
                output.write(data);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private BitSet readRegionBits(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            int magic = readInt(input);
            int version = readInt(input);
            int regionShift = readInt(input);
            readInt(input);
            readInt(input);
            if (magic != V2_MAGIC || version != V2_FORMAT_VERSION || regionShift != REGION_SHIFT) {
                return null;
            }
            byte[] data = input.readNBytes(REGION_BYTES);
            if (data.length < REGION_BYTES) {
                return null;
            }
            return BitSet.valueOf(data);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void writeV2Manifest(String worldKey, boolean migrationComplete, int migratedChunks, int writtenRegions, int expectedRegions, int failedRegions) {
        Path manifest = getV2ManifestPath(worldKey);
        String json = "{\n"
                + "  \"formatVersion\": " + V2_FORMAT_VERSION + ",\n"
                + "  \"regionShift\": " + REGION_SHIFT + ",\n"
                + "  \"worldKey\": \"" + escapeJson(loadedWorldKey) + "\",\n"
                + "  \"migrationComplete\": " + migrationComplete + ",\n"
                + "  \"legacyImportVerified\": " + migrationComplete + ",\n"
                + "  \"overlayTypes\": [\"new\", \"old\", \"block_exploit\", \"being_updated\", \"old_generation\"],\n"
                + "  \"migratedChunks\": " + migratedChunks + ",\n"
                + "  \"writtenRegions\": " + writtenRegions + ",\n"
                + "  \"expectedRegions\": " + expectedRegions + ",\n"
                + "  \"failedRegions\": " + failedRegions + ",\n"
                + "  \"updatedAt\": " + System.currentTimeMillis() + "\n"
                + "}\n";
        try {
            Files.createDirectories(manifest.getParent());
            Files.writeString(manifest, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private void moveLegacyTextFilesToBackup(String worldKey) {
        Path backupDir = getBaseDir(worldKey).resolve("legacy_backup");
        for (OverlayType type : OverlayType.values()) {
            Path source = getDataPath(worldKey, type.legacyFile);
            if (Files.notExists(source)) {
                continue;
            }
            try {
                Files.createDirectories(backupDir);
                Files.move(source, nextBackupPath(backupDir.resolve(type.legacyFile.getFileName())), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                try {
                    Files.move(source, nextBackupPath(backupDir.resolve(type.legacyFile.getFileName())), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignoredToo) {
                }
            }
        }
    }

    private static Path nextBackupPath(Path desiredPath) {
        if (Files.notExists(desiredPath)) {
            return desiredPath;
        }
        String fileName = desiredPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        return desiredPath.resolveSibling(baseName + "." + System.currentTimeMillis() + extension);
    }

    private boolean removeAllIndexed(Set<ChunkPos> target, Set<ChunkPos> remove) {
        if (target.isEmpty() || remove.isEmpty()) {
            return false;
        }
        return target.removeIf(remove::contains);
    }

    private boolean removeChunkFromType(ChunkPos chunkPos, OverlayType type) {
        Set<ChunkPos> chunkSet = getChunkSetForType(type);
        Map<Long, Set<ChunkPos>> regionMap = getRegionMapForType(type);
        if (chunkSet == null || regionMap == null || !chunkSet.remove(chunkPos)) {
            return false;
        }
        deindexChunk(chunkPos, regionMap);
        markV2Dirty(type, chunkPos);
        return true;
    }

    private void markV2Dirty(OverlayType type, ChunkPos chunkPos) {
        if (chunkPos == null) {
            return;
        }
        dirtyV2Regions.get(type).add(regionKey(chunkPos.x() >> REGION_SHIFT, chunkPos.z() >> REGION_SHIFT));
    }

    private void markDataChanged() {
        dataVersion++;
    }

    private Path getDataPath(Path fileName) {
        return getBaseDir().resolve(fileName);
    }

    private Path getDataPath(String worldKey, Path fileName) {
        return getBaseDir(worldKey).resolve(fileName);
    }

    private Path getV2Dir() {
        return getBaseDir().resolve("v2");
    }

    private Path getV2Dir(String worldKey) {
        return getBaseDir(worldKey).resolve("v2");
    }

    private Path getV2OverlayDir(OverlayType type) {
        return getV2Dir().resolve(type.directoryName);
    }

    private Path getV2OverlayDir(String worldKey, OverlayType type) {
        return getV2Dir(worldKey).resolve(type.directoryName);
    }

    private Path getV2RegionPath(OverlayType type, int regionX, int regionZ) {
        return getV2OverlayDir(type).resolve("r." + regionX + "." + regionZ + ".bin");
    }

    private Path getV2RegionPath(String worldKey, OverlayType type, int regionX, int regionZ) {
        return getV2OverlayDir(worldKey, type).resolve("r." + regionX + "." + regionZ + ".bin");
    }

    private Path getV2ManifestPath() {
        return getV2Dir().resolve("manifest.json");
    }

    private Path getV2ManifestPath(String worldKey) {
        return getV2Dir(worldKey).resolve("manifest.json");
    }

    private Path getBaseDir() {
        return getBaseDir(getStorageWorldKey());
    }

    private Path getBaseDir(String worldKey) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameDirectory.toPath()
                .resolve("voxelmap")
                .resolve("chunk_overlays")
                .resolve("newer_new_chunks")
                .resolve(worldKey);
    }

    private String getWorldKey() {
        Level level = GameVariableAccessShim.getWorld();
        String dimension = level == null ? "unknown" : level.dimension().identifier().toString().replace(':', '_');

        Minecraft minecraft = Minecraft.getInstance();
        ServerData serverData = minecraft.getCurrentServer();
        String server = serverData != null && serverData.ip != null && !serverData.ip.isBlank()
                ? serverData.ip.replace(':', '_')
                : minecraft.hasSingleplayerServer() ? "singleplayer" : "unknown";
        return server + "_" + dimension;
    }

    private String getStorageWorldKey() {
        return loadedWorldKey == null || loadedWorldKey.isEmpty() ? getWorldKey() : loadedWorldKey;
    }

    private static void appendChunk(Path path, ChunkPos chunkPos) {
        String line = chunkPos.x() + "," + chunkPos.z() + System.lineSeparator();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static int regionBitIndex(int chunkX, int chunkZ) {
        int localX = chunkX & (REGION_SIZE - 1);
        int localZ = chunkZ & (REGION_SIZE - 1);
        return (localZ << REGION_SHIFT) | localX;
    }

    private static void writeInt(OutputStream output, int value) throws IOException {
        output.write((value >>> 24) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static int readInt(InputStream input) throws IOException {
        int b1 = input.read();
        int b2 = input.read();
        int b3 = input.read();
        int b4 = input.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new IOException("Unexpected end of region file");
        }
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void deleteDirectoryIfExists(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            List<Path> toDelete = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : toDelete) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static ChunkPos parseChunk(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String[] parts = line.split(",");
        if (parts.length != 2) {
            return null;
        }

        try {
            return new ChunkPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Adds a chunk to its region bucket for fast spatial queries
     */
    private void indexChunk(ChunkPos chunk, Map<Long, Set<ChunkPos>> regionMap) {
        if (chunk == null || regionMap == null) {
            return;
        }
        int regionX = chunk.x() >> REGION_SHIFT;
        int regionZ = chunk.z() >> REGION_SHIFT;
        long key = regionKey(regionX, regionZ);
        
        regionMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(chunk);
    }

    /**
     * Removes a chunk from its region bucket
     */
    private void deindexChunk(ChunkPos chunk, Map<Long, Set<ChunkPos>> regionMap) {
        if (chunk == null || regionMap == null || regionMap.isEmpty()) {
            return;
        }
        int regionX = chunk.x() >> REGION_SHIFT;
        int regionZ = chunk.z() >> REGION_SHIFT;
        long key = regionKey(regionX, regionZ);
        
        Set<ChunkPos> bucket = regionMap.get(key);
        if (bucket != null) {
            bucket.remove(chunk);
            if (bucket.isEmpty()) {
                regionMap.remove(key);
            }
        }
    }

    /**
     * Computes a unique key for a region (used for Map lookups)
     */
    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    /**
     * Returns the appropriate region map for a given chunk set
     */
    private Set<ChunkPos> getChunkSetForType(OverlayType type) {
        return switch (type) {
            case NEW -> newChunks;
            case OLD -> oldChunks;
            case BLOCK_EXPLOIT -> tickExploitChunks;
            case BEING_UPDATED -> beingUpdatedOldChunks;
            case OLD_GENERATION -> oldGenerationChunks;
        };
    }

    private Map<Long, Set<ChunkPos>> getRegionMapForType(OverlayType type) {
        return switch (type) {
            case NEW -> newChunksByRegion;
            case OLD -> oldChunksByRegion;
            case BLOCK_EXPLOIT -> tickExploitChunksByRegion;
            case BEING_UPDATED -> beingUpdatedChunksByRegion;
            case OLD_GENERATION -> oldGenerationChunksByRegion;
        };
    }

    private Map<Long, Set<ChunkPos>> getRegionMapForSource(Set<ChunkPos> source) {
        if (source == newChunks) {
            return getRegionMapForType(OverlayType.NEW);
        } else if (source == oldChunks) {
            return getRegionMapForType(OverlayType.OLD);
        } else if (source == tickExploitChunks) {
            return getRegionMapForType(OverlayType.BLOCK_EXPLOIT);
        } else if (source == beingUpdatedOldChunks) {
            return getRegionMapForType(OverlayType.BEING_UPDATED);
        } else if (source == oldGenerationChunks) {
            return getRegionMapForType(OverlayType.OLD_GENERATION);
        }
        return null;
    }

    /**
     * Rebuilds all regional indices from the current chunk sets.
     * Call this after loading chunk data from disk.
     */
    private void rebuildRegionalIndices() {
        synchronized (chunkSetLock) {
            newChunksByRegion.clear();
            oldChunksByRegion.clear();
            tickExploitChunksByRegion.clear();
            beingUpdatedChunksByRegion.clear();
            oldGenerationChunksByRegion.clear();
            
            for (ChunkPos chunk : newChunks) {
                indexChunk(chunk, newChunksByRegion);
            }
            for (ChunkPos chunk : oldChunks) {
                indexChunk(chunk, oldChunksByRegion);
            }
            for (ChunkPos chunk : tickExploitChunks) {
                indexChunk(chunk, tickExploitChunksByRegion);
            }
            for (ChunkPos chunk : beingUpdatedOldChunks) {
                indexChunk(chunk, beingUpdatedChunksByRegion);
            }
            for (ChunkPos chunk : oldGenerationChunks) {
                indexChunk(chunk, oldGenerationChunksByRegion);
            }
        }
    }
}
