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
import java.util.Set;

public class ExploredChunksManager {
    private final Set<ChunkPos> exploredChunks = new HashSet<>();
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
        for (ChunkPos chunk : exploredChunks) {
            if (Math.abs(chunk.x() - centerChunkX) <= radius && Math.abs(chunk.z() - centerChunkZ) <= radius) {
                result.add(chunk);
            }
        }
        return result;
    }

    public void clearCurrentWorld() {
        exploredChunks.clear();
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
            appendChunk(getDataPath(), chunkPos);
        }
    }

    private void loadWorld(String worldKey) {
        exploredChunks.clear();
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
                }
            }
        } catch (IOException ignored) {
        }
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
