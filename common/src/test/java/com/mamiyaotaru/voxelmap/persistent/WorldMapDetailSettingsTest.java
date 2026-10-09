package com.mamiyaotaru.voxelmap.persistent;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapDetailSettingsTest {
    @Test void exactDetailAndVisibilityRemainAvailableAtWidestZoom() {
        var settings = new WorldMapDetailSettings();
        for (var layer : WorldMapDetailSettings.Layer.values()) assertTrue(settings.visible(layer, 1F / 262144));
        assertEquals(1, settings.trailResolution); assertEquals(1, settings.areaResolution);
        settings.hideBeyond.put(WorldMapDetailSettings.Layer.WAYPOINTS, 128);
        assertTrue(settings.visible(WorldMapDetailSettings.Layer.WAYPOINTS, 1F / 128));
        assertFalse(settings.visible(WorldMapDetailSettings.Layer.WAYPOINTS, 1F / 256));
        assertTrue(settings.visible(WorldMapDetailSettings.Layer.NEW_OLD, 1F / 262144));
        settings.balanced(); assertEquals(0, settings.trailResolution);
        settings.fullDetail(); assertEquals(1, settings.trailResolution);
        for (var layer : WorldMapDetailSettings.Layer.values()) assertTrue(settings.visible(layer, 1F / 262144));
    }
    @Test void detailSettingsRoundTripAndRejectInvalidResolutions() {
        var settings = new WorldMapDetailSettings(); settings.balanced();
        settings.trailResolution = 4; settings.terrainResolution = 256; settings.scrollStepsPerOctave = 8; settings.snapZoom = true; settings.zoomCacheMiB = 512;
        var output = new StringWriter(); settings.save(new PrintWriter(output));
        var loaded = new WorldMapDetailSettings();
        for (String line : output.toString().split("\\R")) {
            String[] pair = line.split(":", 2); assertTrue(loaded.load(pair[0], pair[1]));
        }
        for (var layer : WorldMapDetailSettings.Layer.values())
            assertEquals(settings.hideBeyond.getOrDefault(layer, 0), loaded.hideBeyond.getOrDefault(layer, 0));
        assertEquals(4, loaded.trailResolution); assertEquals(256, loaded.terrainResolution);
        assertEquals(512, loaded.zoomCacheMiB);
        loaded.load("Worldmap Zoom Cache MiB", "99999"); assertEquals(1024, loaded.zoomCacheMiB);
        assertTrue(loaded.snapZoom); assertEquals(8, loaded.scrollStepsPerOctave);
        loaded.load("Worldmap Area Resolution", "2147483647"); assertEquals(128, loaded.areaResolution);
        loaded.load("Worldmap Trail Resolution", "-1"); assertEquals(0, loaded.trailResolution);
    }
    @Test void zoomStepsAreReversibleAndSnapToLogarithmicScale() {
        var settings = new WorldMapDetailSettings();
        float zoom = 1;
        for (int i = 0; i < 54; i++) zoom = settings.stepZoom(zoom, -1);
        assertEquals(1F / 262144, zoom, 1e-10);
        for (int i = 0; i < 54; i++) zoom = settings.stepZoom(zoom, 1);
        assertEquals(Float.floatToIntBits(1F), Float.floatToIntBits(zoom));
        for (int i = 0; i < 100; i++) assertEquals(1F, settings.stepZoom(settings.stepZoom(1F, -1), 1));
        settings.snapZoom = true;
        assertEquals(0.25F, settings.stepZoom(0.20F, 1), 1e-6);
        assertEquals("1:1,024", WorldMapDetailSettings.scaleText(1F / 1024));
    }
}
