package com.mamiyaotaru.voxelmap.rendering;

import java.util.function.BiConsumer;

/** Quad-aligned draws stay well below BufferBuilder's 24-bit vertex-count limit. */
public final class WorldMapGeometryBatch {
    public static final int MAX_QUADS = 262_144; // 1,048,576 vertices per isolated draw
    private WorldMapGeometryBatch() { }
    public static void forEach(int count, BiConsumer<Integer, Integer> emit) {
        if (count < 0) throw new IllegalArgumentException("Negative quad count");
        for (int first = 0; first < count;) {
            int length = Math.min(MAX_QUADS, count - first);
            emit.accept(first, length);
            first += length;
        }
    }
}
