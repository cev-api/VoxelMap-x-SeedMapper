package com.mamiyaotaru.voxelmap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PortalMarkersManager {
    private static final int MAX_CHUNK_SCANS_PER_TICK = 2;

    public enum PortalType {
        NETHER,
        END,
        END_BEACON
    }

    public record PortalMarker(PortalType type, BlockPos pos) {}

    private final Map<Long, Set<BlockPos>> netherMarkersByChunk = new HashMap<>();
    private final Map<Long, Set<BlockPos>> endMarkersByChunk = new HashMap<>();
    private final Map<Long, Set<BlockPos>> endBeaconMarkersByChunk = new HashMap<>();
    private final Set<BlockPos> netherMarkers = new HashSet<>();
    private final Set<BlockPos> endMarkers = new HashSet<>();
    private final Set<BlockPos> endBeaconMarkers = new HashSet<>();
    private final ArrayDeque<Long> pendingChunkScanQueue = new ArrayDeque<>();
    private final Set<Long> pendingChunkScanSet = new HashSet<>();
    private String loadedWorldKey = "";
    private boolean dirty;
    private int ticksSinceDirty;

    public void onTick() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }
        String worldKey = getWorldKey();
        if (!worldKey.equals(loadedWorldKey)) {
            loadedWorldKey = worldKey;
            clear();
            loadWorld();
            dirty = false;
            ticksSinceDirty = 0;
        }
        processPendingChunkScans(level);
        if (dirty) {
            ticksSinceDirty++;
            if (ticksSinceDirty >= 20) {
                saveWorld();
                dirty = false;
                ticksSinceDirty = 0;
            }
        }
    }

    public void processChunk(LevelChunk chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        ScanResult result = scanChunk(chunk);
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkKey(chunkPos.x(), chunkPos.z());

        replaceChunkMarkers(netherMarkersByChunk, netherMarkers, chunkKey, result.netherMarkers());
        replaceChunkMarkers(endMarkersByChunk, endMarkers, chunkKey, result.endMarkers());
        replaceChunkMarkers(endBeaconMarkersByChunk, endBeaconMarkers, chunkKey, result.endBeaconMarkers());
    }

    public void onBlockUpdated(BlockPos pos) {
        if (pos == null) {
            return;
        }
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (!level.hasChunk(chunkX, chunkZ)) {
            return;
        }
        queueChunkScan(chunkX, chunkZ);
    }

    public void queueChunkScan(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (pendingChunkScanSet.add(key)) {
            pendingChunkScanQueue.addLast(key);
        }
    }

    public List<PortalMarker> getMarkersInRange(int centerX, int centerZ, int radius, boolean includeNether, boolean includeEnd, boolean includeEndBeacons) {
        List<PortalMarker> result = new ArrayList<>();
        if (includeNether) {
            addInRange(result, netherMarkers, centerX, centerZ, radius, PortalType.NETHER);
        }
        if (includeEnd) {
            addInRange(result, endMarkers, centerX, centerZ, radius, PortalType.END);
        }
        if (includeEndBeacons) {
            addInRange(result, endBeaconMarkers, centerX, centerZ, radius, PortalType.END_BEACON);
        }
        return result;
    }

    public List<PortalMarker> getMarkersInBounds(int minX, int maxX, int minZ, int maxZ, boolean includeNether, boolean includeEnd, boolean includeEndBeacons) {
        List<PortalMarker> result = new ArrayList<>();
        if (includeNether) {
            addInBounds(result, netherMarkers, minX, maxX, minZ, maxZ, PortalType.NETHER);
        }
        if (includeEnd) {
            addInBounds(result, endMarkers, minX, maxX, minZ, maxZ, PortalType.END);
        }
        if (includeEndBeacons) {
            addInBounds(result, endBeaconMarkers, minX, maxX, minZ, maxZ, PortalType.END_BEACON);
        }
        return result;
    }

    private void addInRange(List<PortalMarker> target, Set<BlockPos> source, int centerX, int centerZ, int radius, PortalType type) {
        int radiusSq = radius * radius;
        for (BlockPos marker : source) {
            int dx = marker.getX() - centerX;
            int dz = marker.getZ() - centerZ;
            if (dx * dx + dz * dz <= radiusSq) {
                target.add(new PortalMarker(type, marker));
            }
        }
    }

    private void addInBounds(List<PortalMarker> target, Set<BlockPos> source, int minX, int maxX, int minZ, int maxZ, PortalType type) {
        for (BlockPos marker : source) {
            int x = marker.getX();
            int z = marker.getZ();
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                target.add(new PortalMarker(type, marker));
            }
        }
    }

    private ScanResult scanChunk(LevelChunk chunk) {
        Set<BlockPos> netherBlocks = new HashSet<>();
        Set<BlockPos> endBlocks = new HashSet<>();
        Set<BlockPos> endBeaconBlocks = new HashSet<>();

        int minY = chunk.getMinY();
        int maxYExclusive = chunk.getMaxY() + 1;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            int worldX = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = minZ + localZ;
                for (int y = minY; y < maxYExclusive; y++) {
                    mutablePos.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(mutablePos);
                    Block block = state.getBlock();
                    if (block == Blocks.NETHER_PORTAL) {
                        netherBlocks.add(mutablePos.immutable());
                    } else if (block == Blocks.END_PORTAL || block == Blocks.END_PORTAL_FRAME) {
                        endBlocks.add(mutablePos.immutable());
                    } else if (block == Blocks.END_GATEWAY) {
                        endBeaconBlocks.add(mutablePos.immutable());
                    }
                }
            }
        }

        return new ScanResult(clusterMarkers(netherBlocks), clusterMarkers(endBlocks), clusterMarkers(endBeaconBlocks));
    }

    private Set<BlockPos> clusterMarkers(Set<BlockPos> source) {
        Set<BlockPos> markers = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        for (BlockPos start : source) {
            if (!visited.add(start)) {
                continue;
            }

            int minX = start.getX();
            int maxX = start.getX();
            int minY = start.getY();
            int maxY = start.getY();
            int minZ = start.getZ();
            int maxZ = start.getZ();

            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                minX = Math.min(minX, current.getX());
                maxX = Math.max(maxX, current.getX());
                minY = Math.min(minY, current.getY());
                maxY = Math.max(maxY, current.getY());
                minZ = Math.min(minZ, current.getZ());
                maxZ = Math.max(maxZ, current.getZ());

                tryQueue(source, visited, queue, current.east());
                tryQueue(source, visited, queue, current.west());
                tryQueue(source, visited, queue, current.north());
                tryQueue(source, visited, queue, current.south());
                tryQueue(source, visited, queue, current.above());
                tryQueue(source, visited, queue, current.below());
            }

            markers.add(new BlockPos((minX + maxX) >> 1, (minY + maxY) >> 1, (minZ + maxZ) >> 1));
        }

        return markers;
    }

    private void tryQueue(Set<BlockPos> source, Set<BlockPos> visited, ArrayDeque<BlockPos> queue, BlockPos candidate) {
        if (source.contains(candidate) && visited.add(candidate)) {
            queue.add(candidate);
        }
    }

    private void replaceChunkMarkers(Map<Long, Set<BlockPos>> byChunk, Set<BlockPos> global, long chunkKey, Set<BlockPos> latest) {
        Set<BlockPos> previous = byChunk.get(chunkKey);
        Set<BlockPos> normalizedLatest = latest.isEmpty() ? Set.of() : new HashSet<>(latest);
        if (previous != null && previous.equals(normalizedLatest)) {
            return;
        }
        if (previous != null) {
            global.removeAll(previous);
            byChunk.remove(chunkKey);
        }
        if (!normalizedLatest.isEmpty()) {
            byChunk.put(chunkKey, normalizedLatest);
            global.addAll(normalizedLatest);
        }
        markDirty();
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private int unpackChunkX(long key) {
        return (int) (key >> 32);
    }

    private int unpackChunkZ(long key) {
        return (int) key;
    }

    private void clear() {
        netherMarkersByChunk.clear();
        endMarkersByChunk.clear();
        endBeaconMarkersByChunk.clear();
        netherMarkers.clear();
        endMarkers.clear();
        endBeaconMarkers.clear();
        pendingChunkScanQueue.clear();
        pendingChunkScanSet.clear();
    }

    private void processPendingChunkScans(Level level) {
        int scans = 0;
        while (scans < MAX_CHUNK_SCANS_PER_TICK && !pendingChunkScanQueue.isEmpty()) {
            long key = pendingChunkScanQueue.removeFirst();
            pendingChunkScanSet.remove(key);

            int chunkX = unpackChunkX(key);
            int chunkZ = unpackChunkZ(key);
            if (!level.hasChunk(chunkX, chunkZ)) {
                continue;
            }

            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            if (chunk != null && !chunk.isEmpty()) {
                processChunk(chunk);
            }
            scans++;
        }
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

    private Path getDataDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxelmap")
                .resolve("portal_markers")
                .resolve(loadedWorldKey.isBlank() ? "unknown" : loadedWorldKey);
    }

    private Path getDataFile() {
        return getDataDir().resolve("portals.json");
    }

    private void loadWorld() {
        Path file = getDataFile();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.createFile(file);
                return;
            }

            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return;
            }

            JsonElement rootElement = JsonParser.parseString(raw);
            if (!rootElement.isJsonObject()) {
                return;
            }
            JsonObject root = rootElement.getAsJsonObject();
            loadArray(root.getAsJsonArray("nether"), netherMarkers, netherMarkersByChunk);
            loadArray(root.getAsJsonArray("end"), endMarkers, endMarkersByChunk);
            loadArray(root.getAsJsonArray("endBeacons"), endBeaconMarkers, endBeaconMarkersByChunk);
        } catch (IOException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private void loadArray(JsonArray array, Set<BlockPos> global, Map<Long, Set<BlockPos>> byChunk) {
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (!element.isJsonArray()) {
                continue;
            }
            JsonArray xyz = element.getAsJsonArray();
            if (xyz.size() != 3) {
                continue;
            }
            try {
                int x = xyz.get(0).getAsInt();
                int y = xyz.get(1).getAsInt();
                int z = xyz.get(2).getAsInt();
                BlockPos pos = new BlockPos(x, y, z);
                if (global.add(pos)) {
                    long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
                    byChunk.computeIfAbsent(key, ignored -> new HashSet<>()).add(pos);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void saveWorld() {
        Path file = getDataFile();
        JsonObject root = new JsonObject();
        root.add("nether", toJsonArray(netherMarkers));
        root.add("end", toJsonArray(endMarkers));
        root.add("endBeacons", toJsonArray(endBeaconMarkers));

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file,
                    new GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }

    private JsonArray toJsonArray(Set<BlockPos> markers) {
        JsonArray array = new JsonArray();
        for (BlockPos pos : markers) {
            JsonArray xyz = new JsonArray(3);
            xyz.add(pos.getX());
            xyz.add(pos.getY());
            xyz.add(pos.getZ());
            array.add(xyz);
        }
        return array;
    }

    private void markDirty() {
        dirty = true;
        ticksSinceDirty = 0;
    }

    private record ScanResult(Set<BlockPos> netherMarkers, Set<BlockPos> endMarkers, Set<BlockPos> endBeaconMarkers) {}
}
