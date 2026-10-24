package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;
import com.github.cubiomes.TerrainNoise;
import com.mamiyaotaru.voxelmap.persistent.PersistentMapSettingsManager;
import com.mamiyaotaru.voxelmap.persistent.SeedPreviewTerrainSampling;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Cached, asynchronous SeedMap rendering for the in-game minimap. */
public final class SeedMapperMinimapPreviewSampler {
    public static final int SIZE = 128;
    private static final int MAX_CACHE_ENTRIES = 8;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Voxelmap SeedMapper Minimap");
        thread.setDaemon(true);
        return thread;
    });
    private static final LinkedHashMap<Key, int[]> CACHE = new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75F, true);
    private static final Map<Integer, int[]> PALETTES = new HashMap<>();

    private SeedMapperMinimapPreviewSampler() { }

    public static Future<Sample> request(Key key) {
        synchronized (CACHE) {
            int[] cached = CACHE.get(key);
            if (cached != null) return java.util.concurrent.CompletableFuture.completedFuture(new Sample(key, cached));
        }
        return WORKER.submit(() -> sample(key));
    }

    private static Sample sample(Key key) {
        synchronized (CACHE) {
            int[] cached = CACHE.get(key);
            if (cached != null) return new Sample(key, cached);
        }
        int[] colors;
        try {
            SeedMapperNative.ensureLoaded();
            synchronized (SeedMapperNative.cubiomesLock()) {
                colors = sampleLocked(key);
            }
        } catch (Throwable error) {
            colors = new int[SIZE * SIZE];
        }
        synchronized (CACHE) {
            CACHE.put(key, colors);
            while (CACHE.size() > MAX_CACHE_ENTRIES) CACHE.remove(CACHE.keySet().iterator().next());
        }
        return new Sample(key, colors);
    }

    private static int[] sampleLocked(Key key) {
        int[] palette = PALETTES.computeIfAbsent(key.mcVersion(), SeedMapperMinimapPreviewSampler::loadBiomePalette);
        int[] colors = new int[SIZE * SIZE];
        int sampleY = key.dimension() == Cubiomes.DIM_END() ? 0 : Math.floorDiv(key.biomeY(), 4);
        double spanX = (double) key.maxX() - key.minX();
        double spanZ = (double) key.maxZ() - key.minZ();
        int[] biomes = new int[colors.length];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, key.mcVersion(), key.generatorFlags());
            Cubiomes.applySeed(generator, key.dimension(), key.seed());
            for (int z = 0; z < SIZE; z++) {
                int blockZ = (int) Math.floor(key.minZ() + (z + 0.5D) * spanZ / SIZE);
                for (int x = 0; x < SIZE; x++) {
                    int blockX = (int) Math.floor(key.minX() + (x + 0.5D) * spanX / SIZE);
                    int biome = Cubiomes.getBiomeAt(generator, 4, blockX >> 2, sampleY, blockZ >> 2);
                    biomes[z * SIZE + x] = biome;
                }
            }
        }

        int[] heights = null;
        if (key.terrainEnabled()) heights = sampleHeights(key);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = z * SIZE + x;
                int biome = biomes[index];
                int rgb = biome >= 0 && biome < palette.length ? palette[biome] : 0x39434D;
                int color = 0x96000000 | rgb;
                if (heights != null) color = terrainStyle(color, biome, heights, x, z, key.style(), key.contourStrength());
                colors[index] = color;
            }
        }
        return colors;
    }

    private static int[] loadBiomePalette(int mcVersion) {
        int[] result = new int[256];
        String[] names = new String[result.length];
        for (int id = 0; id < names.length; id++) {
            try {
                MemorySegment name = Cubiomes.biome2str(mcVersion, id);
                names[id] = name == null || name.address() == 0L ? null : name.getString(0);
            } catch (RuntimeException ignored) {
                names[id] = null;
            }
        }
        var level = com.mamiyaotaru.voxelmap.util.GameVariableAccessShim.getWorld();
        for (int id = 0; id < result.length; id++) {
            String name = names[id];
            int fallback = fallbackBiomeColor(name == null ? Integer.toString(id) : name);
            try {
                net.minecraft.resources.Identifier biomeId = parseBiomeId(name);
                if (biomeId != null && level != null) {
                    var biome = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).get(biomeId);
                    if (biome.isPresent()) fallback = com.mamiyaotaru.voxelmap.util.BiomeRepository.getBiomeColor(biome.get().value());
                }
            } catch (RuntimeException ignored) { }
            result[id] = fallback;
        }
        return result;
    }

    private static net.minecraft.resources.Identifier parseBiomeId(String name) {
        if (name == null || name.isBlank()) return null;
        var parsed = net.minecraft.resources.Identifier.tryParse(name);
        if (parsed != null) return parsed;
        if (name.contains("/")) {
            parsed = net.minecraft.resources.Identifier.tryParse(name.replace('/', ':'));
            if (parsed != null) return parsed;
            return net.minecraft.resources.Identifier.tryParse("minecraft:" + name.substring(name.lastIndexOf('/') + 1));
        }
        return net.minecraft.resources.Identifier.tryParse("minecraft:" + name);
    }

    private static int fallbackBiomeColor(String name) {
        int hash = name == null ? 0 : name.hashCode();
        return (64 + (hash & 0x5F)) << 16 | (64 + (hash >> 8 & 0x5F)) << 8 | (64 + (hash >> 16 & 0x5F));
    }

    private static int[] sampleHeights(Key key) {
        var layout = SeedPreviewTerrainSampling.layout(key.minX(), key.maxX(), key.minZ(), key.maxZ());
        int rowWidth = layout.width() + 1;
        int[] coarse = new int[rowWidth * (layout.height() + 1)];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment params = TerrainNoise.allocate(arena);
            Cubiomes.setupTerrainNoise(params, key.mcVersion(), key.generatorFlags());
            Cubiomes.initTerrainNoise(params, key.seed(), key.dimension());
            for (int z = 0; z <= layout.height(); z++) {
                for (int x = 0; x <= layout.width(); x++) {
                    coarse[z * rowWidth + x] = Cubiomes.samplePreliminarySurfaceLevel(
                            params, layout.minX() + x * layout.step(), layout.minZ() + z * layout.step());
                }
            }
        }
        int[] result = new int[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                result[z * SIZE + x] = SeedPreviewTerrainSampling.interpolate(layout, coarse,
                        key.minX() + (x + 0.5D) * (key.maxX() - key.minX()) / SIZE,
                        key.minZ() + (z + 0.5D) * (key.maxZ() - key.minZ()) / SIZE);
            }
        }
        return result;
    }

    private static int terrainStyle(int color, int biomeId, int[] heights, int x, int z,
                                    PersistentMapSettingsManager.SeedMapStyle style, float contourStrength) {
        int index = z * SIZE + x;
        int height = heights[index];
        int left = x > 0 ? heights[index - 1] : height;
        int up = z > 0 ? heights[index - SIZE] : height;
        int base = (Cubiomes.isOceanic(biomeId) != 0 ? waterPaletteColor(height) : landPaletteColor(height));
        color = switch (style.palette()) {
            case BIOME -> color;
            case HEIGHT -> 0x96000000 | base;
            case TOPOGRAPHIC -> blend(color, 0x96000000 | base, 0.65F);
        };
        int shade = Math.max(-36, Math.min(36, (height - left) * 4 + (height - up) * 3));
        color = adjustBrightness(color, shade);
        if (style.drawsContours()
                && (Math.floorDiv(height, 16) != Math.floorDiv(left, 16)
                || Math.floorDiv(height, 16) != Math.floorDiv(up, 16))) {
            color = blend(color, 0xD0000000, contourStrength);
        }
        return color;
    }

    private static int waterPaletteColor(int height) {
        float t = Math.max(0.0F, Math.min(1.0F, (height - 20) / 44.0F));
        return blend(0x0A2342, 0x3D86C6, t) & 0x00FFFFFF;
    }

    private static int landPaletteColor(int height) {
        if (height < 50) return 0x9DBE74;
        if (height < 70) return 0x5DA34E;
        if (height < 95) return 0x4E8A45;
        if (height < 125) return 0xA99268;
        if (height < 160) return 0x8E8E83;
        return 0xE8E8E8;
    }

    private static int adjustBrightness(int color, int amount) {
        int alpha = color >>> 24;
        int red = Math.max(0, Math.min(255, (color >>> 16 & 255) + amount));
        int green = Math.max(0, Math.min(255, (color >>> 8 & 255) + amount));
        int blue = Math.max(0, Math.min(255, (color & 255) + amount));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int blend(int base, int overlay, float alpha) {
        int outAlpha = base >>> 24;
        int red = Math.round((base >>> 16 & 255) * (1.0F - alpha) + (overlay >>> 16 & 255) * alpha);
        int green = Math.round((base >>> 8 & 255) * (1.0F - alpha) + (overlay >>> 8 & 255) * alpha);
        int blue = Math.round((base & 255) * (1.0F - alpha) + (overlay & 255) * alpha);
        return outAlpha << 24 | red << 16 | green << 8 | blue;
    }

    public record Key(long seed, int dimension, int mcVersion, int generatorFlags,
                      int minX, int maxX, int minZ, int maxZ, int biomeY,
                      PersistentMapSettingsManager.SeedMapStyle style, float contourStrength,
                      boolean terrainEnabled) { }
    public record Sample(Key key, int[] colors) {
        public Sample { colors = colors.clone(); }
    }
}
