package com.mamiyaotaru.voxelmap.persistent.explored;

/** morton z-order interleave for 5-bit local coordinates (0..31) */
public final class MortonCode {
    private MortonCode() {
    }

    /** interleave x and z (each 0..31) into a 10 bit slot index (0..1023) */
    public static int encode(int x, int z) {
        int code = 0;
        for (int i = 0; i < 5; i++) {
            code |= ((x >> i) & 1) << (2 * i);
            code |= ((z >> i) & 1) << (2 * i + 1);
        }
        return code;
    }

    public static int decodeX(int code) {
        int x = 0;
        for (int i = 0; i < 5; i++) {
            x |= ((code >> (2 * i)) & 1) << i;
        }
        return x;
    }

    public static int decodeZ(int code) {
        int z = 0;
        for (int i = 0; i < 5; i++) {
            z |= ((code >> (2 * i + 1)) & 1) << i;
        }
        return z;
    }
}
