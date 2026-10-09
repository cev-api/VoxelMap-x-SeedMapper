package com.mamiyaotaru.voxelmap.persistent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapProgressTest {
    private WorldMapProgress.Row row(String name) {
        return WorldMapProgress.rows().stream().filter(row -> row.layer().equals(name)).findFirst().orElseThrow();
    }
    @Test void percentagesReflectCompletedWorkAndAggregateSharedLayers() {
        var a = WorldMapProgress.begin("Test shared trails");
        var b = WorldMapProgress.begin("Test shared trails");
        try {
            a.update("Meshing tiles", 25, 100); b.update("Meshing tiles", 50, 300);
            assertEquals(18, row("Test shared trails").percent());
            a.update("Meshing tiles", 1000, 100); b.update("Meshing tiles", 300, 300);
            assertEquals(100, row("Test shared trails").percent());
            a.update("Preparing image", 0, 0);
            assertEquals(-1, row("Test shared trails").percent());
            assertEquals("Preparing", row("Test shared trails").stage());
        } finally { a.cancel(); b.cancel(); }
    }
    @Test void cancelledWorkersCannotResurrectTheirBars() {
        var task = WorldMapProgress.begin("Test cancelled view");
        task.cancel();
        WorldMapProgress.run(task, () -> { WorldMapProgress.report("Rows", 10, 10); return "discarded"; });
        assertTrue(WorldMapProgress.rows().stream().noneMatch(row -> row.layer().equals("Test cancelled view")));
        assertNull(WorldMapProgress.currentTask());
    }
    @Test void successfulAndFailedWorkersCleanUpTheirContext() {
        var success = WorldMapProgress.begin("Test successful view");
        try {
            assertEquals("result", WorldMapProgress.run(success, () -> { WorldMapProgress.report("Rows", 5, 10); return "result"; }));
            assertEquals(100, row("Test successful view").percent());
            assertEquals("Ready", row("Test successful view").stage());
            assertNull(WorldMapProgress.currentTask());
        } finally { success.cancel(); }
        var failure = WorldMapProgress.begin("Test failed view");
        assertThrows(IllegalStateException.class, () -> WorldMapProgress.run(failure, () -> { throw new IllegalStateException("failed"); }));
        assertTrue(WorldMapProgress.rows().stream().noneMatch(row -> row.layer().equals("Test failed view")));
        assertNull(WorldMapProgress.currentTask());
    }
}
