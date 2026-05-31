package com.mamiyaotaru.voxelmap.persistent.explored;

public final class MortonCode {
    private static final int X_MASK = 0x155; // even bits 0,2,4,6,8
    private static final int Z_MASK = 0x2AA; // odd bits 1,3,5,7,9

    private MortonCode() {
    }

    /** interleave x and z (each 0..31) into a 10 bit slot index (0..1023) */
    public static int encode(int x, int z) {
        return Integer.expand(x, X_MASK) | Integer.expand(z, Z_MASK);
    }

    public static int decodeX(int code) {
        return Integer.compress(code, X_MASK);
    }

    public static int decodeZ(int code) {
        return Integer.compress(code, Z_MASK);
    }
}
