package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.util.CellGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapRasterTest {
    @Test void exactDiagonalCoordinatesAndEveryNodeSurviveDisplayRasterization() {
        var grid = new CellGrid(-150, -150, 301, 301);
        for (int x = -140; x <= 140; x++) grid.mark(x, x);
        var mesh = ExploredLineMesher.build(grid, 1, true, 0, 0);
        try (var painter = new WorldMapRaster.Painter(new WorldMapGeometry.Bounds(-2400, 2400, -2400, 2400), 600, 600, 0xff22a8ff, 8, false)) {
            painter.trails(mesh, 8, true);
            var image = painter.finish();
            for (int x = -140; x <= 140; x++) {
                int pixel = x * 2 + 301;
                assertEquals(0xffffa822, image.pixels()[pixel * image.width() + pixel]);
            }
            assertEquals(0, image.pixels()[50 * image.width() + 400]);
        }
    }
    @Test void crossingRoutesAreClippedAndImageColorsArePremultipliedAbgr() {
        var mesh = new ExploredLineMesher.Result(new float[]{-100, 0, 100, 0}, 1, new float[]{0, 30}, new boolean[]{false}, 1);
        try (var painter = new WorldMapRaster.Painter(new WorldMapGeometry.Bounds(-50, 50, -50, 50), 100, 100, 0x80ff0000, 2, false)) {
            painter.trails(mesh, 2, false);
            var image = painter.finish();
            for (int x = 0; x < 100; x++) assertEquals(0x80000080, image.pixels()[50 * 100 + x]);
            assertEquals(0x80000080, image.pixels()[80 * 100 + 50]);
            assertEquals(0, image.pixels()[10 * 100 + 50]);
        }
    }
}
