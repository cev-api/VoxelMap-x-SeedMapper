package com.mamiyaotaru.voxelmap.persistent;

import java.util.Arrays;

/** Immutable, clipped quad geometry. Arrays are owned by this snapshot across extracted frames. */
final class WorldMapGeometry {
    record Bounds(float minX, float maxX, float minZ, float maxZ) {
        boolean contains(float x, float z, float padding) {
            return x >= minX - padding && x <= maxX + padding && z >= minZ - padding && z <= maxZ + padding;
        }
    }

    record Quads(float[] vertices, int count) { long bytes() { return (long) vertices.length * 4; } }

    static Quads lines(ExploredLineMesher.Result mesh, Bounds bounds, float thickness, boolean linkedNodes) {
        Builder builder = new Builder();
        float[] segments = mesh.segments();
        for (int i = 0; i < mesh.segmentCount(); i++) {
            if ((i & 1023) == 0 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int o = i << 2;
            builder.line(segments[o], segments[o + 1], segments[o + 2], segments[o + 3], thickness, bounds);
        }
        float half = thickness * 0.55F;
        for (int i = 0; i < mesh.nodeCount(); i++) {
            if ((i & 1023) == 0 && Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (!linkedNodes && mesh.nodeLinked()[i]) continue;
            float x = mesh.nodeCoords()[i << 1], z = mesh.nodeCoords()[(i << 1) + 1];
            if (bounds.contains(x, z, half)) builder.rect(x - half, z - half, x + half, z + half);
        }
        return builder.build();
    }

    static final class Builder {
        private float[] coords = new float[128];
        private int size;

        void quad(float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz) {
            if (size + 8 > coords.length) coords = Arrays.copyOf(coords, coords.length * 2);
            coords[size++] = ax; coords[size++] = az; coords[size++] = bx; coords[size++] = bz;
            coords[size++] = cx; coords[size++] = cz; coords[size++] = dx; coords[size++] = dz;
        }

        void rect(float x0, float z0, float x1, float z1) { quad(x0, z0, x0, z1, x1, z1, x1, z0); }

        // Liang-Barsky clipping, expanded for the stroke's caps/width. Crossing lines are retained.
        void line(float x0, float z0, float x1, float z1, float thickness, Bounds bounds) {
            float half = thickness * 0.5F;
            float dx = x1 - x0, dz = z1 - z0;
            float start = 0, end = 1;
            for (int edge = 0; edge < 4; edge++) {
                float p = switch (edge) { case 0 -> -dx; case 1 -> dx; case 2 -> -dz; default -> dz; };
                float q = switch (edge) {
                    case 0 -> x0 - bounds.minX + half; case 1 -> bounds.maxX - x0 + half;
                    case 2 -> z0 - bounds.minZ + half; default -> bounds.maxZ - z0 + half;
                };
                if (p == 0) { if (q < 0) return; }
                else {
                    float t = q / p;
                    if (p < 0) start = Math.max(start, t); else end = Math.min(end, t);
                    if (start > end) return;
                }
            }
            float ax = x0 + start * dx, az = z0 + start * dz;
            float bx = x0 + end * dx, bz = z0 + end * dz;
            float length = (float) Math.hypot(dx, dz);
            if (length < 0.001F) { rect(ax - half, az - half, ax + half, az + half); return; }
            float nx = -dz / length * half, nz = dx / length * half;
            float capX = dx / length * half, capZ = dz / length * half;
            quad(ax - capX - nx, az - capZ - nz, ax - capX + nx, az - capZ + nz,
                    bx + capX + nx, bz + capZ + nz, bx + capX - nx, bz + capZ - nz);
        }

        Quads build() { return new Quads(Arrays.copyOf(coords, size), size >> 3); }
    }
}
