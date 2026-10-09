package com.mamiyaotaru.voxelmap.persistent.explored;

import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.ChunkBounds;

/** A captured store context: background reads never resolve the live Minecraft world. */
public record ExploredCellQuery(ExploredDiskStore store, ExploredAsyncLoader loader,
        ChunkBounds bounds, int cellSize) {
    public record Status(long version, boolean ready) { }

    public Status request() {
        int level = ExploredDiskStore.selectLevelForCellSize(cellSize);
        int shift = 5 * (level + 1) + 5;
        boolean ready = true;
        for (int x = bounds.minX() >> shift; x <= bounds.maxX() >> shift; x++) {
            for (int z = bounds.minZ() >> shift; z <= bounds.maxZ() >> shift; z++) {
                if (!store.isContainerLoaded(level, x, z)) {
                    ready = false;
                    if (loader != null) loader.requestLoad(level, x, z);
                }
            }
        }
        return new Status(store.versionInBounds(bounds, level), ready);
    }

    public CellGrid build() {
        return store.cellsInBounds(bounds, cellSize);
    }
}
