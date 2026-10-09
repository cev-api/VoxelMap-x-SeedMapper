package com.mamiyaotaru.voxelmap.persistent.explored;

import com.mamiyaotaru.voxelmap.util.ChunkBounds;
import com.mamiyaotaru.voxelmap.util.SparseCellGrid;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SparseViewportTest {
    @TempDir Path directory;
    @Test void hugeViewportReadsEverySavedContainerWithoutResidencyThrashingOrDenseAllocation() throws Exception {
        for (int x = -300; x <= 300; x++) {
            var container = new ExploredContainer(0, x, 0);
            container.getOrCreateTile(0, 0).set(0, 0);
            ExploredContainerIo.writeBytes(directory.resolve("lod0/c." + x + ".0.bin"), container.encode());
        }
        var store = new ExploredDiskStore(directory);
        long version = store.contentVersion();
        var cells = store.sparseCellsInBounds(new ChunkBounds(-1_000_000, 1_000_000, -1_000_000, 1_000_000), 1);
        assertEquals(601, cells.count()); assertEquals(601, cells.tileKeys().size());
        for (int x = -300; x <= 300; x++) assertTrue(cells.get(x * 1024, 0));
        assertEquals(version, store.contentVersion());
        assertFalse(store.isContainerLoaded(0, 0, 0)); assertFalse(store.hasDirty());
        store.setChunk(-128, -129);
        assertEquals(602, store.sparseCellsInBounds(new ChunkBounds(-1_000_000, 1_000_000, -1_000_000, 1_000_000), 1).count());
        assertTrue(store.hasDirty()); assertNotEquals(version, store.contentVersion());
    }
    @Test void negativeTileHalosAndSubtractionPreserveExactCoverage() {
        var cells = new SparseCellGrid();
        cells.mark(-129, -129); cells.mark(-128, -128); cells.mark(-1, -1); cells.mark(0, 0);
        var tile = cells.tile(SparseCellGrid.key(-1, -1), 1);
        for (int i : new int[]{-129, -128, -1, 0}) assertTrue(tile.get(i, i));
        var mask = new SparseCellGrid(); mask.mark(-128, -128); mask.mark(0, 0);
        cells.subtract(mask); assertEquals(2, cells.count()); assertFalse(cells.get(-128, -128));
        assertTrue(cells.get(-129, -129)); assertTrue(cells.get(-1, -1));
    }
    @Test void fixedCoarseResolutionStaysAtTheRequestedSize() {
        var store = new ExploredDiskStore(directory); store.setChunk(-1, -1); store.setChunk(127, 127);
        var cells = store.sparseCellsInBounds(new ChunkBounds(-100000, 100000, -100000, 100000), 4);
        assertEquals(2, cells.count()); assertTrue(cells.get(-1, -1)); assertTrue(cells.get(31, 31));
    }
    @Test void offscreenChangesAndResidentLoadsDoNotInvalidateExactViewportSnapshots() {
        var store = new ExploredDiskStore(directory);
        var bounds = new ChunkBounds(0, 31, 0, 31);
        long version = store.contentVersionInBounds(bounds, 1);
        store.setChunk(64, 64); // Same disk container, different source tile.
        assertEquals(version, store.contentVersionInBounds(bounds, 1));
        store.loadContainer(0, 0, 0);
        assertEquals(version, store.contentVersionInBounds(bounds, 1));
        store.setChunk(8, 8);
        assertTrue(store.contentVersionInBounds(bounds, 1) > version);
    }
}
