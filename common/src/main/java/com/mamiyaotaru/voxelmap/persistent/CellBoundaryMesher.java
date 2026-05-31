package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.util.CellGrid;

import java.util.Arrays;


public final class CellBoundaryMesher {
    private CellBoundaryMesher() {
    }

    public static float[] boundarySegments(CellGrid grid, int cellChunkSize) {
        int w = grid.width;
        int h = grid.height;
        boolean[] cells = grid.cells;
        float s = cellChunkSize * 16.0F;

        float[] out = new float[64];
        int n = 0;
        for (int gz = 0; gz < h; gz++) {
            int rowBase = gz * w;
            for (int gx = 0; gx < w; gx++) {
                if (!cells[rowBase + gx]) {
                    continue;
                }
                int cx = grid.minX + gx;
                int cz = grid.minZ + gz;
                float x0 = cx * s;
                float z0 = cz * s;
                float x1 = (cx + 1) * s;
                float z1 = (cz + 1) * s;

                if (gz == 0 || !cells[rowBase - w + gx]) {            // north side
                    out = ensure(out, n + 4);
                    out[n++] = x0; out[n++] = z0; out[n++] = x1; out[n++] = z0;
                }
                if (gz == h - 1 || !cells[rowBase + w + gx]) {        // south side
                    out = ensure(out, n + 4);
                    out[n++] = x0; out[n++] = z1; out[n++] = x1; out[n++] = z1;
                }
                if (gx == 0 || !cells[rowBase + gx - 1]) {            // west side
                    out = ensure(out, n + 4);
                    out[n++] = x0; out[n++] = z0; out[n++] = x0; out[n++] = z1;
                }
                if (gx == w - 1 || !cells[rowBase + gx + 1]) {        // east side
                    out = ensure(out, n + 4);
                    out[n++] = x1; out[n++] = z0; out[n++] = x1; out[n++] = z1;
                }
            }
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    private static float[] ensure(float[] array, int needed) {
        return needed <= array.length ? array : Arrays.copyOf(array, Math.max(needed, array.length * 2));
    }
}
