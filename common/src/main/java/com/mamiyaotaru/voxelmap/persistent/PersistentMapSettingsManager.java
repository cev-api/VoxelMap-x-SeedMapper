package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.interfaces.ISubSettingsManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

import java.util.Locale;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

public class PersistentMapSettingsManager implements ISubSettingsManager {
    private static final int MIN_WORLDMAP_ZOOM_POWER = -9;
    private static final int MAX_WORLDMAP_ZOOM_POWER = 8;
    private static final int MAX_WORLDMAP_CACHE_SIZE = 20000;
    public static final float MIN_PERFORMANCE_MODE_THRESHOLD = 0.0025F;
    public static final float MAX_PERFORMANCE_MODE_THRESHOLD = 0.30F;
    public static final float MIN_CHUNK_LINE_THICKNESS = 0.05F;
    public static final float MAX_CHUNK_LINE_THICKNESS = 2.00F;
    protected int mapX;
    protected int mapZ;
    protected float zoom = 8.0F;
    private float minZoomPower = -9.0F;
    private float maxZoomPower = 8.0F;
    protected float minZoom = 0.001953125F;
    protected float maxZoom = 256.0F;
    protected int cacheSize = 500;
    protected boolean outputImages;
    protected float performanceModeThreshold = 0.10F;
    protected float chunkLineThickness = 1.0F;
    public boolean showWaypointsInPerformanceMode = false;
    public boolean literalLineMode = true;
    public boolean showCoordinates = true;
    public boolean showPlayerDirectionArrow = true;
    public boolean showWaypoints = true;
    public boolean showWaypointNames = true;
    public boolean showDistantWaypoints = true;

    @Override
    public void loadAll(File settingsFile) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(settingsFile));

            String sCurrentLine;
            while ((sCurrentLine = in.readLine()) != null) {
                String[] curLine = sCurrentLine.split(":");
                switch (curLine[0]) {
                    case "Worldmap Zoom" -> zoom = Float.parseFloat(curLine[1]);
                    case "Worldmap Minimum Zoom" -> minZoom = Float.parseFloat(curLine[1]);
                    case "Worldmap Maximum Zoom" -> maxZoom = Float.parseFloat(curLine[1]);
                    case "Worldmap Cache Size" -> cacheSize = Integer.parseInt(curLine[1]);
                    case "Worldmap Performance Mode Threshold" -> performanceModeThreshold = Float.parseFloat(curLine[1]);
                    case "Worldmap Chunk Line Thickness" -> chunkLineThickness = Float.parseFloat(curLine[1]);
                    case "Worldmap Show Waypoints In Performance Mode" -> showWaypointsInPerformanceMode = Boolean.parseBoolean(curLine[1]);
                    case "Worldmap Literal Line Mode" -> literalLineMode = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Coordinates" -> showCoordinates = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Player Direction Arrow" -> showPlayerDirectionArrow = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Waypoints" -> showWaypoints = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Waypoint Names" -> showWaypointNames = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Distant Waypoints" -> showDistantWaypoints = Boolean.parseBoolean(curLine[1]);
                    case "Output Images" -> outputImages = Boolean.parseBoolean(curLine[1]);
                }
            }

            in.close();
        } catch (IOException ignored) {}

        for (int power = MIN_WORLDMAP_ZOOM_POWER; power <= MAX_WORLDMAP_ZOOM_POWER; ++power) {
            if (Math.pow(2.0, power) == minZoom) {
                minZoomPower = power;
            }

            if (Math.pow(2.0, power) == maxZoom) {
                maxZoomPower = power;
            }
        }

        // Keep prior zoom-in cap migration from older defaults.
        if (maxZoom == 16.0F) {
            maxZoom = 256.0F;
            maxZoomPower = 8.0F;
        }

        // Migrate older minimum zoom defaults so users can zoom out much further immediately.
        if (minZoom == 0.5F || minZoom == 0.03125F || minZoom == 0.0078125F) {
            minZoom = 0.001953125F;
            minZoomPower = -9.0F;
        }

        bindCacheSize();
        bindZoom();
        bindPerformanceModeThreshold();
        bindChunkLineThickness();
    }

    @Override
    public void saveAll(PrintWriter out) {
        out.println("Worldmap Zoom:" + zoom);
        out.println("Worldmap Minimum Zoom:" + minZoom);
        out.println("Worldmap Maximum Zoom:" + maxZoom);
        out.println("Worldmap Cache Size:" + cacheSize);
        out.println("Worldmap Performance Mode Threshold:" + performanceModeThreshold);
        out.println("Worldmap Chunk Line Thickness:" + chunkLineThickness);
        out.println("Worldmap Show Waypoints In Performance Mode:" + showWaypointsInPerformanceMode);
        out.println("Worldmap Literal Line Mode:" + literalLineMode);
        out.println("Show Worldmap Coordinates:" + showCoordinates);
        out.println("Show Worldmap Player Direction Arrow:" + showPlayerDirectionArrow);
        out.println("Show Worldmap Waypoints:" + showWaypoints);
        out.println("Show Worldmap Waypoint Names:" + showWaypointNames);
        out.println("Show Worldmap Distant Waypoints:" + showDistantWaypoints);
    }

    @Override
    public String getKeyText(EnumOptionsMinimap option) {
        String s = I18n.get(option.getName()) + ": ";

        switch (option.getType()) {
            case BOOLEAN -> {
                boolean flag = getBooleanValue(option);
                return s + (flag ? I18n.get("options.on") : I18n.get("options.off"));
            }
            case LIST -> {
                String state = getListValue(option);
                return s + state;
            }
            case FLOAT -> {
                float value = getFloatValue(option);
                return switch (option) {
                    case MIN_ZOOM, MAX_ZOOM -> s + (float) Math.pow(2.0, value) + "x";
                    case CACHE_SIZE -> s + (int) value;
                    case WORLDMAP_PERFORMANCE_MODE_THRESHOLD -> s + String.format(Locale.ROOT, "%.2f%%", value * 100.0F);
                    case WORLDMAP_CHUNK_LINE_THICKNESS -> s + String.format(Locale.ROOT, "%.2fx", value);

                    default -> s + (value <= 0.0F ? I18n.get("options.off") : (int) value + "%");
                };
            }
        }

        return s + MapSettingsManager.ERROR_STRING;
    }

    @Override
    public boolean getBooleanValue(EnumOptionsMinimap option) {
        return switch (option) {
            case SHOW_WORLDMAP_COORDS -> showCoordinates;
            case SHOW_WORLDMAP_PLAYER_DIRECTION_ARROW -> showPlayerDirectionArrow;
            case SHOW_WAYPOINTS -> showWaypoints && VoxelConstants.getVoxelMapInstance().getMapOptions().waypointsAllowed;
            case SHOW_WAYPOINT_NAMES -> showWaypointNames && VoxelConstants.getVoxelMapInstance().getMapOptions().waypointsAllowed;
            case SHOW_DISTANT_WAYPOINTS -> showDistantWaypoints && VoxelConstants.getVoxelMapInstance().getMapOptions().waypointsAllowed;
            case WORLDMAP_SHOW_WAYPOINTS_IN_PERFORMANCE_MODE -> showWaypointsInPerformanceMode;
            case WORLDMAP_LITERAL_LINE_MODE -> literalLineMode;

            default -> throw new IllegalArgumentException("Invalid boolean value! Add code to handle EnumOptionMinimap: " + option.getName());
        };
    }

    @Override
    public void toggleBooleanValue(EnumOptionsMinimap option) {
        switch (option) {
            case SHOW_WORLDMAP_COORDS -> showCoordinates = !showCoordinates;
            case SHOW_WORLDMAP_PLAYER_DIRECTION_ARROW -> showPlayerDirectionArrow = !showPlayerDirectionArrow;
            case SHOW_WAYPOINTS -> showWaypoints = !showWaypoints;
            case SHOW_WAYPOINT_NAMES -> showWaypointNames = !showWaypointNames;
            case SHOW_DISTANT_WAYPOINTS -> showDistantWaypoints = !showDistantWaypoints;
            case WORLDMAP_SHOW_WAYPOINTS_IN_PERFORMANCE_MODE -> showWaypointsInPerformanceMode = !showWaypointsInPerformanceMode;
            case WORLDMAP_LITERAL_LINE_MODE -> literalLineMode = !literalLineMode;

            default -> throw new IllegalArgumentException("Invalid boolean value! Add code to handle EnumOptionMinimap: " + option.getName());
        }
    }

    @Override
    public String getListValue(EnumOptionsMinimap option) {
        throw new IllegalArgumentException("Invalid list value! Add code to handle EnumOptionMinimap: " + option.getName());
    }

    @Override
    public void cycleListValue(EnumOptionsMinimap option) {
        throw new IllegalArgumentException("Invalid list value! Add code to handle EnumOptionMinimap: " + option.getName());
    }

    @Override
    public float getFloatValue(EnumOptionsMinimap option) {
        return switch (option) {
            case MIN_ZOOM -> minZoomPower;
            case MAX_ZOOM -> maxZoomPower;
            case CACHE_SIZE -> cacheSize;
            case WORLDMAP_PERFORMANCE_MODE_THRESHOLD -> performanceModeThreshold;
            case WORLDMAP_CHUNK_LINE_THICKNESS -> chunkLineThickness;

            default -> throw new IllegalArgumentException("Invalid float value! Add code to handle EnumOptionMinimap: " + option.getName());
        };
    }

    @Override
    public void setFloatValue(EnumOptionsMinimap option, float value) {
        switch (option) {
            case MIN_ZOOM -> {
                minZoomPower = ((int) (value * (MAX_WORLDMAP_ZOOM_POWER - MIN_WORLDMAP_ZOOM_POWER)) + MIN_WORLDMAP_ZOOM_POWER);
                minZoom = (float) Math.pow(2.0, minZoomPower);
                if (maxZoom < minZoom) {
                    maxZoom = minZoom;
                    maxZoomPower = minZoomPower;
                }
            }
            case MAX_ZOOM -> {
                maxZoomPower = ((int) (value * (MAX_WORLDMAP_ZOOM_POWER - MIN_WORLDMAP_ZOOM_POWER)) + MIN_WORLDMAP_ZOOM_POWER);
                maxZoom = (float) Math.pow(2.0, maxZoomPower);
                if (minZoom > maxZoom) {
                    minZoom = maxZoom;
                    minZoomPower = maxZoomPower;
                }
            }
            case CACHE_SIZE -> {
                cacheSize = (int) (value * MAX_WORLDMAP_CACHE_SIZE);
                cacheSize = Math.max(cacheSize, 30);
            }
            case WORLDMAP_PERFORMANCE_MODE_THRESHOLD -> performanceModeThreshold =
                    Mth.clamp(MIN_PERFORMANCE_MODE_THRESHOLD + value * (MAX_PERFORMANCE_MODE_THRESHOLD - MIN_PERFORMANCE_MODE_THRESHOLD),
                            MIN_PERFORMANCE_MODE_THRESHOLD,
                            MAX_PERFORMANCE_MODE_THRESHOLD);
            case WORLDMAP_CHUNK_LINE_THICKNESS -> chunkLineThickness =
                    Mth.clamp(MIN_CHUNK_LINE_THICKNESS + value * (MAX_CHUNK_LINE_THICKNESS - MIN_CHUNK_LINE_THICKNESS),
                            MIN_CHUNK_LINE_THICKNESS,
                            MAX_CHUNK_LINE_THICKNESS);

            default -> throw new IllegalArgumentException("Invalid float value! Add code to handle EnumOptionMinimap: " + option.getName());
        }

        bindZoom();
        bindCacheSize();
        bindPerformanceModeThreshold();
        bindChunkLineThickness();
    }

    private int calculateMinCacheSize() {
        // Region textures are skipped below performance mode threshold, so cache requirements should
        // be based on the zoom range where texture rendering is still active.
        float cacheReferenceZoom = Math.max(minZoom, performanceModeThreshold);
        return (int) ((1600.0F / cacheReferenceZoom / 256.0F + 4.0F) * (1100.0F / cacheReferenceZoom / 256.0F + 3.0F) * 1.35F);
    }

    private void bindCacheSize() {
        int minCacheSize = calculateMinCacheSize();
        cacheSize = Math.max(cacheSize, minCacheSize);
        cacheSize = Math.min(cacheSize, MAX_WORLDMAP_CACHE_SIZE);
    }

    private void bindZoom() {
        zoom = Math.max(zoom, minZoom);
        zoom = Math.min(zoom, maxZoom);
    }

    private void bindPerformanceModeThreshold() {
        performanceModeThreshold = Mth.clamp(performanceModeThreshold, MIN_PERFORMANCE_MODE_THRESHOLD, MAX_PERFORMANCE_MODE_THRESHOLD);
    }

    private void bindChunkLineThickness() {
        chunkLineThickness = Mth.clamp(chunkLineThickness, MIN_CHUNK_LINE_THICKNESS, MAX_CHUNK_LINE_THICKNESS);
    }

    public float getPerformanceModeThreshold() {
        return performanceModeThreshold;
    }

    public boolean isLiteralLineModeEnabled() {
        return literalLineMode;
    }

    public boolean isShowWaypointsInPerformanceModeEnabled() {
        return showWaypointsInPerformanceMode;
    }

    public float getChunkLineThickness() {
        return chunkLineThickness;
    }
}
