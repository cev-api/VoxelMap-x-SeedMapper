package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.rendering.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.textures.DynamicMutableTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Future;

/** Very wide terrain views stream saved regions into a screen-size raster instead of walking empty pages. */
final class WorldMapTerrainRaster {
    private final PersistentMap map;
    private final Identifier location = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "worldmap/far/" + java.util.UUID.randomUUID());
    private DynamicMutableTexture texture;
    private Future<?> pending;
    private WorldMapProgress.Task progress;
    private volatile long generation;
    private volatile Snapshot latest;
    private Snapshot displayed;
    private Key requested;
    private final java.util.LinkedHashMap<Key, int[]> history = new java.util.LinkedHashMap<>(16, .75F, true);
    private synchronized int[] cached(Key key) { return history.get(key); }
    private synchronized void remember(Key key, int[] pixels) {
        history.put(key, pixels);
        long bytes = history.values().stream().mapToLong(value -> (long) value.length * 4).sum();
        long budget = ((long) map.options.detail.zoomCacheMiB << 20) / 8;
        var iterator = history.entrySet().iterator();
        while (iterator.hasNext() && (bytes > budget || history.size() > 64)) { bytes -= (long) iterator.next().getValue().length * 4; iterator.remove(); }
    }
    private Map<Long, Sample> samples = new java.util.concurrent.ConcurrentHashMap<>();
    private record Sample(long revision, int style, int color) { }
    private record Key(Path directory, ClientLevel world, String worldName, String subworld, Identifier dimension,
            int left, int right, int top, int bottom, int style, int sourceResolution, long indexVersion, int width, int height) { }
    private record Snapshot(Key key, int[] pixels, long generation) { }

    WorldMapTerrainRaster(PersistentMap map) { this.map = map; }
    void render(GuiGraphicsExtractor graphics, Path directory, String worldName, String subworld, Identifier dimension,
            int left, int right, int top, int bottom, int style, int sourceResolution, float scale) {
        MapRegionPack pack = MapRegionPack.requestDirectory(directory.toFile());
        if (pack == null) return;
        float physicalScale = scale * (float) Minecraft.getInstance().getWindow().getGuiScale();
        int width = Math.max(1, Math.min(4096, Math.round((right - left + 1) * 256F * physicalScale)));
        int height = Math.max(1, Math.min(4096, Math.round((bottom - top + 1) * 256F * physicalScale)));
        Key key = new Key(directory, map.world, worldName, subworld, dimension, left, right, top, bottom, style, sourceResolution, pack.indexVersion(), width, height);
        boolean finishingColdView = requested != null && pending != null && !pending.isDone()
                && sameView(requested, key);
        if (!key.equals(requested) && !finishingColdView) {
            boolean sameContext = requested != null && requested.directory().equals(directory) && requested.world() == map.world;
            generation++;
            if (pending != null) pending.cancel(true);
            if (progress != null) progress.cancel();
            if (!sameContext) samples = new java.util.concurrent.ConcurrentHashMap<>();
            Map<Long, Sample> previous = samples;
            requested = key;
            long token = generation;
            int[] warm = cached(key);
            if (warm != null) { latest = new Snapshot(key, warm, token); pending = null; }
            else {
                WorldMapProgress.Task task = WorldMapProgress.begin("Terrain"); progress = task;
                pending = ThreadManager.viewExecutorService.submit(() -> WorldMapProgress.run(task, () -> { build(key, pack, token, previous); return null; }));
            }
        }
        Snapshot ready = latest;
        if (ready != null && ready != displayed && ready.generation() == generation && WorldMapUploadBudget.available(ready.key().width() * ready.key().height() * 4)) {
            if (texture == null || texture.getWidth() != ready.key().width() || texture.getHeight() != ready.key().height()) {
                if (texture != null) Minecraft.getInstance().getTextureManager().release(location);
                texture = new DynamicMutableTexture("World map far terrain", ready.key().width(), ready.key().height(), true);
                Minecraft.getInstance().getTextureManager().register(location, texture);
            }
            texture.setPixelsPremultipliedABGR(ready.pixels());
            long started = WorldMapUploadBudget.begin(ready.key().width() * ready.key().height() * 4);
            try { texture.upload(); } finally { WorldMapUploadBudget.end(started); }
            displayed = ready;
        }
        if (texture != null && displayed != null) {
            Key shown = displayed.key();
            // This snapshot keeps its original world bounds while the next pan/zoom is loading.
            VoxelMapGuiGraphics.blitMapRectangle(graphics, location, shown.left() * 256F, shown.top() * 256F,
                    (shown.right() - shown.left() + 1) * 256F, (shown.bottom() - shown.top() + 1) * 256F);
        }
    }
    private void build(Key key, MapRegionPack pack, long token, Map<Long, Sample> previous) {
        long[] red = new long[key.width() * key.height()], green = new long[key.width() * key.height()], blue = new long[key.width() * key.height()];
        int[] counts = new int[key.width() * key.height()];
        int processed = 0;
        long lastPublished = System.nanoTime();
        try {
            var coordinates = pack.occupiedRegions();
            int visited = 0;
            WorldMapProgress.report("Scanning regions", 0, coordinates.length);
            for (long coordinate : coordinates) {
                WorldMapProgress.report("Scanning regions", visited++, coordinates.length);
                if (token != generation || Thread.currentThread().isInterrupted()) return;
                int x = (int) (coordinate >> 32), z = (int) coordinate;
                if (x < key.left() || x > key.right() || z < key.top() || z > key.bottom()) continue;
                long revision = pack.revision(x, z);
                CachedRegion live = map.overviewRegion(key.dimension(), x, z);
                if (live != null) revision ^= live.getMostRecentChange();
                Sample sample = previous.get(coordinate);
                if (sample == null || sample.revision() != revision || sample.style() != key.style()) {
                    int[] pixel = live == null ? null : live.sampleLoadedOverview(1, key.sourceResolution());
                    if (pixel == null) pixel = new CachedRegion(map, "far overview", x + "," + z, key.world(), key.worldName(),
                            key.subworld(), x, z, key.dimension(), true, key.directory().toFile()).sampleOverview(1, key.sourceResolution());
                    sample = new Sample(revision, key.style(), pixel[0]);
                }
                // Retain partial progress across pans and saves; a new request checks revision and style.
                previous.put(coordinate, sample);
                if ((sample.color() >>> 24) != 0) {
                    int px = (int) ((long) (x - key.left()) * key.width() / ((long) key.right() - key.left() + 1));
                    int pz = (int) ((long) (z - key.top()) * key.height() / ((long) key.bottom() - key.top() + 1));
                    int i = pz * key.width() + px;
                    red[i] += sample.color() & 255; green[i] += (sample.color() >> 8) & 255; blue[i] += (sample.color() >> 16) & 255; counts[i]++;
                }
                if (++processed % 256 == 0 && System.nanoTime() - lastPublished >= 100_000_000L) {
                    publish(key, token, red, green, blue, counts); lastPublished = System.nanoTime();
                }
            }
            publish(key, token, red, green, blue, counts);
            Snapshot finished = latest;
            if (token == generation && finished != null && finished.key().equals(key)) remember(key, finished.pixels());
        } catch (Exception exception) {
            WorldMapProgress.Task task = WorldMapProgress.currentTask();
            if (task != null) task.cancel();
            if (generation == token) VoxelConstants.getLogger().warn("Could not build wide terrain overview", exception);
        }
    }
    private static boolean sameView(Key a, Key b) {
        return a.directory().equals(b.directory()) && a.world() == b.world() && a.dimension().equals(b.dimension())
                && a.left() == b.left() && a.right() == b.right() && a.top() == b.top() && a.bottom() == b.bottom()
                && a.width() == b.width() && a.height() == b.height()
                && a.style() == b.style() && a.sourceResolution() == b.sourceResolution();
    }
    private void publish(Key key, long token, long[] red, long[] green, long[] blue, int[] counts) {
        if (generation != token) return;
        int[] pixels = new int[counts.length];
        for (int i = 0; i < pixels.length; i++) if (counts[i] > 0)
            pixels[i] = 0xff000000 | (int) (red[i] / counts[i]) | ((int) (green[i] / counts[i]) << 8) | ((int) (blue[i] / counts[i]) << 16);
        latest = new Snapshot(key, pixels, token);
    }
    synchronized void suspend() {
        if (pending == null && requested == null) return;
        generation++; if (pending != null) pending.cancel(true);
        if (progress != null) progress.cancel();
        pending = null; requested = null;
    }
    boolean complete() { return requested != null && latest != null && requested.equals(latest.key()) && latest.generation() == generation && (pending == null || pending.isDone()); }
    void invalidateSamples() { suspend(); samples = new java.util.concurrent.ConcurrentHashMap<>(); }
    void clear() {
        suspend(); latest = null; displayed = null; samples = new java.util.concurrent.ConcurrentHashMap<>();
        synchronized (this) { history.clear(); }
        if (texture != null) Minecraft.getInstance().getTextureManager().release(location);
        texture = null;
    }
}
