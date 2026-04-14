package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

public class NewerNewChunksManager {
    public enum DetectMode {
        NORMAL,
        IGNORE_BLOCK_EXPLOIT,
        BLOCK_EXPLOIT_MODE
    }

    private static final Direction[] SEARCH_DIRS =
            new Direction[] {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.UP};

    private final Set<ChunkPos> newChunks = new HashSet<>();
    private final Set<ChunkPos> oldChunks = new HashSet<>();
    private final Set<ChunkPos> blockUpdateChunks = new HashSet<>();
    private String loadedWorldKey = "";

    public void onTick() {
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }
        String worldKey = getWorldKey();
        if (!worldKey.equals(loadedWorldKey)) {
            loadWorld(worldKey);
        }
    }

    public void processChunk(LevelChunk chunk) {
        if (chunk == null) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        if (containsAny(chunkPos)) {
            return;
        }

        boolean foundFlowingFluid = false;
        int minY = chunk.getMinY();
        Level level = GameVariableAccessShim.getWorld();
        int maxY = level == null ? chunk.getMaxY() + 1 : level.getMaxY() + 1;
        for (int localX = 0; localX < 16 && !foundFlowingFluid; localX++) {
            for (int localZ = 0; localZ < 16 && !foundFlowingFluid; localZ++) {
                for (int y = minY; y < maxY; y++) {
                    FluidState fluid = chunk.getFluidState(new BlockPos(chunkPos.getMinBlockX() + localX, y, chunkPos.getMinBlockZ() + localZ));
                    if (!fluid.isEmpty() && !fluid.isSource()) {
                        foundFlowingFluid = true;
                        break;
                    }
                }
            }
        }
        if (foundFlowingFluid) {
            markOld(chunkPos);
        } else {
            markNew(chunkPos);
        }
    }

    public void onBlockUpdated(BlockPos pos, boolean blockUpdateExploit, boolean liquidExploit) {
        if (pos == null) {
            return;
        }

        ChunkPos chunkPos = ChunkPos.containing(pos);

        if (blockUpdateExploit && !containsAny(chunkPos)) {
            markBlockUpdate(chunkPos);
        }

        if (!liquidExploit || containsFinalChunkType(chunkPos)) {
            return;
        }

        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            return;
        }

        FluidState fluid = level.getBlockState(pos).getFluidState();
        if (fluid.isEmpty() || fluid.isSource()) {
            return;
        }

        for (Direction dir : SEARCH_DIRS) {
            BlockPos neighborPos = pos.relative(dir);
            FluidState neighborFluid = level.getBlockState(neighborPos).getFluidState();
            if (!neighborFluid.isEmpty() && neighborFluid.isSource()) {
                blockUpdateChunks.remove(chunkPos);
                markNew(chunkPos);
                return;
            }
        }
    }

    private void markNew(ChunkPos chunkPos) {
        if (newChunks.add(chunkPos)) {
            oldChunks.remove(chunkPos);
            blockUpdateChunks.remove(chunkPos);
            appendChunk(getDataPath("new"), chunkPos);
        }
    }

    private void markOld(ChunkPos chunkPos) {
        if (oldChunks.add(chunkPos)) {
            newChunks.remove(chunkPos);
            blockUpdateChunks.remove(chunkPos);
            appendChunk(getDataPath("old"), chunkPos);
        }
    }

    private void markBlockUpdate(ChunkPos chunkPos) {
        if (blockUpdateChunks.add(chunkPos)) {
            appendChunk(getDataPath("block"), chunkPos);
        }
    }

    private boolean containsAny(ChunkPos chunkPos) {
        return newChunks.contains(chunkPos) || oldChunks.contains(chunkPos) || blockUpdateChunks.contains(chunkPos);
    }

    private boolean containsFinalChunkType(ChunkPos chunkPos) {
        return newChunks.contains(chunkPos) || oldChunks.contains(chunkPos);
    }

    public Set<ChunkPos> getNewChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        return getChunksInRange(newChunks, centerChunkX, centerChunkZ, radius);
    }

    public Set<ChunkPos> getOldChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        return getChunksInRange(oldChunks, centerChunkX, centerChunkZ, radius);
    }

    public Set<ChunkPos> getBlockUpdateChunksInRange(int centerChunkX, int centerChunkZ, int radius) {
        Set<ChunkPos> chunks = getChunksInRange(blockUpdateChunks, centerChunkX, centerChunkZ, radius);
        chunks.removeAll(newChunks);
        chunks.removeAll(oldChunks);
        return chunks;
    }

    private Set<ChunkPos> getChunksInRange(Set<ChunkPos> source, int centerChunkX, int centerChunkZ, int radius) {
        Set<ChunkPos> result = new HashSet<>();
        for (ChunkPos chunk : source) {
            if (Math.abs(chunk.x() - centerChunkX) <= radius && Math.abs(chunk.z() - centerChunkZ) <= radius) {
                result.add(chunk);
            }
        }
        return result;
    }

    private void loadWorld(String worldKey) {
        newChunks.clear();
        oldChunks.clear();
        blockUpdateChunks.clear();
        loadedWorldKey = worldKey;
        loadPath(getDataPath("new"), newChunks);
        loadPath(getDataPath("old"), oldChunks);
        loadPath(getDataPath("block"), blockUpdateChunks);
        reconcileLoadedData();
    }

    private void reconcileLoadedData() {
        // Runtime behavior never keeps block-update chunks once a final classification exists.
        blockUpdateChunks.removeAll(newChunks);
        blockUpdateChunks.removeAll(oldChunks);

        // If both final sets contain a chunk in persisted data, prefer old (conservative match).
        newChunks.removeAll(oldChunks);
    }

    private void loadPath(Path path, Set<ChunkPos> target) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                return;
            }
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                ChunkPos chunkPos = parseChunk(line);
                if (chunkPos != null) {
                    target.add(chunkPos);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private Path getDataPath(String kind) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxelmap")
                .resolve("chunk_overlays")
                .resolve("newer_new_chunks")
                .resolve(getWorldKey() + "_" + kind + ".txt");
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
