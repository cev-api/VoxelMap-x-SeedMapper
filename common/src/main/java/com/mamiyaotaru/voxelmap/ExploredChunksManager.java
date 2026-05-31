package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.persistent.explored.ExploredAsyncLoader;
import com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore;
import com.mamiyaotaru.voxelmap.persistent.explored.ExploredV3Migrator;
import com.mamiyaotaru.voxelmap.persistent.ThreadManager;
import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ExploredChunksManager {
    private static final int MAX_INTERPOLATED_GAP_CHUNKS = 8;
    private static final long FLUSH_INTERVAL_MS = 4000L;
    private static final CellGrid EMPTY_CELLS = new CellGrid(0, 0, 0, 0);

    private record StoreCtx(ExploredDiskStore store, ExploredAsyncLoader loader) {
    }

    private final Object worldLock = new Object();
    private volatile StoreCtx ctx;        // null until the first world is tracked
    private String loadedWorldKey = "";   // main-thread only (written under worldLock)
    private volatile int worldGen = 0;    // bumped on each world (re)load / clear
    private Integer lastChunkX;
    private Integer lastChunkZ;
    private long lastFlushMs = 0L;
    private volatile boolean loadIncomplete = false;
    private volatile java.util.Map<String, StoreCtx> exploredPlayerLayers = java.util.Map.of();

    public void onTick() {
        if (!ensureTrackingWorld()) {
            return;
        }
        StoreCtx c = ctx;
        if (c == null) {
            return;
        }
        int chunkX = GameVariableAccessShim.xCoord() >> 4;
        int chunkZ = GameVariableAccessShim.zCoord() >> 4;
        if (lastChunkX == null || lastChunkX != chunkX || lastChunkZ == null || lastChunkZ != chunkZ) {
            if (lastChunkX == null || lastChunkZ == null) {
                c.store().setChunk(chunkX, chunkZ);
            } else {
                markExploredPath(c.store(), lastChunkX, lastChunkZ, chunkX, chunkZ);
            }
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
        long now = System.currentTimeMillis();
        if (c.store().hasDirty() && now - lastFlushMs >= FLUSH_INTERVAL_MS) {
            lastFlushMs = now;
            c.loader().requestFlush();
        }
    }

    public Set<ChunkPos> getExploredChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        if (!ensureTrackingWorld()) {
            return Set.of();
        }
        StoreCtx c = ctx;
        if (c == null) {
            return Set.of();
        }
        ensureLoadedForBounds(c, 0, centerChunkX, centerChunkZ, radius);
        Set<ChunkPos> result = new HashSet<>();
        c.store().forEachExploredChunkInRange(centerChunkX, centerChunkZ, radius,
                (x, z) -> result.add(new ChunkPos(x, z)));
        return result;
    }

    public CellGrid getExploredCellsInRange(int centerChunkX, int centerChunkZ, int radius, int cellChunkSize) {
        if (!ensureTrackingWorld() || cellChunkSize <= 0) {
            return EMPTY_CELLS;
        }
        StoreCtx c = ctx;
        if (c == null) {
            return EMPTY_CELLS;
        }
        return buildCellGrid(c, centerChunkX, centerChunkZ, radius, cellChunkSize);
    }

    public CellGrid getPlayerExploredCellsInRange(String slug, int centerChunkX, int centerChunkZ, int radius, int cellChunkSize) {
        if (cellChunkSize <= 0) {
            return EMPTY_CELLS;
        }
        StoreCtx c = exploredPlayerLayers.get(slug);
        if (c == null) {
            return EMPTY_CELLS;
        }
        return buildCellGrid(c, centerChunkX, centerChunkZ, radius, cellChunkSize);
    }

    public Set<ChunkPos> getPlayerExploredChunksInRange(String slug, int centerChunkX, int centerChunkZ, int radius) {
        StoreCtx c = exploredPlayerLayers.get(slug);
        if (c == null) {
            return Set.of();
        }
        ensureLoadedForBounds(c, 0, centerChunkX, centerChunkZ, radius);
        Set<ChunkPos> result = new HashSet<>();
        c.store().forEachExploredChunkInRange(centerChunkX, centerChunkZ, radius,
                (x, z) -> result.add(new ChunkPos(x, z)));
        return result;
    }

    public java.util.Set<String> playerLayerSlugs() {
        return exploredPlayerLayers.keySet();
    }

    private CellGrid buildCellGrid(StoreCtx c, int centerChunkX, int centerChunkZ, int radius, int cellChunkSize) {
        int level = ExploredDiskStore.selectLevelForCellSize(cellChunkSize);
        ensureLoadedForBounds(c, level, centerChunkX, centerChunkZ, radius);
        int minCellX = Math.floorDiv(centerChunkX - radius, cellChunkSize);
        int maxCellX = Math.floorDiv(centerChunkX + radius, cellChunkSize);
        int minCellZ = Math.floorDiv(centerChunkZ - radius, cellChunkSize);
        int maxCellZ = Math.floorDiv(centerChunkZ + radius, cellChunkSize);
        int gw = maxCellX - minCellX + 1;
        int gh = maxCellZ - minCellZ + 1;
        if (gw <= 0 || gh <= 0 || (long) gw * gh > 16_000_000L) {
            return EMPTY_CELLS;
        }
        CellGrid grid = new CellGrid(minCellX, minCellZ, gw, gh);
        c.store().forEachExploredCellInRange(centerChunkX, centerChunkZ, radius, cellChunkSize,
                (cx, cz) -> grid.mark(cx, cz));
        return grid;
    }

    public long getDataVersion() {
        ensureTrackingWorld();
        StoreCtx c = ctx;
        long base = c == null ? 0L : c.store().dataVersion();
        return (((long) worldGen) << 48) ^ base; // changes on world transition AND on data change
    }

    public boolean consumeStorageLoadIncomplete() {
        boolean incomplete = loadIncomplete;
        loadIncomplete = false;
        return incomplete;
    }

    public void flushStorage() {
        StoreCtx c = ctx;
        if (c != null) {
            c.store().flush();
        }
    }

    public long[] exportExploredChunks() {
        if (!ensureTrackingWorld()) {
            return new long[0];
        }
        StoreCtx c = ctx;
        if (c == null) {
            return new long[0];
        }
        java.util.List<Long> out = new java.util.ArrayList<>();
        c.store().forEachStoredChunk((x, z) -> out.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)));
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    public int importExploredChunks(long[] chunks) {
        if (!ensureTrackingWorld()) {
            VoxelConstants.getLogger().warn("VoxelMap chunk import: no world is being tracked; nothing imported");
            return 0;
        }
        StoreCtx c = ctx;
        if (c == null) {
            VoxelConstants.getLogger().warn("VoxelMap chunk import: store not ready; nothing imported");
            return 0;
        }
        for (long ch : chunks) {
            c.store().setChunk((int) (ch >> 32), (int) ch);
        }
        c.store().flush();
        boolean verify = chunks.length > 0 && c.store().isChunkExplored((int) (chunks[0] >> 32), (int) chunks[0]);
        VoxelConstants.getLogger().info("VoxelMap chunk import: world='{}' dir={} chunks={} firstChunkStored={}",
                loadedWorldKey, currentV3Dir(), chunks.length, verify);
        return chunks.length;
    }

    public java.util.Map<String, long[]> exportAllDimensionsExplored() {
        flushStorage(); // get the current dimension's in-memory edits onto disk before scanning
        String server = serverName();
        java.util.Map<String, long[]> out = new java.util.LinkedHashMap<>();
        Path base = baseExploredDir();
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
                ExploredDiskStore store = new ExploredDiskStore(dir.resolve("v3"));
                java.util.List<Long> chunks = new java.util.ArrayList<>();
                store.forEachStoredChunk((x, z) -> chunks.add(((long) x << 32) ^ (z & 0xFFFFFFFFL)));
                long[] arr = new long[chunks.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = chunks.get(i);
                }
                out.put(dimension, arr);
            }
        } catch (java.io.IOException ignored) {
        }
        return out;
    }

    public int importDimensionExplored(String dimension, long[] chunks) {
        if (chunks.length == 0) {
            return 0;
        }
        String worldKey = serverName() + "_" + dimension;
        StoreCtx c = ctx;
        if (worldKey.equals(loadedWorldKey) && c != null) {
            for (long ch : chunks) {
                c.store().setChunk((int) (ch >> 32), (int) ch);
            }
            c.store().flush();
        } else {
            ExploredDiskStore store = new ExploredDiskStore(v3DirFor(worldKey));
            for (long ch : chunks) {
                store.setChunk((int) (ch >> 32), (int) ch);
            }
            store.flush();
        }
        return chunks.length;
    }

    public int importPlayerExplored(String slug, String dimension, long[] chunks) {
        if (chunks.length == 0) {
            return 0;
        }
        String worldKey = serverName() + "_" + dimension;
        if (worldKey.equals(loadedWorldKey)) {
            StoreCtx layer = exploredPlayerLayers.get(slug);
            if (layer == null) {
                layer = newContext(playerV3Dir(worldKey, slug));
                java.util.Map<String, StoreCtx> updated = new java.util.LinkedHashMap<>(exploredPlayerLayers);
                updated.put(slug, layer);
                exploredPlayerLayers = updated;
            }
            for (long ch : chunks) {
                layer.store().setChunk((int) (ch >> 32), (int) ch);
            }
            layer.store().flush();
        } else {
            ExploredDiskStore store = new ExploredDiskStore(playerV3Dir(worldKey, slug));
            for (long ch : chunks) {
                store.setChunk((int) (ch >> 32), (int) ch);
            }
            store.flush();
        }
        return chunks.length;
    }

    public boolean removePlayerLayer(String slug) {
        if (exploredPlayerLayers.containsKey(slug)) {
            java.util.Map<String, StoreCtx> updated = new java.util.LinkedHashMap<>(exploredPlayerLayers);
            updated.remove(slug);
            exploredPlayerLayers = updated;
        }
        String server = serverName();
        Path base = baseExploredDir();
        boolean any = false;
        if (java.nio.file.Files.isDirectory(base)) {
            try (java.util.stream.Stream<Path> dirs = java.nio.file.Files.list(base)) {
                for (Path dir : (Iterable<Path>) dirs.filter(java.nio.file.Files::isDirectory)::iterator) {
                    if (!dir.getFileName().toString().startsWith(server + "_")) {
                        continue;
                    }
                    Path slugDir = dir.resolve("players").resolve(slug);
                    if (java.nio.file.Files.exists(slugDir)) {
                        deleteDirectory(slugDir);
                        any = true;
                    }
                }
            } catch (java.io.IOException ignored) {
            }
        }
        return any;
    }

    private Path playersDir(String worldKey) {
        return baseExploredDir().resolve(worldKey).resolve("players");
    }

    private Path playerV3Dir(String worldKey, String slug) {
        return playersDir(worldKey).resolve(slug).resolve("v3");
    }

    private java.util.Map<String, StoreCtx> loadPlayerLayers(String worldKey) {
        java.util.Map<String, StoreCtx> map = new java.util.LinkedHashMap<>();
        Path dir = playersDir(worldKey);
        if (java.nio.file.Files.isDirectory(dir)) {
            try (java.util.stream.Stream<Path> dirs = java.nio.file.Files.list(dir)) {
                for (Path p : (Iterable<Path>) dirs.filter(java.nio.file.Files::isDirectory)::iterator) {
                    map.put(p.getFileName().toString(), newContext(p.resolve("v3")));
                }
            } catch (java.io.IOException ignored) {
            }
        }
        return map;
    }

    public void clearCurrentWorld() {
        synchronized (worldLock) {
            if (ctx == null) {
                return;
            }
            worldGen++;
            Path v3Dir = currentV3Dir();
            deleteDirectory(v3Dir);
            ctx = newContext(v3Dir);
            lastChunkX = null;
            lastChunkZ = null;
            loadIncomplete = false;
        }
    }

    // ---- internals ----

    private void ensureLoadedForBounds(StoreCtx c, int level, int centerChunkX, int centerChunkZ, int radius) {
        int containerShift = 5 * (level + 1) + 5; // chunk -> tile (>>5*(level+1)) -> container (>>5)
        int minContainerX = (centerChunkX - radius) >> containerShift;
        int maxContainerX = (centerChunkX + radius) >> containerShift;
        int minContainerZ = (centerChunkZ - radius) >> containerShift;
        int maxContainerZ = (centerChunkZ + radius) >> containerShift;
        boolean anyMissing = false;
        for (int cx = minContainerX; cx <= maxContainerX; cx++) {
            for (int cz = minContainerZ; cz <= maxContainerZ; cz++) {
                if (!c.store().isContainerLoaded(level, cx, cz)) {
                    anyMissing = true;
                    c.loader().requestLoad(level, cx, cz);
                }
            }
        }
        if (anyMissing) {
            loadIncomplete = true; // render keeps its previous cache and retries until these loads land
        }
    }

    private boolean ensureTrackingWorld() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return false;
        }
        String worldKey = getWorldKey();
        if (ctx == null || !worldKey.equals(loadedWorldKey)) {
            loadWorld(worldKey);
        }
        return true;
    }

    private void loadWorld(String worldKey) {
        synchronized (worldLock) {
            if (ctx != null && worldKey.equals(loadedWorldKey)) {
                return; // already loaded by another call path
            }
            StoreCtx old = ctx;
            if (old != null) {
                old.store().flush();
            }
            loadedWorldKey = worldKey;
            worldGen++;
            Path v3Dir = v3DirFor(worldKey);
            ctx = newContext(v3Dir);
            exploredPlayerLayers = loadPlayerLayers(worldKey);
            lastChunkX = null;
            lastChunkZ = null;
            loadIncomplete = false;
            maybeMigrate(worldKey, v3Dir, worldGen);
        }
    }

    private void maybeMigrate(String worldKey, Path v3Dir, int gen) {
        if (ExploredV3Migrator.isComplete(v3Dir)) {
            return;
        }
        Path v1 = legacyTextPath(worldKey);
        Path v2 = v2RegionDir(worldKey);
        Thread thread = new Thread(() -> {
            long start = System.currentTimeMillis();
            VoxelConstants.getLogger().info("VoxelMap: explored V3 (re)migration starting for {}", worldKey);
            ExploredV3Migrator.migrate(v1, v2, v3Dir);
            VoxelConstants.getLogger().info("VoxelMap: explored V3 (re)migration for {} finished in {} ms", worldKey, System.currentTimeMillis() - start);
            synchronized (worldLock) {
                if (gen == worldGen) {
                    ctx = newContext(v3Dir);
                    loadIncomplete = true;
                }
            }
        }, "VoxelMap Explored V3 Migration");
        thread.setDaemon(true);
        thread.start();
    }

    private StoreCtx newContext(Path v3Dir) {
        ExploredDiskStore store = new ExploredDiskStore(v3Dir);
        return new StoreCtx(store, new ExploredAsyncLoader(store, ThreadManager.executorService));
    }

    private void markExploredPath(ExploredDiskStore store, int startX, int startZ, int endX, int endZ) {
        int dx = Math.abs(endX - startX);
        int dz = Math.abs(endZ - startZ);
        if (Math.max(dx, dz) > MAX_INTERPOLATED_GAP_CHUNKS) {
            store.setChunk(endX, endZ);
            return;
        }
        int stepX = Integer.compare(endX, startX);
        int stepZ = Integer.compare(endZ, startZ);
        int error = dx - dz;
        int x = startX;
        int z = startZ;
        while (true) {
            store.setChunk(x, z);
            if (x == endX && z == endZ) {
                break;
            }
            int e2 = error * 2;
            if (e2 > -dz) {
                error -= dz;
                x += stepX;
            }
            if (e2 < dx) {
                error += dx;
                z += stepZ;
            }
        }
    }


    private Path currentV3Dir() {
        return v3DirFor(loadedWorldKey == null || loadedWorldKey.isEmpty() ? getWorldKey() : loadedWorldKey);
    }

    private Path baseExploredDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxelmap").resolve("chunk_overlays").resolve("explored");
    }

    private Path v3DirFor(String worldKey) {
        return baseExploredDir().resolve(worldKey).resolve("v3");
    }

    private Path v2RegionDir(String worldKey) {
        return baseExploredDir().resolve(worldKey).resolve("v2").resolve("explored");
    }

    private Path legacyTextPath(String worldKey) {
        return baseExploredDir().resolve(worldKey + ".txt");
    }

    private String getWorldKey() {
        Level level = GameVariableAccessShim.getWorld();
        String dimension = level == null ? "unknown" : level.dimension().identifier().toString().replace(':', '_');
        return serverName() + "_" + dimension;
    }

    private String serverName() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData serverData = minecraft.getCurrentServer();
        return serverData != null && serverData.ip != null && !serverData.ip.isBlank()
                ? serverData.ip.replace(':', '_')
                : minecraft.hasSingleplayerServer() ? "singleplayer" : "unknown";
    }

    private static void deleteDirectory(Path path) {
        try {
            if (java.nio.file.Files.notExists(path)) {
                return;
            }
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        java.nio.file.Files.deleteIfExists(p);
                    } catch (java.io.IOException ignored) {
                    }
                });
            }
        } catch (java.io.IOException ignored) {
        }
    }
}
