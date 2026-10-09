package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.rendering.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.textures.DynamicMutableTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Keeps dense exact overlays in reusable display-resolution textures instead of resubmitting millions of vertices. */
final class WorldMapRasterLayers {
    record Key(Object source, WorldMapGeometry.Bounds bounds, float thickness, boolean nodes, int color, int width, int height, boolean smooth) { }
    private final WorldMapViewCache<Key, WorldMapRaster.Image> cache = new WorldMapViewCache<Key, WorldMapRaster.Image>(64L << 20, WorldMapRaster.Image::bytes).trackProgress(GuiPersistentMap::loadingLayerLabel);
    private final Map<String, Texture> textures = new HashMap<>();
    private static final class Texture {
        final Identifier location = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "worldmap/overlay/" + java.util.UUID.randomUUID());
        DynamicMutableTexture image;
        WorldMapRaster.Image displayed;
        long usedFrame;
    }
    private long frame;
    void setBudget(long bytes) { cache.setBudget(bytes); }
    void beginFrame() {
        frame++; cache.beginFrame();
        textures.entrySet().removeIf(entry -> {
            if (frame - entry.getValue().usedFrame <= 120) return false;
            release(entry.getValue()); return true;
        });
    }
    boolean contains(String layer, Key key) { return cache.contains(layer, key); }
    boolean drawCached(GuiGraphicsExtractor graphics, String layer, Key key) {
        if (!cache.contains(layer, key)) return false;
        draw(graphics, layer, key, () -> { throw new IllegalStateException("Cached image disappeared"); });
        return true;
    }
    void draw(GuiGraphicsExtractor graphics, String layer, Key key, Supplier<WorldMapRaster.Image> builder) {
        WorldMapRaster.Image ready = cache.get(layer, key, true, builder, (a, b) -> a.bounds().equals(b.bounds())
                && a.thickness() == b.thickness() && a.nodes() == b.nodes() && a.color() == b.color()
                && a.width() == b.width() && a.height() == b.height() && a.smooth() == b.smooth());
        Texture texture = textures.computeIfAbsent(layer, ignored -> new Texture()); texture.usedFrame = frame;
        if (ready != null && ready != texture.displayed && WorldMapUploadBudget.available(ready.width() * ready.height() * 4)) {
            if (texture.image == null || texture.image.getPixels().getWidth() != ready.width() || texture.image.getPixels().getHeight() != ready.height()) {
                release(texture);
                texture.image = new DynamicMutableTexture("World map exact overlay", ready.width(), ready.height(), true);
                Minecraft.getInstance().getTextureManager().register(texture.location, texture.image);
            }
            texture.image.setPixelsPremultipliedABGR(ready.pixels());
            long started = WorldMapUploadBudget.begin(ready.width() * ready.height() * 4);
            try { texture.image.upload(); } finally { WorldMapUploadBudget.end(started); }
            texture.displayed = ready;
        }
        if (texture.displayed != null) {
            var bounds = texture.displayed.bounds();
            VoxelMapGuiGraphics.blitMapOverlay(graphics, texture.location, bounds.minX(), bounds.minZ(),
                    bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ());
        }
    }
    private static void release(Texture texture) {
        if (texture.image != null) Minecraft.getInstance().getTextureManager().release(texture.location);
        texture.image = null; texture.displayed = null;
    }
    void clear() { cache.clear(); for (Texture texture : textures.values()) release(texture); textures.clear(); }
}
