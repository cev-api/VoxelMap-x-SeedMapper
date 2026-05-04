package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    
    private final Object chunkSetLock = new Object();

    private String loadedWorldKey = "";
    private long lastRescanGameTime = Long.MIN_VALUE;
    private long lastEvictionTime = 0;
    private boolean lastKnownFeatureState = false;
    private long lastDebugLogTick = Long.MIN_VALUE;
    private int windowCenterChunkX = Integer.MIN_VALUE;
    private int windowCenterChunkZ = Integer.MIN_VALUE;
    private long lastWindowLoadTick = Long.MIN_VALUE;

    private record ChunkClassification(boolean isNewChunk, boolean isOldGeneration, boolean chunkIsBeingUpdated) {
    }

    public void onTick() {
        ensureTrackingWorld();
        processPendingChunkPackets();
        rescanLoadedChunksAroundPlayer();
        evictDistantChunksIfNeeded();
        refreshWindowIfNeeded();
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
        return getChunksInRange(newChunks, centerChunkX, centerChunkZ, radius);
    }

    public Set<ChunkPos> getOldChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return getChunksInRange(oldChunks, centerChunkX, centerChunkZ, radius);
    }

    public Set<ChunkPos> getBlockUpdateChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        Set<ChunkPos> chunks = getChunksInRange(tickExploitChunks, centerChunkX, centerChunkZ, radius);
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
        return getChunksInRange(beingUpdatedOldChunks, centerChunkX, centerChunkZ, radius);
    }

    public Set<ChunkPos> getOldGenerationChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return getChunksInRange(oldGenerationChunks, centerChunkX, centerChunkZ, radius);
    }

    private Set<ChunkPos> getChunksInRange(Set<ChunkPos> source, int centerChunkX, int centerChunkZ, int radius) {
        // Use region-based lookup for O(1) query performance
        Set<ChunkPos> result = new HashSet<>();
        Map<Long, Set<ChunkPos>> regionMap = getRegionMapForSource(source);
        
        if (regionMap == null || regionMap.isEmpty()) {
            return result;
        }
        
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;

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
                        }
                    }
                }
            }
        }
        return result;
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
            appendChunk(getDataPath(NEW_CHUNK_DATA), chunkPos);
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
            appendChunk(getDataPath(OLD_CHUNK_DATA), chunkPos);
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
            appendChunk(getDataPath(BLOCK_EXPLOIT_CHUNK_DATA), chunkPos);
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
            appendChunk(getDataPath(BEING_UPDATED_CHUNK_DATA), chunkPos);
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
            appendChunk(getDataPath(OLD_GENERATION_CHUNK_DATA), chunkPos);
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
        synchronized (chunkSetLock) {
            newChunks.remove(chunkPos);
            deindexChunk(chunkPos, newChunksByRegion);
            oldChunks.remove(chunkPos);
            deindexChunk(chunkPos, oldChunksByRegion);
            tickExploitChunks.remove(chunkPos);
            deindexChunk(chunkPos, tickExploitChunksByRegion);
            beingUpdatedOldChunks.remove(chunkPos);
            deindexChunk(chunkPos, beingUpdatedChunksByRegion);
            oldGenerationChunks.remove(chunkPos);
            deindexChunk(chunkPos, oldGenerationChunksByRegion);
        }
    }

    private void removeFromAllSetsExcept(ChunkPos chunkPos, ChunkSet keep) {
        synchronized (chunkSetLock) {
            if (keep != ChunkSet.NEW) {
                newChunks.remove(chunkPos);
                deindexChunk(chunkPos, newChunksByRegion);
            }
            if (keep != ChunkSet.OLD) {
                oldChunks.remove(chunkPos);
                deindexChunk(chunkPos, oldChunksByRegion);
            }
            if (keep != ChunkSet.TICK_EXPLOIT) {
                tickExploitChunks.remove(chunkPos);
                deindexChunk(chunkPos, tickExploitChunksByRegion);
            }
            if (keep != ChunkSet.BEING_UPDATED) {
                beingUpdatedOldChunks.remove(chunkPos);
                deindexChunk(chunkPos, beingUpdatedChunksByRegion);
            }
            if (keep != ChunkSet.OLD_GENERATION) {
                oldGenerationChunks.remove(chunkPos);
                deindexChunk(chunkPos, oldGenerationChunksByRegion);
            }
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
                clearChunkData();
                loadedWorldKey = "";
            }
            lastKnownFeatureState = featureEnabled;
        }
    }

    private void clearChunkData() {
        synchronized (chunkSetLock) {
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
        }
        pendingChunkPackets.clear();
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
        synchronized (chunkSetLock) {
            evictChunksFromSet(newChunks, newChunksByRegion, playerChunkX, playerChunkZ, evictionRadiusChunks);
            evictChunksFromSet(oldChunks, oldChunksByRegion, playerChunkX, playerChunkZ, evictionRadiusChunks);
            evictChunksFromSet(tickExploitChunks, tickExploitChunksByRegion, playerChunkX, playerChunkZ, evictionRadiusChunks);
            evictChunksFromSet(beingUpdatedOldChunks, beingUpdatedChunksByRegion, playerChunkX, playerChunkZ, evictionRadiusChunks);
            evictChunksFromSet(oldGenerationChunks, oldGenerationChunksByRegion, playerChunkX, playerChunkZ, evictionRadiusChunks);
        }
    }

    /**
     * Removes chunks from a set if they're outside the eviction radius from the player
     */
    private void evictChunksFromSet(Set<ChunkPos> chunkSet, Map<Long, Set<ChunkPos>> regionMap, 
            int playerChunkX, int playerChunkZ, int evictionRadiusChunks) {
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
        }
    }

    public void clearCurrentWorldData() {
        ensureTrackingWorld();
        clearChunkData();

        Path baseDir = getBaseDir();
        try {
            Files.deleteIfExists(baseDir.resolve(NEW_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(OLD_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(BLOCK_EXPLOIT_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(BEING_UPDATED_CHUNK_DATA));
            Files.deleteIfExists(baseDir.resolve(OLD_GENERATION_CHUNK_DATA));
        } catch (IOException ignored) {
        }

        ensureDataFiles();
    }

    private void loadWorld(String worldKey) {
        clearChunkData();
        loadedWorldKey = worldKey;
        ensureDataFiles();
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

        // Helper to load and index chunks if in window
        java.util.function.Consumer<ChunkPos> indexNew = cp -> { synchronized (chunkSetLock) { newChunks.add(cp); indexChunk(cp, newChunksByRegion);} };
        java.util.function.Consumer<ChunkPos> indexOld = cp -> { synchronized (chunkSetLock) { oldChunks.add(cp); indexChunk(cp, oldChunksByRegion);} };
        java.util.function.Consumer<ChunkPos> indexBlock = cp -> { synchronized (chunkSetLock) { tickExploitChunks.add(cp); indexChunk(cp, tickExploitChunksByRegion);} };
        java.util.function.Consumer<ChunkPos> indexUpd = cp -> { synchronized (chunkSetLock) { beingUpdatedOldChunks.add(cp); indexChunk(cp, beingUpdatedChunksByRegion);} };
        java.util.function.Consumer<ChunkPos> indexOldGen = cp -> { synchronized (chunkSetLock) { oldGenerationChunks.add(cp); indexChunk(cp, oldGenerationChunksByRegion);} };

        int loadedCount = 0;
        for (Path file : new Path[]{BLOCK_EXPLOIT_CHUNK_DATA, OLD_CHUNK_DATA, NEW_CHUNK_DATA, BEING_UPDATED_CHUNK_DATA, OLD_GENERATION_CHUNK_DATA}) {
            try {
                for (String line : Files.readAllLines(getDataPath(file), StandardCharsets.UTF_8)) {
                    ChunkPos cp = parseChunk(line);
                    if (cp == null) continue;
                    if (cp.x() < minX || cp.x() > maxX || cp.z() < minZ || cp.z() > maxZ) continue;
                    if (file == NEW_CHUNK_DATA) indexNew.accept(cp);
                    else if (file == OLD_CHUNK_DATA) indexOld.accept(cp);
                    else if (file == BLOCK_EXPLOIT_CHUNK_DATA) indexBlock.accept(cp);
                    else if (file == BEING_UPDATED_CHUNK_DATA) indexUpd.accept(cp);
                    else if (file == OLD_GENERATION_CHUNK_DATA) indexOldGen.accept(cp);
                    loadedCount++;
                }
            } catch (IOException ignored) {
            }
        }
        reconcileLoadedData();
        // no debug logging
    }

    private void reconcileLoadedData() {
        synchronized (chunkSetLock) {
            // Precedence: old-generation > being-updated > new > old > block-exploit
            oldChunks.removeAll(newChunks);
            tickExploitChunks.removeAll(newChunks);

            newChunks.removeAll(beingUpdatedOldChunks);
            oldChunks.removeAll(beingUpdatedOldChunks);
            tickExploitChunks.removeAll(beingUpdatedOldChunks);

            newChunks.removeAll(oldGenerationChunks);
            oldChunks.removeAll(oldGenerationChunks);
            beingUpdatedOldChunks.removeAll(oldGenerationChunks);
            tickExploitChunks.removeAll(oldGenerationChunks);

            tickExploitChunks.removeAll(oldChunks);
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

    private Path getDataPath(Path fileName) {
        return getBaseDir().resolve(fileName);
    }

    private Path getBaseDir() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameDirectory.toPath()
                .resolve("voxelmap")
                .resolve("chunk_overlays")
                .resolve("newer_new_chunks")
                .resolve(getWorldKey());
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

    private static void appendChunk(Path path, ChunkPos chunkPos) {
        String line = chunkPos.x() + "," + chunkPos.z() + System.lineSeparator();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
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

    /**
     * Returns the appropriate region map for a given chunk set
     */
    private Map<Long, Set<ChunkPos>> getRegionMapForSource(Set<ChunkPos> source) {
        if (source == newChunks) {
            return newChunksByRegion;
        } else if (source == oldChunks) {
            return oldChunksByRegion;
        } else if (source == tickExploitChunks) {
            return tickExploitChunksByRegion;
        } else if (source == beingUpdatedOldChunks) {
            return beingUpdatedChunksByRegion;
        } else if (source == oldGenerationChunks) {
            return oldGenerationChunksByRegion;
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
