package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.interfaces.ISubSettingsManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Locale;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import net.minecraft.client.resources.language.I18n;

public class PersistentMapSettingsManager implements ISubSettingsManager {
    public enum SeedMapPalette {
        BIOME("Biome"),
        HEIGHT("Height"),
        TOPOGRAPHIC("Topographic");

        private final String displayName;

        SeedMapPalette(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public SeedMapPalette next() {
            SeedMapPalette[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static SeedMapPalette fromString(String value) {
            if (value == null || value.isBlank()) {
                return BIOME;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return BIOME;
            }
        }
    }

    public enum SeedMapStyle {
        FLAT("Flat", SeedMapPalette.BIOME, false, false),
        TOPOGRAPHIC("Topographic", SeedMapPalette.TOPOGRAPHIC, false, true),
        TOPOGRAPHIC_LINES("Topographic + Lines", SeedMapPalette.TOPOGRAPHIC, true, true),
        HEIGHT("Height", SeedMapPalette.HEIGHT, false, true);

        private final String displayName;
        private final SeedMapPalette palette;
        private final boolean contours;
        private final boolean needsTerrain;

        SeedMapStyle(String displayName, SeedMapPalette palette, boolean contours, boolean needsTerrain) {
            this.displayName = displayName;
            this.palette = palette;
            this.contours = contours;
            this.needsTerrain = needsTerrain;
        }

        public String displayName() {
            return displayName;
        }

        public SeedMapPalette palette() {
            return palette;
        }

        public boolean drawsContours() {
            return contours;
        }

        public boolean needsTerrain() {
            return needsTerrain;
        }

        public SeedMapStyle next() {
            SeedMapStyle[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static SeedMapStyle fromString(String value) {
            if (value == null || value.isBlank()) {
                return FLAT;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return FLAT;
            }
        }

        public static SeedMapStyle fromLegacy(SeedMapPalette palette, boolean contours) {
            return switch (palette) {
                case TOPOGRAPHIC -> contours ? TOPOGRAPHIC_LINES : TOPOGRAPHIC;
                case HEIGHT -> HEIGHT;
                case BIOME -> FLAT;
            };
        }
    }

    public enum WorldMapDimensionView {
        CURRENT("worldmap.realm.current"),
        OVERWORLD("worldmap.realm.overworld"),
        NETHER("worldmap.realm.nether"),
        END("worldmap.realm.end");

        private final String translationKey;

        WorldMapDimensionView(String translationKey) {
            this.translationKey = translationKey;
        }

        public String displayName() {
            return I18n.get(this.translationKey);
        }

        public String displayName(Level currentLevel) {
            if (this == CURRENT) {
                return currentRealmView(currentLevel).displayName();
            }
            return this.displayName();
        }

        public WorldMapDimensionView next(Level currentLevel) {
            WorldMapDimensionView currentRealm = currentRealmView(currentLevel);
            WorldMapDimensionView[] orderedAlternates = orderedAlternates(currentRealm);
            if (this == CURRENT || this == currentRealm) {
                return orderedAlternates[0];
            }
            if (this == orderedAlternates[0]) {
                return orderedAlternates[1];
            }
            return CURRENT;
        }

        public Identifier resolveIdentifier(Level currentLevel) {
            return switch (this) {
                case CURRENT -> currentLevel == null ? Level.OVERWORLD.identifier() : currentLevel.dimension().identifier();
                case OVERWORLD -> Level.OVERWORLD.identifier();
                case NETHER -> Level.NETHER.identifier();
                case END -> Level.END.identifier();
            };
        }

        public static WorldMapDimensionView fromString(String value) {
            if (value == null || value.isBlank()) {
                return CURRENT;
            }

            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CURRENT;
            }
        }

        private static WorldMapDimensionView currentRealmView(Level currentLevel) {
            if (currentLevel == null) {
                return OVERWORLD;
            }
            Identifier currentIdentifier = currentLevel.dimension().identifier();
            if (Level.NETHER.identifier().equals(currentIdentifier)) {
                return NETHER;
            }
            if (Level.END.identifier().equals(currentIdentifier)) {
                return END;
            }
            return OVERWORLD;
        }

        private static WorldMapDimensionView[] orderedAlternates(WorldMapDimensionView currentRealm) {
            return switch (currentRealm) {
                case NETHER -> new WorldMapDimensionView[]{OVERWORLD, END};
                case END -> new WorldMapDimensionView[]{OVERWORLD, NETHER};
                case CURRENT, OVERWORLD -> new WorldMapDimensionView[]{NETHER, END};
            };
        }
    }

    private static final int MIN_WORLDMAP_ZOOM_POWER = -9;
    private static final int MAX_WORLDMAP_ZOOM_POWER = 8;
    private static final int MAX_WORLDMAP_CACHE_SIZE = 20000;
    public static final float MIN_PERFORMANCE_MODE_THRESHOLD = 0.0025F;
    public static final float MAX_PERFORMANCE_MODE_THRESHOLD = 0.30F;
    public static final float MIN_CHUNK_LINE_THICKNESS = 0.05F;
    public static final float MAX_CHUNK_LINE_THICKNESS = 2.00F;
    public static final float MIN_SEEDMAP_CONTOUR_STRENGTH = 0.10F;
    public static final float MAX_SEEDMAP_CONTOUR_STRENGTH = 1.00F;
    public static final float MIN_SEEDMAP_MIN_ZOOM = 0.001953125F;
    public static final float MAX_SEEDMAP_MIN_ZOOM = 0.30F;
    public static final float MIN_SEEDMAP_TERRAIN_MIN_ZOOM = 0.001953125F;
    public static final float MAX_SEEDMAP_TERRAIN_MIN_ZOOM = 0.30F;
    public static final float MIN_SEEDMAP_PREVIEW_PADDING = 256.0F;
    public static final float MAX_SEEDMAP_PREVIEW_PADDING = 4096.0F;
    public static final float MIN_SEEDMAP_PREVIEW_RESOLUTION = 256.0F;
    public static final float MAX_SEEDMAP_PREVIEW_RESOLUTION = 2048.0F;
    public static final float MIN_SEEDMAP_PREVIEW_CACHE = 1.0F;
    public static final float MAX_SEEDMAP_PREVIEW_CACHE = 16.0F;
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
    public boolean showNewOldChunks = false;
    public SeedMapStyle seedMapStyle = SeedMapStyle.FLAT;
    private float seedMapContourStrength = 0.55F;
    private float seedMapMinZoom = MIN_SEEDMAP_MIN_ZOOM;
    private float seedMapTerrainMinZoom = MIN_SEEDMAP_TERRAIN_MIN_ZOOM;
    private float seedMapPreviewPadding = 512.0F;
    private float seedMapPreviewResolution = 1024.0F;
    private float seedMapPreviewCacheSize = 8.0F;
    public boolean seedMapPreviewUpdateWhileMoving = true;
    public boolean seedMapShowBiomeUnderCursor = false;
    public WorldMapDimensionView worldMapDimensionView = WorldMapDimensionView.CURRENT;

    @Override
    public void loadAll(File settingsFile) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(settingsFile));

            boolean styleKeySeen = false;
            SeedMapPalette legacyPalette = SeedMapPalette.BIOME;
            boolean legacyContours = false;

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
                    case "Worldmap SeedMap Style" -> {
                        seedMapStyle = SeedMapStyle.fromString(curLine[1]);
                        styleKeySeen = true;
                    }
                    case "Worldmap SeedMap Contours" -> legacyContours = Boolean.parseBoolean(curLine[1]);
                    case "Worldmap SeedMap Palette" -> legacyPalette = SeedMapPalette.fromString(curLine[1]);
                    case "Worldmap SeedMap Contour Strength" -> seedMapContourStrength = Float.parseFloat(curLine[1]);
                    case "Worldmap SeedMap Minimum Zoom" -> seedMapMinZoom = Float.parseFloat(curLine[1]);
                    case "Worldmap SeedMap Terrain Minimum Zoom" -> seedMapTerrainMinZoom = Float.parseFloat(curLine[1]);
                    case "Worldmap SeedMap Preview Padding" -> seedMapPreviewPadding = Float.parseFloat(curLine[1]);
                    case "Worldmap SeedMap Preview Resolution" -> seedMapPreviewResolution = Float.parseFloat(curLine[1]);
                    case "Worldmap SeedMap Preview Cache Size" -> seedMapPreviewCacheSize = Float.parseFloat(curLine[1]);
                    case "Worldmap SeedMap Preview Update While Moving" -> seedMapPreviewUpdateWhileMoving = Boolean.parseBoolean(curLine[1]);
                    case "Worldmap SeedMap Biome Under Cursor" -> seedMapShowBiomeUnderCursor = Boolean.parseBoolean(curLine[1]);
                    case "Worldmap Show Waypoints In Performance Mode" -> showWaypointsInPerformanceMode = Boolean.parseBoolean(curLine[1]);
                    case "Worldmap Literal Line Mode" -> literalLineMode = Boolean.parseBoolean(curLine[1]);
                    case "Worldmap Show New Old Chunks" -> showNewOldChunks = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Coordinates" -> showCoordinates = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Player Direction Arrow" -> showPlayerDirectionArrow = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Waypoints" -> showWaypoints = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Waypoint Names" -> showWaypointNames = Boolean.parseBoolean(curLine[1]);
                    case "Show Worldmap Distant Waypoints" -> showDistantWaypoints = Boolean.parseBoolean(curLine[1]);
                    case "Worldmap Dimension View" -> worldMapDimensionView = WorldMapDimensionView.fromString(curLine[1]);
                    case "Output Images" -> outputImages = Boolean.parseBoolean(curLine[1]);
                }
            }

            in.close();

            if (!styleKeySeen) {
                seedMapStyle = SeedMapStyle.fromLegacy(legacyPalette, legacyContours);
            }
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
        bindSeedMapContourStrength();
        bindSeedMapMinZoom();
        bindSeedMapTerrainMinZoom();
        bindSeedMapPreviewPadding();
        bindSeedMapPreviewResolution();
        bindSeedMapPreviewCacheSize();
    }

    @Override
    public void saveAll(PrintWriter out) {
        out.println("Worldmap Zoom:" + zoom);
        out.println("Worldmap Minimum Zoom:" + minZoom);
        out.println("Worldmap Maximum Zoom:" + maxZoom);
        out.println("Worldmap Cache Size:" + cacheSize);
        out.println("Worldmap Performance Mode Threshold:" + performanceModeThreshold);
        out.println("Worldmap Chunk Line Thickness:" + chunkLineThickness);
        out.println("Worldmap SeedMap Style:" + seedMapStyle.name());
        out.println("Worldmap SeedMap Contour Strength:" + seedMapContourStrength);
        out.println("Worldmap SeedMap Minimum Zoom:" + seedMapMinZoom);
        out.println("Worldmap SeedMap Terrain Minimum Zoom:" + seedMapTerrainMinZoom);
        out.println("Worldmap SeedMap Preview Padding:" + seedMapPreviewPadding);
        out.println("Worldmap SeedMap Preview Resolution:" + seedMapPreviewResolution);
        out.println("Worldmap SeedMap Preview Cache Size:" + seedMapPreviewCacheSize);
        out.println("Worldmap SeedMap Preview Update While Moving:" + seedMapPreviewUpdateWhileMoving);
        out.println("Worldmap SeedMap Biome Under Cursor:" + seedMapShowBiomeUnderCursor);
        out.println("Worldmap Show Waypoints In Performance Mode:" + showWaypointsInPerformanceMode);
        out.println("Worldmap Literal Line Mode:" + literalLineMode);
        out.println("Worldmap Show New Old Chunks:" + showNewOldChunks);
        out.println("Show Worldmap Coordinates:" + showCoordinates);
        out.println("Show Worldmap Player Direction Arrow:" + showPlayerDirectionArrow);
        out.println("Show Worldmap Waypoints:" + showWaypoints);
        out.println("Show Worldmap Waypoint Names:" + showWaypointNames);
        out.println("Show Worldmap Distant Waypoints:" + showDistantWaypoints);
        out.println("Worldmap Dimension View:" + worldMapDimensionView.name());
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
                    case WORLDMAP_SEEDMAP_CONTOUR_STRENGTH -> s + String.format(Locale.ROOT, "%.0f%%", value * 100.0F);
                    case WORLDMAP_SEEDMAP_MIN_ZOOM -> s + String.format(Locale.ROOT, "%.4fx", value);
                    case WORLDMAP_SEEDMAP_TERRAIN_MIN_ZOOM -> s + String.format(Locale.ROOT, "%.4fx", value);
                    case WORLDMAP_SEEDMAP_PREVIEW_PADDING -> s + String.format(Locale.ROOT, "%.0f blocks", value);
                    case WORLDMAP_SEEDMAP_PREVIEW_RESOLUTION -> s + String.format(Locale.ROOT, "%.0f px", value);
                    case WORLDMAP_SEEDMAP_PREVIEW_CACHE -> s + String.format(Locale.ROOT, "%.0f", value);

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
            case WORLDMAP_SHOW_NEW_OLD_CHUNKS -> showNewOldChunks;
            case WORLDMAP_SEEDMAP_UPDATE_WHILE_MOVING -> seedMapPreviewUpdateWhileMoving;
            case WORLDMAP_SEEDMAP_BIOME_UNDER_CURSOR -> seedMapShowBiomeUnderCursor;

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
            case WORLDMAP_SHOW_NEW_OLD_CHUNKS -> showNewOldChunks = !showNewOldChunks;
            case WORLDMAP_SEEDMAP_UPDATE_WHILE_MOVING -> seedMapPreviewUpdateWhileMoving = !seedMapPreviewUpdateWhileMoving;
            case WORLDMAP_SEEDMAP_BIOME_UNDER_CURSOR -> seedMapShowBiomeUnderCursor = !seedMapShowBiomeUnderCursor;

            default -> throw new IllegalArgumentException("Invalid boolean value! Add code to handle EnumOptionMinimap: " + option.getName());
        }
    }

    @Override
    public String getListValue(EnumOptionsMinimap option) {
        return switch (option) {
            case WORLDMAP_SEEDMAP_STYLE -> seedMapStyle.displayName();

            default -> throw new IllegalArgumentException("Invalid list value! Add code to handle EnumOptionMinimap: " + option.getName());
        };
    }

    @Override
    public void cycleListValue(EnumOptionsMinimap option) {
        switch (option) {
            case WORLDMAP_SEEDMAP_STYLE -> seedMapStyle = seedMapStyle.next();

            default -> throw new IllegalArgumentException("Invalid list value! Add code to handle EnumOptionMinimap: " + option.getName());
        }
    }

    @Override
    public float getFloatValue(EnumOptionsMinimap option) {
        return switch (option) {
            case MIN_ZOOM -> minZoomPower;
            case MAX_ZOOM -> maxZoomPower;
            case CACHE_SIZE -> cacheSize;
            case WORLDMAP_PERFORMANCE_MODE_THRESHOLD -> performanceModeThreshold;
            case WORLDMAP_CHUNK_LINE_THICKNESS -> chunkLineThickness;
            case WORLDMAP_SEEDMAP_CONTOUR_STRENGTH -> seedMapContourStrength;
            case WORLDMAP_SEEDMAP_MIN_ZOOM -> seedMapMinZoom;
            case WORLDMAP_SEEDMAP_TERRAIN_MIN_ZOOM -> seedMapTerrainMinZoom;
            case WORLDMAP_SEEDMAP_PREVIEW_PADDING -> seedMapPreviewPadding;
            case WORLDMAP_SEEDMAP_PREVIEW_RESOLUTION -> seedMapPreviewResolution;
            case WORLDMAP_SEEDMAP_PREVIEW_CACHE -> seedMapPreviewCacheSize;

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
            case WORLDMAP_SEEDMAP_CONTOUR_STRENGTH -> seedMapContourStrength =
                    Mth.clamp(MIN_SEEDMAP_CONTOUR_STRENGTH + value * (MAX_SEEDMAP_CONTOUR_STRENGTH - MIN_SEEDMAP_CONTOUR_STRENGTH),
                            MIN_SEEDMAP_CONTOUR_STRENGTH,
                            MAX_SEEDMAP_CONTOUR_STRENGTH);
            case WORLDMAP_SEEDMAP_MIN_ZOOM -> seedMapMinZoom =
                    Mth.clamp(MIN_SEEDMAP_MIN_ZOOM + value * (MAX_SEEDMAP_MIN_ZOOM - MIN_SEEDMAP_MIN_ZOOM),
                            MIN_SEEDMAP_MIN_ZOOM,
                            MAX_SEEDMAP_MIN_ZOOM);
            case WORLDMAP_SEEDMAP_TERRAIN_MIN_ZOOM -> seedMapTerrainMinZoom =
                    Mth.clamp(MIN_SEEDMAP_TERRAIN_MIN_ZOOM + value * (MAX_SEEDMAP_TERRAIN_MIN_ZOOM - MIN_SEEDMAP_TERRAIN_MIN_ZOOM),
                            MIN_SEEDMAP_TERRAIN_MIN_ZOOM,
                            MAX_SEEDMAP_TERRAIN_MIN_ZOOM);
            case WORLDMAP_SEEDMAP_PREVIEW_PADDING -> seedMapPreviewPadding =
                    Mth.clamp(MIN_SEEDMAP_PREVIEW_PADDING + value * (MAX_SEEDMAP_PREVIEW_PADDING - MIN_SEEDMAP_PREVIEW_PADDING),
                            MIN_SEEDMAP_PREVIEW_PADDING,
                            MAX_SEEDMAP_PREVIEW_PADDING);
            case WORLDMAP_SEEDMAP_PREVIEW_RESOLUTION -> seedMapPreviewResolution =
                    Mth.clamp(MIN_SEEDMAP_PREVIEW_RESOLUTION + value * (MAX_SEEDMAP_PREVIEW_RESOLUTION - MIN_SEEDMAP_PREVIEW_RESOLUTION),
                            MIN_SEEDMAP_PREVIEW_RESOLUTION,
                            MAX_SEEDMAP_PREVIEW_RESOLUTION);
            case WORLDMAP_SEEDMAP_PREVIEW_CACHE -> seedMapPreviewCacheSize =
                    Mth.clamp(MIN_SEEDMAP_PREVIEW_CACHE + value * (MAX_SEEDMAP_PREVIEW_CACHE - MIN_SEEDMAP_PREVIEW_CACHE),
                            MIN_SEEDMAP_PREVIEW_CACHE,
                            MAX_SEEDMAP_PREVIEW_CACHE);

            default -> throw new IllegalArgumentException("Invalid float value! Add code to handle EnumOptionMinimap: " + option.getName());
        }

        bindZoom();
        bindCacheSize();
        bindPerformanceModeThreshold();
        bindChunkLineThickness();
        bindSeedMapContourStrength();
        bindSeedMapMinZoom();
        bindSeedMapTerrainMinZoom();
        bindSeedMapPreviewPadding();
        bindSeedMapPreviewResolution();
        bindSeedMapPreviewCacheSize();
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

    private void bindSeedMapContourStrength() {
        seedMapContourStrength = Mth.clamp(seedMapContourStrength, MIN_SEEDMAP_CONTOUR_STRENGTH, MAX_SEEDMAP_CONTOUR_STRENGTH);
    }

    private void bindSeedMapMinZoom() {
        seedMapMinZoom = Mth.clamp(seedMapMinZoom, MIN_SEEDMAP_MIN_ZOOM, MAX_SEEDMAP_MIN_ZOOM);
    }

    private void bindSeedMapTerrainMinZoom() {
        seedMapTerrainMinZoom = Mth.clamp(seedMapTerrainMinZoom, MIN_SEEDMAP_TERRAIN_MIN_ZOOM, MAX_SEEDMAP_TERRAIN_MIN_ZOOM);
    }

    private void bindSeedMapPreviewPadding() {
        seedMapPreviewPadding = Mth.clamp(seedMapPreviewPadding, MIN_SEEDMAP_PREVIEW_PADDING, MAX_SEEDMAP_PREVIEW_PADDING);
    }

    private void bindSeedMapPreviewResolution() {
        seedMapPreviewResolution = Mth.clamp(seedMapPreviewResolution, MIN_SEEDMAP_PREVIEW_RESOLUTION, MAX_SEEDMAP_PREVIEW_RESOLUTION);
    }

    private void bindSeedMapPreviewCacheSize() {
        seedMapPreviewCacheSize = Mth.clamp(seedMapPreviewCacheSize, MIN_SEEDMAP_PREVIEW_CACHE, MAX_SEEDMAP_PREVIEW_CACHE);
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

    public float getSeedMapContourStrength() {
        return seedMapContourStrength;
    }

    public float getSeedMapMinZoom() {
        return seedMapMinZoom;
    }

    public float getSeedMapTerrainMinZoom() {
        return seedMapTerrainMinZoom;
    }

    public int getSeedMapPreviewPadding() {
        return Math.round(seedMapPreviewPadding);
    }

    public int getSeedMapPreviewResolution() {
        return Math.round(seedMapPreviewResolution);
    }

    public int getSeedMapPreviewCacheSize() {
        return Math.max(1, Math.round(seedMapPreviewCacheSize));
    }

    public int getSeedMapPreviewSettingsHash() {
        int hash = seedMapStyle.ordinal();
        hash = 31 * hash + Float.floatToIntBits(seedMapContourStrength);
        hash = 31 * hash + Float.floatToIntBits(seedMapMinZoom);
        hash = 31 * hash + Float.floatToIntBits(seedMapTerrainMinZoom);
        return hash;
    }
}
