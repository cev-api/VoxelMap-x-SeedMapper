package com.mamiyaotaru.voxelmap.persistent;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.util.BiomeParser;
import com.mamiyaotaru.voxelmap.util.BlockStateParser;
import com.mamiyaotaru.voxelmap.util.CommandUtils;
import com.mamiyaotaru.voxelmap.util.MessageUtils;
import com.mamiyaotaru.voxelmap.util.MutableBlockPos;
import com.mamiyaotaru.voxelmap.util.TextUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.IntStream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class ComparisonCachedRegion {
    private final PersistentMap persistentMap;
    private final String key;
    private final ClientLevel world;
    private final String subworldName;
    private final String worldNamePathPart;
    private String subworldNamePathPart;
    private final String dimensionNamePathPart;
    private final boolean underground;
    private final int x;
    private final int z;
    private final CompressibleMapData data;
    final MutableBlockPos blockPos = new MutableBlockPos(0, 0, 0);
    private int loadedChunks;
    private boolean loaded;
    private boolean empty = true;

    public ComparisonCachedRegion(PersistentMap persistentMap, String key, ClientLevel world, String worldName, String subworldName, int x, int z) {
        this.data = new CompressibleMapData(world);
        this.persistentMap = persistentMap;
        this.key = key;
        this.world = world;
        this.subworldName = subworldName;
        this.worldNamePathPart = TextUtils.scrubNameFile(worldName);
        if (!Objects.equals(subworldName, "")) {
            this.subworldNamePathPart = TextUtils.scrubNameFile(subworldName) + "/";
        }
        String dimensionName = VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(world).getStorageName();
        this.dimensionNamePathPart = TextUtils.scrubNameFile(dimensionName);
        this.underground = world.dimensionType().cardinalLightType() != CardinalLighting.Type.NETHER && !world.dimensionType().hasSkyLight() || world.dimensionType().hasCeiling();
        this.x = x;
        this.z = z;
    }

    public void loadCurrent() {
        this.loadedChunks = 0;

        for (int chunkX = 0; chunkX < 16; ++chunkX) {
            for (int chunkZ = 0; chunkZ < 16; ++chunkZ) {
                LevelChunk chunk = this.world.getChunk(this.x * 16 + chunkX, this.z * 16 + chunkZ);
                if (chunk != null && !chunk.isEmpty() && this.world.hasChunk(this.x * 16 + chunkX, this.z * 16 + chunkZ) && !this.isChunkEmpty(this.world, chunk)) {
                    this.loadChunkData(chunk, chunkX, chunkZ);
                    ++this.loadedChunks;
                }
            }
        }

    }

    private boolean isChunkEmpty(ClientLevel world, LevelChunk chunk) {

        return IntStream.range(0, 16).noneMatch(t -> IntStream.range(0, 16).anyMatch(s -> chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, t, s) != 0));
    }

    private void loadChunkData(LevelChunk chunk, int chunkX, int chunkZ) {
        for (int t = 0; t < 16; ++t) {
            for (int s = 0; s < 16; ++s) {
                this.persistentMap.getAndStoreData(this.data, this.world, chunk, this.blockPos, this.underground, this.x * 256, this.z * 256, chunkX * 16 + t, chunkZ * 16 + s);
            }
        }

    }

    public void loadStored() {
        try {
            File cachedRegionFileDir = VoxelConstants.getVoxelMapInstance().getDataStore().getWorldCacheDir(this.subworldNamePathPart + this.dimensionNamePathPart);
            cachedRegionFileDir.mkdirs();
            File legacyFile = new File(cachedRegionFileDir, this.key + ".zip");
            MapRegionPack.PackedRegion region = MapRegionPack.forDirectory(cachedRegionFileDir)
                    .readOrMigrate(legacyFile, this.x, this.z);
            if (region != null) {
                BiMap<BlockState, Integer> stateToInt = HashBiMap.create();
                if (region.key() != null) {
                    try (Scanner scanner = new Scanner(new ByteArrayInputStream(region.key()), StandardCharsets.UTF_8)) {
                        while (scanner.hasNextLine()) BlockStateParser.parseLine(scanner.nextLine(), stateToInt);
                    }
                }

                BiMap<Biome, Integer> biomeMap = HashBiMap.create();
                if (region.biomes() != null) {
                    try (Scanner scanner = new Scanner(new ByteArrayInputStream(region.biomes()), StandardCharsets.UTF_8)) {
                        while (scanner.hasNextLine()) BiomeParser.parseLine(world, scanner.nextLine(), biomeMap);
                    }
                } else {
                    BiomeParser.populateLegacyBiomeMap(world, biomeMap);
                }

                int version = 1;
                if (region.control() != null) {
                    Properties properties = new Properties();
                    try (InputStream input = new ByteArrayInputStream(region.control())) {
                        properties.load(input);
                    }
                    try {
                        version = Integer.parseInt(properties.getProperty("version", "1"));
                    } catch (NumberFormatException ignored) { }
                }

                if (region.data().length == this.data.getExpectedDataLength(version) && !stateToInt.isEmpty()) {
                    this.data.setData(region.data(), stateToInt, biomeMap, version);
                    this.empty = false;
                    this.loaded = true;
                } else {
                    VoxelConstants.getLogger().warn("failed to load data from " + legacyFile.getPath());
                }
            }
        } catch (IOException var15) {
            VoxelConstants.getLogger().error("Failed to load region file for " + this.x + "," + this.z + " in " + this.worldNamePathPart + "/" + this.subworldNamePathPart + this.dimensionNamePathPart, var15);
        }

    }

    public String getSubworldName() {
        return this.subworldName;
    }

    public String getKey() {
        return this.key;
    }

    public CompressibleMapData getMapData() {
        return this.data;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public boolean isEmpty() {
        return this.empty;
    }

    public int getLoadedChunks() {
        return this.loadedChunks;
    }

    public boolean isGroundAt(int blockX, int blockZ) {
        return this.isLoaded() && this.getHeightAt(blockX, blockZ) > 0;
    }

    public int getHeightAt(int blockX, int blockZ) {
        int x = blockX - this.x * 256;
        int z = blockZ - this.z * 256;
        int y = this.data.getHeight(x, z);
        if (this.underground && y == 255) {
            y = CommandUtils.getSafeHeight(blockX, 64, blockZ, this.world);
        }

        return y;
    }

    public int getSimilarityTo(ComparisonCachedRegion candidate) {
        int compared = 0;
        int matched = 0;
        CompressibleMapData candidateData = candidate.getMapData();

        for (int t = 0; t < 16; ++t) {
            for (int s = 0; s < 16; ++s) {
                int nonZeroHeights = 0;
                int nonZeroHeightsInCandidate = 0;
                int matchesInChunk = 0;

                for (int i = 0; i < 16; ++i) {
                    for (int j = 0; j < 16; ++j) {
                        int x = t * 16 + i;
                        int z = s * 16 + j;
                        if (this.data.getHeight(x, z) == candidateData.getHeight(x, z) && this.data.getBlockstate(x, z) == candidateData.getBlockstate(x, z)
                                && (this.data.getOceanFloorHeight(x, z) == 0 || this.data.getOceanFloorHeight(x, z) == candidateData.getOceanFloorHeight(x, z) && this.data.getOceanFloorBlockstate(x, z) == candidateData.getOceanFloorBlockstate(x, z))) {
                            ++matchesInChunk;
                        }

                        if (this.data.getHeight(x, z) != Short.MIN_VALUE) {
                            ++nonZeroHeights;
                        }

                        if (candidateData.getHeight(x, z) != Short.MIN_VALUE) {
                            ++nonZeroHeightsInCandidate;
                        }
                    }
                }

                if (nonZeroHeights != 0 && nonZeroHeightsInCandidate != 0) {
                    compared += 256;
                    matched += matchesInChunk;
                }

                MessageUtils.printDebug("at " + t + "," + s + " there were local non zero: " + nonZeroHeights + " and comparison non zero: " + nonZeroHeightsInCandidate);
            }
        }

        MessageUtils.printDebug("compared: " + compared + ", matched: " + matched);
        return compared >= 256 ? matched * 100 / compared : 0;
    }
}
