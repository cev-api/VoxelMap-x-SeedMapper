package com.mamiyaotaru.voxelmap.persistent;

/** Shared render-thread budget for overview and detailed terrain uploads. */
final class WorldMapUploadBudget {
    private static int remainingBytes;
    private static long spentNanos;
    static void beginFrame() { remainingBytes = 4 * 1024 * 1024; spentNanos = 0; }
    static boolean available(int bytes) {
        // A display-size overlay may exceed 4 MiB. Admit one such upload on an otherwise empty frame.
        return spentNanos < 2_000_000L && (remainingBytes >= bytes || (remainingBytes == 4 * 1024 * 1024 && spentNanos == 0));
    }
    static long begin(int bytes) { remainingBytes -= bytes; WorldMapProfiler.uploaded(bytes); return System.nanoTime(); }
    static void end(long started) { spentNanos += System.nanoTime() - started; }
}
