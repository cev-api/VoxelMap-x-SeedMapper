package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Tracks storage and spawner block entities in chunks currently loaded by the client. */
public final class SeedMapperContainerDetection {
    private static volatile List<SeedMapperContainerMarker> markers = List.of();
    private static volatile List<SeedMapperContainerMarker> rawMarkers = List.of();
    private static long nextScan;
    private static final Map<Long, List<SeedMapperContainerMarker>> markersByChunk = new HashMap<>();
    private static final String ICON_PATH = "images/seedmapper/icons/";
    private static int scanIndex;
    private static int lastCenterChunkX = Integer.MIN_VALUE;
    private static int lastCenterChunkZ = Integer.MIN_VALUE;
    private static int lastRadius = -1;
    private static List<long[]> scanOrder = List.of();
    private static Object lastLevel;
    private static int lastSettingsHash = Integer.MIN_VALUE;
    private static boolean refreshRequested = true;
    private static final Set<Long> dirtyChunks = new HashSet<>();
    private static final Map<Long, List<SeedMapperContainerMarker>> persistedByChunk = new HashMap<>();
    private static String persistenceScope;
    private static long nextPersistenceSave;
    private static boolean persistenceDirty;
    private static boolean lastMarkerPersistence;

    private SeedMapperContainerDetection() {
    }

    /** Requests an immediate refresh after a marker setting or block update. */
    public static void requestRefresh() {
        refreshRequested = true;
    }

    /** Refreshes the affected chunk immediately after a client block update. */
    public static void onBlockUpdated(BlockPos pos) {
        if (pos != null) {
            dirtyChunks.add((((long) (pos.getX() >> 4)) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL));
        }
    }

    public static void onChunkLoaded(int chunkX, int chunkZ) {
        dirtyChunks.add((((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL));
    }

    /** Flushes the per-server cache before Minecraft unloads the client world. */
    public static void flushPersistence() {
        if (VoxelConstants.getVoxelMapInstance() != null) {
            savePersistentMarkers(VoxelConstants.getMinecraft(), VoxelConstants.getVoxelMapInstance().getSeedMapperOptions(), true);
        }
    }

    public static void tick() {
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        Minecraft minecraft = VoxelConstants.getMinecraft();
        if (minecraft.level == null || minecraft.player == null) {
            savePersistentMarkers(minecraft, settings, true);
            markers = List.of();
            rawMarkers = List.of();
            markersByChunk.clear();
            lastCenterChunkX = Integer.MIN_VALUE;
            return;
        }
        int settingsHash = markerSettingsHash(settings);
        boolean settingsChanged = settingsHash != lastSettingsHash;
        lastSettingsHash = settingsHash;
        long now = VoxelConstants.getElapsedTicks();
        if (now < nextScan && !settingsChanged && !refreshRequested) {
            return;
        }
        nextScan = now + 1;

        int centerChunkX = minecraft.player.blockPosition().getX() >> 4;
        int centerChunkZ = minecraft.player.blockPosition().getZ() >> 4;
        int radius = Math.max(2, minecraft.options.renderDistance().get());
        String scope = persistenceScope(minecraft, settings);
        if (!scope.equals(persistenceScope)) {
            savePersistentMarkers(minecraft, settings, true);
            markersByChunk.clear();
            persistedByChunk.clear();
            persistenceScope = scope;
            if (settings.markerPersistence) loadPersistentMarkers(minecraft, settings, scope);
            refreshRequested = true;
        }
        if (settings.markerPersistence != lastMarkerPersistence) {
            lastMarkerPersistence = settings.markerPersistence;
            if (settings.markerPersistence) {
                // Reload the durable per-server cache, then let currently
                // loaded chunks override it with their authoritative state.
                persistedByChunk.clear();
                loadPersistentMarkers(minecraft, settings, persistenceScope);
                markersByChunk.forEach((key, value) -> persistedByChunk.put(key, value));
                persistenceDirty = true;
            }
        }
        int span = radius * 2 + 1;
        if (minecraft.level != lastLevel) {
            markersByChunk.clear();
            scanIndex = 0;
            lastLevel = minecraft.level;
            lastCenterChunkX = Integer.MIN_VALUE;
            lastCenterChunkZ = Integer.MIN_VALUE;
            lastRadius = -1;
            refreshRequested = true;
        }
        if (centerChunkX != lastCenterChunkX || centerChunkZ != lastCenterChunkZ || radius != lastRadius) {
            if (!settings.markerPersistence) {
                int cacheRadius = radius + 1;
                markersByChunk.entrySet().removeIf(entry -> {
                    int chunkX = (int) (entry.getKey() >> 32);
                    int chunkZ = (int) (long) entry.getKey();
                    return Math.abs(chunkX - centerChunkX) > cacheRadius || Math.abs(chunkZ - centerChunkZ) > cacheRadius;
                });
            }
            lastCenterChunkX = centerChunkX;
            lastCenterChunkZ = centerChunkZ;
            lastRadius = radius;
            ArrayList<long[]> order = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                order.add(new long[]{dx, dz});
            }
            order.sort(java.util.Comparator.comparingLong(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
            scanOrder = List.copyOf(order);
        }

        if (settingsChanged || refreshRequested) {
            // Block-entity markers (all containers and spawners) are cheap to
            // inspect. Refresh every loaded chunk now so their display and
            // filtering behaves like a normal ESP rather than a slow sweep.
            refreshLoadedBlockEntityChunks(minecraft, centerChunkX, centerChunkZ, radius, settings);
            refreshRequested = false;
        }

        if (!dirtyChunks.isEmpty()) {
            int refreshed = 0;
            var iterator = dirtyChunks.iterator();
            // Chunk-data packets arrive in bursts while flying.  Consume a
            // substantial burst each client tick so block entities behave like
            // ESP markers instead of waiting for a render-distance sweep.
            while (iterator.hasNext() && refreshed++ < 32) {
                long chunkKey = iterator.next();
                iterator.remove();
                int chunkX = (int) (chunkKey >> 32);
                int chunkZ = (int) chunkKey;
                LevelChunk chunk = minecraft.level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk != null && !chunk.isEmpty()) {
                    recordScannedChunk(chunkKey, scanChunk(chunk, settings, settings.workstationDetection || settings.redstoneDetection), settings);
                } else {
                    markersByChunk.remove(chunkKey);
                }
            }
        }

        // Scan a small batch each tick. Cached chunks remain visible while a
        // new area is being filled, so flying cannot make markers disappear.
        int total = span * span;
        int scansThisTick = 2;
        for (int scan = 0; scan < scansThisTick; scan++) {
            long[] offset = scanOrder.get(scanIndex++ % total);
            int chunkX = centerChunkX + (int) offset[0];
            int chunkZ = centerChunkZ + (int) offset[1];
            LevelChunk chunk = minecraft.level.getChunkSource().getChunk(chunkX, chunkZ, false);
            long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
            if (chunk != null && !chunk.isEmpty()) {
                recordScannedChunk(chunkKey, scanChunk(chunk, settings, settings.workstationDetection || settings.redstoneDetection), settings);
            } else if (chunk == null) {
                // An unloaded chunk is left uncached; a later pass will scan
                // it after Minecraft finishes loading it.
                markersByChunk.remove(chunkKey);
            } else {
                markersByChunk.remove(chunkKey);
            }
        }
        rebuildMarkers(settings);
        savePersistentMarkers(minecraft, settings, false);
    }

    private static void refreshLoadedBlockEntityChunks(Minecraft minecraft, int centerChunkX, int centerChunkZ, int radius, SeedMapperSettingsManager settings) {
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
            LevelChunk chunk = minecraft.level.getChunkSource().getChunk(chunkX, chunkZ, false);
            if (chunk != null && !chunk.isEmpty()) {
                long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
                recordScannedChunk(chunkKey, scanChunk(chunk, settings, settings.workstationDetection || settings.redstoneDetection), settings);
            }
        }
        rebuildMarkers(settings);
    }

    private static List<SeedMapperContainerMarker> scanChunk(LevelChunk chunk, SeedMapperSettingsManager settings, boolean scanTerrain) {
        ArrayList<SeedMapperContainerMarker> found = new ArrayList<>();
        HashSet<Long> positions = new HashSet<>();
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            SeedMapperContainerMarker marker = markerFor(chunk.getBlockState(pos), pos, settings.containerDetection, settings.spawnerDetection);
            if (marker != null) { found.add(marker); positions.add(pos.asLong()); }
        }
        if (scanTerrain) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            LevelChunkSection[] sections = chunk.getSections();
            int minSectionY = chunk.getMinY() >> 4;
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir() || !section.getStates().maybeHas(state -> isTerrainMarker(state))) continue;
                int baseY = (minSectionY + sectionIndex) << 4;
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) {
                    pos.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                    BlockState state = section.getBlockState(x, y, z);
                    SeedMapperMarkerOption option = SeedMapperMarkerOption.fromBlock(state.getBlock());
                    if (option == null || (option.category() != SeedMapperMarkerOption.Category.WORKSTATIONS && option.category() != SeedMapperMarkerOption.Category.REDSTONE) || !positions.add(pos.asLong())) continue;
                    SeedMapperContainerMarker marker = markerFor(state, pos.immutable(), settings.containerDetection, settings.spawnerDetection);
                    if (marker != null) found.add(marker);
                }
            }
        }
        return List.copyOf(found);
    }

    /** A complete received chunk replaces its saved contents, including removals. */
    private static void recordScannedChunk(long chunkKey, List<SeedMapperContainerMarker> found, SeedMapperSettingsManager settings) {
        markersByChunk.put(chunkKey, found);
        if (settings.markerPersistence) {
            persistedByChunk.put(chunkKey, found);
            persistenceDirty = true;
        }
    }

    private static void rebuildMarkers(SeedMapperSettingsManager settings) {
        ArrayList<SeedMapperContainerMarker> all = new ArrayList<>();
        if (settings.markerPersistence) persistedByChunk.values().forEach(all::addAll);
        else markersByChunk.values().forEach(all::addAll);
        rawMarkers = List.copyOf(all);
        markers = rawMarkers.stream().filter(marker -> markerEnabled(marker, settings)).toList();
    }

    private static boolean markerEnabled(SeedMapperContainerMarker marker, SeedMapperSettingsManager settings) {
        SeedMapperMarkerOption option = marker.option();
        if (option == null || !settings.isMarkerOptionEnabled(option)) return false;
        return switch (option.category()) {
            case CONTAINERS -> settings.containerDetection;
            case WORKSTATIONS -> settings.workstationDetection;
            case REDSTONE -> settings.redstoneDetection;
            case SPAWNERS -> settings.spawnerDetection;
        };
    }

    private static int markerSettingsHash(SeedMapperSettingsManager settings) {
        int hash = Boolean.hashCode(settings.containerDetection);
        hash = 31 * hash + Boolean.hashCode(settings.spawnerDetection);
        hash = 31 * hash + Boolean.hashCode(settings.workstationDetection);
        hash = 31 * hash + Boolean.hashCode(settings.redstoneDetection);
        for (SeedMapperMarkerOption option : SeedMapperMarkerOption.values()) hash = 31 * hash + Boolean.hashCode(settings.isMarkerOptionEnabled(option));
        return hash;
    }

    private static boolean isTerrainMarker(BlockState state) {
        SeedMapperMarkerOption.Category category = SeedMapperMarkerOption.categoryFor(state);
        return category == SeedMapperMarkerOption.Category.WORKSTATIONS || category == SeedMapperMarkerOption.Category.REDSTONE;
    }

    private static String persistenceScope(Minecraft minecraft, SeedMapperSettingsManager settings) {
        return settings.getCurrentServerKey() + "|" + minecraft.level.dimension().identifier();
    }

    private static File persistenceFile(Minecraft minecraft, String scope) {
        String safeName = scope.replaceAll("[^a-zA-Z0-9._-]", "_");
        return new File(minecraft.gameDirectory, "config/voxelmap/persistent-markers/" + safeName + ".txt");
    }

    private static void loadPersistentMarkers(Minecraft minecraft, SeedMapperSettingsManager settings, String scope) {
        File file = persistenceFile(minecraft, scope);
        if (!file.isFile()) return;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] parts = line.split(",", 3);
                if (parts.length != 3) continue;
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                SeedMapperMarkerOption option = null;
                for (SeedMapperMarkerOption candidate : SeedMapperMarkerOption.values()) if (candidate.id().equals(parts[2])) { option = candidate; break; }
                if (option == null) continue;
                SeedMapperContainerMarker marker = persistedMarker(x, z, option);
                long chunkKey = (((long) (x >> 4)) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
                persistedByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(marker);
            }
            rebuildMarkers(settings);
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    private static void savePersistentMarkers(Minecraft minecraft, SeedMapperSettingsManager settings, boolean force) {
        if (!settings.markerPersistence || persistenceScope == null || !persistenceDirty || (!force && VoxelConstants.getElapsedTicks() < nextPersistenceSave)) return;
        File file = persistenceFile(minecraft, persistenceScope);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return;
        ArrayList<String> lines = new ArrayList<>(rawMarkers.size());
        for (SeedMapperContainerMarker marker : rawMarkers) {
            if (marker.option() != null) lines.add(marker.blockX() + "," + marker.blockZ() + "," + marker.option().id());
        }
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            persistenceDirty = false;
            nextPersistenceSave = VoxelConstants.getElapsedTicks() + 100;
        } catch (IOException ignored) {
        }
    }

    private static SeedMapperContainerMarker persistedMarker(int x, int z, SeedMapperMarkerOption option) {
        String iconName = switch (option) {
            case SINGLE_CHEST -> "chest_single";
            case DOUBLE_CHEST -> "chest_double";
            case TRAPPED_CHEST -> "chest_trapped";
            case ENDER_CHEST -> "chest_ender";
            case SHULKER_BOX -> "shulker_box";
            case TRIAL_SPAWNER -> "trial_spawner";
            default -> option.id();
        };
        String label = switch (option) {
            case SINGLE_CHEST -> "Single Chest";
            case DOUBLE_CHEST -> "Double Chest";
            default -> option.label();
        };
        return fixedMarker(new BlockPos(x, 0, z), iconName, label, option);
    }

    public static List<SeedMapperContainerMarker> getMarkers() {
        return markers;
    }

    public static List<ContainerCluster> clusterMarkers(double cellSize) {
        if (cellSize <= 1.0D) {
            return markers.stream().map(marker -> new ContainerCluster(marker, 1)).toList();
        }
        // Continuous cell sizes make grid boundaries slide while zooming.  A
        // power-of-two grid changes only at deliberate zoom thresholds, so a
        // cluster cannot flicker between unrelated cells frame-to-frame.
        long gridSize = 1L;
        while (gridSize < cellSize && gridSize < (1L << 30)) gridSize <<= 1;
        Map<ContainerClusterKey, ContainerClusterBuilder> grouped = new HashMap<>();
        for (SeedMapperContainerMarker marker : markers) {
            long cellX = Math.floorDiv(marker.blockX(), gridSize);
            long cellZ = Math.floorDiv(marker.blockZ(), gridSize);
            ContainerClusterKey key = new ContainerClusterKey(marker.option(), cellX, cellZ);
            ContainerClusterBuilder builder = grouped.computeIfAbsent(key, ignored -> new ContainerClusterBuilder(marker));
            builder.add(marker);
        }
        return grouped.values().stream()
                .map(builder -> new ContainerCluster(builder.marker, builder.count))
                .sorted(java.util.Comparator.comparingInt((ContainerCluster cluster) -> cluster.marker().blockX())
                        .thenComparingInt(cluster -> cluster.marker().blockZ())
                        .thenComparing(cluster -> cluster.marker().label()))
                .toList();
    }

    public record ContainerCluster(SeedMapperContainerMarker marker, int count) {
    }

    private record ContainerClusterKey(SeedMapperMarkerOption option, long cellX, long cellZ) {
    }

    private static final class ContainerClusterBuilder {
        private SeedMapperContainerMarker marker;
        private int count;
        private ContainerClusterBuilder(SeedMapperContainerMarker marker) { this.marker = marker; }
        private void add(SeedMapperContainerMarker candidate) {
            count++;
            if (candidate.blockX() < marker.blockX()
                    || (candidate.blockX() == marker.blockX() && candidate.blockZ() < marker.blockZ())) {
                marker = candidate;
            }
        }
    }

    private static SeedMapperContainerMarker markerFor(BlockState state, BlockPos pos, boolean containersEnabled, boolean spawnersEnabled) {
        Block block = state.getBlock();
        SeedMapperMarkerOption selected = optionForState(state);
        String label;
        if (block == Blocks.SPAWNER) {
            return fixedMarker(pos, "spawner", "Spawner", selected);
        } else if (block == Blocks.TRIAL_SPAWNER) {
            return fixedMarker(pos, "trial_spawner", "Trial Spawner", selected);
        } else if (block == Blocks.CHEST) {
            label = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? "Single Chest" : "Double Chest";
            return fixedMarker(pos, state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? "chest_single" : "chest_double", label, selected);
        } else if (block == Blocks.TRAPPED_CHEST) {
            label = "Trapped Chest";
            return fixedMarker(pos, "chest_trapped", label, selected);
        } else if (block == Blocks.ENDER_CHEST) {
            label = "Ender Chest";
            return fixedMarker(pos, "chest_ender", label, selected);
        } else if (block == Blocks.BARREL) {
            label = "Barrel";
        } else if (block instanceof ShulkerBoxBlock) {
            return fixedMarker(pos, "shulker_box", "Shulker Box", selected);
        } else if (block == Blocks.DISPENSER) {
            label = "Dispenser";
        } else if (block == Blocks.DROPPER) {
            label = "Dropper";
        } else if (block == Blocks.HOPPER) {
            label = "Hopper";
        } else if (block == Blocks.BREWING_STAND) {
            label = "Brewing Stand";
        } else if (block == Blocks.CRAFTER) {
            label = "Crafter";
        } else if (block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER || block == Blocks.STONECUTTER || block == Blocks.LOOM
                || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.FLETCHING_TABLE || block == Blocks.SMITHING_TABLE
                || block == Blocks.GRINDSTONE || block == Blocks.ANVIL || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.LECTERN || block == Blocks.REDSTONE_BLOCK || block == Blocks.REDSTONE_TORCH
                || block == Blocks.REPEATER || block == Blocks.COMPARATOR || block == Blocks.OBSERVER
                || block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.NOTE_BLOCK) {
            label = selected == null ? "Marker" : selected.label();
        } else {
            return null;
        }
        return fixedBlockMarker(pos, block, label, selected);
    }

    private static SeedMapperMarkerOption optionForState(BlockState state) {
        if (state.is(Blocks.CHEST)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            return type == ChestType.SINGLE ? SeedMapperMarkerOption.SINGLE_CHEST : SeedMapperMarkerOption.DOUBLE_CHEST;
        }
        return SeedMapperMarkerOption.fromBlock(state.getBlock());
    }

    private static SeedMapperContainerMarker fixedBlockMarker(BlockPos pos, Block block, String label, SeedMapperMarkerOption option) {
        String iconName = block == Blocks.BARREL ? "barrel"
                : block == Blocks.HOPPER ? "hopper"
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        return fixedMarker(pos, iconName, label, option);
    }

    private static SeedMapperContainerMarker fixedMarker(BlockPos pos, String iconName, String label, SeedMapperMarkerOption option) {
        return new SeedMapperContainerMarker(pos.getX(), pos.getZ(),
                Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, ICON_PATH + iconName + ".png"), label, option);
    }
}
