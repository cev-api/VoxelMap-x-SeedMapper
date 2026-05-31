package com.mamiyaotaru.voxelmap.persistent.explored;

import java.util.function.LongFunction;

final class ExploredTileMap {
    // key 0 == tile (0,0); kept in a dedicated slot so 0 can double as the "empty" sentinel in the table
    private static final long EMPTY_KEY = 0L;
    private static final float LOAD_FACTOR = 0.6f;

    private long[] keys;
    private ExploredTile[] values;
    private int mask;
    private int size;
    private int threshold;
    private boolean hasZeroKey;
    private ExploredTile zeroValue;

    ExploredTileMap() {
        allocate(16);
    }

    private void allocate(int capacity) {
        keys = new long[capacity];
        values = new ExploredTile[capacity];
        mask = capacity - 1;
        threshold = (int) (capacity * LOAD_FACTOR);
        size = 0;
    }

    private static int slot(long key, int mask) {
        long h = key * 0x9E3779B97F4A7C15L; // 2^64 / golden ratio, odd -> a bijection on 64 bits
        return (int) (h >>> 32) & mask;     // high bits are the most thoroughly mixed
    }

    ExploredTile get(long key) {
        if (key == EMPTY_KEY) {
            return zeroValue;
        }
        int i = slot(key, mask);
        while (true) {
            long k = keys[i];
            if (k == key) {
                return values[i];
            }
            if (k == EMPTY_KEY) {
                return null;
            }
            i = (i + 1) & mask;
        }
    }

    ExploredTile computeIfAbsent(long key, LongFunction<ExploredTile> supplier) {
        if (key == EMPTY_KEY) {
            if (!hasZeroKey) {
                hasZeroKey = true;
                zeroValue = supplier.apply(key);
            }
            return zeroValue;
        }
        int i = slot(key, mask);
        while (true) {
            long k = keys[i];
            if (k == key) {
                return values[i];
            }
            if (k == EMPTY_KEY) {
                ExploredTile value = supplier.apply(key);
                keys[i] = key;
                values[i] = value;
                if (++size >= threshold) {
                    resize();
                }
                return value;
            }
            i = (i + 1) & mask;
        }
    }

    int size() {
        return size + (hasZeroKey ? 1 : 0);
    }

    interface EntryVisitor {
        void accept(long key, ExploredTile value);
    }

    void forEach(EntryVisitor visitor) {
        if (hasZeroKey) {
            visitor.accept(EMPTY_KEY, zeroValue);
        }
        long[] k = keys;
        ExploredTile[] v = values;
        for (int i = 0; i < k.length; i++) {
            if (k[i] != EMPTY_KEY) {
                visitor.accept(k[i], v[i]);
            }
        }
    }

    private void resize() {
        long[] oldKeys = keys;
        ExploredTile[] oldValues = values;
        allocate(oldKeys.length << 1);
        for (int j = 0; j < oldKeys.length; j++) {
            long k = oldKeys[j];
            if (k != EMPTY_KEY) {
                int i = slot(k, mask);
                while (keys[i] != EMPTY_KEY) {
                    i = (i + 1) & mask;
                }
                keys[i] = k;
                values[i] = oldValues[j];
                size++;
            }
        }
    }
}
