package com.mamiyaotaru.voxelmap.persistent;

/** World-anchored, isotropic sampling; movement never shifts the sampling lattice. */
public final class SeedPreviewSampling {
    public record Layout(int minX, int maxX, int minZ, int maxZ, int width, int height, int step) { }
    private SeedPreviewSampling() { }
    public static Layout layout(int minX, int maxX, int minZ, int maxZ, double blocksPerPixel, int limit) {
        if (limit < 2 || maxX < minX || maxZ < minZ || !Double.isFinite(blocksPerPixel))
            throw new IllegalArgumentException("Invalid seed preview bounds or resolution");
        int step = 1;
        while (step < blocksPerPixel && step < (1 << 28)) step *= 2;
        // Reserve the possible edge texel before aligning the origin. The selected
        // level depends on the view span, not its position near a grid boundary.
        while (Math.floorDiv((long) maxX - minX + step - 1, step) + 1 > limit
                || Math.floorDiv((long) maxZ - minZ + step - 1, step) + 1 > limit) step *= 2;
        {
            int x0 = Math.floorDiv(minX, step) * step, z0 = Math.floorDiv(minZ, step) * step;
            int width = (int) Math.max(1, Math.floorDiv((long) maxX - x0 + step - 1, step));
            int height = (int) Math.max(1, Math.floorDiv((long) maxZ - z0 + step - 1, step));
            return new Layout(x0, x0 + width * step, z0, z0 + height * step, width, height, step);
        }
    }
    /** Spend the resolution budget on the viewport before adding off-screen padding. */
    public static Layout viewport(int minX, int maxX, int minZ, int maxZ,
                                  int paddedMinX, int paddedMaxX, int paddedMinZ, int paddedMaxZ,
                                  double blocksPerPixel, int limit) {
        Layout view = layout(minX, maxX, minZ, maxZ, blocksPerPixel / 2, limit);
        int step = view.step();
        int left = paddingCells((long) view.minX() - paddedMinX, step, (limit - view.width()) / 2);
        int right = paddingCells((long) paddedMaxX - view.maxX(), step, limit - view.width() - left);
        int top = paddingCells((long) view.minZ() - paddedMinZ, step, (limit - view.height()) / 2);
        int bottom = paddingCells((long) paddedMaxZ - view.maxZ(), step, limit - view.height() - top);
        return new Layout(view.minX() - left * step, view.maxX() + right * step,
                view.minZ() - top * step, view.maxZ() + bottom * step,
                view.width() + left + right, view.height() + top + bottom, step);
    }
    private static int paddingCells(long span, int step, int available) {
        return (int) Math.min(Math.max(0, available), Math.max(0L, Math.floorDiv(span + step - 1, step)));
    }
    public static int average(int a, int b, int c, int d) {
        return 0xff000000 | ((((a >>> 16 & 255) + (b >>> 16 & 255) + (c >>> 16 & 255) + (d >>> 16 & 255)) / 4) << 16)
                | ((((a >>> 8 & 255) + (b >>> 8 & 255) + (c >>> 8 & 255) + (d >>> 8 & 255)) / 4) << 8)
                | ((a & 255) + (b & 255) + (c & 255) + (d & 255)) / 4;
    }
}
