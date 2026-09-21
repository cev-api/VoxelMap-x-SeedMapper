package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Bounded asynchronous biome sampling for the standalone SeedMapper map.
 * Cubiomes' structure callback and native generator state are process-wide,
 * so this intentionally uses one worker and the shared native lock.
 */
public final class SeedMapperMapBiomeSampler {
    public static final int SAMPLE_WIDTH = 128;
    public static final int SAMPLE_HEIGHT = 96;
    private static final int MAX_CACHE_ENTRIES = 8;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VoxelMap SeedMapper Biome Map");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object CACHE_LOCK = new Object();
    private static final LinkedHashMap<CacheKey, SoftReference<Sample>> CACHE =
            new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75F, true);
    private static volatile int[] biomePalette;

    private SeedMapperMapBiomeSampler() {
    }

    public static Future<Sample> request(CacheKey key) {
        synchronized (CACHE_LOCK) {
            SoftReference<Sample> reference = CACHE.get(key);
            Sample cached = reference == null ? null : reference.get();
            if (cached != null) {
                return java.util.concurrent.CompletableFuture.completedFuture(cached);
            }
        }
        return WORKER.submit(() -> sample(key));
    }

    private static Sample sample(CacheKey key) {
        synchronized (CACHE_LOCK) {
            SoftReference<Sample> reference = CACHE.get(key);
            Sample cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
        }

        Sample result;
        try {
            SeedMapperNative.ensureLoaded();
            synchronized (SeedMapperNative.cubiomesLock()) {
                result = sampleLocked(key);
            }
        } catch (Throwable ignored) {
            result = Sample.empty(key);
        }

        synchronized (CACHE_LOCK) {
            CACHE.put(key, new SoftReference<>(result));
            while (CACHE.size() > MAX_CACHE_ENTRIES) {
                CACHE.remove(CACHE.keySet().iterator().next());
            }
        }
        return result;
    }

    private static Sample sampleLocked(CacheKey key) {
        int[] colors = new int[SAMPLE_WIDTH * SAMPLE_HEIGHT];
        int sampleY = key.dimension() == Cubiomes.DIM_END() ? 0 : Math.floorDiv(key.biomeY(), 4);
        int[] palette = loadBiomePalette();
        double spanX = Math.max(1.0D, (double) key.maxX() - key.minX());
        double spanZ = Math.max(1.0D, (double) key.maxZ() - key.minZ());

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, key.mcVersion(), key.generatorFlags());
            Cubiomes.applySeed(generator, key.dimension(), key.seed());
            for (int z = 0; z < SAMPLE_HEIGHT; z++) {
                int blockZ = toBlockCoordinate(key.minZ() + (z + 0.5D) * spanZ / SAMPLE_HEIGHT);
                int quartZ = blockZ >> 2;
                int row = z * SAMPLE_WIDTH;
                for (int x = 0; x < SAMPLE_WIDTH; x++) {
                    int blockX = toBlockCoordinate(key.minX() + (x + 0.5D) * spanX / SAMPLE_WIDTH);
                    int biome = Cubiomes.getBiomeAt(generator, 4, blockX >> 2, sampleY, quartZ);
                    int rgb = biome >= 0 && biome < palette.length ? palette[biome] : 0x39434D;
                    colors[row + x] = 0xFF000000 | rgb;
                }
            }
        }
        return new Sample(key, colors);
    }

    private static int toBlockCoordinate(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }

    private static int[] loadBiomePalette() {
        int[] cached = biomePalette;
        if (cached != null) {
            return cached;
        }
        synchronized (CACHE_LOCK) {
            cached = biomePalette;
            if (cached != null) {
                return cached;
            }
            int[] loaded = loadBiomePaletteLocked();
            biomePalette = loaded;
            return loaded;
        }
    }

    private static int[] loadBiomePaletteLocked() {
        int[] palette = new int[256];
        MemoryLayout rgbLayout = MemoryLayout.sequenceLayout(3, Cubiomes.C_CHAR);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativePalette = arena.allocate(rgbLayout, palette.length);
            Cubiomes.initBiomeColors(nativePalette);
            for (int biome = 0; biome < palette.length; biome++) {
                MemorySegment color = nativePalette.asSlice((long) biome * rgbLayout.byteSize());
                int red = color.getAtIndex(Cubiomes.C_CHAR, 0) & 0xFF;
                int green = color.getAtIndex(Cubiomes.C_CHAR, 1) & 0xFF;
                int blue = color.getAtIndex(Cubiomes.C_CHAR, 2) & 0xFF;
                palette[biome] = (red << 16) | (green << 8) | blue;
            }
        }
        return palette;
    }

    public record CacheKey(long seed, int dimension, int mcVersion, int generatorFlags,
                           int minX, int maxX, int minZ, int maxZ, int biomeY) {
    }

    public record Sample(CacheKey key, int[] colors) {
        public Sample {
            colors = colors == null ? new int[0] : colors.clone();
        }

        public static Sample empty(CacheKey key) {
            return new Sample(key, new int[0]);
        }

        public boolean available() {
            return this.colors.length == SAMPLE_WIDTH * SAMPLE_HEIGHT;
        }

        public int colorAt(int x, int z) {
            return this.colors[z * SAMPLE_WIDTH + x];
        }
    }
}
