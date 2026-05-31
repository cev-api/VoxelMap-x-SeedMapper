package com.mamiyaotaru.voxelmap.persistent.explored;

final class LongHashSet {
    private static final long EMPTY_KEY = 0L; // key 0 tracked separately so it can double as the empty sentinel
    private static final float LOAD_FACTOR = 0.6f;

    private long[] keys;
    private int mask;
    private int size;
    private int threshold;
    private boolean hasZeroKey;

    LongHashSet() {
        allocate(64);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        mask = capacity - 1;
        threshold = (int) (capacity * LOAD_FACTOR);
        size = 0;
    }

    private static int slot(long key, int mask) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 32) & mask;
    }

    boolean add(long key) {
        if (key == EMPTY_KEY) {
            if (hasZeroKey) {
                return false;
            }
            hasZeroKey = true;
            return true;
        }
        int i = slot(key, mask);
        while (true) {
            long k = keys[i];
            if (k == key) {
                return false;
            }
            if (k == EMPTY_KEY) {
                keys[i] = key;
                if (++size >= threshold) {
                    resize();
                }
                return true;
            }
            i = (i + 1) & mask;
        }
    }

    private void resize() {
        long[] oldKeys = keys;
        allocate(oldKeys.length << 1); // leaves hasZeroKey untouched
        for (long k : oldKeys) {
            if (k != EMPTY_KEY) {
                int i = slot(k, mask);
                while (keys[i] != EMPTY_KEY) {
                    i = (i + 1) & mask;
                }
                keys[i] = k;
                size++;
            }
        }
    }
}
