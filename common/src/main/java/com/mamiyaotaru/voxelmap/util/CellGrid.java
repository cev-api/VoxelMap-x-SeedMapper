package com.mamiyaotaru.voxelmap.util;

/**
 * flat boolean grid of map cells offset to a bounding box. Replaces {@code HashSet<Long>} of packed
 * cell keys for the map overlays: the cell key {@code (x<<32)^z} has {@code Long.hashCode == x ^ z},
 * which collides along every anti-diagonal and degenerates the HashSet buckets into red-black trees on a
 * dense grid, flat array gives O(1) mark/get with no boxing, hashing, or treeification
 *
 */
public final class CellGrid {
    public final int minX;
    public final int minZ;
    public final int width;
    public final int height;
    public final boolean[] cells;
    private final java.util.BitSet occupied = new java.util.BitSet();

    public CellGrid(int minX, int minZ, int width, int height) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.cells = new boolean[this.width * this.height];
    }

    public void mark(int x, int z) {
        int gx = x - minX;
        int gz = z - minZ;
        if (gx >= 0 && gx < width && gz >= 0 && gz < height) {
            int index = gz * width + gx;
            cells[index] = true;
            occupied.set(index);
        }
    }

    public boolean get(int x, int z) {
        int gx = x - minX;
        int gz = z - minZ;
        return gx >= 0 && gx < width && gz >= 0 && gz < height && cells[gz * width + gx];
    }

    public int nextOccupied(int from) {
        int index = occupied.nextSetBit(from);
        while (index >= 0 && !cells[index]) index = occupied.nextSetBit(index + 1);
        return index;
    }

    public CellGrid coarsen(int factor) {
        int x = Math.floorDiv(minX, factor), z = Math.floorDiv(minZ, factor);
        CellGrid coarse = new CellGrid(x, z, Math.floorDiv(minX + width - 1, factor) - x + 1,
                Math.floorDiv(minZ + height - 1, factor) - z + 1);
        for (int i = nextOccupied(0); i >= 0; i = nextOccupied(i + 1)) {
            coarse.mark(Math.floorDiv(minX + i % width, factor), Math.floorDiv(minZ + i / width, factor));
        }
        return coarse;
    }

    public boolean isEmpty() {
        for (boolean cell : cells) {
            if (cell) {
                return false;
            }
        }
        return true;
    }
}
