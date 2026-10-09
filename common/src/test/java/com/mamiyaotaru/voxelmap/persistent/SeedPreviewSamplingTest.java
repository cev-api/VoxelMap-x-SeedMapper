package com.mamiyaotaru.voxelmap.persistent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedPreviewSamplingTest {
    @Test void paddingCannotReduceViewportDetailWhileDragging() {
        var first = SeedPreviewSampling.viewport(0, 1920, 0, 1080, -10000, 12000, -10000, 12000, 1, 2048);
        assertEquals(1, first.step());
        for (int offset = -128; offset <= 128; offset++) {
            var moved = SeedPreviewSampling.viewport(offset, 1920 + offset, offset, 1080 + offset,
                    offset - 20000, offset + 22000, offset - 20000, offset + 22000, 1, 2048);
            assertEquals(first.step(), moved.step());
            assertTrue(moved.width() <= 2048 && moved.height() <= 2048);
            assertTrue(moved.minX() <= offset && moved.maxX() >= 1920 + offset);
            assertTrue(moved.minZ() <= offset && moved.maxZ() >= 1080 + offset);
        }
    }
    @Test void higherResolutionImprovesWideZoomAndCloseZoomSamplesIndividualBlocks() {
        var close = SeedPreviewSampling.viewport(0, 1500, 0, 850, -512, 2012, -512, 1362, .8, 2048);
        assertEquals(1, close.step());
        var low = SeedPreviewSampling.viewport(0, 32000, 0, 18000, -10000, 42000, -10000, 28000, 16.8, 1024);
        var high = SeedPreviewSampling.viewport(0, 32000, 0, 18000, -10000, 42000, -10000, 28000, 16.8, 4096);
        assertTrue(high.step() < low.step());
    }
    @Test void viewportMovementKeepsTheSameWorldSamplingLattice() {
        var a = SeedPreviewSampling.layout(-19373, 29004, -10501, 15000, 21.15, 1024);
        var b = SeedPreviewSampling.layout(-19310, 29050, -10440, 15050, 21.15, 1024);
        assertEquals(a.step(), b.step());
        assertEquals(0, Math.floorMod(a.minX(), a.step()));
        assertEquals(0, Math.floorMod(b.minX(), b.step()));
        assertEquals(0, Math.floorMod(a.minZ(), a.step()));
        int worldCell = 100;
        assertEquals(worldCell * a.step() + a.step() / 4, a.minX() + (worldCell - a.minX() / a.step()) * a.step() + a.step() / 4);
        assertEquals(worldCell * b.step() + b.step() / 4, b.minX() + (worldCell - b.minX() / b.step()) * b.step() + b.step() / 4);
    }
    @Test void panningAcrossResolutionBoundariesDoesNotChangeSamplingLevel() {
        var first = SeedPreviewSampling.layout(0, 65535, 0, 32000, 32, 1024);
        for (int offset = -128; offset <= 128; offset++) {
            var moved = SeedPreviewSampling.layout(offset, 65535 + offset, offset, 32000 + offset, 32, 1024);
            assertEquals(first.step(), moved.step());
        }
    }
    @Test void allZoomsCoverTheRequestWithSquareSamplesInsideResolutionLimit() {
        for (int span : new int[]{16, 1000, 50000, 1000000, 60000000}) {
            var layout = SeedPreviewSampling.layout(-span / 2, span / 2, -span / 4, span / 4, span / 1920.0, 1024);
            assertTrue(layout.width() <= 1024 && layout.height() <= 1024);
            assertTrue(layout.minX() <= -span / 2 && layout.maxX() >= span / 2);
            assertTrue(layout.minZ() <= -span / 4 && layout.maxZ() >= span / 4);
            assertEquals(layout.step(), (layout.maxX() - layout.minX()) / layout.width());
            assertEquals(layout.step(), (layout.maxZ() - layout.minZ()) / layout.height());
        }
    }
    @Test void coarseTexelsAverageEverySampleAndUniformBiomesKeepTheirColor() {
        assertEquals(0xff3f3f3f, SeedPreviewSampling.average(0xffff0000, 0xff00ff00, 0xff0000ff, 0xff000000));
        assertEquals(0xffabcdef, SeedPreviewSampling.average(0xffabcdef, 0xffabcdef, 0xffabcdef, 0xffabcdef));
    }
    @Test void visibilitySlidersRoundTripAndEndInAlways() {
        for (int position = 2; position <= 19; position++)
            assertEquals(position, WorldMapDetailSettings.visibilitySlider(WorldMapDetailSettings.visibilityCutoff(position)));
        assertEquals(4, WorldMapDetailSettings.visibilityCutoff(2));
        assertEquals(262144, WorldMapDetailSettings.visibilityCutoff(18));
        assertEquals(0, WorldMapDetailSettings.visibilityCutoff(19));
    }
}
