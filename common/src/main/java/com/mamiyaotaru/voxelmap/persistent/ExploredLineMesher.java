package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.util.CellGrid;

import java.util.Arrays;

final class ExploredLineMesher {
    private static final float[] EMPTY_FLOATS = new float[0];
    private static final boolean[] EMPTY_BOOLS = new boolean[0];

    private ExploredLineMesher() {
    }

    /**
     * @param segments     flat (x1, z1, x2, z2) per segment; only the first {@code 4*segmentCount} are valid
     * @param segmentCount number of line segments
     * @param nodeCoords   flat (x, z) per node; only the first {@code 2*nodeCount} are valid
     * @param nodeLinked   per-node "has a neighbour" flag; only the first {@code nodeCount} are valid
     * @param nodeCount    number of centre-dot nodes (0 unless built for literal-line mode)
     */
    record Result(float[] segments, int segmentCount, float[] nodeCoords, boolean[] nodeLinked, int nodeCount) {
        static final Result EMPTY = new Result(EMPTY_FLOATS, 0, EMPTY_FLOATS, EMPTY_BOOLS, 0);
    }

    /**
     * @param buildNodes  build the centre-dot node arrays (only literal-line mode renders them)
     * @param segmentHint prior segment count, to pre-size the segment buffer
     * @param nodeHint    prior node count, to pre-size the node buffers
     */
    static Result build(CellGrid grid, int cellChunkSize, boolean buildNodes, int segmentHint, int nodeHint) {
        int w = grid.width;
        int h = grid.height;
        boolean[] cells = grid.cells;
        int minX = grid.minX;
        int minZ = grid.minZ;

        float[] segs = new float[Math.max(16, (segmentHint + 16) * 4)];
        int segFloats = 0;
        float[] nodeCoords = buildNodes ? new float[Math.max(16, (nodeHint + 16) * 2)] : EMPTY_FLOATS;
        boolean[] nodeLinked = buildNodes ? new boolean[Math.max(16, nodeHint + 16)] : EMPTY_BOOLS;
        int nodeCount = 0;

        for (int gz = 0; gz < h; gz++) {
            int rowBase = gz * w;
            for (int gx = 0; gx < w; gx++) {
                if (!cells[rowBase + gx]) {
                    continue;
                }
                int idx = rowBase + gx;
                boolean left;
                boolean right;
                boolean up;
                boolean down;
                boolean upLeft;
                boolean upRight;
                boolean downLeft;
                boolean downRight;
                if (gx > 0 && gx + 1 < w && gz > 0 && gz + 1 < h) {
                    left = cells[idx - 1];
                    right = cells[idx + 1];
                    up = cells[idx - w];
                    down = cells[idx + w];
                    upLeft = cells[idx - w - 1];
                    upRight = cells[idx - w + 1];
                    downLeft = cells[idx + w - 1];
                    downRight = cells[idx + w + 1];
                } else {
                    left = at(cells, w, h, gx - 1, gz);
                    right = at(cells, w, h, gx + 1, gz);
                    up = at(cells, w, h, gx, gz - 1);
                    down = at(cells, w, h, gx, gz + 1);
                    upLeft = at(cells, w, h, gx - 1, gz - 1);
                    upRight = at(cells, w, h, gx + 1, gz - 1);
                    downLeft = at(cells, w, h, gx - 1, gz + 1);
                    downRight = at(cells, w, h, gx + 1, gz + 1);
                }

                int cellX = minX + gx;
                int cellZ = minZ + gz;

                boolean seHere = downRight && !right && !down;
                boolean neHere = upRight && !right && !up;

                if (buildNodes) {
                    // linked: any 4-neighbour, or a SE/NE diagonal link touching this cell (here, or the
                    // diagonal predecessor whose far end is this cell).
                    boolean linked = left || right || up || down
                            || seHere
                            || (upLeft && !up && !left)
                            || neHere
                            || (downLeft && !down && !left);
                    if (nodeCount + 1 > nodeLinked.length) {
                        nodeLinked = Arrays.copyOf(nodeLinked, nodeLinked.length * 2);
                    }
                    if ((nodeCount << 1) + 2 > nodeCoords.length) {
                        nodeCoords = Arrays.copyOf(nodeCoords, nodeCoords.length * 2);
                    }
                    nodeCoords[nodeCount << 1] = cellCenter(cellX, cellChunkSize);
                    nodeCoords[(nodeCount << 1) + 1] = cellCenter(cellZ, cellChunkSize);
                    nodeLinked[nodeCount] = linked;
                    nodeCount++;
                }

                // horizontal (east) run: starts at a cell with no west neighbour
                if (right && !left) {
                    int endGx = gx + 1;
                    while (at(cells, w, h, endGx + 1, gz)) {
                        endGx++;
                    }
                    segs = ensure(segs, segFloats + 4);
                    segFloats = writeSegment(segs, segFloats, cellX, cellZ, minX + endGx, cellZ, cellChunkSize);
                }
                // vertical (south) run
                if (down && !up) {
                    int endGz = gz + 1;
                    while (at(cells, w, h, gx, endGz + 1)) {
                        endGz++;
                    }
                    segs = ensure(segs, segFloats + 4);
                    segFloats = writeSegment(segs, segFloats, cellX, cellZ, cellX, minZ + endGz, cellChunkSize);
                }
                // SE diagonal run: starts where the up-left diagonal predecessor isn't a link. The
                // predecessor link seLink(x-1,z-1) needs that cell set, so !seLink(x-1,z-1) == !upLeft ||
                // up || left
                if (seHere && (!upLeft || up || left)) {
                    int endGx = gx;
                    int endGz = gz;
                    while (seLink(cells, w, h, endGx, endGz)) {
                        endGx++;
                        endGz++;
                    }
                    segs = ensure(segs, segFloats + 4);
                    segFloats = writeSegment(segs, segFloats, cellX, cellZ, minX + endGx, minZ + endGz, cellChunkSize);
                }
                // NE diagonal run: starts where the down-left diagonal predecessor isn't a link.
                if (neHere && (!downLeft || down || left)) {
                    int endGx = gx;
                    int endGz = gz;
                    while (neLink(cells, w, h, endGx, endGz)) {
                        endGx++;
                        endGz--;
                    }
                    segs = ensure(segs, segFloats + 4);
                    segFloats = writeSegment(segs, segFloats, cellX, cellZ, minX + endGx, minZ + endGz, cellChunkSize);
                }
            }
        }

        return new Result(segs, segFloats >> 2, nodeCoords, nodeLinked, nodeCount);
    }

    private static int writeSegment(float[] segs, int offset, int startX, int startZ, int endX, int endZ, int cellChunkSize) {
        segs[offset] = cellCenter(startX, cellChunkSize);
        segs[offset + 1] = cellCenter(startZ, cellChunkSize);
        segs[offset + 2] = cellCenter(endX, cellChunkSize);
        segs[offset + 3] = cellCenter(endZ, cellChunkSize);
        return offset + 4;
    }

    private static float[] ensure(float[] array, int needed) {
        return needed <= array.length ? array : Arrays.copyOf(array, Math.max(needed, array.length * 2));
    }

    private static boolean at(boolean[] cells, int w, int h, int gx, int gz) {
        return gx >= 0 && gx < w && gz >= 0 && gz < h && cells[gz * w + gx];
    }

    private static boolean seLink(boolean[] cells, int w, int h, int gx, int gz) {
        return at(cells, w, h, gx, gz) && at(cells, w, h, gx + 1, gz + 1)
                && !at(cells, w, h, gx + 1, gz) && !at(cells, w, h, gx, gz + 1);
    }

    private static boolean neLink(boolean[] cells, int w, int h, int gx, int gz) {
        return at(cells, w, h, gx, gz) && at(cells, w, h, gx + 1, gz - 1)
                && !at(cells, w, h, gx + 1, gz) && !at(cells, w, h, gx, gz - 1);
    }

    private static float cellCenter(int cellCoordinate, int cellChunkSize) {
        return (cellCoordinate * cellChunkSize + cellChunkSize * 0.5F) * 16.0F;
    }
}
