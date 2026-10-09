package com.mamiyaotaru.voxelmap.persistent.explored;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.ChunkBounds;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * persistent store for the explored chunk LOD pyramid that persists every level to sparse
 * container files under a base dir, disk I/O performed outside lock and only fast in-memory
 * pyramid mutation/read holds the lock, so a query thread never stalls behind a background disk read
 */
public final class ExploredDiskStore {
    private static final int CONTAINER_SHIFT = ExploredContainer.CONTAINER_SHIFT; // 5
    private static final int TILE_SHIFT = 5;

    private final Path baseDir;
    private final ExploredPyramid pyramid = new ExploredPyramid();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private final long identity = GENERATIONS.incrementAndGet();
    private final AtomicLong dataVersion = new AtomicLong(identity);
    private final AtomicLong contentVersion = new AtomicLong(identity);
    private final Map<TileKey, Long> contentRevisions = new HashMap<>();
    private final int[] contentMinX = new int[ExploredPyramid.LEVELS], contentMaxX = new int[ExploredPyramid.LEVELS];
    private final int[] contentMinZ = new int[ExploredPyramid.LEVELS], contentMaxZ = new int[ExploredPyramid.LEVELS];
    private final long[] contentLevelVersions = new long[ExploredPyramid.LEVELS];
    private long residencyVersion;
    private final Map<ContainerKey, Long> revisions = new HashMap<>();
    private final Map<TileKey, Long> tileRevisions = new HashMap<>();
    private final LinkedHashMap<ContainerKey, Boolean> residency = new LinkedHashMap<>();
    private final Set<ContainerKey> writing = new HashSet<>();
    private static final int MAX_RESIDENT_TILES = 16_384;
    private static final int MAX_RESIDENT_CONTAINERS = 512;

    private final Set<ContainerKey> dirty = new HashSet<>();   // guarded by lock
    private final Set<ContainerKey> loaded = new HashSet<>();  // guarded by lock

    public ExploredDiskStore(Path baseDir) {
        this.baseDir = baseDir;
        java.util.Arrays.fill(contentMinX, Integer.MAX_VALUE); java.util.Arrays.fill(contentMaxX, Integer.MIN_VALUE);
        java.util.Arrays.fill(contentMinZ, Integer.MAX_VALUE); java.util.Arrays.fill(contentMaxZ, Integer.MIN_VALUE);
        java.util.Arrays.fill(contentLevelVersions, identity);
    }

    private record TileKey(int level, int x, int z) { }

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

    public long identity() { return identity; }

    /** Loading/evicting resident containers does not invalidate a complete disk-backed viewport. */
    public long contentVersion() { return contentVersion.get(); }

    public long contentVersionInBounds(ChunkBounds bounds, int cellSize) {
        int level = selectLevelForCellSize(cellSize), shift = TILE_SHIFT * (level + 1);
        int minX = bounds.minX() >> shift, maxX = bounds.maxX() >> shift;
        int minZ = bounds.minZ() >> shift, maxZ = bounds.maxZ() >> shift;
        long version = identity;
        lock.readLock().lock();
        try {
            if (minX <= contentMinX[level] && maxX >= contentMaxX[level] && minZ <= contentMinZ[level] && maxZ >= contentMaxZ[level])
                return contentLevelVersions[level];
            if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) <= contentRevisions.size()) {
                for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                    version = Math.max(version, contentRevisions.getOrDefault(new TileKey(level, x, z), identity));
            } else for (var entry : contentRevisions.entrySet()) {
                TileKey key = entry.getKey();
                if (key.level() == level && key.x() >= minX && key.x() <= maxX && key.z() >= minZ && key.z() <= maxZ)
                    version = Math.max(version, entry.getValue());
            }
            return version;
        } finally { lock.readLock().unlock(); }
    }

    /** Worker-only sparse snapshot. Reads existing files without changing bounded live residency. */
    public com.mamiyaotaru.voxelmap.util.SparseCellGrid sparseCellsInBounds(ChunkBounds bounds, int cellSize) {
        var result = new com.mamiyaotaru.voxelmap.util.SparseCellGrid();
        if (bounds.isEmpty() || cellSize <= 0) return result;
        int level = selectLevelForCellSize(cellSize);
        int coverage = (int) bitCoverage(level);
        int shift = TILE_SHIFT * (level + 1) + CONTAINER_SHIFT;
        Path directory = baseDir.resolve("lod" + level);
        if (Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                var paths = files.filter(file -> {
                    String[] name = file.getFileName().toString().split("\\.");
                    if (name.length != 4 || !name[0].equals("c") || !name[3].equals("bin")) return false;
                    try {
                        int cx = Integer.parseInt(name[1]), cz = Integer.parseInt(name[2]);
                        return cx >= (bounds.minX() >> shift) && cx <= (bounds.maxX() >> shift)
                                && cz >= (bounds.minZ() >> shift) && cz <= (bounds.maxZ() >> shift);
                    } catch (NumberFormatException ignored) { return false; }
                }).toList();
                int completed = 0;
                com.mamiyaotaru.voxelmap.persistent.WorldMapProgress.report("Reading containers", 0, paths.size());
                for (Path file : paths) {
                    if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                    String[] name = file.getFileName().toString().split("\\.");
                    if (name.length != 4 || !name[0].equals("c") || !name[3].equals("bin")) continue;
                    int cx, cz;
                    try { cx = Integer.parseInt(name[1]); cz = Integer.parseInt(name[2]); }
                    catch (NumberFormatException ignored) { continue; }
                    if (cx < (bounds.minX() >> shift) || cx > (bounds.maxX() >> shift)
                            || cz < (bounds.minZ() >> shift) || cz > (bounds.maxZ() >> shift)) continue;
                    ExploredContainer container = ExploredContainerIo.read(file);
                    if (container == null) {
                        com.mamiyaotaru.voxelmap.persistent.WorldMapProgress.report("Reading containers", ++completed, paths.size());
                        continue;
                    }
                    for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
                        ExploredTile tile = container.getTile(x, z);
                        if (tile != null) emitTileCells((cx << 5) + x, (cz << 5) + z, coverage, cellSize, bounds, tile, result::mark);
                    }
                    com.mamiyaotaru.voxelmap.persistent.WorldMapProgress.report("Reading containers", ++completed, paths.size());
                }
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        // Include unsaved exploration. Disk and resident bits are monotonic, so OR is lossless.
        forEachCell(bounds, cellSize, result::mark);
        return result;
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
            pyramid.setChunk(chunkX, chunkZ, (level, tileX, tileZ) -> {
                ContainerKey key = new ContainerKey(level, tileX >> CONTAINER_SHIFT, tileZ >> CONTAINER_SHIFT);
                dirty.add(key);
                residency.put(key, Boolean.TRUE);
                long version = GENERATIONS.incrementAndGet();
                tileRevisions.put(new TileKey(level, tileX, tileZ), version);
                contentRevisions.put(new TileKey(level, tileX, tileZ), version);
                contentMinX[level] = Math.min(contentMinX[level], tileX); contentMaxX[level] = Math.max(contentMaxX[level], tileX);
                contentMinZ[level] = Math.min(contentMinZ[level], tileZ); contentMaxZ[level] = Math.max(contentMaxZ[level], tileZ);
                contentLevelVersions[level] = version;
                dataVersion.set(version);
                contentVersion.set(version);
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long versionInBounds(ChunkBounds bounds, int level) {
        int shift = TILE_SHIFT * (level + 1) + CONTAINER_SHIFT;
        long version;
        lock.readLock().lock();
        try {
            version = Math.max(identity, residencyVersion);
            for (int x = bounds.minX() >> shift; x <= bounds.maxX() >> shift; x++) {
                for (int z = bounds.minZ() >> shift; z <= bounds.maxZ() >> shift; z++) {
                    version = Math.max(version, revisions.getOrDefault(new ContainerKey(level, x, z), identity));
                }
            }
            int tileShift = TILE_SHIFT * (level + 1);
            int minX = bounds.minX() >> tileShift, maxX = bounds.maxX() >> tileShift;
            int minZ = bounds.minZ() >> tileShift, maxZ = bounds.maxZ() >> tileShift;
            long checks = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
            if (checks <= tileRevisions.size()) {
                for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                    version = Math.max(version, tileRevisions.getOrDefault(new TileKey(level, x, z), identity));
            } else {
                for (var entry : tileRevisions.entrySet()) {
                    TileKey key = entry.getKey();
                    if (key.level() == level && key.x() >= minX && key.x() <= maxX && key.z() >= minZ && key.z() <= maxZ)
                        version = Math.max(version, entry.getValue());
                }
            }
            return version;
        } finally { lock.readLock().unlock(); }
    }

    public CellGrid cellsInBounds(ChunkBounds bounds, int cellSize) {
        if (bounds.isEmpty() || cellSize <= 0) return new CellGrid(0, 0, 0, 0);
        int minX = Math.floorDiv(bounds.minX(), cellSize);
        int minZ = Math.floorDiv(bounds.minZ(), cellSize);
        int width = Math.floorDiv(bounds.maxX(), cellSize) - minX + 1;
        int height = Math.floorDiv(bounds.maxZ(), cellSize) - minZ + 1;
        if ((long) width * height > 16_000_000L) return new CellGrid(0, 0, 0, 0);
        CellGrid grid = new CellGrid(minX, minZ, width, height);
        // A grid mark is idempotent; avoid constructing a second hash set to deduplicate cells.
        forEachCell(bounds, cellSize, grid::mark);
        return grid;
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

    public void forEachStoredChunk(ChunkConsumer consumer) {
        flush();
        Path lod0 = baseDir.resolve("lod0");
        if (Files.notExists(lod0)) {
            return;
        }
        try (Stream<Path> files = Files.list(lod0)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".bin"))::iterator) {
                ExploredContainer container = ExploredContainerIo.read(file);
                if (container == null) {
                    continue;
                }
                int baseTileX = container.containerX() << CONTAINER_SHIFT;
                int baseTileZ = container.containerZ() << CONTAINER_SHIFT;
                for (int localX = 0; localX < ExploredContainer.TILES_PER_SIDE; localX++) {
                    for (int localZ = 0; localZ < ExploredContainer.TILES_PER_SIDE; localZ++) {
                        ExploredTile tile = container.getTile(localX, localZ);
                        if (tile == null || tile.isEmpty()) {
                            continue;
                        }
                        int baseChunkX = (baseTileX + localX) << TILE_SHIFT;
                        int baseChunkZ = (baseTileZ + localZ) << TILE_SHIFT;
                        for (int bx = 0; bx < ExploredTile.SIDE; bx++) {
                            for (int bz = 0; bz < ExploredTile.SIDE; bz++) {
                                if (tile.get(bx, bz)) {
                                    consumer.accept(baseChunkX + bx, baseChunkZ + bz);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * calls {@code consumer} exactly once for each cell (size {@code cellChunkSize}) that contains any
     * exploration within [center +/- radius] and reads from the coarsest pyramid level whose per-bit
     * coverage still satisfies the cell size, so far-zoom queries touch only coarse tiles
     *
     */
    public void forEachExploredCellInRange(int centerChunkX, int centerChunkZ, int radius, int cellChunkSize, CellConsumer consumer) {
        LongHashSet emitted = new LongHashSet();
        forEachCell(ChunkBounds.around(centerChunkX, centerChunkZ, radius), cellChunkSize, (x, z) -> {
            if (emitted.add(((long) x << 32) ^ (z & 0xFFFFFFFFL))) consumer.accept(x, z);
        });
    }

    private void forEachCell(ChunkBounds bounds, int cellChunkSize, CellConsumer consumer) {
        if (bounds.isEmpty() || cellChunkSize <= 0) return;
        int level = selectLevelForCellSize(cellChunkSize);
        int coverage = (int) bitCoverage(level);
        int shift = TILE_SHIFT * (level + 1);
        int minTileX = bounds.minX() >> shift, maxTileX = bounds.maxX() >> shift;
        int minTileZ = bounds.minZ() >> shift, maxTileZ = bounds.maxZ() >> shift;
        lock.readLock().lock();
        try {
            long tiles = (long) (maxTileX - minTileX + 1) * (maxTileZ - minTileZ + 1);
            if (tiles <= pyramid.tileCount(level)) {
                for (int x = minTileX; x <= maxTileX; x++) {
                    for (int z = minTileZ; z <= maxTileZ; z++) {
                        ExploredTile tile = pyramid.tileAt(level, x, z);
                        if (tile != null) emitTileCells(x, z, coverage, cellChunkSize, bounds, tile, consumer);
                    }
                }
            } else {
                pyramid.forEachTile(level, (key, tile) -> {
                    int x = (int) (key >> 32), z = (int) key;
                    if (x >= minTileX && x <= maxTileX && z >= minTileZ && z <= maxTileZ) {
                        emitTileCells(x, z, coverage, cellChunkSize, bounds, tile, consumer);
                    }
                });
            }
        } finally { lock.readLock().unlock(); }
    }

    private static void emitTileCells(int tileX, int tileZ, int coverage, int cellSize,
            ChunkBounds bounds, ExploredTile tile, CellConsumer consumer) {
        // Intermediate power-of-two summaries are derived in memory, preserving the V3 disk format.
        int reduction = 0;
        if (cellSize % coverage == 0) {
            int ratio = cellSize / coverage;
            if ((ratio & (ratio - 1)) == 0) reduction = Math.min(4, Integer.numberOfTrailingZeros(ratio));
        }
        int step = coverage << reduction;
        ExploredTile bits = tile.reduced(reduction);
        long baseX = (long) tileX * ExploredTile.SIDE * coverage;
        long baseZ = (long) tileZ * ExploredTile.SIDE * coverage;
        bits.forEachSetBit((x, z) -> {
            long chunkX = baseX + (long) x * step, chunkZ = baseZ + (long) z * step;
            if (chunkX + step - 1 < bounds.minX() || chunkX > bounds.maxX()
                    || chunkZ + step - 1 < bounds.minZ() || chunkZ > bounds.maxZ()) return;
            // Non-aligned coarse bits may span more than one output cell. Mark each intersecting cell.
            int firstX = (int) Math.floorDiv(Math.max(chunkX, bounds.minX()), cellSize);
            int lastX = (int) Math.floorDiv(Math.min(chunkX + step - 1, bounds.maxX()), cellSize);
            int firstZ = (int) Math.floorDiv(Math.max(chunkZ, bounds.minZ()), cellSize);
            int lastZ = (int) Math.floorDiv(Math.min(chunkZ + step - 1, bounds.maxZ()), cellSize);
            for (int cx = firstX; cx <= lastX; cx++) for (int cz = firstZ; cz <= lastZ; cz++) consumer.accept(cx, cz);
        });
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
            residency.put(key, Boolean.TRUE);
            if (container != null) {
                mergeContainerLocked(level, containerX, containerZ, container);
                long version = GENERATIONS.incrementAndGet();
                revisions.put(key, version);
                dataVersion.set(version);
                merged = true;
            }
            pruneResidencyLocked(key);
        } finally {
            lock.writeLock().unlock();
        }
        if (merged) {
            // only a load that actually merged data changes what renders, a missing container is marked
            // loaded (so we don't retry it) but must NOT bump the version and trigger a spurious
            // re-query/re-render of unchanged data
            // The container revision was published under the write lock above.
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

    /** writes all dirty containers to disk, merging existing on-disk data first so a container modified
     *  without ever being loaded (e.g. the player explored its area without opening the map there) can't
     *  overwrite previously-saved chunks */
    public synchronized void flush() {
        List<ContainerKey> keys;
        List<ContainerKey> needMerge = new ArrayList<>();
        lock.writeLock().lock();
        try {
            if (dirty.isEmpty()) return;
            keys = new ArrayList<>(dirty);
            for (ContainerKey key : keys) if (!loaded.contains(key)) needMerge.add(key);
            writing.addAll(keys); dirty.clear();
        } finally { lock.writeLock().unlock(); }

        try {
            for (ContainerKey key : needMerge) {
                ExploredContainer existing = ExploredContainerIo.read(containerPath(key.level(), key.containerX(), key.containerZ()));
                lock.writeLock().lock();
                try {
                    loaded.add(key); residency.put(key, Boolean.TRUE);
                    if (existing != null) {
                        mergeContainerLocked(key.level(), key.containerX(), key.containerZ(), existing);
                        long revision = GENERATIONS.incrementAndGet(); revisions.put(key, revision); dataVersion.set(revision);
                    }
                } finally { lock.writeLock().unlock(); }
            }
            // Snapshot one container at a time. Encoding and disk writes never hold the store lock.
            for (ContainerKey key : keys) {
                ExploredContainer snapshot;
                lock.readLock().lock();
                try { snapshot = buildContainerLocked(key.level(), key.containerX(), key.containerZ()); }
                finally { lock.readLock().unlock(); }
                try { ExploredContainerIo.writeBytes(containerPath(key.level(), key.containerX(), key.containerZ()), snapshot.encode()); }
                catch (IOException failed) {
                    lock.writeLock().lock();
                    try { dirty.add(key); } finally { lock.writeLock().unlock(); }
                } finally {
                    lock.writeLock().lock();
                    try { writing.remove(key); pruneResidencyLocked(key); }
                    finally { lock.writeLock().unlock(); }
                }
            }
        } finally {
            lock.writeLock().lock();
            try { for (ContainerKey key : keys) if (writing.remove(key)) dirty.add(key); }
            finally { lock.writeLock().unlock(); }
        }
    }

    private void pruneResidencyLocked(ContainerKey newest) {
        int tiles = 0;
        for (int level = 0; level < ExploredPyramid.LEVELS; level++) tiles += pyramid.tileCount(level);
        if (tiles <= MAX_RESIDENT_TILES && residency.size() <= MAX_RESIDENT_CONTAINERS) return;
        var iterator = residency.keySet().iterator();
        while (iterator.hasNext() && (tiles > MAX_RESIDENT_TILES || residency.size() > MAX_RESIDENT_CONTAINERS)) {
            ContainerKey key = iterator.next();
            if (key.equals(newest) || dirty.contains(key) || writing.contains(key)) continue;
            int removed = pyramid.removeContainer(key.level(), key.containerX(), key.containerZ());
            tiles -= removed;
            if (removed > 0) for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++)
                tileRevisions.remove(new TileKey(key.level(), (key.containerX() << 5) + x, (key.containerZ() << 5) + z));
            loaded.remove(key);
            residencyVersion = GENERATIONS.incrementAndGet();
            revisions.remove(key);
            iterator.remove();
        }
    }

    private ExploredContainer buildContainerLocked(int level, int containerX, int containerZ) {
        ExploredContainer container = new ExploredContainer(level, containerX, containerZ);
        int baseTileX = containerX << CONTAINER_SHIFT;
        int baseTileZ = containerZ << CONTAINER_SHIFT;
        for (int localX = 0; localX < ExploredContainer.TILES_PER_SIDE; localX++) {
            for (int localZ = 0; localZ < ExploredContainer.TILES_PER_SIDE; localZ++) {
                ExploredTile tile = pyramid.tileAt(level, baseTileX + localX, baseTileZ + localZ);
                if (tile != null && !tile.isEmpty()) {
                    container.putTile(localX, localZ, ExploredTile.fromBytes(tile.toBytes()));
                }
            }
        }
        return container;
    }

    private Path containerPath(int level, int containerX, int containerZ) {
        return baseDir.resolve("lod" + level).resolve("c." + containerX + "." + containerZ + ".bin");
    }
}
