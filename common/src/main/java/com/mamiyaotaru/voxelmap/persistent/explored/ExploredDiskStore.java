package com.mamiyaotaru.voxelmap.persistent.explored;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * persistent store for the explored chunk LOD pyramid that persists every level to sparse
 * container files under a base dir, disk I/O performed outside lock and only fast in-memory
 * pyramid mutation/read holds the lock, so a query thread never stalls behind a background disk read
 *
 *
 */
public final class ExploredDiskStore {
    private static final int CONTAINER_SHIFT = ExploredContainer.CONTAINER_SHIFT; // 5
    private static final int TILE_SHIFT = 5;

    private final Path baseDir;
    private final ExploredPyramid pyramid = new ExploredPyramid();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong dataVersion = new AtomicLong();

    private final Set<ContainerKey> dirty = new HashSet<>();   // guarded by lock
    private final Set<ContainerKey> loaded = new HashSet<>();  // guarded by lock

    public ExploredDiskStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    private record ContainerKey(int level, int containerX, int containerZ) {
    }

    public interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }

    public interface CellConsumer {
        void accept(int cellX, int cellZ);
    }

    public long dataVersion() {
        return dataVersion.get();
    }

    public boolean isChunkExplored(int chunkX, int chunkZ) {
        lock.readLock().lock();
        try {
            return pyramid.isChunkExplored(chunkX, chunkZ);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isContainerLoaded(int level, int containerX, int containerZ) {
        lock.readLock().lock();
        try {
            return loaded.contains(new ContainerKey(level, containerX, containerZ));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasDirty() {
        lock.readLock().lock();
        try {
            return !dirty.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setChunk(int chunkX, int chunkZ) {
        lock.writeLock().lock();
        try {
            pyramid.setChunk(chunkX, chunkZ, (level, tileX, tileZ) ->
                    dirty.add(new ContainerKey(level, tileX >> CONTAINER_SHIFT, tileZ >> CONTAINER_SHIFT)));
        } finally {
            lock.writeLock().unlock();
        }
        dataVersion.incrementAndGet();
    }

    private static long bitCoverage(int level) {
        long c = 1L;
        for (int i = 0; i < level; i++) {
            c <<= TILE_SHIFT;
        }
        return c;
    }

    public static int selectLevelForCellSize(int cellChunkSize) {
        int level = 0;
        while (level + 1 < ExploredPyramid.LEVELS && bitCoverage(level + 1) <= cellChunkSize) {
            level++;
        }
        return level;
    }

    public void forEachExploredChunkInRange(int centerChunkX, int centerChunkZ, int radius, ChunkConsumer consumer) {
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        lock.readLock().lock();
        try {
            int minTileX = Math.floorDiv(minChunkX, ExploredTile.SIDE);
            int maxTileX = Math.floorDiv(maxChunkX, ExploredTile.SIDE);
            int minTileZ = Math.floorDiv(minChunkZ, ExploredTile.SIDE);
            int maxTileZ = Math.floorDiv(maxChunkZ, ExploredTile.SIDE);
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                    ExploredTile tile = pyramid.tileAt(0, tileX, tileZ);
                    if (tile == null || tile.isEmpty()) {
                        continue;
                    }
                    int baseX = tileX << TILE_SHIFT;
                    int baseZ = tileZ << TILE_SHIFT;
                    for (int bx = 0; bx < ExploredTile.SIDE; bx++) {
                        for (int bz = 0; bz < ExploredTile.SIDE; bz++) {
                            if (!tile.get(bx, bz)) {
                                continue;
                            }
                            int chunkX = baseX + bx;
                            int chunkZ = baseZ + bz;
                            if (chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ) {
                                consumer.accept(chunkX, chunkZ);
                            }
                        }
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * calls {@code consumer} exactly once for each cell (size {@code cellChunkSize}) that contains any
     * exploration within [center +/- radius] and reads from the coarsest pyramid level whose per-bit
     * coverage still satisfies the cell size, so far-zoom queries touch only coarse tiles
     *
     */
    public void forEachExploredCellInRange(int centerChunkX, int centerChunkZ, int radius, int cellChunkSize, CellConsumer consumer) {
        if (cellChunkSize <= 0) {
            return;
        }
        int level = selectLevelForCellSize(cellChunkSize);
        long coverage = bitCoverage(level);
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        java.util.HashSet<Long> emittedCells = new java.util.HashSet<>();
        lock.readLock().lock();
        try {
            int levelTileShift = TILE_SHIFT * (level + 1);
            int minTileX = minChunkX >> levelTileShift;
            int maxTileX = maxChunkX >> levelTileShift;
            int minTileZ = minChunkZ >> levelTileShift;
            int maxTileZ = maxChunkZ >> levelTileShift;
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                    ExploredTile tile = pyramid.tileAt(level, tileX, tileZ);
                    if (tile == null || tile.isEmpty()) {
                        continue;
                    }
                    for (int bx = 0; bx < ExploredTile.SIDE; bx++) {
                        for (int bz = 0; bz < ExploredTile.SIDE; bz++) {
                            if (!tile.get(bx, bz)) {
                                continue;
                            }
                            long blockChunkX = ((long) tileX * ExploredTile.SIDE + bx) * coverage;
                            long blockChunkZ = ((long) tileZ * ExploredTile.SIDE + bz) * coverage;
                            if (blockChunkX + coverage - 1 < minChunkX || blockChunkX > maxChunkX
                                    || blockChunkZ + coverage - 1 < minChunkZ || blockChunkZ > maxChunkZ) {
                                continue;
                            }
                            int cellX = (int) Math.floorDiv(blockChunkX, (long) cellChunkSize);
                            int cellZ = (int) Math.floorDiv(blockChunkZ, (long) cellChunkSize);
                            long cellKey = ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
                            if (emittedCells.add(cellKey)) {
                                consumer.accept(cellX, cellZ);
                            }
                        }
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void loadContainer(int level, int containerX, int containerZ) {
        ContainerKey key = new ContainerKey(level, containerX, containerZ);
        lock.readLock().lock();
        try {
            if (loaded.contains(key)) {
                return;
            }
        } finally {
            lock.readLock().unlock();
        }

        ExploredContainer container = ExploredContainerIo.read(containerPath(level, containerX, containerZ));

        boolean merged = false;
        lock.writeLock().lock();
        try {
            if (!loaded.add(key)) {
                return; // another thread loaded it meanwhile
            }
            if (container != null) {
                mergeContainerLocked(level, containerX, containerZ, container);
                merged = true;
            }
        } finally {
            lock.writeLock().unlock();
        }
        if (merged) {
            // only a load that actually merged data changes what renders, a missing container is marked
            // loaded (so we don't retry it) but must NOT bump the version and trigger a spurious
            // re-query/re-render of unchanged data
            dataVersion.incrementAndGet();
        }
    }

    private void mergeContainerLocked(int level, int containerX, int containerZ, ExploredContainer container) {
        int baseTileX = containerX << CONTAINER_SHIFT;
        int baseTileZ = containerZ << CONTAINER_SHIFT;
        for (int localX = 0; localX < ExploredContainer.TILES_PER_SIDE; localX++) {
            for (int localZ = 0; localZ < ExploredContainer.TILES_PER_SIDE; localZ++) {
                ExploredTile tile = container.getTile(localX, localZ);
                if (tile != null) {
                    pyramid.mergeTile(level, baseTileX + localX, baseTileZ + localZ, tile);
                }
            }
        }
    }

    /** writes all dirty containers to disk*/
    public void flush() {
        List<PendingWrite> pending = new ArrayList<>();
        lock.writeLock().lock();
        try {
            if (dirty.isEmpty()) {
                return;
            }
            for (ContainerKey key : dirty) {
                ExploredContainer container = buildContainerLocked(key.level(), key.containerX(), key.containerZ());
                pending.add(new PendingWrite(containerPath(key.level(), key.containerX(), key.containerZ()), container.encode()));
            }
            dirty.clear();
        } finally {
            lock.writeLock().unlock();
        }

        for (PendingWrite write : pending) {
            try {
                ExploredContainerIo.writeBytes(write.path(), write.data());
            } catch (IOException ignored) {
            }
        }
    }

    private record PendingWrite(Path path, byte[] data) {
    }

    private ExploredContainer buildContainerLocked(int level, int containerX, int containerZ) {
        ExploredContainer container = new ExploredContainer(level, containerX, containerZ);
        int baseTileX = containerX << CONTAINER_SHIFT;
        int baseTileZ = containerZ << CONTAINER_SHIFT;
        for (int localX = 0; localX < ExploredContainer.TILES_PER_SIDE; localX++) {
            for (int localZ = 0; localZ < ExploredContainer.TILES_PER_SIDE; localZ++) {
                ExploredTile tile = pyramid.tileAt(level, baseTileX + localX, baseTileZ + localZ);
                if (tile != null && !tile.isEmpty()) {
                    container.putTile(localX, localZ, tile);
                }
            }
        }
        return container;
    }

    private Path containerPath(int level, int containerX, int containerZ) {
        return baseDir.resolve("lod" + level).resolve("c." + containerX + "." + containerZ + ".bin");
    }
}
