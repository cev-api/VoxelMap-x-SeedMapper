package com.mamiyaotaru.voxelmap.persistent;

import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.Locale;

/** Fidelity choices are independent of viewport culling and caching. Zero cutoff means always visible. */
public final class WorldMapDetailSettings {
    public enum Layer { TERRAIN, TRAILS, TRAIL_NODES, NEW_OLD, WAYPOINTS, WAYPOINT_NAMES, ENTITIES, BIOMES }
    public final EnumMap<Layer, Integer> hideBeyond = new EnumMap<>(Layer.class);
    public int trailResolution = 1; // chunks per cell; 0 explicitly selects automatic aggregation
    public int areaResolution = 1;
    public int terrainResolution = 0; // 0 = screen resolution, otherwise source pixels per region
    public boolean reduceMarkersWhileMoving = false;
    public boolean limitMarkerCount = false;
    public int zoomCacheMiB = 512; // retained overlay and terrain viewport history
    public int scrollStepsPerOctave = 3;
    public boolean snapZoom = false;
    public int overlayRenderer = 0; // automatic, cached image, geometry
    public int rasterScalePercent = 100;
    public boolean smoothOverlays = false;

    public boolean visible(Layer layer, float zoom) {
        int denominator = hideBeyond.getOrDefault(layer, 0);
        return denominator == 0 || zoom >= 1.0F / denominator;
    }
    public void fullDetail() {
        hideBeyond.clear(); trailResolution = 1; areaResolution = 1; terrainResolution = 256;
        reduceMarkersWhileMoving = false; limitMarkerCount = false;
    }
    public void balanced() {
        fullDetail(); trailResolution = 0; areaResolution = 0;
        terrainResolution = 0;
        reduceMarkersWhileMoving = true; limitMarkerCount = true;
        hideBeyond.put(Layer.TERRAIN, 128); hideBeyond.put(Layer.NEW_OLD, 64);
        hideBeyond.put(Layer.WAYPOINTS, 128); hideBeyond.put(Layer.ENTITIES, 64);
        hideBeyond.put(Layer.TRAIL_NODES, 32); hideBeyond.put(Layer.BIOMES, 8);
    }
    public boolean load(String key, String value) {
        if (key.startsWith("Worldmap Hide Beyond ")) {
            try { hideBeyond.put(Layer.valueOf(key.substring("Worldmap Hide Beyond ".length())), cutoff(Integer.parseInt(value))); }
            catch (IllegalArgumentException ignored) { }
            return true;
        }
        switch (key) {
            case "Worldmap Trail Resolution" -> trailResolution = resolution(Integer.parseInt(value), 128);
            case "Worldmap Area Resolution" -> areaResolution = resolution(Integer.parseInt(value), 128);
            case "Worldmap Terrain Resolution" -> terrainResolution = resolution(Integer.parseInt(value), 256);
            case "Worldmap Reduce Moving Markers" -> reduceMarkersWhileMoving = Boolean.parseBoolean(value);
            case "Worldmap Limit Marker Count" -> limitMarkerCount = Boolean.parseBoolean(value);
            case "Worldmap Zoom Cache MiB" -> zoomCacheMiB = Math.max(64, Math.min(1024, Integer.parseInt(value)));
            case "Worldmap Scroll Steps Per Octave" -> scrollStepsPerOctave = Math.max(1, Math.min(12, Integer.parseInt(value)));
            case "Worldmap Snap Zoom" -> snapZoom = Boolean.parseBoolean(value);
            case "Worldmap Overlay Renderer" -> overlayRenderer = Math.max(0, Math.min(2, Integer.parseInt(value)));
            case "Worldmap Overlay Image Scale" -> rasterScalePercent = Math.max(50, Math.min(200, Integer.parseInt(value)));
            case "Worldmap Smooth Overlays" -> smoothOverlays = Boolean.parseBoolean(value);
            default -> { return false; }
        }
        return true;
    }
    private static int resolution(int value, int max) { return value <= 0 ? 0 : Integer.highestOneBit(Math.min(max, value)); }
    private static int cutoff(int value) { return value <= 0 ? 0 : Integer.highestOneBit(Math.min(262144, value)); }
    public void save(PrintWriter out) {
        out.println("Worldmap Trail Resolution:" + trailResolution);
        out.println("Worldmap Area Resolution:" + areaResolution);
        out.println("Worldmap Terrain Resolution:" + terrainResolution);
        out.println("Worldmap Reduce Moving Markers:" + reduceMarkersWhileMoving);
        out.println("Worldmap Limit Marker Count:" + limitMarkerCount);
        out.println("Worldmap Zoom Cache MiB:" + zoomCacheMiB);
        out.println("Worldmap Scroll Steps Per Octave:" + scrollStepsPerOctave);
        out.println("Worldmap Snap Zoom:" + snapZoom);
        out.println("Worldmap Overlay Renderer:" + overlayRenderer);
        out.println("Worldmap Overlay Image Scale:" + rasterScalePercent);
        out.println("Worldmap Smooth Overlays:" + smoothOverlays);
        for (Layer layer : Layer.values()) out.println("Worldmap Hide Beyond " + layer + ":" + hideBeyond.getOrDefault(layer, 0));
    }
    public float stepZoom(float value, int direction) {
        double exponent = Math.log(value) / Math.log(2);
        // Remove float rounding drift around existing zoom steps so reverse scrolling
        // returns to the same cache key; arbitrary off-grid zooms stay off-grid.
        double nearest = Math.rint(exponent * scrollStepsPerOctave) / scrollStepsPerOctave;
        if (Math.abs(exponent - nearest) < 0.000001D) exponent = nearest;
        exponent = snapZoom ? (Math.rint(exponent * scrollStepsPerOctave) + direction) / scrollStepsPerOctave
                : exponent + (double) direction / scrollStepsPerOctave;
        return (float) Math.pow(2, exponent);
    }
    public static int visibilitySlider(int denominator) {
        return denominator == 0 ? 19 : Math.max(2, Math.min(18, 31 - Integer.numberOfLeadingZeros(denominator)));
    }
    public static int visibilityCutoff(int position) { return position >= 19 ? 0 : 1 << Math.max(2, position); }
    public static String scaleText(float zoom) {
        if (zoom >= 1) return String.format(Locale.ROOT, "%.2f:1", zoom);
        return String.format(Locale.ROOT, "1:%,.0f", 1.0 / zoom);
    }
}
