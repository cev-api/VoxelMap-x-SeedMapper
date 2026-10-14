package com.mamiyaotaru.voxelmap.persistent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldMapMarkerPositionTest {
    @Test
    void separateWorldLocationsDoNotCollapseToTheScreenCenter() {
        var centered = WorldMapMarkerPosition.project(100, 200, 100.5, 200.5, 0.25F, false, 800, 600);
        var offset = WorldMapMarkerPosition.project(140, 160, 100.5, 200.5, 0.25F, false, 800, 600);
        assertEquals(400F, centered.x());
        assertEquals(300F, centered.y());
        assertEquals(410F, offset.x());
        assertEquals(290F, offset.y());
    }

    @Test
    void panningAndZoomingMoveMarkersRelativeToTheMapCenter() {
        var panned = WorldMapMarkerPosition.project(100, 200, 120.5, 188.5, 0.25F, false, 800, 600);
        assertEquals(395F, panned.x());
        assertEquals(303F, panned.y());
        var zoomed = WorldMapMarkerPosition.project(140, 160, 100.5, 200.5, 0.5F, false, 800, 600);
        assertEquals(420F, zoomed.x());
        assertEquals(280F, zoomed.y());
    }

    @Test
    void oldNorthRotatesWorldLocationsAroundTheViewedCenter() {
        var rotated = WorldMapMarkerPosition.project(140, 160, -200.5, 100.5, 0.25F, true, 800, 600);
        assertEquals(410F, rotated.x());
        assertEquals(310F, rotated.y());
    }

    @Test
    void nearbyMarkersRemainDistinctAtTheWorldBorder() {
        var position = WorldMapMarkerPosition.project(29999998, -29999999,
                29999999.5, -29999998.5, 0.25F, false, 800, 600);
        assertEquals(399.75F, position.x());
        assertEquals(300F, position.y());
    }
}
