package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ExploredChunksManager {
    private static final int REGION_SHIFT = 5; // 32x32 chunk buckets for fast range queries
    private final Set<ChunkPos> exploredChunks = new HashSet<>();
    private final Map<Long, Set<ChunkPos>> exploredChunksByRegion = new HashMap<>();
    private String loadedWorldKey = "";
    private Integer lastChunkX;
    private Integer lastChunkZ;

    public void onTick() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }
        String worldKey = getWorldKey();
        if (!worldKey.equals(loadedWorldKey)) {
            loadWorld(worldKey);
        }
        int chunkX = GameVariableAccessShim.xCoord() >> 4;
        int chunkZ = GameVariableAccessShim.zCoord() >> 4;
        if (lastChunkX == null || lastChunkX != chunkX || lastChunkZ == null || lastChunkZ != chunkZ) {
            markExplored(new ChunkPos(chunkX, chunkZ));
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
    }

    public Set<ChunkPos> getExploredChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        Set<ChunkPos> result = new HashSet<>();
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;

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

    public void clearCurrentWorld() {
        exploredChunks.clear();
        exploredChunksByRegion.clear();
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
    }

    private void markExplored(ChunkPos chunkPos) {
        if (exploredChunks.add(chunkPos)) {
            indexChunk(chunkPos);
            appendChunk(getDataPath(), chunkPos);
        }
    }

    private void loadWorld(String worldKey) {
        exploredChunks.clear();
        exploredChunksByRegion.clear();
        loadedWorldKey = worldKey;
        Path path = getDataPath();
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                return;
            }
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                ChunkPos chunkPos = parseChunk(line);
                if (chunkPos != null) {
                    exploredChunks.add(chunkPos);
                    indexChunk(chunkPos);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void indexChunk(ChunkPos chunkPos) {
        int regionX = chunkPos.x() >> REGION_SHIFT;
        int regionZ = chunkPos.z() >> REGION_SHIFT;
        exploredChunksByRegion
                .computeIfAbsent(regionKey(regionX, regionZ), ignored -> new HashSet<>())
                .add(chunkPos);
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    private Path getDataPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxelmap")
                .resolve("chunk_overlays")
                .resolve("explored")
                .resolve(getWorldKey() + ".txt");
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
}
