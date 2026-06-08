package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.CellGrid;
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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
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
    // Restore original scan cadence; we only limit IO/loading radius, not scanning speed
    private static final int RESCAN_INTERVAL_TICKS = 40; // every 2s (vanilla behavior here)

    private static final int NNC_V2_MAGIC = 0x4E4E4332; // "NNC2"
    private static final long FLUSH_INTERVAL_MS = 4000L;

    private record CategoryStore(com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore store,
                                 com.mamiyaotaru.voxelmap.persistent.explored.ExploredAsyncLoader loader) {
    }

    private final Set<ChunkPos> pendingChunkPackets = ConcurrentHashMap.newKeySet();

    private static final CellGrid EMPTY_CELL_GRID = new CellGrid(0, 0, 0, 0);

    private final Object worldLock = new Object();
    private volatile EnumMap<OverlayType, CategoryStore> stores;
    private volatile java.util.Map<String, EnumMap<OverlayType, CategoryStore>> newOldPlayerLayers = java.util.Map.of();
    private volatile int worldGen = 0;
    private long lastFlushMs = 0L;

    private String loadedWorldKey = "";
    private long dataVersion = 0L;
    private long lastRescanGameTime = Long.MIN_VALUE;
    private boolean lastKnownFeatureState = false;
    private volatile boolean storageLoadIncomplete = false;

    private record ChunkClassification(boolean isNewChunk, boolean isOldGeneration, boolean chunkIsBeingUpdated) {
    }

    public record NewOldChunksSnapshot(Set<ChunkPos> oldChunks, Set<ChunkPos> newChunks) {
    }

    public record NewOldCellsSnapshot(CellGrid oldCells, CellGrid newCells, int oldChunks, int newChunks) {
        static final NewOldCellsSnapshot EMPTY = new NewOldCellsSnapshot(new CellGrid(0, 0, 0, 0), new CellGrid(0, 0, 0, 0), 0, 0);
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
    }

    // -------------------------------------------------------------------------
    // World tracking / store lifecycle
    // -------------------------------------------------------------------------

    private boolean ensureTrackingWorld() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            flushAll();
            return false;
        }
        String worldKey = getWorldKey();
        if (stores == null || !worldKey.equals(loadedWorldKey)) {
            loadWorld(worldKey);
        }
        return true;
    }

    private void loadWorld(String worldKey) {
        synchronized (worldLock) {
            if (stores != null && worldKey.equals(loadedWorldKey)) {
                return;
            }
            flushAll();
            loadedWorldKey = worldKey;
            worldGen++;
            dataVersion++;
            stores = buildStores(worldKey);
            newOldPlayerLayers = loadPlayerLayers(worldKey);
            maybeMigrate(worldKey, worldGen);
        }
    }

    private EnumMap<OverlayType, CategoryStore> buildStores(String worldKey) {
        EnumMap<OverlayType, CategoryStore> map = new EnumMap<>(OverlayType.class);
        for (OverlayType type : OverlayType.values()) {
            var store = new com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore(v3DirFor(worldKey, type));
            map.put(type, new CategoryStore(store,
                    new com.mamiyaotaru.voxelmap.persistent.explored.ExploredAsyncLoader(store, ThreadManager.executorService)));
        }
        return map;
    }

    private EnumMap<OverlayType, CategoryStore> buildPlayerStores(String worldKey, String slug) {
        EnumMap<OverlayType, CategoryStore> map = new EnumMap<>(OverlayType.class);
        for (OverlayType type : OverlayType.values()) {
            var store = new com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore(playerV3Dir(worldKey, slug, type));
            map.put(type, new CategoryStore(store,
                    new com.mamiyaotaru.voxelmap.persistent.explored.ExploredAsyncLoader(store, ThreadManager.executorService)));
        }
        return map;
    }

    private Path playerV3Dir(String worldKey, String slug, OverlayType type) {
        return nncBaseDir(worldKey).resolve("players").resolve(slug).resolve("v3").resolve(type.directoryName);
    }

    private java.util.Map<String, EnumMap<OverlayType, CategoryStore>> loadPlayerLayers(String worldKey) {
        java.util.Map<String, EnumMap<OverlayType, CategoryStore>> map = new java.util.LinkedHashMap<>();
        Path dir = nncBaseDir(worldKey).resolve("players");
        if (java.nio.file.Files.isDirectory(dir)) {
            try (java.util.stream.Stream<Path> dirs = java.nio.file.Files.list(dir)) {
                for (Path p : (Iterable<Path>) dirs.filter(java.nio.file.Files::isDirectory)::iterator) {
                    map.put(p.getFileName().toString(), buildPlayerStores(worldKey, p.getFileName().toString()));
                }
            } catch (java.io.IOException ignored) {
            }
        }
        return map;
    }

    private synchronized EnumMap<OverlayType, CategoryStore> adoptPlayerLayerFromDisk(String slug) {
        EnumMap<OverlayType, CategoryStore> existing = newOldPlayerLayers.get(slug);
        if (existing != null) {
            return existing;
        }
        String worldKey = loadedWorldKey;
        if (worldKey == null || worldKey.isEmpty()) {
            return null;
        }
        Path slugDir = nncBaseDir(worldKey).resolve("players").resolve(slug);
        if (!java.nio.file.Files.isDirectory(slugDir)) {
            return null;
        }
        EnumMap<OverlayType, CategoryStore> layer = buildPlayerStores(worldKey, slug);
        var updated = new java.util.LinkedHashMap<>(newOldPlayerLayers);
        updated.put(slug, layer);
        newOldPlayerLayers = updated;
        return layer;
    }

    public java.util.Set<String> playerLayerSlugs() {
        ensureTrackingWorld();
        return newOldPlayerLayers.keySet();
    }

    public int importPlayerNewOld(String slug, String dimension, java.util.Map<String, long[]> byCategory) {
        ensureTrackingWorld();
        String worldKey = serverName() + "_" + dimension;
        boolean current = worldKey.equals(loadedWorldKey);
        EnumMap<OverlayType, CategoryStore> layer = null;
        if (current) {
            layer = newOldPlayerLayers.get(slug);
            if (layer == null) {
                layer = buildPlayerStores(worldKey, slug);
                var updated = new java.util.LinkedHashMap<>(newOldPlayerLayers);
                updated.put(slug, layer);
                newOldPlayerLayers = updated;
            }
        }
        int total = 0;
        for (OverlayType type : OverlayType.values()) {
            long[] chunks = byCategory.get(type.directoryName);
            if (chunks == null || chunks.length == 0) {
                continue;
            }
            var store = layer != null ? layer.get(type).store()
                    : new com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore(playerV3Dir(worldKey, slug, type));
            for (long ch : chunks) {
                store.setChunk((int) (ch >> 32), (int) ch);
            }
            store.flush();
            total += chunks.length;
        }
        return total;
    }

    public boolean removePlayerLayer(String slug) {
        if (newOldPlayerLayers.containsKey(slug)) {
            var updated = new java.util.LinkedHashMap<>(newOldPlayerLayers);
            updated.remove(slug);
            newOldPlayerLayers = updated;
        }
        String server = serverName();
        Path base = newOldRoot();
        boolean any = false;
        if (java.nio.file.Files.isDirectory(base)) {
            try (java.util.stream.Stream<Path> dirs = java.nio.file.Files.list(base)) {
                for (Path dir : (Iterable<Path>) dirs.filter(java.nio.file.Files::isDirectory)::iterator) {
                    if (!dir.getFileName().toString().startsWith(server + "_")) {
                        continue;
                    }
                    Path slugDir = dir.resolve("players").resolve(slug);
                    if (java.nio.file.Files.exists(slugDir)) {
                        deletePlayerDir(slugDir);
                        any = true;
                    }
                }
            } catch (java.io.IOException ignored) {
            }
        }
        return any;
    }

    private static void deletePlayerDir(Path path) {
        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (java.io.IOException ignored) {
                }
            });
        } catch (java.io.IOException ignored) {
        }
    }

    private void maybeMigrate(String worldKey, int gen) {
        boolean anyNeeded = false;
        for (OverlayType type : OverlayType.values()) {
            if (!com.mamiyaotaru.voxelmap.persistent.explored.ExploredV3Migrator.isComplete(v3DirFor(worldKey, type))) {
                anyNeeded = true;
                break;
            }
        }
        if (!anyNeeded) {
            return;
        }
        Thread thread = new Thread(() -> {
            for (OverlayType type : OverlayType.values()) {
                Path v3Dir = v3DirFor(worldKey, type);
                if (com.mamiyaotaru.voxelmap.persistent.explored.ExploredV3Migrator.isComplete(v3Dir)) {
                    continue;
                }
                com.mamiyaotaru.voxelmap.persistent.explored.ExploredV3Migrator.migrate(
                        legacyTextPath(worldKey, type), v2OverlayDir(worldKey, type), v3Dir, NNC_V2_MAGIC);
            }
            synchronized (worldLock) {
                if (gen == worldGen) {
                    stores = buildStores(worldKey);
                    storageLoadIncomplete = true;
                }
            }
        }, "VoxelMap NewOld V3 Migration");
        thread.setDaemon(true);
        thread.start();
    }

    private Path nncBaseDir(String worldKey) {
        return newOldRoot().resolve(worldKey);
    }

    private Path newOldRoot() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxelmap").resolve("chunk_overlays").resolve("newer_new_chunks");
    }

    private String serverName() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData serverData = minecraft.getCurrentServer();
        return serverData != null && serverData.ip != null && !serverData.ip.isBlank()
                ? serverData.ip.replace(':', '_')
                : minecraft.hasSingleplayerServer() ? "singleplayer" : "unknown";
    }

    public java.util.Map<String, java.util.Map<String, long[]>> exportAllDimensionsNewOld() {
        flushAll();
        String server = serverName();
        java.util.Map<String, java.util.Map<String, long[]>> out = new java.util.LinkedHashMap<>();
        Path base = newOldRoot();
        if (java.nio.file.Files.notExists(base)) {
            return out;
        }
        try (java.util.stream.Stream<Path> dirs = java.nio.file.Files.list(base)) {
            for (Path dir : (Iterable<Path>) dirs.filter(java.nio.file.Files::isDirectory)::iterator) {
                String name = dir.getFileName().toString();
                if (!name.startsWith(server + "_")) {
                    continue;
                }
                String dimension = name.substring(server.length() + 1);
                java.util.Map<String, long[]> byCategory = new java.util.LinkedHashMap<>();
                for (OverlayType type : OverlayType.values()) {
                    var store = new com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore(
                            dir.resolve("v3").resolve(type.directoryName));
                    java.util.List<Long> chunks = new java.util.ArrayList<>();
                    store.forEachStoredChunk((x, z) -> chunks.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)));
                    long[] arr = new long[chunks.size()];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = chunks.get(i);
                    }
                    byCategory.put(type.directoryName, arr);
                }
                out.put(dimension, byCategory);
            }
        } catch (java.io.IOException ignored) {
        }
        return out;
    }

    public int importDimensionNewOld(String dimension, java.util.Map<String, long[]> byCategory) {
        String worldKey = serverName() + "_" + dimension;
        var s = stores;
        boolean current = worldKey.equals(loadedWorldKey) && s != null;
        int total = 0;
        for (OverlayType type : OverlayType.values()) {
            long[] chunks = byCategory.get(type.directoryName);
            if (chunks == null || chunks.length == 0) {
                continue;
            }
            var store = current ? s.get(type).store()
                    : new com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore(v3DirFor(worldKey, type));
            for (long ch : chunks) {
                store.setChunk((int) (ch >> 32), (int) ch);
            }
            store.flush();
            total += chunks.length;
        }
        return total;
    }

    private Path v3DirFor(String worldKey, OverlayType type) {
        return nncBaseDir(worldKey).resolve("v3").resolve(type.directoryName);
    }

    private Path v2OverlayDir(String worldKey, OverlayType type) {
        return nncBaseDir(worldKey).resolve("v2").resolve(type.directoryName);
    }

    private Path legacyTextPath(String worldKey, OverlayType type) {
        return nncBaseDir(worldKey).resolve(type.legacyFile.getFileName());
    }

    private void flushAll() {
        var s = stores;
        if (s != null) {
            for (CategoryStore cs : s.values()) {
                cs.store().flush();
            }
        }
    }

    public void flushStorage() {
        flushAll();
    }

    public java.util.Map<String, long[]> exportNewOld() {
        if (!ensureTrackingWorld()) {
            return java.util.Map.of();
        }
        var s = stores;
        if (s == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, long[]> out = new java.util.LinkedHashMap<>();
        for (OverlayType type : OverlayType.values()) {
            java.util.List<Long> chunks = new java.util.ArrayList<>();
            s.get(type).store().forEachStoredChunk((x, z) -> chunks.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)));
            long[] arr = new long[chunks.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = chunks.get(i);
            }
            out.put(type.directoryName, arr);
        }
        return out;
    }

    public int importNewOld(java.util.Map<String, long[]> data) {
        if (!ensureTrackingWorld()) {
            return 0;
        }
        var s = stores;
        if (s == null) {
            return 0;
        }
        int total = 0;
        for (OverlayType type : OverlayType.values()) {
            long[] arr = data.get(type.directoryName);
            if (arr == null) {
                continue;
            }
            var store = s.get(type).store();
            for (long ch : arr) {
                store.setChunk((int) (ch >> 32), (int) ch);
            }
            store.flush();
            total += arr.length;
        }
        return total;
    }

    public void reloadWindowNow() {
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    public void onTick() {
        if (!ensureTrackingWorld()) {
            return;
        }
        processPendingChunkPackets();
        rescanLoadedChunksAroundPlayer();
        long now = System.currentTimeMillis();
        if (now - lastFlushMs >= FLUSH_INTERVAL_MS) {
            lastFlushMs = now;
            var s = stores;
            if (s != null) {
                for (CategoryStore cs : s.values()) {
                    if (cs.store().hasDirty()) {
                        cs.loader().requestFlush();
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Classification
    // -------------------------------------------------------------------------

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
                markNew(chunkPos);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Write routing (append-only; categories are permanent in V3)
    // -------------------------------------------------------------------------

    private void markNew(ChunkPos c)           { setChunk(OverlayType.NEW, c); }
    private void markOld(ChunkPos c)           { setChunk(OverlayType.OLD, c); }
    private void markTickExploit(ChunkPos c)   { setChunk(OverlayType.BLOCK_EXPLOIT, c); }
    private void markBeingUpdated(ChunkPos c)  { setChunk(OverlayType.BEING_UPDATED, c); }
    private void markOldGeneration(ChunkPos c) { setChunk(OverlayType.OLD_GENERATION, c); }

    private void setChunk(OverlayType type, ChunkPos c) {
        var s = stores;
        if (s != null) {
            s.get(type).store().setChunk(c.x(), c.z());
            markDataChanged();
        }
    }

    private boolean containsAny(ChunkPos c) {
        var s = stores;
        if (s == null) {
            return false;
        }
        for (CategoryStore cs : s.values()) {
            if (cs.store().isChunkExplored(c.x(), c.z())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsFinalChunkType(ChunkPos c) {
        var s = stores;
        if (s == null) {
            return false;
        }
        return s.get(OverlayType.NEW).store().isChunkExplored(c.x(), c.z())
                || s.get(OverlayType.OLD).store().isChunkExplored(c.x(), c.z())
                || s.get(OverlayType.BEING_UPDATED).store().isChunkExplored(c.x(), c.z())
                || s.get(OverlayType.OLD_GENERATION).store().isChunkExplored(c.x(), c.z());
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public Set<ChunkPos> getNewChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return queryChunksInRange(OverlayType.NEW, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE, null);
    }

    public Set<ChunkPos> getNewChunksInRange(int centerChunkX, int centerChunkZ, int radius, int maxResults) {
        ensureTrackingWorld();
        return queryChunksInRange(OverlayType.NEW, centerChunkX, centerChunkZ, radius, maxResults, null);
    }

    public Set<ChunkPos> getOldChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return queryChunksInRange(OverlayType.OLD, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE, null);
    }

    public Set<ChunkPos> getOldChunksInRange(int centerChunkX, int centerChunkZ, int radius, int maxResults) {
        ensureTrackingWorld();
        return queryChunksInRange(OverlayType.OLD, centerChunkX, centerChunkZ, radius, maxResults, null);
    }

    public Set<ChunkPos> getBlockUpdateChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        var s = stores;
        if (s == null) {
            return new HashSet<>();
        }
        ensureLoaded(s, OverlayType.BLOCK_EXPLOIT, 0, centerChunkX, centerChunkZ, radius);
        ensureLoaded(s, OverlayType.NEW, 0, centerChunkX, centerChunkZ, radius);
        ensureLoaded(s, OverlayType.OLD, 0, centerChunkX, centerChunkZ, radius);
        ensureLoaded(s, OverlayType.BEING_UPDATED, 0, centerChunkX, centerChunkZ, radius);
        ensureLoaded(s, OverlayType.OLD_GENERATION, 0, centerChunkX, centerChunkZ, radius);
        HashSet<ChunkPos> result = new HashSet<>();
        var buStore = s.get(OverlayType.BLOCK_EXPLOIT).store();
        var newStore = s.get(OverlayType.NEW).store();
        var oldStore = s.get(OverlayType.OLD).store();
        var beingUpdStore = s.get(OverlayType.BEING_UPDATED).store();
        var ogStore = s.get(OverlayType.OLD_GENERATION).store();
        buStore.forEachExploredChunkInRange(centerChunkX, centerChunkZ, radius, (x, z) -> {
            if (!newStore.isChunkExplored(x, z)
                    && !oldStore.isChunkExplored(x, z)
                    && !beingUpdStore.isChunkExplored(x, z)
                    && !ogStore.isChunkExplored(x, z)) {
                result.add(new ChunkPos(x, z));
            }
        });
        return result;
    }

    public Set<ChunkPos> getBeingUpdatedChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return queryChunksInRange(OverlayType.BEING_UPDATED, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE, null);
    }

    public Set<ChunkPos> getOldGenerationChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        return queryChunksInRange(OverlayType.OLD_GENERATION, centerChunkX, centerChunkZ, radius, Integer.MAX_VALUE, null);
    }

    private Set<ChunkPos> queryChunksInRange(OverlayType type, int cx, int cz, int radius, int maxResults,
            java.util.function.BiPredicate<Integer, Integer> extraFilter) {
        var s = stores;
        if (s == null || maxResults <= 0) {
            return new HashSet<>();
        }
        ensureLoaded(s, type, 0, cx, cz, radius);
        HashSet<ChunkPos> result = new HashSet<>();
        int cap = maxResults <= 0 ? Integer.MAX_VALUE : maxResults;
        s.get(type).store().forEachExploredChunkInRange(cx, cz, radius, (x, z) -> {
            if (result.size() < cap) {
                if (extraFilter == null || extraFilter.test(x, z)) {
                    result.add(new ChunkPos(x, z));
                }
            }
        });
        return result;
    }

    public NewOldChunksSnapshot getNewOldChunksInRange(int centerChunkX, int centerChunkZ, int radius, int maxResults) {
        if (!ensureTrackingWorld()) {
            return new NewOldChunksSnapshot(Set.of(), Set.of());
        }
        var s = stores;
        if (s == null) {
            return new NewOldChunksSnapshot(Set.of(), Set.of());
        }
        ensureLoaded(s, OverlayType.NEW, 0, centerChunkX, centerChunkZ, radius);
        ensureLoaded(s, OverlayType.OLD, 0, centerChunkX, centerChunkZ, radius);
        ensureLoaded(s, OverlayType.BEING_UPDATED, 0, centerChunkX, centerChunkZ, radius);
        ensureLoaded(s, OverlayType.OLD_GENERATION, 0, centerChunkX, centerChunkZ, radius);

        var newStore = s.get(OverlayType.NEW).store();
        var oldStore = s.get(OverlayType.OLD).store();
        var buStore = s.get(OverlayType.BEING_UPDATED).store();
        var ogStore = s.get(OverlayType.OLD_GENERATION).store();

        int budget = maxResults <= 0 ? Integer.MAX_VALUE : maxResults;

        HashSet<ChunkPos> newResult = new HashSet<>();
        newStore.forEachExploredChunkInRange(centerChunkX, centerChunkZ, radius, (x, z) -> {
            if (newResult.size() < budget) {
                newResult.add(new ChunkPos(x, z));
            }
        });

        int remainingBudget = budget == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, budget - newResult.size());
        HashSet<ChunkPos> oldResult = new HashSet<>();
        oldStore.forEachExploredChunkInRange(centerChunkX, centerChunkZ, radius, (x, z) -> {
            if (oldResult.size() < remainingBudget
                    && !newStore.isChunkExplored(x, z)
                    && !buStore.isChunkExplored(x, z)
                    && !ogStore.isChunkExplored(x, z)) {
                oldResult.add(new ChunkPos(x, z));
            }
        });

        return new NewOldChunksSnapshot(oldResult, newResult);
    }

    public NewOldCellsSnapshot getNewOldCellsInRange(int cx, int cz, int radius, int cellChunkSize) {
        return getNewOldCellsInRange(cx, cz, radius, cellChunkSize, null);
    }

    public NewOldCellsSnapshot getNewOldCellsInRange(int cx, int cz, int radius, int cellChunkSize, Identifier viewedDimension) {
        EnumMap<OverlayType, CategoryStore> s = resolveStores(viewedDimension);
        if (s == null) {
            return NewOldCellsSnapshot.EMPTY;
        }
        return snapshotFrom(s, cx, cz, radius, cellChunkSize);
    }

    public NewOldCellsSnapshot getPlayerNewOldCellsInRange(String slug, int cx, int cz, int radius, int cellChunkSize) {
        return getPlayerNewOldCellsInRange(slug, cx, cz, radius, cellChunkSize, null);
    }

    public NewOldCellsSnapshot getPlayerNewOldCellsInRange(String slug, int cx, int cz, int radius, int cellChunkSize, Identifier viewedDimension) {
        if (viewedDimension != null && !getWorldKeyForDimension(viewedDimension).equals(loadedWorldKey)) {
            // Different dimension — create a temporary store from disk
            String worldKey = getWorldKeyForDimension(viewedDimension);
            EnumMap<OverlayType, CategoryStore> layer = buildPlayerStoresIfNeeded(worldKey, slug);
            if (layer == null) {
                return NewOldCellsSnapshot.EMPTY;
            }
            return snapshotFrom(layer, cx, cz, radius, cellChunkSize);
        }
        ensureTrackingWorld();
        EnumMap<OverlayType, CategoryStore> layer = newOldPlayerLayers.get(slug);
        if (layer == null) {
            layer = adoptPlayerLayerFromDisk(slug);
        }
        if (layer == null) {
            return NewOldCellsSnapshot.EMPTY;
        }
        return snapshotFrom(layer, cx, cz, radius, cellChunkSize);
    }

    private NewOldCellsSnapshot snapshotFrom(EnumMap<OverlayType, CategoryStore> s, int cx, int cz, int radius, int cellChunkSize) {
        int cell = Math.max(1, cellChunkSize);
        int level = com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore.selectLevelForCellSize(cell);
        ensureLoaded(s, OverlayType.NEW, level, cx, cz, radius);
        ensureLoaded(s, OverlayType.OLD, level, cx, cz, radius);
        ensureLoaded(s, OverlayType.BEING_UPDATED, level, cx, cz, radius);
        ensureLoaded(s, OverlayType.OLD_GENERATION, level, cx, cz, radius);

        // Mark cells into flat grids (no HashSet<Long> -> no boxing / no x^z-collision treeification)
        int minCellX = Math.floorDiv(cx - radius, cell);
        int maxCellX = Math.floorDiv(cx + radius, cell);
        int minCellZ = Math.floorDiv(cz - radius, cell);
        int maxCellZ = Math.floorDiv(cz + radius, cell);
        int gw = maxCellX - minCellX + 1;
        int gh = maxCellZ - minCellZ + 1;
        if (gw <= 0 || gh <= 0 || (long) gw * gh > 16_000_000L) {
            return NewOldCellsSnapshot.EMPTY;
        }

        CellGrid newGrid = new CellGrid(minCellX, minCellZ, gw, gh);
        CellGrid oldGrid = new CellGrid(minCellX, minCellZ, gw, gh);
        CellGrid buOg = new CellGrid(minCellX, minCellZ, gw, gh); // beingUpdated | oldGeneration: always hide old

        s.get(OverlayType.NEW).store().forEachExploredCellInRange(cx, cz, radius, cell, (qx, qz) -> newGrid.mark(qx, qz));
        s.get(OverlayType.OLD).store().forEachExploredCellInRange(cx, cz, radius, cell, (qx, qz) -> oldGrid.mark(qx, qz));
        s.get(OverlayType.BEING_UPDATED).store().forEachExploredCellInRange(cx, cz, radius, cell, (qx, qz) -> buOg.mark(qx, qz));
        s.get(OverlayType.OLD_GENERATION).store().forEachExploredCellInRange(cx, cz, radius, cell, (qx, qz) -> buOg.mark(qx, qz));

        boolean oldPriority = cell > 1;
        boolean[] oldArr = oldGrid.cells;
        boolean[] newArr = newGrid.cells;
        boolean[] buOgArr = buOg.cells;

        for (int i = 0; i < oldArr.length; i++) {
            if (oldArr[i] && (buOgArr[i] || (!oldPriority && newArr[i]))) {
                oldArr[i] = false;
            }
        }
        int oldN = 0;
        int newN = 0;
        for (int i = 0; i < newArr.length; i++) {
            if (oldPriority && newArr[i] && oldArr[i]) {
                newArr[i] = false; // OLD wins this cell so the trail shows through
            }
            if (oldArr[i]) {
                oldN++;
            }
            if (newArr[i]) {
                newN++;
            }
        }
        return new NewOldCellsSnapshot(oldGrid, newGrid, oldN, newN);
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void ensureLoaded(EnumMap<OverlayType, CategoryStore> s, OverlayType type, int level, int cx, int cz, int radius) {
        if (s == null) {
            return;
        }
        CategoryStore cs = s.get(type);
        int containerShift = 5 * (level + 1) + 5;
        int minCX = (cx - radius) >> containerShift, maxCX = (cx + radius) >> containerShift;
        int minCZ = (cz - radius) >> containerShift, maxCZ = (cz + radius) >> containerShift;
        boolean missing = false;
        for (int ccx = minCX; ccx <= maxCX; ccx++) {
            for (int ccz = minCZ; ccz <= maxCZ; ccz++) {
                if (!cs.store().isContainerLoaded(level, ccx, ccz)) {
                    if (cs.loader() != null) {
                        missing = true;
                        cs.loader().requestLoad(level, ccx, ccz);
                    } else {
                        // Synchronous load for temporary/alternate-dimension stores
                        cs.store().loadContainer(level, ccx, ccz);
                    }
                }
            }
        }
        if (missing) {
            storageLoadIncomplete = true;
        }
    }

    // -------------------------------------------------------------------------
    // Data management
    // -------------------------------------------------------------------------

    public long getDataVersion() {
        return getDataVersion(null);
    }

    public long getDataVersion(Identifier viewedDimension) {
        EnumMap<OverlayType, CategoryStore> s = resolveStores(viewedDimension);
        if (s == null) {
            return 0L;
        }
        return dataVersion;
    }

    public String getLoadedWorldKey() {
        return getLoadedWorldKey(null);
    }

    public String getLoadedWorldKey(Identifier viewedDimension) {
        return viewedDimension != null ? getWorldKeyForDimension(viewedDimension) : loadedWorldKey;
    }

    // Debug getters
    public long getLastStorageLoadTimeMs() {
        return 0L;
    }

    public int getLastLoadedRegionFiles() {
        return 0;
    }

    public int getLastDecodedChunks() {
        return 0;
    }

    public int getLastSkippedMissingRegionFiles() {
        return 0;
    }

    public long getLastMigrationTimeMs() {
        return 0L;
    }

    public boolean consumeStorageLoadIncomplete() {
        boolean incomplete = storageLoadIncomplete;
        storageLoadIncomplete = false;
        return incomplete;
    }

    public void clearCurrentWorldData() {
        synchronized (worldLock) {
            worldGen++;
            flushAll();
            if (!loadedWorldKey.isEmpty()) {
                try {
                    deleteDirectoryIfExists(nncBaseDir(loadedWorldKey).resolve("v3"));
                } catch (IOException ignored) {
                }
            }
            stores = buildStores(loadedWorldKey);
            storageLoadIncomplete = false;
        }
        markDataChanged();
    }

    private void markDataChanged() {
        dataVersion++;
    }

    // -------------------------------------------------------------------------
    // Chunk processing helpers
    // -------------------------------------------------------------------------

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
    }

    // -------------------------------------------------------------------------
    // Classification logic
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // World key helpers
    // -------------------------------------------------------------------------

    private String getWorldKeyForDimension(Identifier dimensionId) {
        String dimension = dimensionId.toString().replace(':', '_');
        Minecraft minecraft = Minecraft.getInstance();
        ServerData serverData = minecraft.getCurrentServer();
        String server = serverData != null && serverData.ip != null && !serverData.ip.isBlank()
                ? serverData.ip.replace(':', '_')
                : minecraft.hasSingleplayerServer() ? "singleplayer" : "unknown";
        return server + "_" + dimension;
    }

    private EnumMap<OverlayType, CategoryStore> resolveStores(Identifier viewedDimension) {
        if (viewedDimension == null) {
            if (!ensureTrackingWorld()) {
                return null;
            }
            return stores;
        }
        String viewedKey = getWorldKeyForDimension(viewedDimension);
        if (viewedKey.equals(loadedWorldKey)) {
            if (!ensureTrackingWorld()) {
                return null;
            }
            return stores;
        }
        // Different dimension — create temporary read-only stores from disk (no async loaders)
        return buildStoresNoLoaders(viewedKey);
    }

    private EnumMap<OverlayType, CategoryStore> buildPlayerStoresIfNeeded(String worldKey, String slug) {
        Path slugDir = nncBaseDir(worldKey).resolve("players").resolve(slug);
        if (!java.nio.file.Files.isDirectory(slugDir)) {
            return null;
        }
        // Temporary read-only stores (no async loaders)
        EnumMap<OverlayType, CategoryStore> map = new EnumMap<>(OverlayType.class);
        for (OverlayType type : OverlayType.values()) {
            var store = new com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore(playerV3Dir(worldKey, slug, type));
            map.put(type, new CategoryStore(store, null));
        }
        return map;
    }

    private EnumMap<OverlayType, CategoryStore> buildStoresNoLoaders(String worldKey) {
        EnumMap<OverlayType, CategoryStore> map = new EnumMap<>(OverlayType.class);
        for (OverlayType type : OverlayType.values()) {
            var store = new com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore(v3DirFor(worldKey, type));
            map.put(type, new CategoryStore(store, null));
        }
        return map;
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

    // -------------------------------------------------------------------------
    // Static utilities
    // -------------------------------------------------------------------------

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
}
