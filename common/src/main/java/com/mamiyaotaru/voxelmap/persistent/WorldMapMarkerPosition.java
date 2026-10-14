package com.mamiyaotaru.voxelmap.persistent;

/** Projects world coordinates into GUI coordinates before submitting marker geometry. */
record WorldMapMarkerPosition(float x, float y) {
    static WorldMapMarkerPosition project(int blockX, int blockZ, double mapCenterX, double mapCenterZ,
                                         float pixelsPerBlock, boolean oldNorth, int width, int height) {
        double worldX = blockX + 0.5D;
        double worldZ = blockZ + 0.5D;
        double viewedX = oldNorth ? -worldZ : worldX;
        double viewedZ = oldNorth ? worldX : worldZ;
        return new WorldMapMarkerPosition(
                (float) (width / 2.0D + (viewedX - mapCenterX) * pixelsPerBlock),
                (float) (height / 2.0D + (viewedZ - mapCenterZ) * pixelsPerBlock));
    }
}
