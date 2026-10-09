package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.persistent.explored.ExploredCellQuery;
import com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore;
import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.ChunkBounds;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapGeometryTest {
    @TempDir Path directory;
    @Test void crossingDiagonalProducesOneOrientedQuadAndOutsideLinesAreCulled() {
        var mesh = new ExploredLineMesher.Result(new float[]{-100, -100, 100, 100, -100, 100, -50, 100}, 2, new float[0], new boolean[0], 0);
        var quads = WorldMapGeometry.lines(mesh, new WorldMapGeometry.Bounds(-10, 10, -10, 10), 2, false);
        assertEquals(1, quads.count());
        float[] v = quads.vertices();
        assertNotEquals(v[0], v[2]); assertNotEquals(v[1], v[3]);
        for (float value : v) { assertTrue(Float.isFinite(value)); assertTrue(Math.abs(value) < 14); }
    }
    @Test void sparseDiagonalAndIsolatedNodesRemainVisible() {
        CellGrid grid = new CellGrid(-20, -20, 40, 40);
        grid.mark(-4, -4); grid.mark(-3, -3); grid.mark(10, -10);
        var mesh = ExploredLineMesher.build(grid, 1, true, 0, 0);
        assertEquals(1, mesh.segmentCount()); assertEquals(3, mesh.nodeCount());
        var quads = WorldMapGeometry.lines(mesh, new WorldMapGeometry.Bounds(-500, 500, -500, 500), 1, false);
        assertEquals(2, quads.count());
    }
    @Test void tileHaloKeepsContinuousRoutesWithoutDuplicatingNodes() {
        ExploredDiskStore store = new ExploredDiskStore(directory);
        for (int x = -140; x <= 140; x++) store.setChunk(x, x);
        ChunkBounds bounds = new ChunkBounds(-150, 150, -150, 150);
        ExploredCellQuery query = new ExploredCellQuery(store, null, bounds, 1);
        CellGrid grid = query.build();
        WorldMapTrailTiles tiles = new WorldMapTrailTiles();
        long version = store.versionInBounds(bounds, 0);
        var result = tiles.build(query, grid, 1, version);
        assertEquals(281, result.nodeCount());
        for (boolean linked : result.nodeLinked()) assertTrue(linked);
        double length = 0;
        for (int i = 0; i < result.segmentCount(); i++) {
            float[] s = result.segments(); int p = i * 4;
            length += Math.hypot(s[p + 2] - s[p], s[p + 3] - s[p + 1]);
        }
        assertEquals(280 * 16 * Math.sqrt(2), length, .01);
        var hit = tiles.build(query, grid, 1, version);
        assertArrayEquals(result.segments(), hit.segments());
        store.setChunk(141, 141);
        var updated = tiles.build(query, query.build(), 1, store.versionInBounds(bounds, 0));
        assertEquals(282, updated.nodeCount());
    }
    @Test void negativeCoarseningPreservesCoverageAndEmptyBounds() {
        var grid = new CellGrid(-3, -5, 9, 8); grid.mark(-3, -5); grid.mark(5, 2);
        var coarse = grid.coarsen(2);
        assertTrue(coarse.get(-2, -3)); assertTrue(coarse.get(2, 1));
        assertEquals(new ChunkBounds(-4, 7, -8, 3), new ChunkBounds(-3, 5, -5, 2).align(4, 0));
        assertTrue(new ChunkBounds(0, -1, 0, -1).isEmpty());
    }
    @Test void sparseExactTrailsKeepOriginalCoordinatesAcrossNegativeAndPositiveTileSeams() {
        var sparse = new com.mamiyaotaru.voxelmap.util.SparseCellGrid();
        var dense = new CellGrid(-260, -260, 520, 520);
        for (int x = -255; x <= 255; x++) {
            sparse.mark(x, x); dense.mark(x, x);
            sparse.mark(x, 20); dense.mark(x, 20);
        }
        sparse.mark(100000, -100000); // A distant isolated chunk must not force coarser trails.
        var result = new WorldMapTrailTiles().buildSparse(sparse, 1);
        var original = ExploredLineMesher.build(dense, 1, true, 0, 0);
        assertEquals(original.nodeCount() + 1, result.nodeCount());
        var centers = new java.util.HashSet<Long>();
        for (int i = 0; i < result.nodeCount(); i++) {
            float x = result.nodeCoords()[i * 2], z = result.nodeCoords()[i * 2 + 1];
            assertEquals(8, Math.floorMod((int) x, 16)); assertEquals(8, Math.floorMod((int) z, 16));
            assertTrue(centers.add(((long) Float.floatToIntBits(x) << 32) | Integer.toUnsignedLong(Float.floatToIntBits(z))));
        }
        assertEquals(length(original), length(result), .02);
    }
    private static double length(ExploredLineMesher.Result result) {
        double length = 0;
        for (int i = 0; i < result.segmentCount(); i++) {
            int p = i * 4; float[] s = result.segments();
            length += Math.hypot(s[p + 2] - s[p], s[p + 3] - s[p + 1]);
        }
        return length;
    }
}
