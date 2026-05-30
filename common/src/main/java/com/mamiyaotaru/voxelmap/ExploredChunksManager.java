package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.persistent.explored.ExploredAsyncLoader;
import com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore;
import com.mamiyaotaru.voxelmap.persistent.explored.ExploredV3Migrator;
import com.mamiyaotaru.voxelmap.persistent.ThreadManager;
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

    public HashSet<Long> getExploredCellsInRange(int centerChunkX, int centerChunkZ, int radius, int cellChunkSize) {
        HashSet<Long> result = new HashSet<>();
        if (!ensureTrackingWorld() || cellChunkSize <= 0) {
            return result;
        }
        StoreCtx c = ctx;
        if (c == null) {
            return result;
        }
        int level = ExploredDiskStore.selectLevelForCellSize(cellChunkSize);
        ensureLoadedForBounds(c, level, centerChunkX, centerChunkZ, radius);
        c.store().forEachExploredCellInRange(centerChunkX, centerChunkZ, radius, cellChunkSize,
                (cx, cz) -> result.add(cellKey(cx, cz)));
        return result;
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
        if (anyMissing || c.loader().pendingCount() > 0) {
            loadIncomplete = true; // render keeps its previous cache and retries until loads land
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
        ThreadManager.executorService.execute(() -> {
            ExploredV3Migrator.migrate(v1, v2, v3Dir);
            synchronized (worldLock) {
                if (gen == worldGen) {
                    ctx = newContext(v3Dir);
                    loadIncomplete = true;
                }
            }
        });
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

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
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
        Minecraft minecraft = Minecraft.getInstance();
        ServerData serverData = minecraft.getCurrentServer();
        String server = serverData != null && serverData.ip != null && !serverData.ip.isBlank()
                ? serverData.ip.replace(':', '_')
                : minecraft.hasSingleplayerServer() ? "singleplayer" : "unknown";
        return server + "_" + dimension;
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
