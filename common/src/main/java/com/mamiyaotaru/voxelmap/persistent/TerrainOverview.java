package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.rendering.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.textures.DynamicMutableTexture;
import com.mamiyaotaru.voxelmap.util.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

/** Far-zoom terrain pyramid: 256px atlas pages containing downsampled source regions. */
final class TerrainOverview {
    private static final int PAGE_SIZE = 256;
    private static final int MAX_PAGES = 128; // 32 MiB each of CPU/native/GPU pixel storage at most.
    private static final int MAGIC = 0x564F5631;
    private final PersistentMap map;
    private final WorldMapTerrainRaster farRaster;
    private final LinkedHashMap<PageKey, Page> pages = new LinkedHashMap<>(32, 0.75F, true);
    private Context context;
    private int frameUploads;
    private volatile long generation;
    private int lastLightHash;
    private long lastLightingRefresh;
    private final java.util.concurrent.atomic.AtomicLong lastDiskPrune = new java.util.concurrent.atomic.AtomicLong();

    private record Context(Path directory, ClientLevel world, String worldName, String subworld,
            Identifier dimension) { }
    private record PageKey(int x, int z, int resolution) { }
    private record Pixels(int[] colors, long[] revisions, int style, boolean complete) { }
    private static final class Page {
        final Identifier location = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "worldmap/overview/" + java.util.UUID.randomUUID());
        DynamicMutableTexture texture;
        Future<Pixels> pending;
        WorldMapProgress.Task progress;
        Pixels pixels;
        long nextValidation;
        long requestedGeneration;
        boolean forceRebuild;
        volatile boolean released;
    }

    TerrainOverview(PersistentMap map) { this.map = map; this.farRaster = new WorldMapTerrainRaster(map); }

    void render(GuiGraphicsExtractor graphics, int left, int right, int top, int bottom,
            Identifier dimension, float scale) {
        if (map.world == null) return;
        String worldName = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentWorldName();
        String subworld = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false);
        if (context == null || context.world() != map.world || !context.dimension().equals(dimension)
                || !context.worldName().equals(worldName) || !context.subworld().equals(subworld)) {
            clear();
            CachedRegion source = new CachedRegion(map, "overview", "0,0", map.world, worldName, subworld, 0, 0, dimension, true);
            context = new Context(source.cacheDirectory().toPath(), map.world, worldName, subworld, dimension);
        }
        int resolution = 8;
        while (resolution < 64 && resolution < 256 * scale * 1.5F) resolution *= 2;
        int group = PAGE_SIZE / resolution;
        while (resolution > 1 && (long) (Math.floorDiv(right, group) - Math.floorDiv(left, group) + 1)
                * (Math.floorDiv(bottom, group) - Math.floorDiv(top, group) + 1) > 96) {
            resolution /= 2; group = PAGE_SIZE / resolution;
        }
        if ((long) (Math.floorDiv(right, group) - Math.floorDiv(left, group) + 1)
                * (Math.floorDiv(bottom, group) - Math.floorDiv(top, group) + 1) > 96) {
            boolean lightingReady = !map.mapOptions.dynamicLighting;
            if (!lightingReady) for (int color : map.lightmapColors) if ((color & 0x00ffffff) != 0) { lightingReady = true; break; }
            if (!map.colorManager.worldMapPaletteReady() || !lightingReady) return;
            long now = System.currentTimeMillis();
            int light = Arrays.hashCode(map.lightmapColors);
            if (lastLightingRefresh == 0 || (map.mapOptions.dynamicLighting && light != lastLightHash
                    && now - lastLightingRefresh >= 30_000 && farRaster.complete())) {
                farRaster.invalidateSamples(); lastLightHash = light; lastLightingRefresh = now;
            }
            farRaster.render(graphics, context.directory(), worldName, subworld,
                    dimension, left, right, top, bottom, 31 * style() + lastLightHash, map.options.detail.terrainResolution, scale);
            return;
        }
        farRaster.suspend();
        int lightHash = Arrays.hashCode(map.lightmapColors);
        long lightNow = System.currentTimeMillis();
        if (lastLightingRefresh == 0) { lastLightingRefresh = lightNow; lastLightHash = lightHash; }
        else if (map.mapOptions.dynamicLighting && lightHash != lastLightHash && lightNow - lastLightingRefresh >= 30_000) {
            boolean complete = true;
            for (int z = Math.floorDiv(top, group); z <= Math.floorDiv(bottom, group); z++)
                for (int x = Math.floorDiv(left, group); x <= Math.floorDiv(right, group); x++) {
                    Page page = pages.get(new PageKey(x, z, resolution));
                    if (page == null || page.pixels == null || !page.pixels.complete()) complete = false;
                }
            // Finish cold loading before refreshing day/night shading; retain the previous atlas meanwhile.
            if (complete) { invalidate(); lastLightingRefresh = lightNow; lastLightHash = lightHash; }
        }
        boolean lightingReady = !map.mapOptions.dynamicLighting;
        if (!lightingReady) for (int light : map.lightmapColors) if ((light & 0x00ffffff) != 0) { lightingReady = true; break; }
        boolean paletteReady = map.colorManager.worldMapPaletteReady() && lightingReady;
        int style = style();
        long now = System.currentTimeMillis();
        frameUploads = 0;
        int scheduled = 0;
        for (int z = Math.floorDiv(top, group); z <= Math.floorDiv(bottom, group); z++) {
            for (int x = Math.floorDiv(left, group); x <= Math.floorDiv(right, group); x++) {
                PageKey key = new PageKey(x, z, resolution);
                Page page = pages.computeIfAbsent(key, ignored -> new Page());
                if (page.pending != null && page.pending.isDone() && frameUploads < 4 && WorldMapUploadBudget.available(PAGE_SIZE * PAGE_SIZE * 4)) {
                    try {
                        Pixels pixels = page.pending.get();
                        if (pixels != null && pixels.style() == style && page.requestedGeneration == generation) {
                            boolean upload = page.pixels != pixels;
                            page.pixels = pixels;
                            page.forceRebuild = false;
                            if (page.texture == null) {
                                page.texture = new DynamicMutableTexture("World map overview", PAGE_SIZE, PAGE_SIZE, true);
                                Minecraft.getInstance().getTextureManager().register(page.location, page.texture);
                            }
                            if (upload) {
                                page.texture.setPixelsPremultipliedABGR(pixels.colors());
                                long started = WorldMapUploadBudget.begin(PAGE_SIZE * PAGE_SIZE * 4);
                                try { page.texture.upload(); frameUploads++; }
                                finally { WorldMapUploadBudget.end(started); }
                            }
                        }
                    } catch (Exception exception) {
                        VoxelConstants.getLogger().warn("Could not build terrain overview", exception);
                    }
                    page.pending = null;
                    page.nextValidation = page.pixels != null && !page.pixels.complete() ? 0 : now + 5000;
                }
                if (paletteReady && page.pending == null && scheduled < 4 && (page.pixels == null || page.pixels.style() != style || now >= page.nextValidation)) {
                    Context captured = context;
                    Pixels previous = page.forceRebuild ? null : page.pixels;
                    boolean readCache = !page.forceRebuild;
                    long token = generation;
                    int sourceResolution = map.options.detail.terrainResolution;
                    page.requestedGeneration = token;
                    WorldMapProgress.Task task = WorldMapProgress.begin("Terrain"); page.progress = task;
                    page.pending = ThreadManager.executorService.submit(() -> WorldMapProgress.run(task, () -> {
                        try { return build(captured, key, style, previous, readCache, sourceResolution, () -> generation != token || page.released); }
                        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    }));
                    scheduled++;
                }
                if (page.texture != null) VoxelMapGuiGraphics.blitMapTile(graphics, page.location,
                        (float) x * group * 256, (float) z * group * 256, group * 256.0F);
            }
        }
        var iterator = pages.entrySet().iterator();
        while (pages.size() > MAX_PAGES && iterator.hasNext()) {
            Page page = iterator.next().getValue();
            release(page); iterator.remove();
        }
    }

    private int style() {
        var options = map.mapOptions;
        return java.util.Objects.hash(options.biomeOverlay, options.biomes, options.dynamicLighting,
                options.waterTransparency, options.blockTransparency, options.heightmap, options.slopemap,
                map.colorManager.worldMapPaletteFingerprint(), map.options.detail.terrainResolution, Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
    }

    private Pixels build(Context context, PageKey key, int style, Pixels previous, boolean readCache, int sourceResolution, BooleanSupplier cancelled) throws IOException {
        int group = PAGE_SIZE / key.resolution();
        Path path = context.directory().resolve("overview-v1").resolve("r" + key.resolution()).resolve(key.x() + "," + key.z() + ".bin");
        MapRegionPack pack = MapRegionPack.forDirectory(context.directory().toFile());
        if (previous == null && readCache) previous = read(path, group, style);
        long[] revisions = new long[group * group];
        boolean changed = previous == null || previous.style() != style;
        for (int z = 0; z < group; z++) for (int x = 0; x < group; x++) {
            int index = z * group + x;
            int rx = key.x() * group + x, rz = key.z() * group + z;
            CachedRegion live = map.overviewRegion(context.dimension(), rx, rz);
            revisions[index] = pack.revision(rx, rz) ^ (live == null ? 0 : live.getMostRecentChange());
            if (previous != null && revisions[index] != previous.revisions()[index]) changed = true;
        }
        if (!changed) return previous;
        int[] colors = previous == null || previous.style() != style ? new int[PAGE_SIZE * PAGE_SIZE] : previous.colors().clone();
        int sampled = 0;
        int visited = 0;
        WorldMapProgress.report("Page batch", 0, group * group);
        boolean complete = true;
        for (int z = 0; z < group; z++) for (int x = 0; x < group; x++) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) return null;
            WorldMapProgress.report("Page batch", visited++, group * group);
            int index = z * group + x;
            if (previous != null && previous.style() == style && previous.revisions()[index] == revisions[index]) continue;
            int rx = key.x() * group + x, rz = key.z() * group + z;
            int[] thumbnail;
            if (revisions[index] == 0) thumbnail = new int[key.resolution() * key.resolution()];
            else {
                if (sampled++ >= 64) { revisions[index] = Long.MIN_VALUE; complete = false; continue; }
                CachedRegion live = map.overviewRegion(context.dimension(), rx, rz);
                thumbnail = live == null ? null : live.sampleLoadedOverview(key.resolution(), sourceResolution);
                if (thumbnail == null) thumbnail = new CachedRegion(map, "overview", rx + "," + rz, context.world(), context.worldName(),
                        context.subworld(), rx, rz, context.dimension(), true, context.directory().toFile()).sampleOverview(key.resolution(), sourceResolution);
            }
            for (int row = 0; row < key.resolution(); row++) {
                System.arraycopy(thumbnail, row * key.resolution(), colors,
                        (z * key.resolution() + row) * PAGE_SIZE + x * key.resolution(), key.resolution());
            }
        }
        Pixels result = new Pixels(colors, revisions, style, complete);
        if (!cancelled.getAsBoolean()) { write(path, result); pruneDiskCache(context.directory().resolve("overview-v1")); }
        return result;
    }

    private static Pixels read(Path path, int group, int style) {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != MAGIC || input.readInt() != style || input.readInt() != group) return null;
            long[] revisions = new long[group * group];
            int[] pixels = new int[PAGE_SIZE * PAGE_SIZE];
            for (int i = 0; i < revisions.length; i++) revisions[i] = input.readLong();
            for (int i = 0; i < pixels.length; i++) pixels[i] = input.readInt();
            if (input.read() != -1) return null;
            return new Pixels(pixels, revisions, style, Arrays.stream(revisions).noneMatch(v -> v == Long.MIN_VALUE));
        } catch (IOException ignored) { return null; }
    }

    private static void write(Path path, Pixels pixels) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-" + Thread.currentThread().threadId());
        try {
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
                output.writeInt(MAGIC); output.writeInt(pixels.style());
                output.writeInt((int) Math.sqrt(pixels.revisions().length));
                for (long revision : pixels.revisions()) output.writeLong(revision);
                for (int color : pixels.colors()) output.writeInt(color);
            }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private void pruneDiskCache(Path directory) {
        long now = System.currentTimeMillis(), previous = lastDiskPrune.get();
        if (now - previous < 60_000 || !lastDiskPrune.compareAndSet(previous, now)) return;
        record FileAge(Path path, long size, long modified) { }
        try (var files = Files.walk(directory, 2)) {
            java.util.List<FileAge> cached = new java.util.ArrayList<>();
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".bin") && Files.isRegularFile(p)).toList()) {
                var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
                cached.add(new FileAge(path, attributes.size(), attributes.lastModifiedTime().toMillis()));
            }
            long bytes = 0; for (FileAge file : cached) bytes += file.size();
            cached.sort(java.util.Comparator.comparingLong(FileAge::modified));
            for (FileAge file : cached) {
                if (bytes <= (256L << 20)) break;
                if (Files.deleteIfExists(file.path())) bytes -= file.size();
            }
        } catch (IOException exception) { VoxelConstants.getLogger().debug("Could not prune derived overview cache", exception); }
    }

    void suspend() {
        farRaster.suspend();
        generation++;
        for (Page page : pages.values()) {
            if (page.pending != null) page.pending.cancel(false);
            if (page.progress != null) page.progress.cancel();
            page.pending = null;
        }
        ThreadManager.executorService.purge();
    }

    void invalidate() {
        farRaster.invalidateSamples();
        generation++;
        for (Page page : pages.values()) {
            if (page.pending != null) page.pending.cancel(false);
            if (page.progress != null) page.progress.cancel();
            page.pending = null; page.nextValidation = 0; page.forceRebuild = true;
        }
    }

    void clear() {
        farRaster.clear();
        generation++;
        for (Page page : pages.values()) release(page);
        pages.clear(); context = null; lastLightingRefresh = 0;
    }

    private void release(Page page) {
        page.released = true;
        if (page.pending != null) page.pending.cancel(false);
        if (page.progress != null) page.progress.cancel();
        if (page.texture != null) Minecraft.getInstance().getTextureManager().release(page.location);
    }
}
