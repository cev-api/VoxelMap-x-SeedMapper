package com.mamiyaotaru.voxelmap.persistent.explored;

/**
 * 32x32 grid of bits, bit index is {@code (z<<5)|x}, and the byte
 * serialization is little-endian per 64 bit word, which is byte compatible with V2 storage
 * {@code BitSet.toByteArray()} layout so V2 region files can migrate by direct copy easily
 */
public final class ExploredTile {
    public static final int SIDE = 32;
    public static final int BYTE_SIZE = 128;
    private static final int WORDS = 16;

    private final long[] words = new long[WORDS];

    private static int bitIndex(int x, int z) {
        return (z << 5) | x;
    }

    public boolean get(int x, int z) {
        int i = bitIndex(x, z);
        return (words[i >> 6] & (1L << (i & 63))) != 0L;
    }

    /** set bit and return true if newly set */
    public boolean set(int x, int z) {
        int i = bitIndex(x, z);
        int w = i >> 6;
        long mask = 1L << (i & 63);
        boolean was = (words[w] & mask) != 0L;
        words[w] |= mask;
        return !was;
    }

    /** clears bit and returns true if was set */
    public boolean clear(int x, int z) {
        int i = bitIndex(x, z);
        int w = i >> 6;
        long mask = 1L << (i & 63);
        boolean was = (words[w] & mask) != 0L;
        words[w] &= ~mask;
        return was;
    }

    public boolean isEmpty() {
        for (long w : words) {
            if (w != 0L) {
                return false;
            }
        }
        return true;
    }

    public int cardinality() {
        int c = 0;
        for (long w : words) {
            c += Long.bitCount(w);
        }
        return c;
    }

    public void orFrom(ExploredTile other) {
        for (int i = 0; i < WORDS; i++) {
            words[i] |= other.words[i];
        }
    }

    public byte[] toBytes() {
        byte[] out = new byte[BYTE_SIZE];
        for (int w = 0; w < WORDS; w++) {
            long v = words[w];
            int base = w << 3;
            for (int b = 0; b < 8; b++) {
                out[base + b] = (byte) (v >>> (8 * b));
            }
        }
        return out;
    }

    public static ExploredTile fromBytes(byte[] data) {
        ExploredTile tile = new ExploredTile();
        int n = Math.min(BYTE_SIZE, data.length);
        for (int i = 0; i < n; i++) {
            tile.words[i >> 3] |= (data[i] & 0xFFL) << (8 * (i & 7));
        }
        return tile;
    }
}
