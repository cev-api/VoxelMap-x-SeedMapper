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
            cells[gz * width + gx] = true;
        }
    }

    public boolean get(int x, int z) {
        int gx = x - minX;
        int gz = z - minZ;
        return gx >= 0 && gx < width && gz >= 0 && gz < height && cells[gz * width + gx];
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
