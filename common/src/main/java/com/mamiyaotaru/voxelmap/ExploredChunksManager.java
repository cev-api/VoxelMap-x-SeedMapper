package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.persistent.ThreadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ExploredChunksManager {
    private static final int REGION_SHIFT = 5; // 32x32 chunk buckets for fast range queries
    private static final int REGION_SIZE = 1 << REGION_SHIFT;
    private static final int REGION_CHUNK_COUNT = REGION_SIZE * REGION_SIZE;
    private static final int REGION_BYTES = REGION_CHUNK_COUNT / 8;
    private static final int V2_FORMAT_VERSION = 2;
    private static final int V2_MAGIC = 0x45585032; // EXP2
    private static final int MAX_SYNC_REGION_LOADS = 4096;
    private static final int WINDOW_LOAD_RADIUS_CHUNKS = 128;
    private static final int MAX_INTERPOLATED_GAP_CHUNKS = 8;
    private final Set<ChunkPos> exploredChunks = new HashSet<>();
    private final Map<Long, Set<ChunkPos>> exploredChunksByRegion = new HashMap<>();
    private final Set<Long> loadedV2Regions = new HashSet<>();
    private final Set<Long> dirtyV2Regions = new HashSet<>();
    private String loadedWorldKey = "";
    private boolean v2StorageReady = false;
    private boolean migrationQueued = false;
    private boolean migrationRunning = false;
    private volatile boolean legacyWritesEnabled = true;
    private long dataVersion = 0L;
    private long lastStorageDebugMs = 0L;
    private long lastStorageLoadTimeMs = 0L;
    private int lastLoadedRegionFiles = 0;
    private int lastDecodedChunks = 0;
    private int lastSkippedMissingRegionFiles = 0;
    private long lastMigrationTimeMs = 0L;
    private boolean storageLoadIncomplete = false;
    private Integer lastChunkX;
    private Integer lastChunkZ;

    public void onTick() {
        if (!ensureTrackingWorld()) {
            return;
        }
        int chunkX = GameVariableAccessShim.xCoord() >> 4;
        int chunkZ = GameVariableAccessShim.zCoord() >> 4;
        if (lastChunkX == null || lastChunkX != chunkX || lastChunkZ == null || lastChunkZ != chunkZ) {
            if (lastChunkX == null || lastChunkZ == null) {
                markExplored(new ChunkPos(chunkX, chunkZ));
            } else {
                markExploredPath(lastChunkX, lastChunkZ, chunkX, chunkZ, true);
            }
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
        flushDirtyV2Regions(2);
    }

    public Set<ChunkPos> getExploredChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        ensureTrackingWorld();
        Set<ChunkPos> result = new HashSet<>();
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        loadV2RegionsForBounds(minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);

        int minRegionX = minChunkX >> REGION_SHIFT;
        int maxRegionX = maxChunkX >> REGION_SHIFT;
        int minRegionZ = minChunkZ >> REGION_SHIFT;
        int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                Set<ChunkPos> bucket = exploredChunksByRegion.get(regionKey(regionX, regionZ));
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
        return result;
    }

    public HashSet<Long> getExploredCellsInRange(int centerChunkX, int centerChunkZ, int radius, int cellChunkSize) {
        ensureTrackingWorld();
        HashSet<Long> result = new HashSet<>();
        if (cellChunkSize <= 0) {
            return result;
        }
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        loadV2RegionsForBounds(minChunkX, maxChunkX, minChunkZ, maxChunkZ, MAX_SYNC_REGION_LOADS);

        int minRegionX = minChunkX >> REGION_SHIFT;
        int maxRegionX = maxChunkX >> REGION_SHIFT;
        int minRegionZ = minChunkZ >> REGION_SHIFT;
        int maxRegionZ = maxChunkZ >> REGION_SHIFT;

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                Set<ChunkPos> bucket = exploredChunksByRegion.get(regionKey(regionX, regionZ));
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }
                for (ChunkPos chunk : bucket) {
                    int chunkX = chunk.x();
                    int chunkZ = chunk.z();
                    if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                        continue;
                    }
                    result.add(cellKey(Math.floorDiv(chunkX, cellChunkSize), Math.floorDiv(chunkZ, cellChunkSize)));
                }
            }
        }
        return result;
    }

    public long getDataVersion() {
        ensureTrackingWorld();
        return dataVersion;
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

    public void flushStorage() {
        flushDirtyV2Regions(Integer.MAX_VALUE);
    }

    public void clearCurrentWorld() {
        exploredChunks.clear();
        exploredChunksByRegion.clear();
        loadedV2Regions.clear();
        dirtyV2Regions.clear();
        v2StorageReady = false;
        storageLoadIncomplete = false;
        lastChunkX = null;
        lastChunkZ = null;
        Path path = getDataPath();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            try {
                Files.writeString(path, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored2) {
            }
        }
        try {
            deleteDirectoryIfExists(getV2Dir());
        } catch (IOException ignored) {
        }
        ensureV2Storage();
        markDataChanged();
    }

    private void markExplored(ChunkPos chunkPos) {
        if (exploredChunks.add(chunkPos)) {
            indexChunk(chunkPos);
            markV2Dirty(chunkPos);
            markDataChanged();
            if (legacyWritesEnabled) {
                appendChunk(getDataPath(), chunkPos);
            }
        }
    }

    private void loadWorld(String worldKey) {
        flushDirtyV2Regions(Integer.MAX_VALUE);
        exploredChunks.clear();
        exploredChunksByRegion.clear();
        loadedV2Regions.clear();
        dirtyV2Regions.clear();
        loadedWorldKey = worldKey;
        v2StorageReady = false;
        storageLoadIncomplete = false;
        ensureV2Storage();
        if (legacyWritesEnabled) {
            ensureLegacyDataFile();
        }
        int px = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.chunkPosition().x() : 0;
        int pz = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.chunkPosition().z() : 0;
        loadV2RegionsForBounds(px - WINDOW_LOAD_RADIUS_CHUNKS, px + WINDOW_LOAD_RADIUS_CHUNKS,
                pz - WINDOW_LOAD_RADIUS_CHUNKS, pz + WINDOW_LOAD_RADIUS_CHUNKS, MAX_SYNC_REGION_LOADS);
        markDataChanged();
    }

    private void addExploredToMemory(ChunkPos chunkPos) {
        if (exploredChunks.add(chunkPos)) {
            indexChunk(chunkPos);
        }
    }

    private boolean ensureTrackingWorld() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            flushDirtyV2Regions(Integer.MAX_VALUE);
            exploredChunks.clear();
            exploredChunksByRegion.clear();
            loadedV2Regions.clear();
            dirtyV2Regions.clear();
            loadedWorldKey = "";
            v2StorageReady = false;
            storageLoadIncomplete = false;
            lastChunkX = null;
            lastChunkZ = null;
            return false;
        }

        String worldKey = getWorldKey();
        if (!worldKey.equals(loadedWorldKey)) {
            loadWorld(worldKey);
        }
        return true;
    }

    private void markExploredPath(int startX, int startZ, int endX, int endZ, boolean persist) {
        int dx = Math.abs(endX - startX);
        int dz = Math.abs(endZ - startZ);
        if (Math.max(dx, dz) > MAX_INTERPOLATED_GAP_CHUNKS) {
            ChunkPos endChunk = new ChunkPos(endX, endZ);
            if (persist) {
                markExplored(endChunk);
            } else {
                addExploredToMemory(endChunk);
            }
            return;
        }

        int stepX = Integer.compare(endX, startX);
        int stepZ = Integer.compare(endZ, startZ);
        int error = dx - dz;
        int x = startX;
        int z = startZ;

        while (true) {
            ChunkPos chunkPos = new ChunkPos(x, z);
            if (persist) {
                markExplored(chunkPos);
            } else {
                addExploredToMemory(chunkPos);
            }

            if (x == endX && z == endZ) {
                break;
            }

            int doubleError = error * 2;
            if (doubleError > -dz) {
                error -= dz;
                x += stepX;
            }
            if (doubleError < dx) {
                error += dx;
                z += stepZ;
            }
        }
    }

    private void ensureLegacyDataFile() {
        Path path = getDataPath();
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
            }
        } catch (IOException ignored) {
        }
    }

    private void ensureV2Storage() {
        if (v2StorageReady) {
            return;
        }
        try {
            Files.createDirectories(getV2RegionDir());
            if (isV2MigrationComplete()) {
                legacyWritesEnabled = false;
                moveLegacyTextFileToBackup(getStorageWorldKey());
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
                migrateLegacyTextFileToV2(migrationWorldKey);
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
            String compact = Files.readString(manifest, StandardCharsets.UTF_8)
                    .replace(" ", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("\t", "");
            return compact.contains("\"formatVersion\":2")
                    && compact.contains("\"regionShift\":" + REGION_SHIFT)
                    && compact.contains("\"migrationComplete\":true")
                    && compact.contains("\"legacyImportVerified\":true");
        } catch (IOException ignored) {
            return false;
        }
    }

    private void migrateLegacyTextFileToV2(String worldKey) {
        long start = System.nanoTime();
        Map<Long, BitSet> regionBits = new HashMap<>();
        int migratedEntries = 0;
        int migratedExploredChunks = 0;
        Path legacyPath = getDataPath(worldKey);
        if (Files.exists(legacyPath)) {
            try (Stream<String> lines = Files.lines(legacyPath, StandardCharsets.UTF_8)) {
                Iterator<String> iterator = lines.iterator();
                ChunkPos previousChunk = null;
                while (iterator.hasNext()) {
                    ChunkPos chunkPos = parseChunk(iterator.next());
                    if (chunkPos == null) {
                        continue;
                    }
                    migratedEntries++;
                    if (previousChunk == null) {
                        if (addExploredToRegionBits(regionBits, chunkPos.x(), chunkPos.z())) {
                            migratedExploredChunks++;
                        }
                    } else {
                        migratedExploredChunks += addExploredPathToRegionBits(regionBits, previousChunk.x(), previousChunk.z(), chunkPos.x(), chunkPos.z());
                    }
                    previousChunk = chunkPos;
                }
            } catch (IOException ignored) {
            }
        }

        int expectedRegions = regionBits.size();
        int writtenRegions = 0;
        int failedRegions = 0;
        for (Map.Entry<Long, BitSet> entry : regionBits.entrySet()) {
            int regionX = (int) (entry.getKey() >> 32);
            int regionZ = (int) (long) entry.getKey();
            BitSet existingBits = readRegionBits(getV2RegionPath(worldKey, regionX, regionZ));
            if (existingBits != null && !existingBits.isEmpty()) {
                entry.getValue().or(existingBits);
            }
            if (writeRegionBits(worldKey, regionX, regionZ, entry.getValue())) {
                writtenRegions++;
            } else {
                failedRegions++;
            }
        }

        lastMigrationTimeMs = (System.nanoTime() - start) / 1_000_000L;
        boolean complete = failedRegions == 0;
        writeV2Manifest(worldKey, complete, migratedEntries, migratedExploredChunks, writtenRegions, expectedRegions, failedRegions);
        if (complete) {
            moveLegacyTextFileToBackup(worldKey);
        }
        if (worldKey.equals(loadedWorldKey)) {
            loadedV2Regions.clear();
            legacyWritesEnabled = !complete;
            markDataChanged();
        }
        if (VoxelConstants.DEBUG) {
            VoxelConstants.getLogger().info("Explored chunks v2 migration: world={} entries={} exploredChunks={} regions={}/{} failedRegions={} timeMs={}",
                    worldKey, migratedEntries, migratedExploredChunks, writtenRegions, expectedRegions, failedRegions, lastMigrationTimeMs);
        }
    }

    private int addExploredPathToRegionBits(Map<Long, BitSet> regionBits, int startX, int startZ, int endX, int endZ) {
        int dx = Math.abs(endX - startX);
        int dz = Math.abs(endZ - startZ);
        if (Math.max(dx, dz) > MAX_INTERPOLATED_GAP_CHUNKS) {
            return addExploredToRegionBits(regionBits, endX, endZ) ? 1 : 0;
        }

        int added = 0;
        int stepX = Integer.compare(endX, startX);
        int stepZ = Integer.compare(endZ, startZ);
        int error = dx - dz;
        int x = startX;
        int z = startZ;

        while (true) {
            if (addExploredToRegionBits(regionBits, x, z)) {
                added++;
            }

            if (x == endX && z == endZ) {
                break;
            }

            int doubleError = error * 2;
            if (doubleError > -dz) {
                error -= dz;
                x += stepX;
            }
            if (doubleError < dx) {
                error += dx;
                z += stepZ;
            }
        }
        return added;
    }

    private boolean addExploredToRegionBits(Map<Long, BitSet> regionBits, int chunkX, int chunkZ) {
        int regionX = chunkX >> REGION_SHIFT;
        int regionZ = chunkZ >> REGION_SHIFT;
        BitSet bits = regionBits.computeIfAbsent(regionKey(regionX, regionZ), ignored -> new BitSet(REGION_CHUNK_COUNT));
        int before = bits.cardinality();
        bits.set(regionBitIndex(chunkX, chunkZ));
        return bits.cardinality() != before;
    }

    private void loadV2RegionsForBounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, int maxRegionLoads) {
        ensureV2Storage();
        int minRegionX = minChunkX >> REGION_SHIFT;
        int maxRegionX = maxChunkX >> REGION_SHIFT;
        int minRegionZ = minChunkZ >> REGION_SHIFT;
        int maxRegionZ = maxChunkZ >> REGION_SHIFT;
        int loaded = 0;
        int decoded = 0;
        int skipped = 0;
        long start = System.nanoTime();

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                if (loaded >= maxRegionLoads) {
                    storageLoadIncomplete = true;
                    recordStorageLoadStats(start, loaded, decoded, skipped);
                    return;
                }
                long key = regionKey(regionX, regionZ);
                if (loadedV2Regions.contains(key)) {
                    continue;
                }
                Path path = getV2RegionPath(regionX, regionZ);
                if (Files.notExists(path)) {
                    loadedV2Regions.add(key);
                    skipped++;
                    continue;
                }
                BitSet bits = readRegionBits(path);
                loadedV2Regions.add(key);
                loaded++;
                if (bits == null || bits.isEmpty()) {
                    continue;
                }
                decoded += addRegionBitsToMemory(regionX, regionZ, bits);
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
            VoxelConstants.getLogger().info("Explored chunks v2 load: world={} files={} decoded={} missing={} timeMs={} version={}",
                    loadedWorldKey, loaded, decoded, skipped, lastStorageLoadTimeMs, dataVersion);
        }
    }

    private int addRegionBitsToMemory(int regionX, int regionZ, BitSet bits) {
        int decoded = 0;
        int baseChunkX = regionX << REGION_SHIFT;
        int baseChunkZ = regionZ << REGION_SHIFT;
        for (int bit = bits.nextSetBit(0); bit >= 0 && bit < REGION_CHUNK_COUNT; bit = bits.nextSetBit(bit + 1)) {
            int localX = bit & (REGION_SIZE - 1);
            int localZ = bit >> REGION_SHIFT;
            ChunkPos chunkPos = new ChunkPos(baseChunkX + localX, baseChunkZ + localZ);
            if (exploredChunks.add(chunkPos)) {
                indexChunk(chunkPos);
                decoded++;
            }
        }
        return decoded;
    }

    private void flushDirtyV2Regions(int maxRegions) {
        if (loadedWorldKey == null || loadedWorldKey.isEmpty()) {
            return;
        }
        int flushed = 0;
        Iterator<Long> iterator = dirtyV2Regions.iterator();
        while (iterator.hasNext() && flushed < maxRegions) {
            long key = iterator.next();
            iterator.remove();
            int regionX = (int) (key >> 32);
            int regionZ = (int) key;
            loadV2RegionIfNeeded(regionX, regionZ);
            writeRegionBits(getStorageWorldKey(), regionX, regionZ, buildRegionBits(regionX, regionZ));
            flushed++;
        }
    }

    private void loadV2RegionIfNeeded(int regionX, int regionZ) {
        long key = regionKey(regionX, regionZ);
        if (loadedV2Regions.contains(key)) {
            return;
        }
        Path path = getV2RegionPath(regionX, regionZ);
        loadedV2Regions.add(key);
        if (Files.notExists(path)) {
            return;
        }
        BitSet bits = readRegionBits(path);
        if (bits != null && !bits.isEmpty()) {
            addRegionBitsToMemory(regionX, regionZ, bits);
        }
    }

    private BitSet buildRegionBits(int regionX, int regionZ) {
        BitSet bits = new BitSet(REGION_CHUNK_COUNT);
        Set<ChunkPos> bucket = exploredChunksByRegion.get(regionKey(regionX, regionZ));
        if (bucket == null || bucket.isEmpty()) {
            return bits;
        }
        for (ChunkPos chunkPos : bucket) {
            bits.set(regionBitIndex(chunkPos.x(), chunkPos.z()));
        }
        return bits;
    }

    private boolean writeRegionBits(String worldKey, int regionX, int regionZ, BitSet bits) {
        Path path = getV2RegionPath(worldKey, regionX, regionZ);
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

    private void writeV2Manifest(String worldKey, boolean migrationComplete, int migratedEntries, int migratedExploredChunks,
            int writtenRegions, int expectedRegions, int failedRegions) {
        Path manifest = getV2ManifestPath(worldKey);
        String json = "{\n"
                + "  \"formatVersion\": " + V2_FORMAT_VERSION + ",\n"
                + "  \"regionShift\": " + REGION_SHIFT + ",\n"
                + "  \"worldKey\": \"" + escapeJson(loadedWorldKey) + "\",\n"
                + "  \"migrationComplete\": " + migrationComplete + ",\n"
                + "  \"legacyImportVerified\": " + migrationComplete + ",\n"
                + "  \"migratedEntries\": " + migratedEntries + ",\n"
                + "  \"migratedExploredChunks\": " + migratedExploredChunks + ",\n"
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

    private void moveLegacyTextFileToBackup(String worldKey) {
        Path source = getDataPath(worldKey);
        if (Files.notExists(source)) {
            return;
        }
        Path backupDir = source.getParent().resolve("legacy_backup");
        try {
            Files.createDirectories(backupDir);
            Files.move(source, nextBackupPath(backupDir.resolve(source.getFileName())), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            try {
                Files.move(source, nextBackupPath(backupDir.resolve(source.getFileName())), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignoredToo) {
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

    private void indexChunk(ChunkPos chunkPos) {
        int regionX = chunkPos.x() >> REGION_SHIFT;
        int regionZ = chunkPos.z() >> REGION_SHIFT;
        exploredChunksByRegion
                .computeIfAbsent(regionKey(regionX, regionZ), ignored -> new HashSet<>())
                .add(chunkPos);
    }

    private void markV2Dirty(ChunkPos chunkPos) {
        dirtyV2Regions.add(regionKey(chunkPos.x() >> REGION_SHIFT, chunkPos.z() >> REGION_SHIFT));
    }

    private void markDataChanged() {
        dataVersion++;
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    private static int regionBitIndex(int chunkX, int chunkZ) {
        int localX = chunkX & (REGION_SIZE - 1);
        int localZ = chunkZ & (REGION_SIZE - 1);
        return (localZ << REGION_SHIFT) | localX;
    }

    private Path getDataPath() {
        return getDataPath(getStorageWorldKey());
    }

    private Path getDataPath(String worldKey) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxelmap")
                .resolve("chunk_overlays")
                .resolve("explored")
                .resolve(worldKey + ".txt");
    }

    private Path getV2Dir() {
        return getV2Dir(getStorageWorldKey());
    }

    private Path getV2Dir(String worldKey) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxelmap")
                .resolve("chunk_overlays")
                .resolve("explored")
                .resolve(worldKey)
                .resolve("v2");
    }

    private Path getV2RegionDir() {
        return getV2Dir().resolve("explored");
    }

    private Path getV2RegionPath(int regionX, int regionZ) {
        return getV2RegionDir().resolve("r." + regionX + "." + regionZ + ".bin");
    }

    private Path getV2RegionPath(String worldKey, int regionX, int regionZ) {
        return getV2Dir(worldKey).resolve("explored").resolve("r." + regionX + "." + regionZ + ".bin");
    }

    private Path getV2ManifestPath() {
        return getV2Dir().resolve("manifest.json");
    }

    private Path getV2ManifestPath(String worldKey) {
        return getV2Dir(worldKey).resolve("manifest.json");
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
            throw new IOException("Unexpected end of explored chunk region file");
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
}
