package com.mamiyaotaru.voxelmap.persistent;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;
import com.github.cubiomes.TerrainNoise;
import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.NewerNewChunksManager;
import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSharePlayerSettings;
import com.mamiyaotaru.voxelmap.gui.GuiAddWaypoint;
import com.mamiyaotaru.voxelmap.gui.GuiMinimapOptions;
import com.mamiyaotaru.voxelmap.gui.GuiSubworldsSelect;
import com.mamiyaotaru.voxelmap.gui.GuiWaypoints;
import com.mamiyaotaru.voxelmap.gui.IGuiWaypoints;
import com.mamiyaotaru.voxelmap.integration.BaritoneHelper;
import com.mamiyaotaru.voxelmap.gui.overridden.Popup;
import com.mamiyaotaru.voxelmap.gui.overridden.PopupGuiButton;
import com.mamiyaotaru.voxelmap.gui.overridden.PopupGuiScreen;
import com.mamiyaotaru.voxelmap.interfaces.AbstractMapData;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperChestLootData;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperChestLootWidget;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperLocatorService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperLootService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperMarker;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerDetection;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerMarker;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCompat;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperClusterManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperBuriedTreasureClusterService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperVaultService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperVaultLootWidget;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperImportedDatapackManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperNative;
import com.mamiyaotaru.voxelmap.rendering.RenderUtils;
import com.mamiyaotaru.voxelmap.rendering.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.textures.ConfiguredDynamicTexture;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.textures.TextureAtlas;
import com.mamiyaotaru.voxelmap.util.BackgroundImageInfo;
import com.mamiyaotaru.voxelmap.util.BiomeMapData;
import com.mamiyaotaru.voxelmap.util.BiomeRepository;
import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.ChunkBounds;
import com.mamiyaotaru.voxelmap.persistent.explored.ExploredCellQuery;
import com.mamiyaotaru.voxelmap.util.ColorUtils;
import com.mamiyaotaru.voxelmap.util.CommandUtils;
import com.mamiyaotaru.voxelmap.util.AppChatMessages;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.textures.DynamicMutableTexture;
import com.mamiyaotaru.voxelmap.util.EasingUtils;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.ImageUtils;
import com.mamiyaotaru.voxelmap.rendering.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.util.TextUtils;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import com.mamiyaotaru.voxelmap.util.DimensionManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import org.joml.Vector2f;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Locale;
public class GuiPersistentMap extends PopupGuiScreen implements IGuiWaypoints {
    private static final int COORD_TEXT_COLOR_OK = 0xFFFFFFFF;
    private static final int COORD_TEXT_COLOR_ERROR = 0xFFFF0000;
    private static final int WAYPOINT_LABEL_LINE_LIMIT = 4;
    private static final int WAYPOINT_LABEL_PADDING = 2;
    private static final int WAYPOINT_CLUSTER_BUCKET_SIZE = 56;
    private static final int WAYPOINT_SEARCH_BOX_WIDTH = 140;
    private static final int FAR_ZOOM_MAX_REGION_RADIUS_MOVING = 320;
    private static final int FAR_ZOOM_MAX_REGION_RADIUS_STILL = 768;
    private static double pendingCenterX = Double.NaN;
    private static double pendingCenterZ = Double.NaN;
    private final Random generator = new Random();
    private final PersistentMap persistentMap;
    private final PlotManager plotManager = VoxelConstants.getVoxelMapInstance().getPlotManager();
    private final WaypointManager waypointManager;
    private final MapSettingsManager mapOptions;
    private final RadarSettingsManager radarOptions;
    private final PersistentMapSettingsManager options;
    private final SeedMapperSettingsManager seedMapperOptions;
    protected String screenTitle = "World Map";
    protected String worldNameDisplay = "";
    protected int worldNameDisplayLength;
    protected int maxWorldNameDisplayLength;
    private String subworldName = "";
    private PopupGuiButton buttonMultiworld;
    private int top;
    private int bottom;
    private boolean oldNorth;
    private boolean editingCoordinates;
    private boolean lastEditingCoordinates;
    private EditBox coordinateXInput;
    private EditBox coordinateZInput;
    private EditBox waypointSearchInput;
    private int coordinateLabelLeft;
    private int coordinateLabelRight;
    private int coordinateLabelTop;
    private int coordinateLabelBottom;
    private int coordinateHoverX;
    private int coordinateHoverZ;
    private long lastMapLeftClickMs;
    private int lastMapLeftClickX;
    private int lastMapLeftClickY;
    int centerX;
    int centerY;
    float mapCenterX;
    float mapCenterZ;
    float deltaX;
    float deltaY;
    float deltaXonRelease;
    float deltaYonRelease;
    long timeOfRelease;
    boolean mouseCursorShown = true;
    long timeAtLastTick;
    long timeOfLastKBInput;
    long timeOfLastMouseInput;
    float lastMouseX;
    float lastMouseY;
    protected int mouseX;
    protected int mouseY;
    boolean leftMouseButtonDown;
    float zoom;
    float zoomStart;
    float zoomGoal;
    long timeOfZoom;
    float zoomDirectX;
    float zoomDirectY;
    float zoomAnchorMapCenterX;
    float zoomAnchorMapCenterZ;
    float zoomAnchorScale;
    private float scScale = 1.0F;
    private float guiToMap = 2.0F;
    private float mapToGui = 0.5F;
    private float mouseDirectToMap = 1.0F;
    private float guiToDirectMouse = 2.0F;
    private static boolean gotSkin;
    private boolean closed;
    private CachedRegion[] regions = new CachedRegion[0];
    BackgroundImageInfo backGroundImageInfo;
    private final WorldMapViewCache<BiomeViewKey, BiomeMapData> biomeViews = new WorldMapViewCache<>(4L << 20, data -> (long) data.getWidth() * data.getHeight() * 32);
    private static final CachedRegion[] NO_REGIONS = new CachedRegion[0];
    private BiomeDiskSource biomeDiskSource;
    private record BiomeDiskSource(PersistentMap map, net.minecraft.client.multiplayer.ClientLevel world, String worldName,
            String subworld, Identifier dimension, java.io.File directory) { }
    private record BiomeViewKey(CachedRegion[] regions, BiomeDiskSource disk, long version, Identifier dimension, float centerX, float centerY,
            double mapX, double mapZ, float guiToMap, float mouseToMap, float pixelsX, float pixelsY, boolean north) { }
    private float mapPixelsX;
    private float mapPixelsY;
    private final Object closedLock = new Object();
    private Component multiworldButtonName;
    private MutableComponent multiworldButtonNameRed;
    int sideMargin = 10;
    int buttonCount = 5;
    int buttonSeparation = 4;
    int buttonWidth = 66;
    public boolean editClicked;
    public boolean deleteClicked;
    public boolean addClicked;
    Waypoint newWaypoint;
    Waypoint selectedWaypoint;
    Waypoint pendingDeleteWaypoint;
    Waypoint hoverdWaypoint;
    SeedMapperMarker selectedSeedMapperMarker;
    String selectedSeedMapperWorldKey;
    Waypoint selectedSeedMapperWaypoint;
    Waypoint selectedSeedMapperAssociatedWaypoint;
    private final Map<String, String> seedMapperHighlightWaypoints = new HashMap<>();
    private PopupGuiButton buttonWaypoints;
    private PopupGuiButton buttonExploredChunks;
    private PopupGuiButton buttonNewOldChunks;
    private PopupGuiButton buttonWorldMapEntities;
    private PopupGuiButton buttonRealmView;
    private PopupGuiButton buttonSeedPreview;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Identifier voxelmapSkinLocation = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "persistentmap/playerskin");
    private final Identifier crosshairResource = Identifier.parse("textures/gui/sprites/hud/crosshair.png");
    private final Identifier seedMapperDirectionArrowResource = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/seedmapper/arrow.png");
    private final Identifier seedPreviewTextureLocation = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "persistentmap/seedpreview");
    private final List<FeatureIconHitbox> seedMapperIconHitboxes = new ArrayList<>();
    private final List<SeedMapperMarkerHitbox> seedMapperMarkerHitboxes = new ArrayList<>();
    private Set<SeedMapperFeature> seedMapperSavedToggles;
    private SeedMapperFeature seedMapperIsolatedFeature;
    private int seedMapperIsolationBaseHash;
    private int seedMapperLegendPage = 0;
    private int seedMapperLegendMaxPage = 0;
    private int legendPrevX;
    private int legendPrevY;
    private int legendNextX;
    private int legendNextY;
    private int legendArrowSize;
    private int seedMapperStripLeft = -1;
    private int seedMapperStripRight = -1;
    private int seedMapperStripTop = -1;
    private int seedMapperStripBottom = -1;
    private int seedMapperTitleLeft = -1;
    private int seedMapperTitleRight = -1;
    private int seedMapperTitleTop = -1;
    private int seedMapperTitleBottom = -1;
    private int seedHeaderLeft = -1;
    private int seedHeaderRight = -1;
    private int seedHeaderTop = -1;
    private int seedHeaderBottom = -1;
    private long seedMapperLastMarkerQueryMs = 0L;
    private SeedMapperQueryCacheKey seedMapperLastMarkerQueryKey;
    private List<SeedMapperMarker> seedMapperLastMarkerResult = List.of();
    private boolean seedMapperQueryLoading = false;
    private String seedMapperLoadingSummary = "";
    private SeedMapperQueryCacheKey seedMapperLoadingKey;
    private long seedMapperLoadingStickyUntilMs = 0L;
    private static final int SEED_PREVIEW_CONTOUR_INTERVAL = 16;
    private static final long SEED_PREVIEW_REQUEST_INTERVAL_MOVING_MS = 125L;
    private static final long SEED_PREVIEW_REQUEST_INTERVAL_STILL_MS = 50L;
    private DynamicMutableTexture seedPreviewTexture;
    private SeedPreviewQueryCacheKey seedPreviewDisplayedKey;
    private SeedPreviewQueryCacheKey seedPreviewPendingKey;
    private int[] seedPreviewPendingPixels;
    private Future<?> seedPreviewFuture;
    private WorldMapProgress.Task seedPreviewProgress;
    private long seedPreviewLastRequestMs = 0L;
    private boolean seedPreviewLoading = false;
    private boolean seedPreviewDrewThisFrame = false;
    private long seedPreviewLoadingStartedMs = 0L;
    private static final long SEED_PREVIEW_LOADING_DEBOUNCE_MS = 500L;
    private static final ExecutorService seedBiomeNameWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Voxelmap Cursor Biome");
        thread.setDaemon(true);
        return thread;
    });
    private final AsyncLatestValue<SeedBiomeNameKey, String> seedBiomeNames =
            new AsyncLatestValue<>(seedBiomeNameWorker, 128);
    private record SeedBiomeNameKey(long seed, int dimension, int quartX, int quartZ,
                                    int mcVersion, int flags, int sampleY) { }
    private final Object seedPreviewLock = new Object();
    private int seedPreviewCacheLimit = 8;
    private final LinkedHashMap<SeedPreviewQueryCacheKey, int[]> seedPreviewCache =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<SeedPreviewQueryCacheKey, int[]> eldest) {
                    return size() > Math.max(1, seedPreviewCacheLimit);
                }
            };
    private static final ExecutorService seedPreviewCoordinator = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Voxelmap SeedPreview Coordinator");
        thread.setDaemon(true);
        return thread;
    });
    private record SeedHeightChunk(long seed, int dimension, int version, int flags, int x, int z) { }
    private final Map<SeedHeightChunk, int[]> seedHeightChunks = new LinkedHashMap<>(256, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<SeedHeightChunk, int[]> eldest) { return size() > 8192; }
    };
    private static final int SEED_PREVIEW_WORKER_THREADS =
            Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() - 1, 8));
    private static final ExecutorService seedPreviewSampler =
            Executors.newFixedThreadPool(SEED_PREVIEW_WORKER_THREADS, r -> {
                Thread t = new Thread(r, "Voxelmap SeedPreview Sampler");
                t.setDaemon(true);
                return t;
            });
    // Buried-treasure cluster search walks the whole world border. Keep it off
    // the preview workers so a cluster query cannot stall map band sampling.
    private static final ExecutorService clusterSearchExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Voxelmap Treasure Clusters");
                t.setDaemon(true);
                return t;
            });
    private final Map<Integer, Integer> seedPreviewBiomeColorCache = new HashMap<>();
    private final Map<Integer, Boolean> seedPreviewOceanicCache = new HashMap<>();
    private record OverlayViewKey(ChunkBounds bounds, int cell, long version, boolean literal, boolean outlines, int budget) { }
    private record TrailView(ExploredLineMesher.Result mesh, List<CellGrid> squares, int cell, boolean outlines) {
        long bytes() { return (long) mesh.segments().length * 4 + (long) mesh.nodeCoords().length * 4
                + mesh.nodeLinked().length + (squares == null ? 0 : squares.stream().mapToLong(grid -> grid.cells.length).sum()); }
    }
    private record AreaView(List<NewOldChunkRenderRect> oldRects, List<NewOldChunkRenderRect> newRects) {
        long bytes() { return (long) (oldRects.size() + newRects.size()) * 32; }
    }
    private record RenderSourceKey(String layer, OverlayViewKey view) { }
    private record GeometryKey(Object source, WorldMapGeometry.Bounds bounds, float thickness, boolean nodes) { }
    private record GeometryView(GeometryKey key, WorldMapGeometry.Quads quads) { }
    private final WorldMapViewCache<OverlayViewKey, TrailView> trailViews = new WorldMapViewCache<OverlayViewKey, TrailView>(48L << 20, TrailView::bytes).trackProgress(layer -> "Chunk Trails");
    private final WorldMapViewCache<OverlayViewKey, AreaView> areaViews = new WorldMapViewCache<OverlayViewKey, AreaView>(16L << 20, AreaView::bytes).trackProgress(layer -> "New/Old Chunks");
    private final WorldMapRasterLayers rasterLayers = new WorldMapRasterLayers();
    private final WorldMapViewCache<GeometryKey, GeometryView> geometryViews = new WorldMapViewCache<GeometryKey, GeometryView>(64L << 20, view -> view.quads().bytes()).trackProgress(GuiPersistentMap::loadingLayerLabel);
    private final Map<String, Integer> trailDetail = new HashMap<>();
    private int trailZoomBucket = Integer.MIN_VALUE;
    private long overlayProfileLastMs;

    private float[] exploredQuadCoords = new float[4096];
    private int[] exploredQuadColors = new int[1024];
    private int exploredQuadCount = 0;
    private final List<PlayerLayerStatusHitbox> playerLayerStatusHitboxes = new ArrayList<>();
    private final List<WaypointLabelBounds> waypointLabelBounds = new ArrayList<>();
    private final java.util.Map<Long, List<WaypointLabelBounds>> waypointLabelBuckets = new java.util.HashMap<>();
    private final java.util.Map<Long, WaypointClusterData> waypointIconBuckets = new java.util.HashMap<>();
    private final java.util.LinkedHashMap<String, Integer> waypointTextWidths = new java.util.LinkedHashMap<>(128, .75F, true);
    private List<PendingWaypointLabel> lastLabelInput = List.of();
    private List<PlacedWaypointLabel> lastLabelLayout = List.of();
    private java.util.Map<Long, WaypointClusterData> lastLabelClusters = java.util.Map.of();
    private int lastLabelOptions;
    private String frameWaypointSearch = "";
    private long waypointFontVersion;
    private record PlacedWaypointLabel(PendingWaypointLabel label, int row) { }

    private final List<PendingWaypointLabel> pendingWaypointLabels = new ArrayList<>();
    private final Map<Long, WaypointClusterData> waypointClusters = new HashMap<>();
    private long newOldChunkLastMotionMs = 0L;
    private SeedMapperChestLootWidget seedMapperChestLootWidget;
    private SeedMapperVaultLootWidget seedMapperVaultLootWidget;
    private long seedMapperLootWidgetOpenedAtMs;
    private int seedMapperLootWidgetOpenedX;
    private int seedMapperLootWidgetOpenedY;
    private Set<SeedMapperFeature> seedMapperAllFeaturesSaved;
    private boolean currentDragging;
    private boolean plotMode;
    private boolean plotStartSet;
    private boolean ignoreNextPlotRelease;
    private double ignoredPlotReleaseX = Double.NaN;
    private double ignoredPlotReleaseY = Double.NaN;
    private boolean plotClickHandled;
    private long lastPlotInputMs;
    private double lastPlotInputX = Double.NaN;
    private double lastPlotInputY = Double.NaN;
    private double plotStartX;
    private double plotStartZ;
    private PlotManager.Plot selectedPlot;
    private PlotManager.Plot editingPlot;
    private int editingPlotEndpoint;
    private PlotManager.Plot placingDuplicatePlot;
    private boolean rightMapDrag;
    private boolean rightMapDragMoved;
    private boolean keySprintPressed;
    private boolean keyUpPressed;
    private boolean keyDownPressed;
    private boolean keyLeftPressed;
    private boolean keyRightPressed;
    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 16;
    private static final int COMPLETED_TICK_COLOR = 0xFF22C84A;
    private static final int COMPLETED_TICK_OUTLINE_COLOR = 0xFF000000;
    public GuiPersistentMap(Screen parent) {
        this.lastScreen = parent;

        this.waypointManager = VoxelConstants.getVoxelMapInstance().getWaypointManager();
        mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
        radarOptions = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        this.persistentMap = VoxelConstants.getVoxelMapInstance().getPersistentMap();
        this.options = VoxelConstants.getVoxelMapInstance().getPersistentMapOptions();
        this.seedMapperOptions = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        if (parent == null) {
            this.options.worldMapDimensionView = PersistentMapSettingsManager.WorldMapDimensionView.CURRENT;
        }
        this.zoom = this.options.zoom;
        this.zoomStart = this.options.zoom;
        this.zoomGoal = this.options.zoom;
        this.persistentMap.setLightMapArray(VoxelConstants.getVoxelMapInstance().getMap().getLightmapArray());
        if (!gotSkin) {
            this.getSkin();
        }

    }

    public static void openAndCenterOn(Screen parent, double x, double z) {
        pendingCenterX = x;
        pendingCenterZ = z;
        VoxelConstants.getMinecraft().gui.setScreen(new GuiPersistentMap(parent));
    }

    private void getSkin() {
        BufferedImage skinImage = ImageUtils.createBufferedImageFromIdentifier(VoxelConstants.getPlayer().getSkin().body().texturePath());

        if (skinImage == null) {
            if (VoxelConstants.DEBUG) {
                VoxelConstants.getLogger().warn("Got no player skin!");
            }
            return;
        }

        gotSkin = true;

        boolean showHat = VoxelConstants.getPlayer().isModelPartShown(PlayerModelPart.HAT);
        if (showHat) {
            skinImage = ImageUtils.addImages(ImageUtils.loadImage(skinImage, 8, 8, 8, 8), ImageUtils.loadImage(skinImage, 40, 8, 8, 8), 0.0F, 0.0F, 8, 8);
        } else {
            skinImage = ImageUtils.loadImage(skinImage, 8, 8, 8, 8);
        }

        float scale = skinImage.getWidth() / 8.0F;
        skinImage = ImageUtils.fillOutline(ImageUtils.pad(ImageUtils.scaleImage(skinImage, 2.0F / scale)), true, 1);

        ConfiguredDynamicTexture texture = new ConfiguredDynamicTexture(() -> "Voxelmap player", ImageUtils.nativeImageFromBufferedImage(skinImage));
        texture.setSampler(RenderUtils.getSampler(true, false));
        minecraft.getTextureManager().register(voxelmapSkinLocation, texture);
    }

    @Override
    public void init() {
        String coordinateXValue = this.coordinateXInput == null ? "" : this.coordinateXInput.getValue();
        String coordinateZValue = this.coordinateZInput == null ? "" : this.coordinateZInput.getValue();
        String searchValue = this.waypointSearchInput == null ? "" : this.waypointSearchInput.getValue();

        this.oldNorth = mapOptions.oldNorth;
        this.centerAt(this.options.mapX, this.options.mapZ);
        if (!Double.isNaN(pendingCenterX) && !Double.isNaN(pendingCenterZ)) {
            this.centerAt(pendingCenterX, pendingCenterZ);
            pendingCenterX = Double.NaN;
            pendingCenterZ = Double.NaN;
        }
        if (minecraft.gui.screen() == this) {
            this.closed = false;
        }

        normalizeWorldMapDimensionView();
        loadPlotsForViewedDimension();
        this.screenTitle = "VoxelMapper by CevAPI";
        this.buildWorldName();
        this.leftMouseButtonDown = false;
        this.sideMargin = 10;
        boolean showMultiworldButton = !this.options.hideMultiworldButton
                && !minecraft.hasSingleplayerServer()
                && !VoxelConstants.getVoxelMapInstance().getWaypointManager().receivedAutoSubworldName();
        this.buttonCount = showMultiworldButton ? 9 : 8;
        this.buttonSeparation = 4;
        this.buttonWidth = (this.width - this.sideMargin * 2 - this.buttonSeparation * (this.buttonCount - 1)) / this.buttonCount;
        int buttonIndex = 0;
        this.buttonWaypoints = new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("options.minimap.waypoints"), button -> minecraft.gui.setScreen(new GuiWaypoints(this)), this);
        this.addRenderableWidget(this.buttonWaypoints);
        this.multiworldButtonName = Component.translatable(VoxelConstants.isRealmServer() ? "menu.online" : "options.worldmap.multiworld");
        this.multiworldButtonNameRed = (Component.translatable(VoxelConstants.isRealmServer() ? "menu.online" : "options.worldmap.multiworld")).withStyle(ChatFormatting.RED);
        if (showMultiworldButton) {
            this.addRenderableWidget(this.buttonMultiworld = new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, this.multiworldButtonName, button -> minecraft.gui.setScreen(new GuiSubworldsSelect(this)), this));
        }

        this.buttonRealmView = this.addRenderableWidget(new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("worldmap.realm.button", getDisplayedWorldMapDimensionName()), button -> cycleWorldMapDimensionView(), this));
        this.buttonSeedPreview = this.addRenderableWidget(new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("worldmap.seedpreview.button", I18n.get(this.seedMapperOptions.worldMapSeedPreview ? "options.on" : "options.off")), button -> toggleSeedPreview(), this));
        this.buttonExploredChunks = this.addRenderableWidget(new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.empty(), button -> toggleExploredChunks(), this));
        this.buttonNewOldChunks = this.addRenderableWidget(new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.empty(), button -> toggleNewOldChunks(), this));
        this.buttonWorldMapEntities = this.addRenderableWidget(new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.empty(), button -> toggleWorldMapEntities(), this));
        this.addRenderableWidget(new PopupGuiButton(this.sideMargin + buttonIndex++ * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("menu.options"), button -> minecraft.gui.setScreen(new GuiMinimapOptions(this)), this));
        this.addRenderableWidget(new PopupGuiButton(this.sideMargin + buttonIndex * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("gui.done"), button -> this.onClose(), this));
        refreshWorldMapControlLabels();
        this.coordinateXInput = new EditBox(this.getFont(), this.sideMargin, 10, 68, 20, Component.literal("X"));
        this.coordinateZInput = new EditBox(this.getFont(), this.sideMargin + 74, 10, 68, 20, Component.literal("Z"));
        this.coordinateXInput.setMaxLength(12);
        this.coordinateZInput.setMaxLength(12);
        this.coordinateXInput.setValue(coordinateXValue);
        this.coordinateZInput.setValue(coordinateZValue);
        this.coordinateXInput.setHint(Component.literal("X"));
        this.coordinateZInput.setHint(Component.literal("Z"));
        this.coordinateXInput.setVisible(false);
        this.coordinateZInput.setVisible(false);
        this.coordinateXInput.active = false;
        this.coordinateZInput.active = false;
        // These fields are drawn and routed explicitly while editing.  Registering
        // them as normal children as well causes 26.3's Screen dispatcher to
        // consume the click before this map can switch focus between X and Z.
        this.coordinateLabelLeft = -1;
        this.coordinateLabelRight = -1;
        this.coordinateLabelTop = -1;
        this.coordinateLabelBottom = -1;
        this.top = 32;
        this.bottom = this.getHeight() - 32;
        this.waypointSearchInput = new EditBox(this.getFont(), this.getWidth() - this.sideMargin - WAYPOINT_SEARCH_BOX_WIDTH, this.bottom - 22, WAYPOINT_SEARCH_BOX_WIDTH, 20, Component.translatable("worldmap.waypointSearch"));
        this.waypointSearchInput.setMaxLength(48);
        this.waypointSearchInput.setValue(searchValue);
        this.waypointSearchInput.setHint(Component.translatable("worldmap.waypointSearch"));
        this.waypointSearchInput.setVisible(true);
        this.waypointSearchInput.active = true;
        this.waypointSearchInput.setFocused(false);
        this.addRenderableWidget(this.waypointSearchInput);
        this.centerX = this.getWidth() / 2;
        this.centerY = (this.bottom - this.top) / 2;
        this.scScale = (float) minecraft.getWindow().getGuiScale();
        this.mapPixelsX = minecraft.getWindow().getWidth();
        this.mapPixelsY = (minecraft.getWindow().getHeight() - (int) (64.0F * this.scScale));
        this.timeAtLastTick = System.currentTimeMillis();
        ensureSeedPreviewTextureSize(1, 1);
    }

    @Override
    public void added() {
        currentDragging = false;
        super.added();
    }

    private void centerAt(int x, int z) {
        centerAt((double) x, (double) z);
    }

    private void centerAt(double x, double z) {
        // Stop ongoing inertial panning so manual coordinate jumps stay put.
        this.deltaX = 0.0F;
        this.deltaY = 0.0F;
        this.deltaXonRelease = 0.0F;
        this.deltaYonRelease = 0.0F;
        this.timeOfRelease = 0L;
        if (this.oldNorth) {
            this.mapCenterX = (float) (-z);
            this.mapCenterZ = (float) x;
        } else {
            this.mapCenterX = (float) x;
            this.mapCenterZ = (float) z;
        }
        // Keep the persisted center in sync immediately.  The render tick also
        // writes these values, but doing it here prevents a coordinate submit
        // from being overwritten before the next frame is drawn.
        this.options.mapX = (int) x;
        this.options.mapZ = (int) z;

    }

    private void refreshWorldMapControlLabels() {
        if (this.buttonRealmView != null) {
            this.buttonRealmView.setMessage(Component.translatable("worldmap.realm.button", getDisplayedWorldMapDimensionName()));
        }
        if (this.buttonSeedPreview != null) {
            boolean seedPreviewAvailable = hasWorldMapSeed();
            this.buttonSeedPreview.active = seedPreviewAvailable;
            this.buttonSeedPreview.setMessage(Component.translatable(
                    "worldmap.seedpreview.button",
                    I18n.get(seedPreviewAvailable
                            ? (this.seedMapperOptions.worldMapSeedPreview ? "options.on" : "options.off")
                            : "worldmap.seedpreview.unavailable")));
        }
        if (this.buttonExploredChunks != null) {
            this.buttonExploredChunks.active = this.radarOptions.showExploredChunks;
            this.buttonExploredChunks.setMessage(Component.translatable("worldmap.explored.button", I18n.get(this.options.showExploredChunks ? "options.on" : "options.off")));
        }
        if (this.buttonNewOldChunks != null) {
            this.buttonNewOldChunks.active = this.radarOptions.showNewerNewChunks;
            this.buttonNewOldChunks.setMessage(Component.translatable("worldmap.newold.button", I18n.get(this.options.showNewOldChunks ? "options.on" : "options.off")));
        }
        if (this.buttonWorldMapEntities != null) {
            this.buttonWorldMapEntities.active = hasWorldMapEntitySource();
            this.buttonWorldMapEntities.setMessage(Component.translatable("worldmap.entities.button", I18n.get(this.options.showWorldMapEntities ? "options.on" : "options.off")));
        }
    }

    private boolean hasWorldMapEntitySource() {
        return this.seedMapperOptions.containerDetection
                || this.seedMapperOptions.workstationDetection
                || this.seedMapperOptions.redstoneDetection
                || this.seedMapperOptions.spawnerDetection;
    }

    private String getWorldMapSeedFallbackText() {
        String worldSeed = VoxelConstants.getVoxelMapInstance().getWorldSeed();
        return worldSeed == null ? "" : worldSeed.trim();
    }

    private String getWorldMapSeedText() {
        return this.seedMapperOptions.resolveSeedText(getWorldMapSeedFallbackText());
    }

    private boolean hasWorldMapSeed() {
        return this.seedMapperOptions.hasSeed(getWorldMapSeedFallbackText());
    }

    private long resolveWorldMapSeed() {
        return this.seedMapperOptions.resolveSeed(getWorldMapSeedFallbackText());
    }

    private void cycleWorldMapDimensionView() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        this.options.worldMapDimensionView = this.options.worldMapDimensionView.next(currentLevel);
        recenterForViewedDimension();
        clearSeedMapperLoadingState();
        clearExploredLineCaches();
        synchronized (this.seedPreviewLock) {
            if (this.seedPreviewFuture != null) {
                this.seedPreviewFuture.cancel(true);
                if (this.seedPreviewProgress != null) this.seedPreviewProgress.cancel();
                this.seedPreviewFuture = null;
            }
            this.seedPreviewDisplayedKey = null;
            this.seedPreviewPendingKey = null;
            this.seedPreviewPendingPixels = null;
            this.seedPreviewLoading = false;
            this.seedPreviewCache.clear();
        }
        refreshWorldMapControlLabels();
        buildWorldName();
        loadPlotsForViewedDimension();
        MapSettingsManager.instance.saveAll();
        // A button callback must remain on this screen.  Some 26.3 input
        // paths route the callback through the parent screen after a child
        // widget has consumed the click; restore this screen if that happens.
        if (minecraft.gui.screen() != this) {
            minecraft.gui.setScreen(this);
        }
    }

    private void loadPlotsForViewedDimension() {
        plotManager.load(waypointManager.getCurrentWorldName(), waypointManager.getCurrentSubworldDescriptor(false), getViewedDimensionIdentifier().toString());
        plotStartSet = false;
        selectedPlot = null;
        editingPlot = null;
    }

    private void normalizeWorldMapDimensionView() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        if (currentLevel != null
                && this.options.worldMapDimensionView != PersistentMapSettingsManager.WorldMapDimensionView.CURRENT
                && currentLevel.dimension().identifier().equals(this.options.worldMapDimensionView.resolveIdentifier(currentLevel))) {
            this.options.worldMapDimensionView = PersistentMapSettingsManager.WorldMapDimensionView.CURRENT;
        }
    }

    private String getDisplayedWorldMapDimensionName() {
        return this.options.worldMapDimensionView.displayName(GameVariableAccessShim.getWorld());
    }

    private void toggleSeedPreview() {
        if (!hasWorldMapSeed()) {
            return;
        }
        this.seedMapperOptions.worldMapSeedPreview = !this.seedMapperOptions.worldMapSeedPreview;
        refreshWorldMapControlLabels();
        MapSettingsManager.instance.saveAll();
    }

    private void toggleExploredChunks() {
        if (!this.radarOptions.showExploredChunks) {
            return;
        }
        this.options.showExploredChunks = !this.options.showExploredChunks;
        refreshWorldMapControlLabels();
        MapSettingsManager.instance.saveAll();
    }

    private void toggleNewOldChunks() {
        if (!this.radarOptions.showNewerNewChunks) {
            return;
        }
        this.options.showNewOldChunks = !this.options.showNewOldChunks;
        refreshWorldMapControlLabels();
        MapSettingsManager.instance.saveAll();
    }

    private void toggleWorldMapEntities() {
        if (!hasWorldMapEntitySource()) {
            return;
        }
        this.options.showWorldMapEntities = !this.options.showWorldMapEntities;
        refreshWorldMapControlLabels();
        MapSettingsManager.instance.saveAll();
    }

    private void buildWorldName() {
        final AtomicReference<String> worldName = new AtomicReference<>();

        VoxelConstants.getIntegratedServer().ifPresentOrElse(integratedServer -> {
            worldName.set(integratedServer.getWorldData().getLevelName());

            if (worldName.get() == null || worldName.get().isBlank()) {
                worldName.set("Singleplayer World");
            }
        }, () -> {
            ServerData info = minecraft.getCurrentServer();

            if (info != null) {
                worldName.set(info.name);
            }
            if (worldName.get() == null || worldName.get().isBlank()) {
                worldName.set("Multiplayer Server");
            }
            if (VoxelConstants.isRealmServer()) {
                worldName.set("Realms");
            }
        });

        StringBuilder worldNameBuilder = (new StringBuilder("§r")).append(worldName.get());
        String subworldName = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true);
        this.subworldName = subworldName;
        String viewSuffix = this.options.worldMapDimensionView == PersistentMapSettingsManager.WorldMapDimensionView.CURRENT
                ? ""
                : " [" + this.options.worldMapDimensionView.displayName() + "]";
        if ((subworldName == null || subworldName.isEmpty()) && VoxelConstants.getVoxelMapInstance().getWaypointManager().isMultiworld()) {
            subworldName = "???";
        }

        if (subworldName != null && !subworldName.isEmpty()) {
            worldNameBuilder.append(" - ").append(subworldName);
        }
        worldNameBuilder.append(viewSuffix);

        this.worldNameDisplay = worldNameBuilder.toString();
        this.worldNameDisplayLength = this.getFont().width(this.worldNameDisplay);

        for (this.maxWorldNameDisplayLength = this.getWidth() / 2 - this.getFont().width(this.screenTitle) / 2 - this.sideMargin * 2; this.worldNameDisplayLength > this.maxWorldNameDisplayLength
                && worldName.get().length() > 5; this.worldNameDisplayLength = this.getFont().width(this.worldNameDisplay)) {
            worldName.set(worldName.get().substring(0, worldName.get().length() - 1));
            worldNameBuilder = new StringBuilder(worldName.get());
            worldNameBuilder.append("...");
            if (subworldName != null && !subworldName.isEmpty()) {
                worldNameBuilder.append(" - ").append(subworldName);
            }
            worldNameBuilder.append(viewSuffix);

            this.worldNameDisplay = worldNameBuilder.toString();
        }

        if (subworldName != null && !subworldName.isEmpty()) {
            while (this.worldNameDisplayLength > this.maxWorldNameDisplayLength && subworldName.length() > 5) {
                worldNameBuilder = new StringBuilder(worldName.get());
                worldNameBuilder.append("...");
                subworldName = subworldName.substring(0, subworldName.length() - 1);
                worldNameBuilder.append(" - ").append(subworldName);
                worldNameBuilder.append(viewSuffix);
                this.worldNameDisplay = worldNameBuilder.toString();
                this.worldNameDisplayLength = this.getFont().width(this.worldNameDisplay);
            }
        }

    }

    private float bindZoom(float zoom) {
        zoom = Math.max(this.options.minZoom, zoom);
        return Math.min(this.options.maxZoom, zoom);
    }

    private float scaledWorldMapZoom(float zoom) {
        if (minecraft.getWindow().getScreenWidth() > 1600) {
            return zoom * minecraft.getWindow().getScreenWidth() / 1600.0F;
        }
        return zoom;
    }

    private void captureZoomAnchor() {
        this.zoomAnchorMapCenterX = this.mapCenterX;
        this.zoomAnchorMapCenterZ = this.mapCenterZ;
        this.zoomAnchorScale = Math.max(0.0000001F, this.scaledWorldMapZoom(this.zoomStart));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        this.timeOfLastMouseInput = System.currentTimeMillis();
        this.switchToMouseInput();
        float mouseDirectX = (float) getRawMouseX();
        float mouseDirectY = (float) getRawMouseY();
        if (amount != 0.0) {
            updateZoomForDirection(amount > 0.0 ? 1.0F : -1.0F, mouseDirectX, mouseDirectY);
        }

        return true;
    }

    /** Called by the SDL pinch-event bridge used by touchpads and trackpads. */
    public void pinchUpdated(float scale) {
        if (scale == 1.0F || Float.isNaN(scale) || Float.isInfinite(scale)) {
            return;
        }
        this.timeOfLastMouseInput = System.currentTimeMillis();
        this.switchToMouseInput();
        updateZoomForDirection(scale > 1.0F ? 1.0F : -1.0F,
                (float) minecraft.mouseHandler.xpos(),
                (float) minecraft.mouseHandler.ypos());
    }

    private void updateZoomForDirection(float direction, float mouseDirectX, float mouseDirectY) {
        if (direction > 0.0F) {
            this.zoomGoal = options.detail.stepZoom(this.zoomGoal, 1);
        } else {
            this.zoomGoal = options.detail.stepZoom(this.zoomGoal, -1);
        }
        this.zoomStart = this.zoom;
        this.zoomGoal = this.bindZoom(this.zoomGoal);
        this.timeOfZoom = System.currentTimeMillis();
        this.zoomDirectX = mouseDirectX;
        this.zoomDirectY = mouseDirectY;
        this.captureZoomAnchor();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        boolean wasRightMapDrag = rightMapDrag && rightMapDragMoved;
        currentDragging = false;
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            rightMapDrag = false;
            rightMapDragMoved = false;
            if (wasRightMapDrag) {
                return true;
            }
        }
        int mouseX = (int) mouseButtonEvent.x();
        int mouseY = (int) mouseButtonEvent.y();

        // Selecting Plot from the context menu closes the popup during the
        // click callback, but 26.3 still sends that popup's matching release
        // event to this screen. That release is not a map placement.
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && ignoreNextPlotRelease) {
            boolean samePopupRelease = !Double.isNaN(ignoredPlotReleaseX)
                    && !Double.isNaN(ignoredPlotReleaseY)
                    && Math.abs(mouseButtonEvent.x() - ignoredPlotReleaseX) <= 4.0D
                    && Math.abs(mouseButtonEvent.y() - ignoredPlotReleaseY) <= 4.0D;
            boolean outsideMap = !isInMap(mouseX, mouseY);
            ignoreNextPlotRelease = false;
            ignoredPlotReleaseX = Double.NaN;
            ignoredPlotReleaseY = Double.NaN;
            // Consume the popup menu's matching release. If 26.3 did not
            // deliver a matching click callback, allow a map release through
            // so it can still place the plot endpoint.
            if (samePopupRelease || outsideMap) {
                plotClickHandled = false;
                return true;
            }
        }

        // 26.3 can deliver the release event without delivering the matching
        // click callback to this screen.  Keep plot placement working from
        // either event, but never place the same point twice when both arrive.
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT
                && !plotClickHandled
                && plotMode
                && isInMap(mouseX, mouseY)
                && !this.hasOpenPopup()) {
            if (!plotClickHandled) {
                handlePlotInput(mouseButtonEvent.x(), mouseButtonEvent.y());
            }
            // Clear this for the next physical click. Keeping it set until a
            // later release makes release-only input paths skip the endpoint.
            plotClickHandled = false;
            return true;
        }
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            plotClickHandled = false;
        }

        if (isInTopHeader(mouseX, mouseY) || isInSeedMapperStrip(mouseX, mouseY)) {
            return true;
        }
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && editingPlot != null) {
            double[] mapPoint = mapPointFromGui(mouseButtonEvent.x(), mouseButtonEvent.y());
            PlotManager.Plot plot = editingPlot;
            double[] viewPlot = plotCoordinatesForView(plot);
            double sourceX = mapPoint[0] / viewPlot[4];
            double sourceZ = mapPoint[1] / viewPlot[4];
            PlotManager.Plot updated = editingPlotEndpoint == 1
                    ? new PlotManager.Plot(sourceX, sourceZ, plot.x2(), plot.z2(), plot.dimension(), plot.showOppositeDimension(), plot.thickness(), plot.color())
                    : new PlotManager.Plot(plot.x1(), plot.z1(), sourceX, sourceZ, plot.dimension(), plot.showOppositeDimension(), plot.thickness(), plot.color());
            plotManager.replace(plot, updated);
            editingPlot = null;
            return true;
        }

        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_RIGHT && handleSeedMapperMarkerRightClick(mouseX, mouseY)) {
            return true;
        }

        selectedWaypoint = getHoveredWaypoint();
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_RIGHT && (selectedWaypoint != null || (mouseY > this.top && mouseY < this.bottom))) {
            this.timeOfLastKBInput = 0L;
            int mouseDirectX = (int) getRawMouseX();
            int mouseDirectY = (int) getRawMouseY();
            if (mapOptions.worldmapAllowed) {
                double[] mapPoint = mapPointFromGui(mouseButtonEvent.x(), mouseButtonEvent.y());
                selectedPlot = findPlotAt(mapPoint[0], mapPoint[1]);
                this.createPopup((int) mouseButtonEvent.x(), (int) mouseButtonEvent.y(), mouseDirectX, mouseDirectY);
            }
        }

        return super.mouseReleased(mouseButtonEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        int mouseX = (int) mouseButtonEvent.x();
        int mouseY = (int) mouseButtonEvent.y();

        // An exception thrown in a GUI input callback is swallowed by the event loop and is
        // invisible in game, so surface it instead of silently dropping the click.
        try {
            return this.mouseClickedImpl(mouseButtonEvent, doubleClick, mouseX, mouseY);
        } catch (Throwable error) {
            VoxelConstants.getLogger().error("World map click failed", error);
            return true;
        }
    }

    private boolean mouseClickedImpl(MouseButtonEvent mouseButtonEvent, boolean doubleClick, int mouseX, int mouseY) {

        // Coordinate editing is a screen-level interaction rather than a map
        // click. Handle it before the map, popup, and overlay handlers.
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && this.editingCoordinates) {
            if (isInCoordinateXInput(mouseX, mouseY)) {
                this.waypointSearchInput.setFocused(false);
                this.coordinateZInput.setFocused(false);
                this.coordinateXInput.setFocused(true);
                this.setFocused(this.coordinateXInput);
                return this.coordinateXInput.mouseClicked(mouseButtonEvent, doubleClick);
            }
            if (isInCoordinateZInput(mouseX, mouseY)) {
                this.waypointSearchInput.setFocused(false);
                this.coordinateXInput.setFocused(false);
                this.coordinateZInput.setFocused(true);
                this.setFocused(this.coordinateZInput);
                return this.coordinateZInput.mouseClicked(mouseButtonEvent, doubleClick);
            }
        }

        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT
                && mapOptions.worldmapAllowed
                && options.showCoordinates
                && !this.editingCoordinates
                && isInCoordinateLabel(mouseX, mouseY)) {
            openCoordinateInputs(this.coordinateHoverX, this.coordinateHoverZ);
            return true;
        }

        // These controls are drawn over the map's normal interaction area.
        // Resolve them before map clicks can start panning, plotting, or
        // selecting an overlay underneath them.
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (mapOptions.worldmapAllowed && isInSeedHeader(mouseX, mouseY)) {
                minecraft.gui.setScreen(new GuiMinimapOptions(this, "seedmapper"));
                return true;
            }
            if (isInSeedMapperStrip(mouseX, mouseY)) {
                if (handleSeedMapperTitleClick(mouseX, mouseY) || handleSeedMapperIconClick(mouseX, mouseY)) {
                    return true;
                }
                return true;
            }
        }

        // Right-button drag is only used to pan the map while drawing a plot
        // line. In all other modes a right-click opens the context menu, which
        // must not be preceded by a map drag (that would hide SeedMapper
        // markers and clear their hitboxes before the release handler runs).
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_RIGHT && isInMap(mouseX, mouseY) && !this.hasOpenPopup() && plotMode) {
            rightMapDrag = true;
            rightMapDragMoved = false;
            currentDragging = true;
        }
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && isInMap(mouseX, mouseY) && !this.hasOpenPopup()) {
            // A SeedMapper marker that can open loot takes priority over plot-line editing.
            // The icon is a far more specific target than a plot line, and plot mode (which
            // never clears when clicks repeat within its 250 ms / 3 px guard) would otherwise
            // swallow every click before the marker handler is ever reached.
            if (!isLootableMarkerAt(mouseX, mouseY)) {
                try {
                    if (handlePlotMapClick(mouseButtonEvent)) {
                        return true;
                    }
                } catch (Throwable error) {
                    VoxelConstants.getLogger().error("Plot hit-test failed for a map click", error);
                }
            }
        }
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && handlePlayerLayerStatusClick(mouseX, mouseY)) {
            return true;
        }
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            long now = System.currentTimeMillis();
            boolean closeInTime = now - this.lastMapLeftClickMs <= 300L;
            boolean closeInSpace = Math.abs(mouseX - this.lastMapLeftClickX) <= 10
                    && Math.abs(mouseY - this.lastMapLeftClickY) <= 10;
            doubleClick = closeInTime && closeInSpace;
            this.lastMapLeftClickMs = now;
            this.lastMapLeftClickX = mouseX;
            this.lastMapLeftClickY = mouseY;
        }

        if (seedMapperVaultLootWidget != null) {
            if (seedMapperVaultLootWidget.mouseClicked(mouseButtonEvent)) {
                if (seedMapperVaultLootWidget.shouldClose()) {
                    seedMapperVaultLootWidget = null;
                }
                return true;
            }
            if (seedMapperVaultLootWidget.isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                return true;
            }
            if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && !isDuplicateOfLootWidgetOpen(mouseX, mouseY)) {
                seedMapperVaultLootWidget = null;
            }
        }

        if (seedMapperChestLootWidget != null) {
            if (seedMapperChestLootWidget.mouseClicked(mouseButtonEvent, doubleClick)) {
                return true;
            }
            if (seedMapperChestLootWidget.isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                return true;
            }
            if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && !isDuplicateOfLootWidgetOpen(mouseX, mouseY)) {
                seedMapperChestLootWidget = null;
            }
        }

        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && this.editingCoordinates) {
            closeCoordinateInputs();
        }

        if (mapOptions.worldmapAllowed && isInSeedHeader(mouseX, mouseY)) {
            if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                minecraft.gui.setScreen(new GuiMinimapOptions(this, "seedmapper"));
            }
            return true;
        }

        if (this.waypointSearchInput != null && isInWaypointSearchInput(mouseX, mouseY)) {
            if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                this.waypointSearchInput.setFocused(true);
                this.setFocused(this.waypointSearchInput);
            }
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        // Popup must consume clicks before map/marker handlers to prevent click-through.
        if (this.hasOpenPopup()) {
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        if (isInSeedMapperStrip(mouseX, mouseY)) {
            if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && handleSeedMapperTitleClick(mouseX, mouseY)) {
                return true;
            }
            if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && handleSeedMapperIconClick(mouseX, mouseY)) {
                return true;
            }
            return true;
        }

        if (isInTopHeader(mouseX, mouseY)) {
            return true;
        }

        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT && handleSeedMapperIconClick(mouseX, mouseY)) {
            return true;
        }
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            boolean markerHandled;
            try {
                markerHandled = handleSeedMapperMarkerLeftClick(mouseX, mouseY);
            } catch (Throwable error) {
                // An exception here would otherwise vanish into the GUI event loop and
                // look exactly like "clicking a structure does nothing".
                VoxelConstants.getLogger().error("SeedMapper marker click failed", error);
                markerHandled = true;
            }
            if (markerHandled) {
                return true;
            }
        }
        if (mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            currentDragging = true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick) || mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    /** True when the cursor is over a marker that can open a loot view. */
    private boolean isLootableMarkerAt(int mouseX, int mouseY) {
        for (SeedMapperMarkerHitbox hitbox : seedMapperMarkerHitboxes) {
            if (!hitbox.contains(mouseX, mouseY)) {
                continue;
            }
            SeedMapperFeature feature = hitbox.marker().feature();
            if (feature == SeedMapperFeature.TRIAL_CHAMBERS || SeedMapperLootService.hasPredictableLoot(feature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 26.3 delivers {@code mouseClicked} several times for one physical press (with a few px of
     * jitter). The first delivery opens the loot widget; later deliveries of the same press land
     * outside it and would otherwise close it again. Treat a click near the open position within a
     * short window as the same press.
     */
    private boolean isDuplicateOfLootWidgetOpen(int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        return now - seedMapperLootWidgetOpenedAtMs <= 500L
                && Math.abs(mouseX - seedMapperLootWidgetOpenedX) <= 12
                && Math.abs(mouseY - seedMapperLootWidgetOpenedY) <= 12;
    }

    /** Applies a left click to the plot-line layer. Returns true when the click was consumed. */
    private boolean handlePlotMapClick(MouseButtonEvent mouseButtonEvent) {
        double[] mapPoint = mapPointFromGui(mouseButtonEvent.x(), mouseButtonEvent.y());
        if (placingDuplicatePlot != null) {
            double[] original = plotCoordinatesForView(placingDuplicatePlot);
            double centerX = (original[0] + original[2]) / 2.0D;
            double centerZ = (original[1] + original[3]) / 2.0D;
            double sourceScale = original[4];
            double dx = (mapPoint[0] - centerX) / sourceScale;
            double dz = (mapPoint[1] - centerZ) / sourceScale;
            plotManager.add(new PlotManager.Plot(placingDuplicatePlot.x1() + dx, placingDuplicatePlot.z1() + dz,
                    placingDuplicatePlot.x2() + dx, placingDuplicatePlot.z2() + dz, placingDuplicatePlot.dimension(),
                    placingDuplicatePlot.showOppositeDimension(), placingDuplicatePlot.thickness(), placingDuplicatePlot.color()));
            placingDuplicatePlot = null;
            return true;
        }
        if (plotMode) {
            handlePlotInput(mouseButtonEvent.x(), mouseButtonEvent.y());
            return true;
        }
        PlotManager.Plot endpointPlot = findPlotAt(mapPoint[0], mapPoint[1]);
        int endpoint = findPlotEndpointAt(mapPoint[0], mapPoint[1]);
        if (endpoint != 0) {
            editingPlot = endpointPlot;
            selectedPlot = endpointPlot;
            editingPlotEndpoint = endpoint;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (this.waypointSearchInput != null && this.waypointSearchInput.isFocused()) {
            if (keyEvent.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                this.waypointSearchInput.setFocused(false);
                return true;
            }
            boolean handled = this.waypointSearchInput.keyPressed(keyEvent);
            if (!handled) {
                handled = super.keyPressed(keyEvent);
            }
            return handled;
        }
        if (!this.editingCoordinates && minecraft.options.keyJump.matches(keyEvent)) {
            if (minecraft.options.keyJump.matches(keyEvent)) {
                this.zoomGoal = options.detail.stepZoom(this.zoomGoal, -1);
            }

            this.zoomStart = this.zoom;
            this.zoomGoal = this.bindZoom(this.zoomGoal);
            this.timeOfZoom = System.currentTimeMillis();
            this.zoomDirectX = (minecraft.getWindow().getWidth() / 2f);
            this.zoomDirectY = (minecraft.getWindow().getHeight() - minecraft.getWindow().getHeight() / 2f);
            this.captureZoomAnchor();
            this.switchToKeyboardInput();
        }

        this.clearPopups();
        if (this.editingCoordinates) {
            if (!this.coordinateXInput.isFocused() && !this.coordinateZInput.isFocused()) {
                this.coordinateXInput.setFocused(true);
                this.setFocused(this.coordinateXInput);
            }
            if (keyEvent.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                closeCoordinateInputs();
                return true;
            }

            if (keyEvent.key() == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
                if (this.coordinateXInput.isFocused()) {
                    this.coordinateXInput.setFocused(false);
                    this.coordinateZInput.setFocused(true);
                    this.setFocused(this.coordinateZInput);
                } else {
                    this.coordinateZInput.setFocused(false);
                    this.coordinateXInput.setFocused(true);
                    this.setFocused(this.coordinateXInput);
                }
                return true;
            }

            boolean isGood = this.isAcceptableCoordinates();
            this.coordinateXInput.setTextColor(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateZInput.setTextColor(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateXInput.setTextColorUneditable(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateZInput.setTextColorUneditable(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            if (isCoordinateSubmitKey(keyEvent)) {
                if (isGood) {
                    commitCoordinateInputs();
                }
                return true;
            }
            EditBox focusedCoordinateInput = this.coordinateXInput.isFocused() ? this.coordinateXInput : this.coordinateZInput.isFocused() ? this.coordinateZInput : null;
            boolean handledByWidget = focusedCoordinateInput != null && focusedCoordinateInput.keyPressed(keyEvent);
            if (!handledByWidget) {
                handledByWidget = super.keyPressed(keyEvent);
            }
            boolean stillGood = this.isAcceptableCoordinates();
            this.coordinateXInput.setTextColor(stillGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateZInput.setTextColor(stillGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateXInput.setTextColorUneditable(stillGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateZInput.setTextColorUneditable(stillGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            return handledByWidget;
        }

        if (VoxelConstants.getVoxelMapInstance().getMapOptions().keyBindMenu.matches(keyEvent)) {
            keyEvent = new KeyEvent(InputConstants.KEY_ESCAPE, -1, -1);
        }

        keySprintPressed = minecraft.options.keySprint.matches(keyEvent) || keySprintPressed;
        keyUpPressed = minecraft.options.keyUp.matches(keyEvent) || keyUpPressed;
        keyDownPressed = minecraft.options.keyDown.matches(keyEvent) || keyDownPressed;
        keyLeftPressed = minecraft.options.keyLeft.matches(keyEvent) || keyLeftPressed;
        keyRightPressed = minecraft.options.keyRight.matches(keyEvent) || keyRightPressed;

        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        keySprintPressed = !minecraft.options.keySprint.matches(keyEvent) && keySprintPressed;
        keyUpPressed = !minecraft.options.keyUp.matches(keyEvent) && keyUpPressed;
        keyDownPressed = !minecraft.options.keyDown.matches(keyEvent) && keyDownPressed;
        keyLeftPressed = !minecraft.options.keyLeft.matches(keyEvent) && keyLeftPressed;
        keyRightPressed = !minecraft.options.keyRight.matches(keyEvent) && keyRightPressed;

        return super.keyReleased(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        this.clearPopups();
        if (this.waypointSearchInput != null && this.waypointSearchInput.isFocused()) {
            boolean handled = this.waypointSearchInput.charTyped(characterEvent);
            if (!handled) {
                handled = super.charTyped(characterEvent);
            }
            return handled;
        }
        if (this.editingCoordinates) {
            if ((characterEvent.codepoint() == '\r' || characterEvent.codepoint() == '\n')
                    && isAcceptableCoordinates()) {
                commitCoordinateInputs();
                return true;
            }
            if (!this.coordinateXInput.isFocused() && !this.coordinateZInput.isFocused()) {
                this.coordinateXInput.setFocused(true);
                this.setFocused(this.coordinateXInput);
            }
            EditBox focusedCoordinateInput = this.coordinateXInput.isFocused() ? this.coordinateXInput : this.coordinateZInput.isFocused() ? this.coordinateZInput : null;
            boolean handled = focusedCoordinateInput != null && focusedCoordinateInput.charTyped(characterEvent);
            if (!handled) {
                handled = super.charTyped(characterEvent);
            }
            boolean isGood = this.isAcceptableCoordinates();
            this.coordinateXInput.setTextColor(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateZInput.setTextColor(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateXInput.setTextColorUneditable(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            this.coordinateZInput.setTextColorUneditable(isGood ? COORD_TEXT_COLOR_OK : COORD_TEXT_COLOR_ERROR);
            return handled;
        }

        return super.charTyped(characterEvent);
    }

    private boolean isAcceptableCoordinates() {
        try {
            Integer.valueOf(this.coordinateXInput.getValue().trim());
            Integer.valueOf(this.coordinateZInput.getValue().trim());
            return true;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException var3) {
            return false;
        }
    }

    private double getRawMouseX() {
        return minecraft.mouseHandler.xpos() * RenderUtils.getRetinaScaleX();
    }

    private double getRawMouseY() {
        return minecraft.mouseHandler.ypos() * RenderUtils.getRetinaScaleY();
    }

    private void switchToMouseInput() {
        this.timeOfLastKBInput = 0L;
        if (!this.mouseCursorShown) {
            minecraft.mouseHandler.releaseMouse();
        }

        this.mouseCursorShown = true;
    }

    private void switchToKeyboardInput() {
        this.timeOfLastKBInput = System.currentTimeMillis();
        this.mouseCursorShown = false;
        minecraft.mouseHandler.grabMouse();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        WorldMapProfiler.begin();
        WorldMapUploadBudget.beginFrame();
        graphics.pose().pushMatrix();
        this.waypointLabelBounds.clear();
        this.pendingWaypointLabels.clear();
        this.waypointClusters.clear();
        this.waypointLabelBuckets.clear();
        this.waypointIconBuckets.clear();
        this.frameWaypointSearch = getWaypointSearchQuery();
        long fontVersion = persistentMap.colorManager.worldMapPaletteVersion();
        if (fontVersion != waypointFontVersion) {
            waypointFontVersion = fontVersion; waypointTextWidths.clear(); lastLabelInput = List.of();
        }
        this.buttonWaypoints.active = mapOptions.waypointsAllowed;
        refreshWorldMapControlLabels();
        this.zoomGoal = this.bindZoom(this.zoomGoal);
        if (this.mouseX != mouseX || this.mouseY != mouseY) {
            this.timeOfLastMouseInput = System.currentTimeMillis();
            this.switchToMouseInput();
        }

        this.mouseX = mouseX;
        this.mouseY = mouseY;
        float mouseDirectX = (float) getRawMouseX();
        float mouseDirectY = (float) getRawMouseY();
        if (this.zoom != this.zoomGoal) {
            long timeSinceZoom = System.currentTimeMillis() - this.timeOfZoom;
            if (timeSinceZoom < 700.0F) {
                this.zoom = EasingUtils.easeOutExpo(this.zoomStart, this.zoomGoal, timeSinceZoom, 700.0F);
            } else {
                this.zoom = this.zoomGoal;
            }

            float scaledZoom = this.scaledWorldMapZoom(this.zoom);
            float directOffsetX = this.zoomDirectX - this.centerX * this.scScale;
            float directOffsetY = this.zoomDirectY - (this.top + this.centerY) * this.scScale;
            this.mapCenterX = this.zoomAnchorMapCenterX
                    + directOffsetX * (1.0F / this.zoomAnchorScale - 1.0F / scaledZoom);
            this.mapCenterZ = this.zoomAnchorMapCenterZ
                    + directOffsetY * (1.0F / this.zoomAnchorScale - 1.0F / scaledZoom);
        }

        this.options.zoom = this.zoomGoal;
        float scaledZoom = this.scaledWorldMapZoom(this.zoom);

        this.guiToMap = this.scScale / scaledZoom;
        this.mapToGui = 1.0F / this.scScale * scaledZoom;
        this.mouseDirectToMap = 1.0F / scaledZoom;
        this.guiToDirectMouse = this.scScale;
        this.renderBackground(graphics);
        if (currentDragging) {
            if (!this.leftMouseButtonDown && this.overPopup(mouseX, mouseY)) {
                this.deltaX = 0.0F;
                this.deltaY = 0.0F;
                this.lastMouseX = mouseDirectX;
                this.lastMouseY = mouseDirectY;
                this.leftMouseButtonDown = true;
            } else if (this.leftMouseButtonDown) {
                if (rightMapDrag && (Math.abs(this.lastMouseX - mouseDirectX) > 2.0F || Math.abs(this.lastMouseY - mouseDirectY) > 2.0F)) {
                    rightMapDragMoved = true;
                }
                this.deltaX = (this.lastMouseX - mouseDirectX) * this.mouseDirectToMap;
                this.deltaY = (this.lastMouseY - mouseDirectY) * this.mouseDirectToMap;
                this.lastMouseX = mouseDirectX;
                this.lastMouseY = mouseDirectY;
                this.deltaXonRelease = this.deltaX;
                this.deltaYonRelease = this.deltaY;
                this.timeOfRelease = System.currentTimeMillis();
            }
        } else {
            long timeSinceRelease = System.currentTimeMillis() - this.timeOfRelease;
            if (timeSinceRelease < 700.0F) {
                this.deltaX = EasingUtils.easeOutExpo(this.deltaXonRelease, 0.0F, timeSinceRelease, 700.0F);
                this.deltaY = EasingUtils.easeOutExpo(this.deltaYonRelease, 0.0F, timeSinceRelease, 700.0F);
            } else {
                this.deltaX = 0.0F;
                this.deltaY = 0.0F;
                this.deltaXonRelease = 0.0F;
                this.deltaYonRelease = 0.0F;
            }

            this.leftMouseButtonDown = false;
        }

        long timeSinceLastTick = System.currentTimeMillis() - this.timeAtLastTick;
        this.timeAtLastTick = System.currentTimeMillis();
        if (!this.editingCoordinates) {
            int kbDelta = 5;
            if (keySprintPressed) {
                kbDelta = 10;
            }

            if (keyUpPressed) {
                this.deltaY -= kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }

            if (keyDownPressed) {
                this.deltaY += kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }

            if (keyLeftPressed) {
                this.deltaX -= kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }

            if (keyRightPressed) {
                this.deltaX += kbDelta / scaledZoom * timeSinceLastTick / 12.0F;
                this.switchToKeyboardInput();
            }
        }

        this.mapCenterX += this.deltaX;
        this.mapCenterZ += this.deltaY;
        if (this.oldNorth) {
            this.options.mapX = (int) this.mapCenterZ;
            this.options.mapZ = -((int) this.mapCenterX);
        } else {
            this.options.mapX = (int) this.mapCenterX;
            this.options.mapZ = (int) this.mapCenterZ;
        }

        this.centerX = this.getWidth() / 2;
        this.centerY = (this.bottom - this.top) / 2;
        int left;
        int right;
        int top;
        int bottom;
        if (this.oldNorth) {
            left = (int) Math.floor((this.mapCenterZ - this.centerY * this.guiToMap) / 256.0F);
            right = (int) Math.floor((this.mapCenterZ + this.centerY * this.guiToMap) / 256.0F);
            top = (int) Math.floor((-this.mapCenterX - this.centerX * this.guiToMap) / 256.0F);
            bottom = (int) Math.floor((-this.mapCenterX + this.centerX * this.guiToMap) / 256.0F);
        } else {
            left = (int) Math.floor((this.mapCenterX - this.centerX * this.guiToMap) / 256.0F);
            right = (int) Math.floor((this.mapCenterX + this.centerX * this.guiToMap) / 256.0F);
            top = (int) Math.floor((this.mapCenterZ - this.centerY * this.guiToMap) / 256.0F);
            bottom = (int) Math.floor((this.mapCenterZ + this.centerY * this.guiToMap) / 256.0F);
        }
        Identifier viewedDimension = getViewedDimensionIdentifier();
        PreviewBounds visibleBounds = getVisibleWorldBounds();
        long cacheBytes = (long) options.detail.zoomCacheMiB << 20;
        trailViews.setBudget(cacheBytes / 8); areaViews.setBudget(cacheBytes / 8);
        geometryViews.setBudget(cacheBytes / 8); rasterLayers.setBudget(cacheBytes / 2);
        trailViews.beginFrame(); areaViews.beginFrame(); geometryViews.beginFrame(); rasterLayers.beginFrame();
        boolean farZoomPerformanceMode = isFarZoomPerformanceMode();
        boolean terrainVisible = !farZoomPerformanceMode && layerVisible(WorldMapDetailSettings.Layer.TERRAIN);
        int exploredLeftRegion = left - 1;
        int exploredRightRegion = right + 1;
        int exploredTopRegion = top - 1;
        int exploredBottomRegion = bottom + 1;
        // Keep full visible explored-line bounds in performance mode to avoid "windowed" clipping while panning.

        boolean detailedTerrain = mapToGui >= 0.125F && (long) (right - left + 3) * (bottom - top + 3) <= 128;
        synchronized (this.closedLock) {
            if (this.closed) {
                return;
            }
            if (terrainVisible && mapOptions.worldmapAllowed && detailedTerrain) {
                this.regions = this.persistentMap.getRegions(left - 1, right + 1, top - 1, bottom + 1, viewedDimension);
            } else {
                this.regions = NO_REGIONS;
            }
        }

        this.backGroundImageInfo = this.waypointManager.getBackgroundImageInfo();
        if (this.backGroundImageInfo != null && terrainVisible) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, backGroundImageInfo.getImageLocation(), backGroundImageInfo.left, backGroundImageInfo.top + 32, 0, 0, backGroundImageInfo.width, backGroundImageInfo.height, backGroundImageInfo.width, backGroundImageInfo.height);
        }

        graphics.pose().translate(this.centerX - this.mapCenterX * this.mapToGui, (this.top + this.centerY) - this.mapCenterZ * this.mapToGui);
        if (this.oldNorth) {
            graphics.pose().rotate(90.0F * Mth.DEG_TO_RAD);
        }

        float cursorCoordZ = 0.0f;
        float cursorCoordX = 0.0f;
        graphics.pose().scale(this.mapToGui, this.mapToGui);
        if (mapOptions.worldmapAllowed) {
            WorldMapProfiler.phaseBegin();
            if (!farZoomPerformanceMode) drawSeedPreview(graphics, visibleBounds);
            if (terrainVisible) {
                graphics.nextStratum();
                this.persistentMap.drawTerrainOverview(graphics, left, right, top, bottom, viewedDimension, mapToGui);
                graphics.nextStratum();
                for (CachedRegion region : this.regions) {
                    if (region == null) {
                        continue;
                    }
                    Identifier resource = region.getTextureLocation(this.zoom);
                    if (resource != null) {
                        VoxelMapGuiGraphics.blitMapTile(graphics, resource, region.getX() * 256, region.getZ() * 256, region.getWidth());
                    }
                }
            }
            WorldMapProfiler.terrainEnd();
            WorldMapProfiler.phaseBegin();
            drawExploredChunkLinesWorldMap(graphics, exploredLeftRegion, exploredRightRegion, exploredTopRegion, exploredBottomRegion);
            drawNewOldChunkOverlayWorldMap(graphics, exploredLeftRegion, exploredRightRegion, exploredTopRegion, exploredBottomRegion);
            WorldMapProfiler.overlaysEnd();

            if (!farZoomPerformanceMode && mapOptions.worldBorder) {
                WorldBorder worldBorder = minecraft.level.getWorldBorder();
                float scale = 1.0f / (float) minecraft.getWindow().getGuiScale() / mapToGui;

                float x1 = (float) (worldBorder.getMinX());
                float z1 = (float) (worldBorder.getMinZ());
                float x2 = (float) (worldBorder.getMaxX());
                float z2 = (float) (worldBorder.getMaxZ());

                VoxelMapGuiGraphics.fillGradient(graphics, x1 - scale, z1 - scale, x2 + scale, z1 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);
                VoxelMapGuiGraphics.fillGradient(graphics, x1 - scale, z2 - scale, x2 + scale, z2 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);

                VoxelMapGuiGraphics.fillGradient(graphics, x1 - scale, z1 - scale, x1 + scale, z2 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);
                VoxelMapGuiGraphics.fillGradient(graphics, x2 - scale, z1 - scale, x2 + scale, z2 + scale, 0xffff0000, 0xffff0000, 0xffff0000, 0xffff0000);
            }

            float cursorX;
            float cursorY;
            if (this.mouseCursorShown) {
                cursorX = mouseDirectX;
                cursorY = mouseDirectY - this.top * this.guiToDirectMouse;
            } else {
                cursorX = (minecraft.getWindow().getWidth() / 2f);
                cursorY = (minecraft.getWindow().getHeight() - minecraft.getWindow().getHeight() / 2f) - this.top * this.guiToDirectMouse;
            }

            if (this.oldNorth) {
                cursorCoordX = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
                cursorCoordZ = -(cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
            } else {
                cursorCoordX = cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
                cursorCoordZ = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
            }

            drawPlots(graphics, cursorCoordX, cursorCoordZ);

            if (this.oldNorth) {
                graphics.pose().rotate(-90.0F * Mth.DEG_TO_RAD);
            }

            graphics.pose().scale(this.guiToMap, this.guiToMap);
            graphics.pose().translate(-(this.centerX - this.mapCenterX * this.mapToGui), -((this.top + this.centerY) - this.mapCenterZ * this.mapToGui));
            if (!farZoomPerformanceMode && layerVisible(WorldMapDetailSettings.Layer.BIOMES) && mapOptions.biomeOverlay != 0) {
                float biomeScaleX = this.mapPixelsX / 190.0F;
                float biomeScaleY = this.mapPixelsY / 90.0F;
                boolean still = !mapIsMoving();
                long version = 0;
                for (CachedRegion region : regions) if (region != null)
                    version = 31 * version + region.getMostRecentChange() + (region.isLoaded() ? 1 : 0);
                BiomeDiskSource disk = null;
                if (regions.length == 0) {
                    if (biomeDiskSource == null) {
                        String worldName = waypointManager.getCurrentWorldName();
                        String subworld = waypointManager.getCurrentSubworldDescriptor(false);
                        CachedRegion origin = new CachedRegion(persistentMap, "biome overview", "0,0", minecraft.level, worldName, subworld, 0, 0, viewedDimension, true);
                        biomeDiskSource = new BiomeDiskSource(persistentMap, minecraft.level, worldName, subworld, viewedDimension, origin.cacheDirectory());
                    }
                    disk = biomeDiskSource;
                    MapRegionPack index = MapRegionPack.requestDirectory(disk.directory());
                    if (index != null) version = index.indexVersion();
                }
                BiomeViewKey biomeKey = new BiomeViewKey(regions, disk, version, viewedDimension, centerX, centerY,
                        mapCenterX, mapCenterZ, guiToMap, mouseDirectToMap, mapPixelsX, mapPixelsY, oldNorth);
                BiomeMapData biomeMapData = biomeViews.get("biomes", biomeKey, still, () -> buildBiomeLabels(biomeKey));
                boolean displayStill = !this.leftMouseButtonDown;
                displayStill = displayStill && this.zoom == this.zoomGoal;
                displayStill = displayStill && this.deltaX == 0.0F && this.deltaY == 0.0F;
                if (displayStill && biomeMapData != null && biomeViews.matches("biomes", biomeKey)) {
                    int minimumSize = (int) (20.0F * this.scScale / biomeScaleX);
                    minimumSize *= minimumSize;
                    ArrayList<AbstractMapData.BiomeLabel> labels = biomeMapData.getBiomeLabels();
                    for (AbstractMapData.BiomeLabel biomeLabel : labels) {
                        if (biomeLabel.segmentSize > minimumSize) {
                            String label = biomeLabel.name; // + " (" + biomeLabel.x + "," + biomeLabel.z + ")";
                            float x = biomeLabel.x * biomeScaleX / this.scScale;
                            float z = biomeLabel.z * biomeScaleY / this.scScale;

                            this.writeCentered(graphics, label, x, this.top + z - 3.0F, 0xFFFFFFFF, true);
                        }
                    }
                }
            }
        }
        graphics.pose().popMatrix();

        if (!farZoomPerformanceMode && options.showDistantWaypoints) {
            this.overlayBackground(graphics, 0, this.top, 255, 255);
            this.overlayBackground(graphics, this.bottom, this.getHeight(), 255, 255);
        }

        graphics.nextStratum();
        graphics.enableScissor(0, this.top, this.width, this.bottom);

        WorldMapProfiler.phaseBegin();
        Waypoint currentlyHovered = null;
        boolean showWaypointsInThisMode = layerVisible(WorldMapDetailSettings.Layer.WAYPOINTS) && (!farZoomPerformanceMode || this.options.isShowWaypointsInPerformanceModeEnabled());
        if (showWaypointsInThisMode && mapOptions.waypointsAllowed && options.showWaypoints) {
            TextureAtlas textureAtlas = VoxelConstants.getVoxelMapInstance().getWaypointManager().getTextureAtlas();
            for (Waypoint waypoint : waypointManager.getWaypoints()) {
                if (!isWaypointVisibleInViewedDimension(waypoint)) continue;

                boolean isHighlighted = waypointManager.isHighlightedWaypoint(waypoint);
                boolean isHovered = drawWaypoint(graphics, waypoint, textureAtlas, null, isHighlighted, -1, mouseX, mouseY);
                if (isHovered) {
                    currentlyHovered = waypoint;
                }
            }

            Waypoint highlightedPoint = waypointManager.getHighlightedWaypoint();
            if (highlightedPoint != null) {
                boolean isHovered = drawWaypoint(graphics, highlightedPoint, textureAtlas, textureAtlas.getAtlasSprite("marker/target"), true, 0xFFFF0000, mouseX, mouseY);
                if (isHovered) {
                    currentlyHovered = highlightedPoint;
                }
            }
        }
        hoverdWaypoint = currentlyHovered;

        drawQueuedWaypointLabels(graphics);
        WorldMapProfiler.waypointsEnd();

        if (!farZoomPerformanceMode && layerVisible(WorldMapDetailSettings.Layer.ENTITIES) && mapOptions.worldmapAllowed) {
            drawSeedMapperFeatureStrip(graphics, mouseX, mouseY);
        }
        if (!farZoomPerformanceMode && layerVisible(WorldMapDetailSettings.Layer.ENTITIES)) {
            drawSeedMapperMarkers(graphics, mouseX, mouseY);
            drawContainerMarkers(graphics, mouseX, mouseY);
        }
        drawPlayerLayerStatuses(graphics);

        graphics.disableScissor();

        if (!farZoomPerformanceMode && !options.showDistantWaypoints) {
            this.overlayBackground(graphics, 0, this.top, 255, 255);
            this.overlayBackground(graphics, this.bottom, this.getHeight(), 255, 255);
        }

        if (gotSkin && hasMeaningfulViewedPositionForCurrentPlayer()) {
            float playerX = (float) convertCurrentCoordinateToViewed(GameVariableAccessShim.xCoordDouble());
            float playerZ = (float) convertCurrentCoordinateToViewed(GameVariableAccessShim.zCoordDouble());
            drawPlayer(graphics, voxelmapSkinLocation, playerX, playerZ, mouseX, mouseY);
        }

        if (System.currentTimeMillis() - this.timeOfLastKBInput < 2000L) {
            int scWidth = minecraft.getWindow().getGuiScaledWidth();
            int scHeight = minecraft.getWindow().getGuiScaledHeight();
            graphics.blit(RenderPipelines.CROSSHAIR, crosshairResource, scWidth / 2 - 8, scHeight / 2 - 8, 0, 0, 15, 15, 15, 15);
        } else {
            this.switchToMouseInput();
        }

        if (mapOptions.worldmapAllowed) {
            graphics.centeredText(this.getFont(), this.screenTitle, this.getWidth() / 2, 4, 0xFFFFFFFF);
            graphics.centeredText(this.getFont(), Component.literal("Zoom " + WorldMapDetailSettings.scaleText(zoom)
                    + " | " + String.format(java.util.Locale.ROOT, "%,.1f blocks/pixel", guiToMap)), this.getWidth() / 2, 16, 0xFFAAAAAA);
            if (plotMode) {
                graphics.text(this.getFont(), plotStartSet ? "Plot: click the end point" : "Plot: click the start point",
                        this.getWidth() / 2 - 58, 28, 0xFFFFF27A);
            }
            int x = (int) Math.floor(cursorCoordX);
            int z = (int) Math.floor(cursorCoordZ);
            if (options.showCoordinates) {
                if (!this.editingCoordinates) {
                    String xText = "X: " + x;
                    String zText = "Z: " + z;
                    int xTextX = this.sideMargin;
                    int zTextX = this.sideMargin + 64;
                    graphics.text(this.getFont(), xText, xTextX, 16, 0xFFFFFFFF);
                    graphics.text(this.getFont(), zText, zTextX, 16, 0xFFFFFFFF);
                    this.coordinateHoverX = x;
                    this.coordinateHoverZ = z;
                    this.coordinateLabelLeft = xTextX - 2;
                    this.coordinateLabelRight = zTextX + this.getFont().width(zText) + 2;
                    this.coordinateLabelTop = 15;
                    this.coordinateLabelBottom = 16 + this.getFont().lineHeight + 1;
                } else {
                    this.coordinateXInput.extractRenderState(graphics, mouseX, mouseY, delta);
                    this.coordinateZInput.extractRenderState(graphics, mouseX, mouseY, delta);
                }
            }
            if (options.seedMapShowBiomeUnderCursor) {
                String biomeName = resolveSeedBiomeNameAt(x, z);
                if (!biomeName.isEmpty()) {
                    int biomeX = options.showCoordinates
                            ? this.sideMargin + 64 + this.getFont().width("Z: " + z) + 12
                            : this.sideMargin;
                    graphics.text(this.getFont(), "Biome: " + biomeName, biomeX, 16, 0xFFFFFFFF);
                }
            }
            String seedTextValue = getWorldMapSeedText();
            boolean showSeedHeader = !seedTextValue.isEmpty();
            seedHeaderLeft = -1;
            seedHeaderRight = -1;
            seedHeaderTop = -1;
            seedHeaderBottom = -1;
            if (showSeedHeader) {
                String seedText = "Seed: " + seedTextValue;
                int seedWidth = this.getFont().width(seedText);
                int seedX = this.getWidth() - this.sideMargin - seedWidth;
                int seedY = 16;
                graphics.text(this.getFont(), seedText, seedX, seedY, 0xFFFFFFFF);
                seedHeaderLeft = seedX - 2;
                seedHeaderRight = seedX + seedWidth + 2;
                seedHeaderTop = seedY - 1;
                seedHeaderBottom = seedY + this.getFont().lineHeight + 1;
            }
            if (farZoomPerformanceMode) {
                String perfText = "Legacy layer hiding active";
                int perfWidth = this.getFont().width(perfText);
                graphics.text(this.getFont(), perfText, this.getWidth() - this.sideMargin - perfWidth, 28, 0xFFAAAAAA);
            }

            if (this.subworldName != null && !this.subworldName.equals(VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true))
                    || VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true) != null && !VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true).equals(this.subworldName)) {
                this.buildWorldName();
            }

            if (isInSeedHeader(mouseX, mouseY)) {
                renderTooltip(graphics, Component.literal("Open SeedMapper Options"), mouseX, mouseY);
            }
            if (this.buttonMultiworld != null) {
                if ((this.subworldName == null || this.subworldName.isEmpty()) && VoxelConstants.getVoxelMapInstance().getWaypointManager().isMultiworld()) {
                    if ((int) (System.currentTimeMillis() / 1000L % 2L) == 0) {
                        this.buttonMultiworld.setMessage(this.multiworldButtonNameRed);
                    } else {
                        this.buttonMultiworld.setMessage(this.multiworldButtonName);
                    }
                } else {
                    this.buttonMultiworld.setMessage(this.multiworldButtonName);
                }
            }
        } else {
            graphics.text(this.getFont(), Component.translatable("worldmap.disabled"), this.sideMargin, 16, 0xFFFFFFFF);
        }

        if (seedMapperChestLootWidget != null) {
            graphics.nextStratum();
            seedMapperChestLootWidget.extractRenderState(graphics, mouseX, mouseY, this.getFont());
            List<ClientTooltipComponent> tooltip = seedMapperChestLootWidget.getPendingItemTooltip();
            if (tooltip != null) {
                graphics.nextStratum();
                graphics.tooltip(this.getFont(), tooltip, seedMapperChestLootWidget.getPendingTooltipX(), seedMapperChestLootWidget.getPendingTooltipY(), DefaultTooltipPositioner.INSTANCE, null, false);
            }
        }
        if (seedMapperVaultLootWidget != null) {
            graphics.nextStratum();
            seedMapperVaultLootWidget.extractRenderState(graphics, mouseX, mouseY, this.getFont());
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (mapOptions.worldmapAllowed) {
            graphics.nextStratum();
            drawBottomStatusTexts(graphics);
        }
        WorldMapProfiler.end(trailViews.hits(), trailViews.builds(), areaViews.hits(), areaViews.builds());
    }

    private static BiomeMapData buildBiomeLabels(BiomeViewKey view) {
        java.util.Map<Long, CompressibleMapData> sources = new java.util.HashMap<>();
        for (CachedRegion region : view.regions()) if (region != null && region.isLoaded() && !region.isEmpty()) {
            CompressibleMapData data = region.getMapData();
            if (data != null) sources.put(packXZ(region.getX(), region.getZ()), data);
        }
        MapRegionPack disk = view.disk() == null ? null : MapRegionPack.forDirectory(view.disk().directory());
        BiomeMapData result = new BiomeMapData(190, 90);
        float sx = view.pixelsX() / result.getWidth(), sy = view.pixelsY() / result.getHeight();
        java.util.Map<Long, List<int[]>> samples = new java.util.LinkedHashMap<>();
        for (int z = 0; z < result.getHeight(); z++) for (int x = 0; x < result.getWidth(); x++) {
            int wx = (int) Math.floor(view.north() ? z * sy * view.mouseToMap() + view.mapZ() - view.centerY() * view.guiToMap()
                    : x * sx * view.mouseToMap() + view.mapX() - view.centerX() * view.guiToMap());
            int wz = (int) Math.floor(view.north() ? -(x * sx * view.mouseToMap() + view.mapX() - view.centerX() * view.guiToMap())
                    : z * sy * view.mouseToMap() + view.mapZ() - view.centerY() * view.guiToMap());
            samples.computeIfAbsent(packXZ(Math.floorDiv(wx, 256), Math.floorDiv(wz, 256)), ignored -> new ArrayList<>())
                    .add(new int[]{x, z, wx & 255, wz & 255});
        }
        // Group samples by region: each saved region is decoded once, and its temporary data can be released immediately.
        for (var entry : samples.entrySet()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int rx = (int) (entry.getKey() >> 32), rz = (int) (long) entry.getKey();
            CompressibleMapData data = sources.get(entry.getKey());
            if (data == null && disk != null && disk.contains(rx, rz)) {
                BiomeDiskSource captured = view.disk();
                data = new CachedRegion(captured.map(), "biome overview", rx + "," + rz, captured.world(), captured.worldName(),
                        captured.subworld(), rx, rz, captured.dimension(), true, captured.directory()).loadOverviewMapData();
            }
            if (data != null) for (int[] sample : entry.getValue())
                if (data.getHeight(sample[2], sample[3]) != Short.MIN_VALUE || data.getLight(sample[2], sample[3]) != 0)
                    result.setBiome(sample[0], sample[1], data.getBiome(sample[2], sample[3]));
        }
        result.segmentBiomes(); result.findCenterOfSegments(true);
        return result;
    }

    private ChunkBounds overlayBounds(int cell) {
        PreviewBounds bounds = getVisibleWorldBounds();
        return new ChunkBounds(Math.floorDiv(bounds.minX(), 16), Math.floorDiv(bounds.maxX(), 16),
                Math.floorDiv(bounds.minZ(), 16), Math.floorDiv(bounds.maxZ(), 16)).align(cell, cell);
    }

    private WorldMapGeometry.Bounds overlayClipBounds() {
        PreviewBounds bounds = getVisibleWorldBounds();
        return new WorldMapGeometry.Bounds(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ());
    }

    private boolean mapIsMoving() {
        return currentDragging || Math.abs(deltaX) > 0.01F || Math.abs(deltaY) > 0.01F || zoom != zoomGoal;
    }

    private int exploredCellSize(boolean literal) {
        if (options.detail.trailResolution != 0) return options.detail.trailResolution;
        int size = 1;
        float chunkPixels = 16 * Math.max(0.0000001F, mapToGui);
        float target = literal ? 0.45F : (mapIsMoving() ? 2.0F : 1.0F);
        while (size * chunkPixels < target) size *= 2;
        ChunkBounds bounds = overlayBounds(size);
        while ((long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1) / size / size > 750_000L) size *= 2;
        return size;
    }

    private final WorldMapTrailTiles trailTiles = new WorldMapTrailTiles();

    private TrailView buildTrailView(ExploredCellQuery query, boolean outlines, boolean nodes, int budget) {
        if (budget == Integer.MAX_VALUE) {
            var sparse = query.store().sparseCellsInBounds(query.bounds(), query.cellSize());
            if (outlines) {
                List<CellGrid> tiles = new ArrayList<>();
                for (long key : sparse.tileKeys()) tiles.add(sparse.tile(key, 0));
                return new TrailView(ExploredLineMesher.Result.EMPTY, tiles, query.cellSize(), true);
            }
            return new TrailView(trailTiles.buildSparse(sparse, query.cellSize()), null, query.cellSize(), false);
        }
        long snapshotVersion = query.store().versionInBounds(query.bounds(), com.mamiyaotaru.voxelmap.persistent.explored.ExploredDiskStore.selectLevelForCellSize(query.cellSize()));
        CellGrid cells = query.build();
        int size = query.cellSize();
        if (outlines) {
            while (cellsCount(cells) * 4 > budget) { cells = cells.coarsen(2); size *= 2; }
            return new TrailView(ExploredLineMesher.Result.EMPTY, List.of(cells), size, true);
        }
        ExploredLineMesher.Result mesh;
        while (true) {
            mesh = trailTiles.build(query, cells, size, snapshotVersion);
            int count = mesh.segmentCount();
            for (int i = 0; i < mesh.nodeCount(); i++) if (nodes || !mesh.nodeLinked()[i]) count++;
            if (count <= budget || size >= 1_048_576) break;
            cells = cells.coarsen(2);
            size *= 2;
        }
        if (!nodes) {
            int isolated = 0;
            for (boolean linked : mesh.nodeLinked()) if (!linked) isolated++;
            float[] centers = new float[isolated * 2];
            int next = 0;
            for (int i = 0; i < mesh.nodeCount(); i++) if (!mesh.nodeLinked()[i]) {
                centers[next++] = mesh.nodeCoords()[i * 2]; centers[next++] = mesh.nodeCoords()[i * 2 + 1];
            }
            mesh = new ExploredLineMesher.Result(mesh.segments(), mesh.segmentCount(), centers, new boolean[isolated], isolated);
        }
        return new TrailView(mesh, null, size, false);
    }

    private static int cellsCount(CellGrid cells) {
        int count = 0;
        for (int i = cells.nextOccupied(0); i >= 0; i = cells.nextOccupied(i + 1)) count++;
        return count;
    }

    private void drawExploredChunkLinesWorldMap(GuiGraphicsExtractor graphics, int leftRegion, int rightRegion, int topRegion, int bottomRegion) {
        exploredQuadCount = 0;
        playerLayerStatusHitboxes.clear();
        if (!options.showExploredChunks || !layerVisible(WorldMapDetailSettings.Layer.TRAILS)) return;
        int alpha = Mth.clamp((int) Math.round(radarOptions.exploredChunksOpacity * 2.55D), 0, 255);
        boolean literal = options.isLiteralLineModeEnabled();
        if (isFarZoomPerformanceMode()) alpha = Math.max(alpha, 210);
        if (literal) alpha = Math.max(alpha, 235);
        if (alpha == 0) return;
        int color = (alpha << 24) | radarOptions.getExploredChunksColorRgb();
        float thickness = Math.max(0.15F, 1.25F * options.getChunkLineThickness() / Math.max(0.0000001F, mapToGui));
        Identifier dimension = getViewedDimensionIdentifier();
        var manager = VoxelConstants.getVoxelMapInstance().getExploredChunksManager();
        int baseCell = exploredCellSize(literal);
        int bucket = Math.getExponent(mapToGui);
        if (bucket != trailZoomBucket) { trailDetail.clear(); trailZoomBucket = bucket; }
        var slugs = manager.playerLayerSlugs(dimension);
        int players = 0;
        for (String slug : slugs) if (ChunkSharePlayerSettings.isEnabled(slug)) players++;
        boolean outlines = !literal;
        graphics.nextStratum();
        drawTrailLayer(graphics, manager, dimension, null, baseCell, thickness, color, literal, outlines, players == 0 ? 65_536 : 32_768);
        for (String slug : slugs) {
            if (!ChunkSharePlayerSettings.isEnabled(slug)) continue;
            drawTrailLayer(graphics, manager, dimension, slug, baseCell, thickness, playerLayerColor(slug, color),
                    literal, false, Math.max(1, 32_768 / Math.max(1, players)));
        }
        if (VoxelConstants.DEBUG && System.currentTimeMillis() - overlayProfileLastMs > 5000) {
            overlayProfileLastMs = System.currentTimeMillis();
            VoxelConstants.getLogger().info("World map view caches: trail hits={} builds={} area hits={} builds={} geometryBuilds={}",
                    trailViews.hits(), trailViews.builds(), areaViews.hits(), areaViews.builds(), geometryViews.builds());
        }
    }

    private void drawTrailLayer(GuiGraphicsExtractor graphics, com.mamiyaotaru.voxelmap.ExploredChunksManager manager,
            Identifier dimension, String slug, int baseCell, float thickness, int color, boolean literal, boolean outlines, int budget) {
        String name = dimension + ":" + (slug == null ? "self" : slug);
        boolean automatic = options.detail.trailResolution == 0;
        if (!automatic) budget = Integer.MAX_VALUE;
        final int buildBudget = budget;
        int cell = automatic ? Math.max(baseCell, trailDetail.getOrDefault(name, baseCell)) : baseCell;
        ChunkBounds bounds = overlayBounds(cell);
        ExploredCellQuery query = manager.prepareCells(bounds, cell, dimension, slug);
        if (query == null) return;
        String layer = name + ":" + query.store().identity();
        var status = automatic ? query.request() : new ExploredCellQuery.Status(query.store().contentVersionInBounds(bounds, cell), true);
        boolean nodes = literal && layerVisible(WorldMapDetailSettings.Layer.TRAIL_NODES);
        OverlayViewKey key = new OverlayViewKey(bounds, cell, status.version(), nodes, outlines, budget);
        RenderSourceKey requestedSource = new RenderSourceKey(layer, key);
        if (options.detail.overlayRenderer != 2 && rasterLayers.drawCached(graphics, layer,
                rasterKey(requestedSource, overlayClipBounds(), thickness, nodes, color))) {
            trailViews.cancelPending(layer); geometryViews.cancelPending(layer);
            return;
        }
        TrailView view = trailViews.get(layer, key, status.ready(), () -> buildTrailView(query, outlines, nodes, buildBudget), GuiPersistentMap::sameOverlayView);
        if (view == null) return;
        if (automatic) trailDetail.put(name, view.cell());
        GeometryKey geometryKey = new GeometryKey(new RenderSourceKey(layer, trailViews.displayedKey(layer)), overlayClipBounds(), thickness, nodes);
        long primitives = view.outlines() ? view.squares().stream().mapToLong(grid -> grid.cells.length * 4L).sum()
                : (long) view.mesh().segmentCount() + view.mesh().nodeCount();
        if (useRaster(primitives)) {
            var rasterKey = rasterKey(geometryKey.source(), geometryKey.bounds(), thickness, nodes, color);
            rasterLayers.draw(graphics, layer, rasterKey, () -> {
                try (var painter = new WorldMapRaster.Painter(rasterKey.bounds(), rasterKey.width(), rasterKey.height(), rasterKey.color(), thickness, rasterKey.smooth())) {
                    if (view.outlines()) {
                        float width = view.cell() * 16F;
                        for (CellGrid grid : view.squares()) {
                            for (int i = grid.nextOccupied(0); i >= 0; i = grid.nextOccupied(i + 1)) {
                                float x = (grid.minX + i % grid.width) * width, z = (grid.minZ + i / grid.width) * width;
                                painter.outline(x, z, x + width, z + width);
                            }
                        }
                    } else painter.trails(view.mesh(), thickness, nodes);
                    return painter.finish();
                }
            });
            return;
        }
        GeometryView geometry = geometryViews.get(layer, geometryKey, true, () -> {
            WorldMapGeometry.Quads quads;
            if (view.outlines()) {
                WorldMapGeometry.Builder builder = new WorldMapGeometry.Builder();
                float width = view.cell() * 16.0F;
                for (CellGrid grid : view.squares()) for (int i = grid.nextOccupied(0); i >= 0; i = grid.nextOccupied(i + 1)) {
                    float x = (grid.minX + i % grid.width) * width, z = (grid.minZ + i / grid.width) * width;
                    WorldMapGeometry.Bounds clip = geometryKey.bounds();
                    if (x > clip.maxX() || x + width < clip.minX() || z > clip.maxZ() || z + width < clip.minZ()) continue;
                    builder.line(x, z, x + width, z, thickness, clip); builder.line(x, z + width, x + width, z + width, thickness, clip);
                    builder.line(x, z, x, z + width, thickness, clip); builder.line(x + width, z, x + width, z + width, thickness, clip);
                }
                quads = builder.build();
            } else quads = WorldMapGeometry.lines(view.mesh(), geometryKey.bounds(), thickness, nodes);
            return new GeometryView(geometryKey, quads);
        }, GuiPersistentMap::sameGeometryView);
        if (geometry != null) VoxelMapGuiGraphics.fillMapQuads(graphics, geometry.quads().vertices(), geometry.quads().count(), color);
    }

    private static boolean sameOverlayView(OverlayViewKey a, OverlayViewKey b) {
        return a.bounds().equals(b.bounds()) && a.cell() == b.cell() && a.literal() == b.literal()
                && a.outlines() == b.outlines() && a.budget() == b.budget();
    }

    private static boolean sameGeometryView(GeometryKey a, GeometryKey b) {
        return a.bounds().equals(b.bounds()) && a.thickness() == b.thickness() && a.nodes() == b.nodes();
    }

    private boolean useRaster(long primitives) {
        return options.detail.overlayRenderer == 1 || (options.detail.overlayRenderer == 0 && primitives > 50_000);
    }

    private WorldMapRasterLayers.Key rasterKey(Object source, WorldMapGeometry.Bounds bounds, float thickness, boolean nodes, int color) {
        float factor = options.detail.rasterScalePercent / 100F;
        int width = Math.max(1, Math.min(8192, Math.round((oldNorth ? mapPixelsY : mapPixelsX) * factor)));
        int height = Math.max(1, Math.min(8192, Math.round((oldNorth ? mapPixelsX : mapPixelsY) * factor)));
        return new WorldMapRasterLayers.Key(source, bounds, thickness, nodes, color, width, height, options.detail.smoothOverlays);
    }

    private void drawNewOldChunkOverlayWorldMap(GuiGraphicsExtractor graphics, int leftRegion, int rightRegion, int topRegion, int bottomRegion) {
        if (!options.showNewOldChunks || !radarOptions.showNewerNewChunks || !layerVisible(WorldMapDetailSettings.Layer.NEW_OLD)) return;
        boolean moving = mapIsMoving();
        if (moving) newOldChunkLastMotionMs = System.currentTimeMillis();
        moving |= System.currentTimeMillis() - newOldChunkLastMotionMs < 250;
        int cell = options.detail.areaResolution == 0 ? getNewOldChunkCellChunkSize(moving, isFarZoomPerformanceMode()) : options.detail.areaResolution;
        ChunkBounds bounds = overlayBounds(cell);
        var manager = VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager();
        Identifier dimension = getViewedDimensionIdentifier();
        int oldColor = (Mth.clamp((int) Math.round(radarOptions.newerNewChunksOldOpacity * 2.55D), 0, 255) << 24)
                | radarOptions.getNewerNewChunksOldColorRgb();
        int newColor = (Mth.clamp((int) Math.round(radarOptions.newerNewChunksNewOpacity * 2.55D), 0, 255) << 24)
                | radarOptions.getNewerNewChunksNewColorRgb();
        if ((oldColor >>> 24) == 0 && (newColor >>> 24) == 0) return;
        var slugs = manager.playerLayerSlugs(dimension);
        int players = 0;
        for (String slug : slugs) if (ChunkSharePlayerSettings.isEnabled(slug)) players++;
        int budget = options.detail.areaResolution == 0 ? Math.min(32_768, getNewOldChunkMaxDraw(moving, isFarZoomPerformanceMode())) : Integer.MAX_VALUE;
        graphics.nextStratum();
        drawAreaLayer(graphics, manager, dimension, null, bounds, cell, oldColor, newColor, budget);
        for (String slug : slugs) {
            if (ChunkSharePlayerSettings.isEnabled(slug)) drawAreaLayer(graphics, manager, dimension, slug, bounds, cell,
                    playerLayerColor(slug, oldColor), playerLayerColor(slug, newColor), (budget == Integer.MAX_VALUE ? budget : Math.max(1, budget / Math.max(1, players))));
        }
    }

    private void drawAreaLayer(GuiGraphicsExtractor graphics, NewerNewChunksManager manager, Identifier dimension,
            String slug, ChunkBounds bounds, int cell, int oldColor, int newColor, int budget) {
        var query = manager.prepareCells(bounds, cell, dimension, slug);
        if (query == null) return;
        boolean automatic = options.detail.areaResolution == 0;
        var status = automatic ? query.request() : new ExploredCellQuery.Status(Math.max(Math.max(query.fresh().store().contentVersionInBounds(bounds, cell), query.old().store().contentVersionInBounds(bounds, cell)),
                Math.max(query.updating().store().contentVersionInBounds(bounds, cell), query.generation().store().contentVersionInBounds(bounds, cell))), true);
        String layer = "area:" + dimension + ":" + slug + ":" + query.fresh().store().identity();
        OverlayViewKey key = new OverlayViewKey(bounds, cell, status.version(), false, false, budget);
        RenderSourceKey requestedSource = new RenderSourceKey(layer, key);
        var oldImageKey = rasterKey(requestedSource, overlayClipBounds(), 0, false, oldColor);
        var newImageKey = rasterKey(requestedSource, overlayClipBounds(), 0, false, newColor);
        if (options.detail.overlayRenderer != 2 && rasterLayers.contains(layer + ":old", oldImageKey) && rasterLayers.contains(layer + ":new", newImageKey)) {
            rasterLayers.drawCached(graphics, layer + ":old", oldImageKey);
            rasterLayers.drawCached(graphics, layer + ":new", newImageKey);
            areaViews.cancelPending(layer); geometryViews.cancelPending(layer + ":old"); geometryViews.cancelPending(layer + ":new");
            return;
        }
        AreaView view = areaViews.get(layer, key, status.ready(), () -> {
            if (!automatic) return buildSparseAreas(query, cell);
            var snap = query.build();
            CellGrid oldGrid = snap.oldCells(), newGrid = snap.newCells();
            int size = cell;
            while (true) {
                // Greedy meshing consumes its input. Copies retain the grids for a coarser retry.
                CellGrid oldCopy = copyCells(oldGrid), newCopy = copyCells(newGrid);
                var oldRects = greedyMeshNewOldChunkCells(oldCopy, size, 0, Integer.MAX_VALUE);
                var newRects = greedyMeshNewOldChunkCells(newCopy, size, 0, Integer.MAX_VALUE);
                if (oldRects.size() + newRects.size() <= budget || size >= 1024) return new AreaView(oldRects, newRects);
                oldGrid = oldGrid.coarsen(2); newGrid = newGrid.coarsen(2); size *= 2;
                for (int i = oldGrid.nextOccupied(0); i >= 0; i = oldGrid.nextOccupied(i + 1)) newGrid.cells[i] = false;
            }
        }, GuiPersistentMap::sameOverlayView);
        if (view == null) return;
        drawAreaGeometry(graphics, layer + ":old", new RenderSourceKey(layer, areaViews.displayedKey(layer)), view.oldRects(), oldColor);
        drawAreaGeometry(graphics, layer + ":new", new RenderSourceKey(layer, areaViews.displayedKey(layer)), view.newRects(), newColor);
    }

    private static CellGrid copyCells(CellGrid source) {
        CellGrid copy = new CellGrid(source.minX, source.minZ, source.width, source.height);
        for (int i = source.nextOccupied(0); i >= 0; i = source.nextOccupied(i + 1)) copy.mark(source.minX + i % source.width, source.minZ + i / source.width);
        return copy;
    }

    private void drawAreaGeometry(GuiGraphicsExtractor graphics, String layer, RenderSourceKey source, List<NewOldChunkRenderRect> rects, int color) {
        WorldMapGeometry.Bounds bounds = overlayClipBounds();
        GeometryKey key = new GeometryKey(source, bounds, 0, false);
        if (useRaster(rects.size())) {
            var rasterKey = rasterKey(source, bounds, 0, false, color);
            rasterLayers.draw(graphics, layer, rasterKey, () -> {
                try (var painter = new WorldMapRaster.Painter(bounds, rasterKey.width(), rasterKey.height(), color, 1, rasterKey.smooth())) {
                    int completed = 0;
                    WorldMapProgress.report("Drawing chunks", 0, rects.size());
                    for (NewOldChunkRenderRect rect : rects) {
                        painter.rect(rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ());
                        if ((++completed & 4095) == 0) WorldMapProgress.report("Drawing chunks", completed, rects.size());
                    }
                    return painter.finish();
                }
            });
            return;
        }
        GeometryView geometry = geometryViews.get(layer, key, true, () -> {
            WorldMapGeometry.Builder builder = new WorldMapGeometry.Builder();
            for (NewOldChunkRenderRect rect : rects) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                float x0 = Math.max(bounds.minX(), rect.minX()), x1 = Math.min(bounds.maxX(), rect.maxX());
                float z0 = Math.max(bounds.minZ(), rect.minZ()), z1 = Math.min(bounds.maxZ(), rect.maxZ());
                if (x1 > x0 && z1 > z0) builder.rect(x0, z0, x1, z1);
            }
            return new GeometryView(key, builder.build());
        }, GuiPersistentMap::sameGeometryView);
        if (geometry != null) VoxelMapGuiGraphics.fillMapQuads(graphics, geometry.quads().vertices(), geometry.quads().count(), color);
    }

    private AreaView buildSparseAreas(NewerNewChunksManager.CellQuery query, int size) {
        var fresh = query.fresh().store().sparseCellsInBounds(query.fresh().bounds(), size);
        var old = query.old().store().sparseCellsInBounds(query.old().bounds(), size);
        old.subtract(query.updating().store().sparseCellsInBounds(query.updating().bounds(), size));
        old.subtract(query.generation().store().sparseCellsInBounds(query.generation().bounds(), size));
        if (size == 1) old.subtract(fresh); else fresh.subtract(old);
        List<NewOldChunkRenderRect> oldRects = new ArrayList<>(), newRects = new ArrayList<>();
        var oldTiles = old.tileKeys(); var freshTiles = fresh.tileKeys();
        int completed = 0, total = oldTiles.size() + freshTiles.size();
        WorldMapProgress.report("Meshing tiles", 0, total);
        for (long tile : oldTiles) {
            oldRects.addAll(greedyMeshNewOldChunkCells(old.tile(tile, 0), size, 0, Integer.MAX_VALUE));
            WorldMapProgress.report("Meshing tiles", ++completed, total);
        }
        for (long tile : freshTiles) {
            newRects.addAll(greedyMeshNewOldChunkCells(fresh.tile(tile, 0), size, 0, Integer.MAX_VALUE));
            WorldMapProgress.report("Meshing tiles", ++completed, total);
        }
        return new AreaView(List.copyOf(oldRects), List.copyOf(newRects));
    }

    private List<NewOldChunkRenderRect> greedyMeshNewOldChunkCells(CellGrid grid, int cellChunkSize, int color, int maxDraw) {
        int w = grid.width;
        int h = grid.height;
        if (w <= 0 || h <= 0) {
            return List.of();
        }
        boolean[] cells = grid.cells;
        int minCellX = grid.minX;
        int minCellZ = grid.minZ;
        float worldCellSize = 16.0F * cellChunkSize;

        ArrayList<NewOldChunkRenderRect> rects = new ArrayList<>();
        for (int gz = 0; gz < h; gz++) {
            int rowBase = gz * w;
            for (int gx = 0; gx < w; gx++) {
                if (!cells[rowBase + gx]) {
                    continue;
                }
                if (rects.size() >= maxDraw) {
                    return List.copyOf(rects);
                }
                int rectWidth = 1;
                while (gx + rectWidth < w && cells[rowBase + gx + rectWidth]) {
                    rectWidth++;
                }
                int rectHeight = 1;
                boolean canGrow = true;
                while (canGrow && gz + rectHeight < h) {
                    int nextRow = (gz + rectHeight) * w;
                    for (int x = gx; x < gx + rectWidth; x++) {
                        if (!cells[nextRow + x]) {
                            canGrow = false;
                            break;
                        }
                    }
                    if (canGrow) {
                        rectHeight++;
                    }
                }
                for (int z = gz; z < gz + rectHeight; z++) {
                    int clearRow = z * w;
                    for (int x = gx; x < gx + rectWidth; x++) {
                        cells[clearRow + x] = false;
                    }
                }
                float minX = (minCellX + gx) * worldCellSize;
                float minZ = (minCellZ + gz) * worldCellSize;
                rects.add(new NewOldChunkRenderRect(minX, minZ, minX + rectWidth * worldCellSize, minZ + rectHeight * worldCellSize, color));
            }
        }

        return List.copyOf(rects);
    }

    private int getNewOldChunkCellChunkSize(boolean movingLod, boolean farZoomPerformanceMode) {
        if (farZoomPerformanceMode || this.mapToGui < 0.03F) {
            return movingLod ? 32 : 16;
        }
        if (this.mapToGui < 0.06F) {
            return movingLod ? 16 : 8;
        }
        if (this.mapToGui < 0.10F) {
            return movingLod ? 8 : 4;
        }
        if (this.mapToGui < 0.18F) {
            return movingLod ? 4 : 2;
        }
        return 1;
    }

    private int getNewOldChunkMaxDraw(boolean movingLod, boolean farZoomPerformanceMode) {
        if (farZoomPerformanceMode) {
            return movingLod ? 1600 : 4200;
        }
        if (this.mapToGui < 0.06F) {
            return movingLod ? 700 : 1800;
        }
        if (this.mapToGui < 0.10F) {
            return movingLod ? 1200 : 2600;
        }
        return Integer.MAX_VALUE;
    }

    private long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void clearExploredLineCaches() {
        biomeViews.clear(); biomeDiskSource = null;
        trailTiles.clear();
        trailViews.clear(); areaViews.clear(); geometryViews.clear(); rasterLayers.clear(); trailDetail.clear();
    }

    private static int playerLayerColor(String slug, int alphaSource) {
        return ChunkSharePlayerSettings.colorFor(slug, alphaSource);
    }

    private void drawPlayerLayerStatuses(GuiGraphicsExtractor graphics) {
        this.playerLayerStatusHitboxes.clear();
        java.util.Set<String> slugs = new java.util.LinkedHashSet<>();
        slugs.addAll(VoxelConstants.getVoxelMapInstance().getExploredChunksManager().playerLayerSlugs(getViewedDimensionIdentifier()));
        slugs.addAll(VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().playerLayerSlugs(getViewedDimensionIdentifier()));
        int y = this.top + 5;
        for (String slug : slugs) {
            boolean enabled = ChunkSharePlayerSettings.isEnabled(slug);
            String text = enabled ? "Chunk Share Active: " + layerDisplayName(slug) : "Chunk Share: " + layerDisplayName(slug);
            int color = enabled ? playerLayerColor(slug, 0xFF000000) : 0xFFFFFFFF;
            int x = this.width - this.sideMargin - this.getFont().width(text);
            drawTextBackground(graphics, x - 3, y - 2, this.getFont().width(text) + 6, this.getFont().lineHeight + 4, 0x80000000);
            graphics.text(this.getFont(), text, x, y, color, true);
            this.playerLayerStatusHitboxes.add(new PlayerLayerStatusHitbox(slug, x, y - 1, this.width - this.sideMargin, y + 10));
            y += 11;
        }
    }

    private void drawTextBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    private void drawStatusText(GuiGraphicsExtractor graphics, String text, int x, int y, int textColor, int backgroundColor) {
        int width = this.getFont().width(text);
        drawTextBackground(graphics, x - 2, y - 1, width + 4, this.getFont().lineHeight + 2, backgroundColor);
        graphics.text(this.getFont(), text, x, y, textColor);
    }

    static String loadingLayerLabel(String layer) { return layer.startsWith("area:") ? "New/Old Chunks" : "Chunk Trails"; }

    private void drawBottomStatusTexts(GuiGraphicsExtractor graphics) {
        if (!this.options.showLoadingBars) return;
        int y = this.bottom - 22;
        int x = this.sideMargin + 2;
        int width = Math.min(220, Math.max(80, this.getWidth() / 3));
        for (WorldMapProgress.Row row : WorldMapProgress.rows()) {
            if (y < this.top + 16) break;
            graphics.fill(x - 2, y - 2, x + width + 2, y + 17, 0xD0000000);
            String text = row.layer() + ": " + (row.percent() < 0 ? "Loading" : row.percent() + "%") + " (" + row.stage() + ")";
            graphics.text(this.getFont(), this.getFont().plainSubstrByWidth(text, width), x, y, 0xFFE0E0E0);
            graphics.fill(x, y + 11, x + width, y + 15, 0xFF343434);
            if (row.percent() >= 0) graphics.fill(x, y + 11, x + width * row.percent() / 100, y + 15, 0xFF00BEE8);
            else {
                int offset = (int) ((System.currentTimeMillis() / 15) % Math.max(1, width - 24));
                graphics.fill(x + offset, y + 11, x + offset + 24, y + 15, 0xFF00BEE8);
            }
            y -= 22;
        }
        if (seedMapperQueryLoading && y >= this.top + 16) {
            drawStatusText(graphics, "Structures: Loading", x, y, 0xFFE0E0E0, 0xD0000000);
            graphics.fill(x, y + 11, x + width, y + 15, 0xFF343434);
            int offset = (int) ((System.currentTimeMillis() / 15) % Math.max(1, width - 24));
            graphics.fill(x + offset, y + 11, x + offset + 24, y + 15, 0xFF00BEE8);
        }
    }

    private boolean handlePlayerLayerStatusClick(int mouseX, int mouseY) {
        for (PlayerLayerStatusHitbox hitbox : this.playerLayerStatusHitboxes) {
            if (mouseX >= hitbox.left() && mouseX <= hitbox.right()
                    && mouseY >= hitbox.top() && mouseY <= hitbox.bottom()) {
                ChunkSharePlayerSettings.toggleEnabled(hitbox.slug());
                return true;
            }
        }
        return false;
    }

    /** A human-readable label for a player layer slug (slugs use only filesystem-safe chars already). */
    private static String layerDisplayName(String slug) {
        return slug;
    }

    private record PlayerLayerStatusHitbox(String slug, int left, int top, int right, int bottom) {
    }

    private void appendExploredQuad(float x0, float y0, float x1, float y1, int color) {
        int index = this.exploredQuadCount;
        if (index == this.exploredQuadColors.length) {
            this.exploredQuadColors = java.util.Arrays.copyOf(this.exploredQuadColors, this.exploredQuadColors.length * 2);
            this.exploredQuadCoords = java.util.Arrays.copyOf(this.exploredQuadCoords, this.exploredQuadCoords.length * 2);
        }
        int offset = index << 2;
        this.exploredQuadCoords[offset] = x0;
        this.exploredQuadCoords[offset + 1] = y0;
        this.exploredQuadCoords[offset + 2] = x1;
        this.exploredQuadCoords[offset + 3] = y1;
        this.exploredQuadColors[index] = color;
        this.exploredQuadCount = index + 1;
    }

    private void flushExploredQuads(GuiGraphicsExtractor graphics) {
        int count = this.exploredQuadCount;
        if (count <= 0) {
            return;
        }
        // exact size arrays so the batched element can own making it so the buffers can be reused next frame
        float[] coords = java.util.Arrays.copyOf(this.exploredQuadCoords, count << 2);
        int[] colors = java.util.Arrays.copyOf(this.exploredQuadColors, count);
        VoxelMapGuiGraphics.fillRectsBatched(graphics, coords, colors, count);
        this.exploredQuadCount = 0;
    }

    private void appendThickInterpolatedLine(float x1, float z1, float x2, float z2, float thickness, int color) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length <= 0.01F) {
            float half = thickness / 2.0F;
            appendExploredQuad(x1 - half, z1 - half, x1 + half, z1 + half, color);
            return;
        }
        float half = thickness / 2.0F;
        if (Math.abs(dz) < 0.001F) {
            float minX = Math.min(x1, x2);
            float maxX = Math.max(x1, x2);
            appendExploredQuad(minX - half, z1 - half, maxX + half, z1 + half, color);
            return;
        }
        if (Math.abs(dx) < 0.001F) {
            float minZ = Math.min(z1, z2);
            float maxZ = Math.max(z1, z2);
            appendExploredQuad(x1 - half, minZ - half, x1 + half, maxZ + half, color);
            return;
        }
        // Sub-pixel stepping keeps diagonal lines visually continuous while still cheap at far zoom.
        float step = Math.max(Math.max(1.0F, thickness * 0.45F), 0.35F / Math.max(0.0000001F, this.mapToGui));
        if (this.mapToGui < 0.006F) {
            step = Math.max(step, 10.0F / Math.max(0.0000001F, this.mapToGui));
        } else if (this.mapToGui < 0.012F) {
            step = Math.max(step, 7.0F / Math.max(0.0000001F, this.mapToGui));
        } else if (this.mapToGui < 0.03F) {
            step = Math.max(step, 4.0F / Math.max(0.0000001F, this.mapToGui));
        }
        int steps = Math.max(1, (int) Math.ceil(length / step));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float px = x1 + dx * t;
            float pz = z1 + dz * t;
            appendExploredQuad(px - half, pz - half, px + half, pz + half, color);
        }
    }

    private boolean layerVisible(WorldMapDetailSettings.Layer layer) {
        return options.detail.visible(layer, zoom);
    }

    private boolean isFarZoomPerformanceMode() {
        return options.automaticLayerHiding && this.mapToGui < this.options.getPerformanceModeThreshold();
    }

    private boolean drawPlayer(GuiGraphicsExtractor graphics, Identifier skin, float playerX, float playerZ, int mouseX, int mouseY) {
        float headWidth = ICON_WIDTH * 0.75F;
        float headHeight = ICON_HEIGHT * 0.75F;

        int x = this.width / 2;
        int y = this.height / 2;
        int borderX = x - 4;
        int borderY = y - this.top;

        double wayX = this.mapCenterX - (this.oldNorth ? -playerZ : playerX);
        double wayY = this.mapCenterZ - (this.oldNorth ? playerX : playerZ);
        double dispX = wayX * mapToGui;
        double dispY = wayY * mapToGui;
        float locate = 0;
        float hypot = 0;
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
            locate = (float) Math.atan2(wayX, wayY);
            hypot = (float) Math.hypot(dispX, dispY);
            hypot *= (float) Math.min(borderX / Math.abs(dispX), borderY / Math.abs(dispY));
        }

        graphics.pose().pushMatrix();

        if (far) {
            graphics.pose().translate(x, y);
            graphics.pose().rotate(-locate);
            graphics.pose().translate(0.0F, -hypot);
            graphics.pose().rotate(locate);
            graphics.pose().translate(-x, -y);
        } else {
            graphics.pose().translate((float) -dispX, (float) -dispY);
        }

        Vector2f guiVector = graphics.pose().transformPosition(new Vector2f(x, y));
        float screenX = guiVector.x();
        float screenY = guiVector.y();

        boolean isHovered = mouseX >= screenX - ICON_WIDTH / 2.0F && mouseX <= screenX + ICON_WIDTH / 2.0F
                && mouseY >= screenY - ICON_HEIGHT / 2.0F && mouseY <= screenY + ICON_HEIGHT / 2.0F;
        if (isHovered) {
            graphics.requestCursor(CursorTypes.CROSSHAIR);
            if (options.showCoordinates) {
                renderTooltip(graphics, Component.literal("X: " + Math.round(playerX) + ", Y: " + GameVariableAccessShim.yCoord() + ", Z: " + Math.round(playerZ)), this.mouseX, this.mouseY);
            }
        }

        VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, skin, x - headWidth / 2.0F, y - headHeight / 2.0F, headWidth, headHeight, 0, 1, 0, 1, 0xFFFFFFFF);
        if (options.showPlayerDirectionArrow) {
            float arrowSize = 10.0F;
            float angle = (float) (Math.toRadians(GameVariableAccessShim.rotationYaw()) + Math.PI);
            if (this.oldNorth) {
                angle += (float) (Math.PI / 2.0D);
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().rotate(angle);
            VoxelMapGuiGraphics.blitFloat(
                    graphics,
                    RenderPipelines.GUI_TEXTURED,
                    seedMapperDirectionArrowResource,
                    -arrowSize / 2.0F,
                    -headHeight / 2.0F - arrowSize - 2.0F,
                    arrowSize,
                    arrowSize,
                    0, 1, 0, 1,
                    0xFFFFFFFF
            );
            graphics.pose().popMatrix();
        }

        graphics.pose().popMatrix();

        return isHovered;
    }

    private boolean drawWaypoint(GuiGraphicsExtractor graphics, Waypoint waypoint, TextureAtlas textureAtlas, Sprite icon, boolean isHighlighted, int color, int mouseX, int mouseY) {
        int viewedX = getWaypointXInViewedDimension(waypoint);
        int viewedZ = getWaypointZInViewedDimension(waypoint);
        float ptX = viewedX + 0.5F;
        float ptZ = viewedZ + 0.5F;

        int x = this.width / 2;
        int y = this.height / 2;

        int borderOffsetX = options.showDistantWaypoints ? -4 : ICON_WIDTH / 2;
        int borderOffsetY = options.showDistantWaypoints ? 0 : ICON_HEIGHT / 2;
        int borderX = x + borderOffsetX;
        int borderY = y - this.top + borderOffsetY;

        double wayX = this.mapCenterX - (this.oldNorth ? -ptZ : ptX);
        double wayY = this.mapCenterZ - (this.oldNorth ? ptX : ptZ);
        double dispX = wayX * mapToGui;
        double dispY = wayY * mapToGui;
        float locate = 0;
        float hypot = 0;
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
            if (!options.showDistantWaypoints) {
                return false;
            }

            locate = (float) Math.atan2(wayX, wayY);
            hypot = (float) Math.hypot(dispX, dispY);
            hypot *= (float) Math.min(borderX / Math.abs(dispX), borderY / Math.abs(dispY));
        }

        boolean uprightIcon = icon != null;
        String name = waypoint.name;
        if (waypointManager.isCoordinateHighlight(waypoint)) {
            name = "X:" + viewedX + ", Y:" + waypoint.getY() + ", Z:" + viewedZ;
        }

        if (icon == null) {
            String iconLocation = (far ? "marker/" : "selectable/") + waypoint.imageSuffix;
            String fallbackLocation = far ? "marker/arrow" : WaypointManager.fallbackIconLocation;

            icon = textureAtlas.getAtlasSprite(iconLocation);
            if (icon == textureAtlas.getMissingImage()) {
                icon = textureAtlas.getAtlasSprite(fallbackLocation);
            }
        }

        graphics.pose().pushMatrix();

        if (far) {
            graphics.pose().translate(x, y);
            graphics.pose().rotate(-locate);
            if (uprightIcon) {
                graphics.pose().translate(0.0F, -hypot);
                graphics.pose().rotate(locate);
                graphics.pose().translate(-x, -y);
            } else {
                graphics.pose().translate(-x, -y);
                graphics.pose().translate(0.0F, -hypot);
            }
        } else {
            graphics.pose().translate((float) -dispX, (float) -dispY);
        }

        Vector2f guiVector = graphics.pose().transformPosition(new Vector2f(x, y));
        float screenX = guiVector.x();
        float screenY = guiVector.y();

        boolean isHovered = mouseX >= screenX - ICON_WIDTH / 2.0F && mouseX <= screenX + ICON_WIDTH / 2.0F
                && mouseY >= screenY - ICON_HEIGHT / 2.0F && mouseY <= screenY + ICON_HEIGHT / 2.0F;
        if (isHovered) {
            graphics.requestCursor(CursorTypes.CROSSHAIR);
            if (options.showCoordinates) {
                renderTooltip(graphics, Component.literal("X: " + viewedX + ", Y: " + waypoint.getY() + ", Z: " + viewedZ), this.mouseX, this.mouseY);
            }
        }

        String searchQuery = frameWaypointSearch;
        boolean searchActive = !searchQuery.isEmpty();
        boolean searchMatch = !searchActive || waypointMatchesSearch(waypoint, searchQuery);

        if (options.clusterWaypointNames && mapToGui < 1.0F && !isHighlighted && !isHovered && !(searchActive && searchMatch)) {
            long bucket = packXZ(Math.round(screenX / 8), Math.round(screenY / 8));
            WaypointClusterData cluster = waypointIconBuckets.get(bucket);
            if (cluster != null) {
                cluster.count++;
                graphics.pose().popMatrix();
                return false;
            }
            cluster = new WaypointClusterData(Math.round(screenX), Math.round(screenY));
            cluster.count = 1;
            waypointIconBuckets.put(bucket, cluster);
        }

        int iconColor = color == -1
                ? waypoint.getUnifiedColor(!waypoint.enabled && !isHighlighted && !isHovered ? 0.3F : 1.0F)
                : color;
        if (searchActive && !searchMatch) {
            iconColor = (iconColor & 0x00FFFFFF) | (0x40 << 24);
        }
        int textColor = searchMatch && searchActive
                ? 0xFFFFFF66
                : (!waypoint.enabled && !isHighlighted && !isHovered ? 0x55FFFFFF : 0xFFFFFFFF);

        icon.blit(graphics, RenderPipelines.GUI_TEXTURED, x - ICON_WIDTH / 2.0F, y - ICON_HEIGHT / 2.0F, ICON_WIDTH, ICON_HEIGHT, iconColor);

        boolean showLabel = options.showWaypointNames && layerVisible(WorldMapDetailSettings.Layer.WAYPOINT_NAMES) && searchMatch && !far;
        if (showLabel) {
            int labelWidth = textWidth(name);
            float labelBaseY = screenY + ICON_HEIGHT / 2.0F + WAYPOINT_LABEL_PADDING;
            this.pendingWaypointLabels.add(new PendingWaypointLabel(
                    screenX,
                    screenY,
                    labelBaseY,
                    labelWidth,
                    this.getFont().lineHeight,
                    textColor,
                    name,
                    searchActive,
                    searchMatch,
                    isHighlighted,
                    packXZ(Math.round(screenX / WAYPOINT_CLUSTER_BUCKET_SIZE), Math.round(screenY / WAYPOINT_CLUSTER_BUCKET_SIZE))
            ));
        }

        graphics.pose().popMatrix();

        return isHovered;
    }

    private void drawSeedPreview(GuiGraphicsExtractor graphics, PreviewBounds visibleBounds) {
        this.seedPreviewDrewThisFrame = false;
        if (!this.seedMapperOptions.worldMapSeedPreview || this.seedPreviewTexture == null) {
            return;
        }
        if (this.zoom < this.options.getSeedMapMinZoom()) {
            this.seedPreviewLoading = false;
            return;
        }

        applyPendingSeedPreview();

        long seed;
        try {
            seed = resolveWorldMapSeed();
        } catch (IllegalArgumentException ignored) {
            return;
        }

        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            return;
        }

        boolean mapInMotion = currentDragging
                || Math.abs(this.deltaX) > 0.01F
                || Math.abs(this.deltaY) > 0.01F
                || this.zoom != this.zoomGoal;
        PreviewBounds requestBounds = getSeedPreviewRequestBounds(visibleBounds);
        SeedPreviewSampling.Layout sampling = SeedPreviewSampling.viewport(visibleBounds.minX(), visibleBounds.maxX(),
                visibleBounds.minZ(), visibleBounds.maxZ(), requestBounds.minX(), requestBounds.maxX(),
                requestBounds.minZ(), requestBounds.maxZ(), this.guiToMap / this.guiToDirectMouse,
                this.options.getSeedMapPreviewResolution());
        requestBounds = new PreviewBounds(sampling.minX(), sampling.maxX(), sampling.minZ(), sampling.maxZ());
        int requestTextureWidth = sampling.width(), requestTextureHeight = sampling.height();
        SeedPreviewQueryCacheKey requestKey = new SeedPreviewQueryCacheKey(
                seed,
                getViewedDimensionIdentifier(),
                dimension,
                requestBounds.minX(),
                requestBounds.maxX(),
                requestBounds.minZ(),
                requestBounds.maxZ(),
                getSeedMapperGeneratorFlags(),
                requestTextureWidth,
                requestTextureHeight,
                SeedMapperCompat.getMcVersion(),
                this.options.seedMapStyle.needsTerrain() && this.zoom >= this.options.getSeedMapTerrainMinZoom(),
                this.seedMapperOptions.seedMapBiomeY,
                this.options.getSeedMapPreviewSettingsHash()
        );
        synchronized (this.seedPreviewLock) {
            this.seedPreviewCacheLimit = this.options.getSeedMapPreviewCacheSize();
            boolean needNew = this.seedPreviewDisplayedKey == null
                    || !canReuseSeedPreviewForRequest(this.seedPreviewDisplayedKey, requestKey, visibleBounds);
            boolean allowRequeue = !mapInMotion
                    || this.options.seedMapPreviewUpdateWhileMoving
                    || this.seedPreviewDisplayedKey == null;
            if (needNew && allowRequeue) {
                SeedPreviewQueryCacheKey cachedKey = requestKey;
                int[] cached = this.seedPreviewCache.get(requestKey);
                if (cached == null) {
                    // The viewport may fit a retained padded image even if its new
                    // padding differs. Reuse that image without sampling or uploading
                    // a succession of nearly identical views while dragging.
                    for (var entry : this.seedPreviewCache.entrySet()) {
                        if (canReuseSeedPreviewForRequest(entry.getKey(), requestKey, visibleBounds)) {
                            cachedKey = entry.getKey(); cached = entry.getValue();
                        }
                    }
                }
                if (cached != null) {
                    this.seedPreviewPendingPixels = cached;
                    this.seedPreviewPendingKey = cachedKey;
                    this.seedPreviewLoading = false;
                } else {
                    boolean workerIdle = this.seedPreviewFuture == null || this.seedPreviewFuture.isDone() || this.seedPreviewFuture.isCancelled();
                    long minRequestIntervalMs = mapInMotion ? SEED_PREVIEW_REQUEST_INTERVAL_MOVING_MS : SEED_PREVIEW_REQUEST_INTERVAL_STILL_MS;
                    if (workerIdle && System.currentTimeMillis() - this.seedPreviewLastRequestMs >= minRequestIntervalMs) {
                        queueSeedPreview(requestKey);
                    }
                }
            }
        }

        if (this.seedPreviewDisplayedKey == null || !canDisplaySeedPreview(this.seedPreviewDisplayedKey, requestKey)) {
            return;
        }

        int overlapMinX = Math.max(this.seedPreviewDisplayedKey.minX(), visibleBounds.minX());
        int overlapMaxX = Math.min(this.seedPreviewDisplayedKey.maxX(), visibleBounds.maxX());
        int overlapMinZ = Math.max(this.seedPreviewDisplayedKey.minZ(), visibleBounds.minZ());
        int overlapMaxZ = Math.min(this.seedPreviewDisplayedKey.maxZ(), visibleBounds.maxZ());
        if (overlapMaxX <= overlapMinX || overlapMaxZ <= overlapMinZ) {
            return;
        }
        this.seedPreviewDrewThisFrame = true;

        float previewSpanX = Math.max(1.0F, (float) this.seedPreviewDisplayedKey.maxX() - this.seedPreviewDisplayedKey.minX());
        float previewSpanZ = Math.max(1.0F, (float) this.seedPreviewDisplayedKey.maxZ() - this.seedPreviewDisplayedKey.minZ());
        float minU = ((float) overlapMinX - this.seedPreviewDisplayedKey.minX()) / previewSpanX;
        float maxU = ((float) overlapMaxX - this.seedPreviewDisplayedKey.minX()) / previewSpanX;
        float minV = ((float) overlapMinZ - this.seedPreviewDisplayedKey.minZ()) / previewSpanZ;
        float maxV = ((float) overlapMaxZ - this.seedPreviewDisplayedKey.minZ()) / previewSpanZ;
        VoxelMapGuiGraphics.blitFloat(
                graphics,
                RenderPipelines.GUI_TEXTURED,
                this.seedPreviewTexture,
                overlapMinX,
                overlapMinZ,
                overlapMaxX - overlapMinX,
                overlapMaxZ - overlapMinZ,
                minU,
                maxU,
                minV,
                maxV,
                0xFFFFFFFF
        );
    }

    private void queueSeedPreview(SeedPreviewQueryCacheKey requestKey) {
        this.seedPreviewLastRequestMs = System.currentTimeMillis();
        if (!this.seedPreviewLoading) {
            this.seedPreviewLoadingStartedMs = this.seedPreviewLastRequestMs;
        }
        this.seedPreviewLoading = true;
        if (this.seedPreviewProgress != null) this.seedPreviewProgress.cancel();
        WorldMapProgress.Task task = WorldMapProgress.begin("Seed Map");
        this.seedPreviewProgress = task;
        this.seedPreviewFuture = seedPreviewCoordinator.submit(() -> {
            int[] pixels = WorldMapProgress.run(task, () -> buildSeedPreviewPixels(requestKey));
            SeedPreviewJobs.checkCancelled();
            synchronized (this.seedPreviewLock) {
                this.seedPreviewPendingPixels = pixels;
                this.seedPreviewPendingKey = requestKey;
                this.seedPreviewCache.put(requestKey, pixels);
            }
        });
    }

    private boolean canDisplaySeedPreview(SeedPreviewQueryCacheKey displayedKey, SeedPreviewQueryCacheKey requestKey) {
        return displayedKey.seed() == requestKey.seed()
                && displayedKey.dimensionIdentifier().equals(requestKey.dimensionIdentifier())
                && displayedKey.dimension() == requestKey.dimension()
                && displayedKey.generatorFlags() == requestKey.generatorFlags()
                && displayedKey.mcVersion() == requestKey.mcVersion()
                && displayedKey.settingsHash() == requestKey.settingsHash()
                && displayedKey.biomeY() == requestKey.biomeY()
                && displayedKey.terrainEnabled() == requestKey.terrainEnabled();
    }

    private boolean canReuseSeedPreviewForRequest(SeedPreviewQueryCacheKey displayedKey, SeedPreviewQueryCacheKey requestKey, PreviewBounds visibleBounds) {
        return displayedKey.seed() == requestKey.seed()
                && displayedKey.dimensionIdentifier().equals(requestKey.dimensionIdentifier())
                && displayedKey.dimension() == requestKey.dimension()
                && displayedKey.generatorFlags() == requestKey.generatorFlags()
                && ((long) displayedKey.maxX() - displayedKey.minX()) / displayedKey.textureWidth()
                        <= ((long) requestKey.maxX() - requestKey.minX()) / requestKey.textureWidth()
                && displayedKey.biomeY() == requestKey.biomeY()
                && displayedKey.mcVersion() == requestKey.mcVersion()
                && displayedKey.terrainEnabled() == requestKey.terrainEnabled()
                && displayedKey.settingsHash() == requestKey.settingsHash()
                && displayedKey.minX() <= visibleBounds.minX()
                && displayedKey.maxX() >= visibleBounds.maxX()
                && displayedKey.minZ() <= visibleBounds.minZ()
                && displayedKey.maxZ() >= visibleBounds.maxZ();
    }

    private void applyPendingSeedPreview() {
        synchronized (this.seedPreviewLock) {
            if (this.seedPreviewTexture == null || this.seedPreviewPendingPixels == null || this.seedPreviewPendingKey == null) {
                return;
            }

            ensureSeedPreviewTextureSize(this.seedPreviewPendingKey.textureWidth(), this.seedPreviewPendingKey.textureHeight());
            this.seedPreviewTexture.setPixelsPremultipliedABGR(this.seedPreviewPendingPixels);
            this.seedPreviewTexture.upload();
            this.seedPreviewDisplayedKey = this.seedPreviewPendingKey;
            this.seedPreviewPendingPixels = null;
            this.seedPreviewPendingKey = null;
            this.seedPreviewLoading = false;
        }
    }

    private int[] buildSeedPreviewPixels(SeedPreviewQueryCacheKey requestKey) {
        int width = requestKey.textureWidth();
        int height = requestKey.textureHeight();
        int[] pixels = new int[width * height];
        try {
            SeedMapperNative.ensureLoaded();
            warmupCubiomes(requestKey);
            boolean terrain = requestKey.terrainEnabled();
            int[] biomeIds = new int[width * height * 4];
            sampleSeedPreviewBands(requestKey, biomeIds);
            WorldMapProgress.report("Coloring rows", 0, height);
            for (int y = 0; y < height; y++) {
                SeedPreviewJobs.checkCancelled();
                WorldMapProgress.report("Coloring rows", y, height);
                int rowOffset = y * width;
                for (int x = 0; x < width; x++) {
                    int idx = rowOffset + x;
                    int color = SeedPreviewSampling.average(resolveSeedPreviewColor(biomeIds[idx * 4], requestKey.mcVersion()),
                            resolveSeedPreviewColor(biomeIds[idx * 4 + 1], requestKey.mcVersion()),
                            resolveSeedPreviewColor(biomeIds[idx * 4 + 2], requestKey.mcVersion()),
                            resolveSeedPreviewColor(biomeIds[idx * 4 + 3], requestKey.mcVersion()));
                    pixels[idx] = ColorUtils.premultiplyWithAlpha(color);
                }
            }
            if (terrain) {
                SeedPreviewJobs.checkCancelled();
                synchronized (this.seedPreviewLock) {
                    // Keep an immutable biome preview visible while terrain is refined.
                    this.seedPreviewPendingPixels = pixels.clone();
                    this.seedPreviewPendingKey = requestKey;
                }
                int[] effectiveHeights = sampleSeedPreviewHeights(requestKey);
                WorldMapProgress.report("Shading rows", 0, height);
                for (int y = 0; y < height; y++) {
                    SeedPreviewJobs.checkCancelled();
                    for (int x = 0; x < width; x++) {
                        int index = y * width + x;
                        int color = SeedPreviewSampling.average(resolveSeedPreviewColor(biomeIds[index * 4], requestKey.mcVersion()),
                                resolveSeedPreviewColor(biomeIds[index * 4 + 1], requestKey.mcVersion()),
                                resolveSeedPreviewColor(biomeIds[index * 4 + 2], requestKey.mcVersion()),
                                resolveSeedPreviewColor(biomeIds[index * 4 + 3], requestKey.mcVersion()));
                        pixels[index] = ColorUtils.premultiplyWithAlpha(applySeedPreviewTerrainStyle(color,
                                biomeIds[index * 4], effectiveHeights, width, x, y));
                    }
                    WorldMapProgress.report("Shading rows", y + 1, height);
                }
            }
        } catch (java.util.concurrent.CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException ex) {
            // Returning the zero-filled buffer renders a blank map, so the cause has to be
            // logged loudly and surfaced to the player rather than only warned about.
            WorldMapProgress.Task progress = WorldMapProgress.currentTask();
            if (progress != null) progress.cancel();
            VoxelConstants.getLogger().error("Failed generating SeedMapper world-map preview", ex);
            SeedMapperNative.reportFailureOnce();
        }
        return pixels;
    }

    private void warmupCubiomes(SeedPreviewQueryCacheKey requestKey) {
        synchronized (SeedMapperNative.cubiomesLock()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment generator = Generator.allocate(arena);
                Cubiomes.setupGenerator(generator, requestKey.mcVersion(), requestKey.generatorFlags());
                Cubiomes.applySeed(generator, requestKey.dimension(), requestKey.seed());
                Cubiomes.getBiomeAt(generator, 4, 0, getSeedPreviewSampleQuartY(requestKey.dimension(), requestKey.biomeY()), 0);
            }
            if (requestKey.terrainEnabled() && requestKey.dimension() == Cubiomes.DIM_OVERWORLD()) {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment params = TerrainNoise.allocate(arena);
                    Cubiomes.setupTerrainNoise(params, requestKey.mcVersion(), requestKey.generatorFlags());
                    Cubiomes.initTerrainNoise(params, requestKey.seed(), requestKey.dimension());
                    Cubiomes.samplePreliminarySurfaceLevel(params, 0, 0);
                }
            }
        }
    }

    private void sampleSeedPreviewBands(SeedPreviewQueryCacheKey requestKey, int[] biomeIds) {
        int width = requestKey.textureWidth();
        int height = requestKey.textureHeight();
        int sampleY = getSeedPreviewSampleQuartY(requestKey.dimension(), requestKey.biomeY());
        double spanX = Math.max(1.0D, (double) requestKey.maxX() - requestKey.minX());
        double spanZ = Math.max(1.0D, (double) requestKey.maxZ() - requestKey.minZ());
        int bands = Math.max(1, Math.min(SEED_PREVIEW_WORKER_THREADS, height));
        List<Future<?>> futures = new ArrayList<>(bands);
        WorldMapProgress.Task progress = WorldMapProgress.currentTask();
        java.util.concurrent.atomic.AtomicInteger completedRows = new java.util.concurrent.atomic.AtomicInteger();
        if (progress != null) progress.update("Sampling rows", 0, height);
        for (int b = 0; b < bands; b++) {
            final int y0 = b * height / bands;
            final int y1 = (b + 1) * height / bands;
            futures.add(seedPreviewSampler.submit(() -> {
                SeedPreviewJobs.checkCancelled();
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment generator = Generator.allocate(arena);
                    synchronized (SeedMapperNative.cubiomesLock()) {
                        SeedPreviewJobs.checkCancelled();
                        Cubiomes.setupGenerator(generator, requestKey.mcVersion(), requestKey.generatorFlags());
                        Cubiomes.applySeed(generator, requestKey.dimension(), requestKey.seed());
                    }
                    for (int y = y0; y < y1; y++) {
                        synchronized (SeedMapperNative.cubiomesLock()) {
                            SeedPreviewJobs.checkCancelled();
                            int blockZ = Mth.floor(requestKey.minZ() + (y + 0.5D) * spanZ / height);
                            int quartZ = blockZ >> 2;
                            int rowOffset = y * width;
                            for (int x = 0; x < width; x++) {
                                if ((x & 31) == 0) SeedPreviewJobs.checkCancelled();
                                int blockX = Mth.floor(requestKey.minX() + (x + 0.5D) * spanX / width);
                                int index = (rowOffset + x) * 4;
                                if (spanX / width <= 4) {
                                    // Scale 1 applies the game's block-level biome boundary
                                    // sampling; quart coordinates are only valid at scale 4.
                                    boolean blockSampling = spanX / width < 4;
                                    int biome = blockSampling
                                            ? Cubiomes.getBiomeAt(generator, 1, blockX, sampleY * 4, blockZ)
                                            : Cubiomes.getBiomeAt(generator, 4, blockX >> 2, sampleY, quartZ);
                                    java.util.Arrays.fill(biomeIds, index, index + 4, biome);
                                } else {
                                    for (int sample = 0; sample < 4; sample++) {
                                        int sx = Mth.floor(requestKey.minX() + (x + ((sample & 1) == 0 ? .25D : .75D)) * spanX / width);
                                        int sz = Mth.floor(requestKey.minZ() + (y + ((sample & 2) == 0 ? .25D : .75D)) * spanZ / height);
                                        biomeIds[index + sample] = Cubiomes.getBiomeAt(generator, 4, sx >> 2, sampleY, sz >> 2);
                                    }
                                }
                            }
                            if (progress != null) progress.update("Sampling rows", completedRows.incrementAndGet(), height);
                        }
                    }
                }
            }));
        }
        SeedPreviewJobs.await(futures);
    }

    private int[] sampleSeedPreviewHeights(SeedPreviewQueryCacheKey key) {
        // Terrain heights need a bounded world-space lattice, not a full 256-column
        // chunk generation for each high-resolution biome texel. Interpolate the
        // height field independently so biome resolution can remain high.
        var layout = SeedPreviewTerrainSampling.layout(key.minX(), key.maxX(), key.minZ(), key.maxZ());
        int[] coarse = new int[(layout.width() + 1) * (layout.height() + 1)];
        int rowWidth = layout.width() + 1;
        WorldMapProgress.report("Sampling terrain", 0, coarse.length);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment params = TerrainNoise.allocate(arena);
            synchronized (SeedMapperNative.cubiomesLock()) {
                SeedPreviewJobs.checkCancelled();
                Cubiomes.setupTerrainNoise(params, key.mcVersion(), key.generatorFlags());
                Cubiomes.initTerrainNoise(params, key.seed(), key.dimension());
            }
            MemorySegment chunkBuffer = arena.allocate(Cubiomes.C_INT, 256);
            for (int z = 0; z <= layout.height(); z++) {
                for (int x = 0; x <= layout.width(); x++) {
                    SeedPreviewJobs.checkCancelled();
                    int blockX = layout.minX() + x * layout.step(), blockZ = layout.minZ() + z * layout.step();
                    int height;
                    synchronized (SeedMapperNative.cubiomesLock()) {
                        SeedPreviewJobs.checkCancelled();
                        if (key.dimension() == Cubiomes.DIM_OVERWORLD()) {
                            height = Cubiomes.samplePreliminarySurfaceLevel(params, blockX, blockZ);
                        } else {
                            int chunkX = Math.floorDiv(blockX, 16), chunkZ = Math.floorDiv(blockZ, 16);
                            SeedHeightChunk chunkKey = new SeedHeightChunk(key.seed(), key.dimension(), key.mcVersion(), key.generatorFlags(), chunkX, chunkZ);
                            int[] chunk = this.seedHeightChunks.get(chunkKey);
                            if (chunk == null) {
                                Cubiomes.generateRegion(params, chunkX, chunkZ, 1, 1, MemorySegment.NULL, 0,
                                        key.dimension() == Cubiomes.DIM_END() ? 32 : 16, chunkBuffer, 1);
                                chunk = new int[256];
                                for (int i = 0; i < chunk.length; i++) chunk[i] = chunkBuffer.getAtIndex(Cubiomes.C_INT, i);
                                this.seedHeightChunks.put(chunkKey, chunk);
                            }
                            height = chunk[Math.floorMod(blockX, 16) * 16 + Math.floorMod(blockZ, 16)];
                        }
                    }
                    int index = z * rowWidth + x;
                    coarse[index] = height;
                    if ((index & 31) == 0) WorldMapProgress.report("Sampling terrain", index + 1, coarse.length);
                }
            }
        }
        int[] heights = new int[key.textureWidth() * key.textureHeight()];
        double step = ((double) key.maxX() - key.minX()) / key.textureWidth();
        for (int z = 0; z < key.textureHeight(); z++) {
            SeedPreviewJobs.checkCancelled();
            for (int x = 0; x < key.textureWidth(); x++) {
                heights[z * key.textureWidth() + x] = SeedPreviewTerrainSampling.interpolate(layout, coarse,
                        key.minX() + (x + .5) * step, key.minZ() + (z + .5) * step);
            }
        }
        return heights;
    }

    private int applySeedPreviewTerrainStyle(int color, int biomeId, int[] heights, int width, int x, int y) {
        int index = y * width + x;
        int h = heights[index];
        if (h == Integer.MIN_VALUE) {
            return color;
        }

        color = applySeedPreviewPalette(color, biomeId, h);
        int left = x > 0 ? heights[index - 1] : h;
        int up = y > 0 ? heights[index - width] : h;
        int shade = Mth.clamp((h - left) * 4 + (h - up) * 3, -36, 36);
        int styled = adjustSeedPreviewBrightness(color, shade);
        if (this.options.seedMapStyle.drawsContours() && (crossesSeedPreviewContour(h, left) || crossesSeedPreviewContour(h, up))) {
            styled = blendSeedPreviewColor(styled, 0xD0000000, this.options.getSeedMapContourStrength());
        }
        return styled;
    }

    private int applySeedPreviewPalette(int biomeColor, int biomeId, int height) {
        int base = isSeedBiomeOceanic(biomeId) ? waterPaletteColor(height) : landPaletteColor(height);
        return switch (this.options.seedMapStyle.palette()) {
            case BIOME -> biomeColor;
            case HEIGHT -> base;
            case TOPOGRAPHIC -> blendSeedPreviewColor(base, biomeColor, 0.35F);
        };
    }

    private int waterPaletteColor(int height) {
        int deep = ARGB.toABGR(0x96000000 | 0x0A2342);
        int shallow = ARGB.toABGR(0x96000000 | 0x3D86C6);
        float t = Mth.clamp((height - 20) / 44.0F, 0.0F, 1.0F);
        return blendSeedPreviewColor(deep, shallow, t);
    }

    private int landPaletteColor(int height) {
        int rgb;
        if (height < 50) {
            rgb = 0x9DBE74;
        } else if (height < 70) {
            rgb = 0x5DA34E;
        } else if (height < 95) {
            rgb = 0x4E8A45;
        } else if (height < 125) {
            rgb = 0xA99268;
        } else if (height < 160) {
            rgb = 0x8E8E83;
        } else {
            rgb = 0xE8E8E8;
        }
        return ARGB.toABGR(0x96000000 | rgb);
    }

    private boolean isSeedBiomeOceanic(int biomeId) {
        Boolean cached = this.seedPreviewOceanicCache.get(biomeId);
        if (cached != null) {
            return cached;
        }
        boolean oceanic;
        try {
            oceanic = Cubiomes.isOceanic(biomeId) != 0;
        } catch (RuntimeException ex) {
            oceanic = false;
        }
        this.seedPreviewOceanicCache.put(biomeId, oceanic);
        return oceanic;
    }

    private boolean crossesSeedPreviewContour(int heightA, int heightB) {
        if (heightA == Integer.MIN_VALUE || heightB == Integer.MIN_VALUE) {
            return false;
        }
        return Math.floorDiv(heightA, SEED_PREVIEW_CONTOUR_INTERVAL) != Math.floorDiv(heightB, SEED_PREVIEW_CONTOUR_INTERVAL);
    }

    private int adjustSeedPreviewBrightness(int color, int amount) {
        int alpha = color >>> 24;
        int red = Mth.clamp((color >>> 16 & 0xFF) + amount, 0, 255);
        int green = Mth.clamp((color >>> 8 & 0xFF) + amount, 0, 255);
        int blue = Mth.clamp((color & 0xFF) + amount, 0, 255);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private int blendSeedPreviewColor(int base, int overlay, float overlayAlpha) {
        int alpha = base >>> 24;
        int red = Mth.clamp(Math.round((base >>> 16 & 0xFF) * (1.0F - overlayAlpha) + (overlay >>> 16 & 0xFF) * overlayAlpha), 0, 255);
        int green = Mth.clamp(Math.round((base >>> 8 & 0xFF) * (1.0F - overlayAlpha) + (overlay >>> 8 & 0xFF) * overlayAlpha), 0, 255);
        int blue = Mth.clamp(Math.round((base & 0xFF) * (1.0F - overlayAlpha) + (overlay & 0xFF) * overlayAlpha), 0, 255);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private int resolveSeedPreviewColor(int biomeId, int mcVersion) {
        Integer cached = this.seedPreviewBiomeColorCache.get(biomeId);
        if (cached != null) {
            return cached;
        }

        String biomeName = null;
        try {
            MemorySegment biomeNameSegment = Cubiomes.biome2str(mcVersion, biomeId);
            if (biomeNameSegment != null && biomeNameSegment.address() != 0L) {
                biomeName = biomeNameSegment.getString(0);
            }
        } catch (RuntimeException ignored) {
        }

        int rgb = fallbackSeedPreviewColor(biomeName == null ? Integer.toString(biomeId) : biomeName);
        Identifier biomeIdentifier = parseCubiomesBiomeIdentifier(biomeName);
        Level currentLevel = GameVariableAccessShim.getWorld();
        if (biomeIdentifier != null && currentLevel != null) {
            try {
                var biomeHolder = currentLevel.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).get(biomeIdentifier);
                if (biomeHolder.isPresent()) {
                    rgb = BiomeRepository.getBiomeColor(biomeHolder.get().value());
                }
            } catch (RuntimeException ignored) {
            }
        }

        int color = ARGB.toABGR(0x96000000 | rgb);
        this.seedPreviewBiomeColorCache.put(biomeId, color);
        return color;
    }

    private Identifier parseCubiomesBiomeIdentifier(String biomeName) {
        if (biomeName == null || biomeName.isBlank()) {
            return null;
        }

        Identifier parsed = Identifier.tryParse(biomeName);
        if (parsed != null) {
            return parsed;
        }

        if (biomeName.contains("/")) {
            parsed = Identifier.tryParse(biomeName.replace('/', ':'));
            if (parsed != null) {
                return parsed;
            }
            String tail = biomeName.substring(biomeName.lastIndexOf('/') + 1);
            return Identifier.tryParse("minecraft:" + tail);
        }

        return Identifier.tryParse("minecraft:" + biomeName);
    }

    private int fallbackSeedPreviewColor(String biomeName) {
        int hash = biomeName == null ? 0 : biomeName.hashCode();
        int red = 64 + (hash & 0x5F);
        int green = 64 + (hash >> 8 & 0x5F);
        int blue = 64 + (hash >> 16 & 0x5F);
        return red << 16 | green << 8 | blue;
    }

    private void drawSeedMapperMarkers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        seedMapperMarkerHitboxes.clear();
        boolean showNetherPortals = mapOptions.showNetherPortalMarkers;
        boolean showEndPortals = mapOptions.showEndPortalMarkers;
        boolean showEndBeacons = mapOptions.showEndGatewayMarkers;
        boolean showPortalMarkers = showNetherPortals || showEndPortals || showEndBeacons;
        if (!seedMapperOptions.enabled && !showPortalMarkers) {
            return;
        }
        boolean mapInMotion = currentDragging
                || Math.abs(this.deltaX) > 0.01F
                || Math.abs(this.deltaY) > 0.01F
                || this.zoom != this.zoomGoal;

        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            return;
        }

        PreviewBounds visibleBounds = getVisibleWorldBounds();
        int margin = 256;
        int rawMinX = visibleBounds.minX() - margin;
        int rawMaxX = visibleBounds.maxX() + margin;
        int rawMinZ = visibleBounds.minZ() - margin;
        int rawMaxZ = visibleBounds.maxZ() + margin;
        int keySnap = mapInMotion ? 4096 : 512;
        int minX = Math.floorDiv(rawMinX, keySnap) * keySnap;
        int maxX = Math.floorDiv(rawMaxX + keySnap - 1, keySnap) * keySnap;
        int minZ = Math.floorDiv(rawMinZ, keySnap) * keySnap;
        int maxZ = Math.floorDiv(rawMaxZ + keySnap - 1, keySnap) * keySnap;
        int maxSpan = 131072;
        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        if (spanX > maxSpan) {
            int cx = (minX + maxX) / 2;
            minX = cx - maxSpan / 2;
            maxX = cx + maxSpan / 2;
        }
        if (spanZ > maxSpan) {
            int cz = (minZ + maxZ) / 2;
            minZ = cz - maxSpan / 2;
            maxZ = cz + maxSpan / 2;
        }

        List<SeedMapperMarker> markers = new ArrayList<>();

        // Seed-derived structure markers require a valid seed and are hidden while dragging/zooming.
        Set<SeedMapperFeature> enabledSeedMapperFeatures = seedMapperOptions.getEnabledFeaturesSnapshot();
        if (!seedMapperOptions.enabled || enabledSeedMapperFeatures.isEmpty() || mapInMotion) {
            clearSeedMapperLoadingState();
        }

        if (seedMapperOptions.enabled
                && !enabledSeedMapperFeatures.isEmpty()
                && !mapInMotion) {
            try {
                long seed = resolveWorldMapSeed();
                int generatorFlags = getSeedMapperGeneratorFlags();
                SeedMapperQueryCacheKey key = new SeedMapperQueryCacheKey(
                        seed,
                        dimension,
                        generatorFlags,
                        minX,
                        maxX,
                        minZ,
                        maxZ,
                        seedMapperOptions.showLootableOnly,
                        enabledFeatureSetHash(),
                        seedMapperOptions.getDatapackMarkerHash(),
                        seedMapperOptions.getCustomStructureSaltHash(),
                        seedMapperOptions.lootSearch == null ? "" : seedMapperOptions.lootSearch,
                        currentSeedMapperWorldKey()
                );

                long now = System.currentTimeMillis();
                long minIntervalMs = 1000L;
                String datapackWorldKey = currentSeedMapperWorldKey();
                boolean keyChanged = seedMapperLastMarkerQueryKey == null
                        || !seedMapperLastMarkerQueryKey.equals(key);
                boolean intervalElapsed = now - seedMapperLastMarkerQueryMs >= minIntervalMs;
                boolean shouldRefresh = seedMapperLastMarkerResult.isEmpty() || keyChanged || intervalElapsed;
                if (shouldRefresh) {
                    SeedMapperLocatorService.QueryResult result = SeedMapperLocatorService.get().queryWithStatus(
                            seed,
                            dimension,
                            SeedMapperCompat.getMcVersion(),
                            generatorFlags,
                            minX,
                            maxX,
                            minZ,
                            maxZ,
                            seedMapperOptions,
                            datapackWorldKey);
                    if (!result.exact()) {
                        seedMapperQueryLoading = true;
                        seedMapperLoadingKey = key;
                        seedMapperLoadingSummary = buildSeedMapperLoadingSummary();
                        seedMapperLoadingStickyUntilMs = now + 1500L;
                    } else {
                        if (seedMapperLoadingKey == null || seedMapperLoadingKey.equals(key) || now >= seedMapperLoadingStickyUntilMs) {
                            clearSeedMapperLoadingState();
                        }
                    }
                    if (result.exact() || seedMapperLastMarkerResult.isEmpty()) {
                        seedMapperLastMarkerResult = result.markers();
                        seedMapperLastMarkerQueryKey = key;
                    }
                    seedMapperLastMarkerQueryMs = now;
                }
                markers.addAll(seedMapperLastMarkerResult);
                markers.removeIf(marker -> !enabledSeedMapperFeatures.contains(marker.feature()));
                if (seedMapperQueryLoading && now >= seedMapperLoadingStickyUntilMs) {
                    clearSeedMapperLoadingState();
                }
            } catch (IllegalArgumentException ignored) {
                // No valid seed set: keep drawing non-seed portal markers below.
            }
        }

        if (seedMapperOptions.enabled && enabledSeedMapperFeatures.contains(SeedMapperFeature.TREASURE_CLUSTER)) {
            try {
                long seed = resolveWorldMapSeed();
                int clusterMcVersion = SeedMapperCompat.getMcVersion();
                int clusterFlags = getSeedMapperGeneratorFlags();
                int clusterSaltHash = seedMapperOptions.getCustomStructureSaltHash();
                String clusterWorldKey = currentSeedMapperWorldKey();
                // The cluster search covers the whole world border, so run it once
                // per seed in the background instead of requiring a chat command.
                if (!SeedMapperClusterManager.hasStateFor(clusterWorldKey, seed, clusterMcVersion, clusterFlags, clusterSaltHash)
                        && SeedMapperClusterManager.beginPending(clusterWorldKey, seed, clusterMcVersion, clusterFlags, clusterSaltHash)) {
                    clusterSearchExecutor.submit(() -> {
                        List<SeedMapperBuriedTreasureClusterService.ClusterResult> clusters =
                                SeedMapperBuriedTreasureClusterService.find(seed, clusterMcVersion, clusterFlags, seedMapperOptions);
                        SeedMapperClusterManager.finishPending(
                                clusterWorldKey, seed, clusterMcVersion, clusterFlags, clusterSaltHash, clusters);
                    });
                }
                markers.addAll(SeedMapperClusterManager.getMarkersInBounds(
                        clusterWorldKey, seed, clusterMcVersion, clusterFlags, clusterSaltHash,
                        dimension, minX, maxX, minZ, maxZ));
            } catch (IllegalArgumentException ignored) {
                // No valid seed set; normal map markers still render.
            }
        }

        // Portal markers are scanned from the currently loaded level. Do not
        // draw them while previewing a different dimension.
        if (showPortalMarkers && isViewingCurrentDimension()) {
            for (com.mamiyaotaru.voxelmap.PortalMarkersManager.PortalMarker marker :
                    VoxelConstants.getVoxelMapInstance().getPortalMarkersManager()
                            .getMarkersInBounds(minX, maxX, minZ, maxZ, showNetherPortals, showEndPortals, showEndBeacons)) {
                SeedMapperFeature feature = switch (marker.type()) {
                    case NETHER -> SeedMapperFeature.NETHER_PORTAL;
                    case END -> SeedMapperFeature.END_PORTAL;
                    case END_BEACON -> SeedMapperFeature.END_BEACON;
                };
                if (feature == SeedMapperFeature.END_BEACON && !showEndBeacons) {
                    continue;
                }
                markers.add(new SeedMapperMarker(feature, marker.pos().getX(), marker.pos().getZ()));
            }
        }
        if (markers.isEmpty()) {
            return;
        }

        boolean lowDetail = options.detail.reduceMarkersWhileMoving && mapToGui < 0.35F;
        boolean ultraLowDetail = options.detail.reduceMarkersWhileMoving && mapToGui < 0.20F;
        if (!mapInMotion && !lowDetail && markers.size() < 2000) {
            final double priorityX = GameVariableAccessShim.xCoordDouble();
            final double priorityZ = GameVariableAccessShim.zCoordDouble();
            markers.sort(Comparator.comparingDouble(marker -> {
                double dx = marker.blockX() - priorityX;
                double dz = marker.blockZ() - priorityZ;
                return dx * dx + dz * dz;
            }));
        }

        int markerLimit = options.detail.limitMarkerCount ? Math.max(200, seedMapperOptions.worldMapMarkerLimit) : Integer.MAX_VALUE;
        if (options.detail.limitMarkerCount && seedMapperOptions.worldMapEntityLimit > 0) {
            markerLimit = Math.min(markerLimit, seedMapperOptions.worldMapEntityLimit);
        }
        if (mapInMotion && options.detail.reduceMarkersWhileMoving) {
            if (ultraLowDetail) {
                markerLimit = Math.min(markerLimit, 180);
            } else if (lowDetail) {
                markerLimit = Math.min(markerLimit, 600);
            }
        }
        int maxTotal = markerLimit;
        if (mapInMotion && options.detail.reduceMarkersWhileMoving) {
            maxTotal = Math.min(maxTotal, 1200);
            if (ultraLowDetail) {
                maxTotal = Math.min(maxTotal, 120);
            }
        }
        int maxDense = Integer.MAX_VALUE;
        int denseDrawn = 0;
        int totalDrawn = 0;
        int scanned = 0;
        int maxScanned = mapInMotion && options.detail.reduceMarkersWhileMoving ? Math.min(Math.max(2500, markerLimit), 6000) : Integer.MAX_VALUE;
        if (mapInMotion && ultraLowDetail) {
            maxScanned = Math.min(maxScanned, 1500);
        }
        int decimationMask = 0;
        if (mapInMotion && options.detail.reduceMarkersWhileMoving) {
            if (mapToGui < 0.12F) {
                decimationMask = 0x7; // keep about 1/8 while moving
            } else if (mapToGui < 0.18F) {
                decimationMask = 0x3; // keep about 1/4 while moving
            } else if (mapToGui < 0.28F) {
                decimationMask = 0x1; // keep about 1/2 while moving
            }
        }
        double visibleHalfX = (this.centerX + ICON_WIDTH + 12.0D) / Math.max(0.0001D, this.mapToGui);
        double visibleHalfZ = (this.centerY + ICON_HEIGHT + 12.0D) / Math.max(0.0001D, this.mapToGui);
        final double playerX = GameVariableAccessShim.xCoordDouble();
        final double playerZ = GameVariableAccessShim.zCoordDouble();

        // World-map marker limit should prioritize a circular region around the player.
        if (!mapInMotion && markers.size() > maxTotal) {
            var visibleMarkers = new ArrayList<SeedMapperMarker>(Math.min(markers.size(), 32768));
            for (SeedMapperMarker marker : markers) {
                if (Math.abs(marker.blockX() - this.mapCenterX) <= visibleHalfX
                        && Math.abs(marker.blockZ() - this.mapCenterZ) <= visibleHalfZ) {
                    visibleMarkers.add(marker);
                }
            }
            visibleMarkers.sort(Comparator.comparingDouble(marker -> {
                double dx = marker.blockX() - playerX;
                double dz = marker.blockZ() - playerZ;
                return dx * dx + dz * dz;
            }));
            markers = visibleMarkers;
        }

        for (SeedMapperMarker marker : markers) {
            if (++scanned > maxScanned || totalDrawn >= maxTotal) {
                break;
            }
            if (Math.abs(marker.blockX() - this.mapCenterX) > visibleHalfX
                    || Math.abs(marker.blockZ() - this.mapCenterZ) > visibleHalfZ) {
                continue;
            }
            if (decimationMask != 0 && marker.feature() != SeedMapperFeature.WORLD_SPAWN) {
                int hash = (marker.blockX() * 73428767) ^ (marker.blockZ() * 912931);
                if ((hash & decimationMask) != 0) {
                    continue;
                }
            }
            if (lowDetail && (marker.feature() == SeedMapperFeature.SLIME_CHUNK
                    || marker.feature() == SeedMapperFeature.IRON_ORE_VEIN
                    || marker.feature() == SeedMapperFeature.COPPER_ORE_VEIN)) {
                if (denseDrawn >= maxDense) {
                    continue;
                }
                denseDrawn++;
            }
            drawSeedMapperMarker(graphics, marker, mouseX, mouseY);
            totalDrawn++;
        }
    }

    private void clearSeedMapperLoadingState() {
        seedMapperQueryLoading = false;
        seedMapperLoadingKey = null;
        seedMapperLoadingSummary = "";
        seedMapperLoadingStickyUntilMs = 0L;
    }

    private void drawSeedMapperLoadingStatus(GuiGraphicsExtractor graphics) {
        if (!seedMapperQueryLoading) {
            return;
        }
        drawStatusText(graphics, "Loading SeedMapper: " + (seedMapperLoadingSummary == null || seedMapperLoadingSummary.isBlank() ? "structures" : seedMapperLoadingSummary), this.sideMargin + 2, this.bottom - 14, 0xFFE0E0E0, 0xFF000000);
    }

    private void drawContainerMarkers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!options.showWorldMapEntities) {
            return;
        }
        // Container/entity markers come from the currently loaded level and
        // cannot represent an alternate dimension preview.
        if (!isViewingCurrentDimension()) {
            return;
        }
        if (!seedMapperOptions.containerDetection && !seedMapperOptions.workstationDetection
                && !seedMapperOptions.redstoneDetection && !seedMapperOptions.spawnerDetection) {
            return;
        }
        // Match SeedMapper markers: keep a readable screen-space icon while
        // the map zooms, and begin grouping before individual entity icons
        // overlap each other.
        double clusterCell = seedMapperOptions.markerClustering && mapToGui < 1.5F
                ? 16.0D / Math.max(0.025F, mapToGui) : 1.0D;
        ArrayList<SeedMapperContainerDetection.ContainerCluster> clusters = new ArrayList<>(
                SeedMapperContainerDetection.clusterMarkers(clusterCell));
        // Bound the per-frame icon work at close zoom, where detections are no
        // longer spatially grouped. Select by distance first so an icon does
        // not disappear merely because a distant cluster has a larger count.
        int containerIconLimit = seedMapperOptions.worldMapEntityLimit > 0
                ? seedMapperOptions.worldMapEntityLimit
                : Integer.MAX_VALUE;
        if (clusters.size() > containerIconLimit) {
            final double containerCenterX = this.mapCenterX;
            final double containerCenterZ = this.mapCenterZ;
            clusters.sort(java.util.Comparator.comparingDouble(cluster -> {
                SeedMapperContainerMarker marker = cluster.marker();
                double ptX = marker.blockX() + 0.5D;
                double ptZ = marker.blockZ() + 0.5D;
                double wayX = containerCenterX - (this.oldNorth ? -ptZ : ptX);
                double wayZ = containerCenterZ - (this.oldNorth ? ptX : ptZ);
                return wayX * wayX + wayZ * wayZ;
            }));
            clusters = new ArrayList<>(clusters.subList(0, containerIconLimit));
        }
        // Draw lower-count clusters first so the highest count remains on top.
        clusters.sort(java.util.Comparator.comparingInt(SeedMapperContainerDetection.ContainerCluster::count));
        for (SeedMapperContainerDetection.ContainerCluster cluster : clusters) {
            SeedMapperContainerMarker marker = cluster.marker();
            float ptX = marker.blockX() + 0.5F;
            float ptZ = marker.blockZ() + 0.5F;
            double wayX = this.mapCenterX - (this.oldNorth ? -ptZ : ptX);
            double wayY = this.mapCenterZ - (this.oldNorth ? ptX : ptZ);
            float locate = (float) Math.atan2(wayX, wayY);
            float hypot = (float) Math.sqrt(wayX * wayX + wayY * wayY) * mapToGui;
            double dispX = hypot * Math.sin(locate);
            double dispY = hypot * Math.cos(locate);
            if (Math.abs(dispX) > this.centerX + 10 || Math.abs(dispY) > this.centerY + 10) {
                continue;
            }
            graphics.pose().pushMatrix();
            graphics.pose().rotate(-locate);
            graphics.pose().translate(0.0F, -hypot);
            graphics.pose().rotate(locate);
            float centerX = this.width / 2.0F;
            float centerY = this.height / 2.0F;
            Vector2f guiVector = graphics.pose().transformPosition(new Vector2f(centerX, centerY));
            float iconSize = ICON_WIDTH * (float) seedMapperOptions.mapEntityScale;
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, marker.texture(),
                    centerX - iconSize / 2.0F, centerY - iconSize / 2.0F, iconSize, iconSize,
                    0.0F, 1.0F, 0.0F, 1.0F, 0xFFFFFFFF);
            if (cluster.count() > 1) {
                String countText = Integer.toString(cluster.count());
                float badgeWidth = Math.max(12.0F, getFont().width(countText) + 8.0F);
                float badgeHeight = getFont().lineHeight + 4.0F;
                graphics.pose().pushMatrix();
                graphics.pose().translate(centerX + iconSize * 0.42F, centerY - iconSize * 0.42F);
                graphics.pose().scale(Math.max(0.45F, iconSize / 16.0F), Math.max(0.45F, iconSize / 16.0F));
                int left = Math.round(-badgeWidth / 2.0F);
                int top = Math.round(-badgeHeight / 2.0F);
                int right = Math.round(badgeWidth / 2.0F);
                int bottom = Math.round(badgeHeight / 2.0F);
                graphics.fill(left, top, right, bottom, 0xE6000000);
                graphics.fill(left, top, right, top + 1, 0xFFFFFFFF);
                graphics.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF);
                graphics.fill(left, top, left + 1, bottom, 0xFFFFFFFF);
                graphics.fill(right - 1, top, right, bottom, 0xFFFFFFFF);
                graphics.centeredText(getFont(), Component.literal(countText), 0, top + 2, 0xFFFFFFFF);
                graphics.pose().popMatrix();
            }
            if (mouseX >= guiVector.x() - iconSize / 2.0F && mouseX <= guiVector.x() + iconSize / 2.0F
                    && mouseY >= guiVector.y() - iconSize / 2.0F && mouseY <= guiVector.y() + iconSize / 2.0F && popupOpen()) {
                renderTooltip(graphics, Component.literal(marker.label() + " (X: " + marker.blockX() + ", Z: " + marker.blockZ() + ")"), mouseX, mouseY);
            }
            graphics.pose().popMatrix();
        }
    }

    private void drawSeedPreviewLoadingStatus(GuiGraphicsExtractor graphics) {
        if (!this.seedMapperOptions.worldMapSeedPreview || !this.seedPreviewLoading) {
            return;
        }
        if (this.seedPreviewDrewThisFrame) {
            return;
        }
        if (System.currentTimeMillis() - this.seedPreviewLoadingStartedMs < SEED_PREVIEW_LOADING_DEBOUNCE_MS) {
            return;
        }

        int y = seedMapperQueryLoading ? this.bottom - 26 : this.bottom - 14;
        drawStatusText(graphics, "Loading SeedMap terrain...", this.sideMargin + 2, y, 0xFFE0E0E0, 0xFF000000);
    }

    private String buildSeedMapperLoadingSummary() {
        boolean datapackFeatureOn = seedMapperOptions.isFeatureEnabled(SeedMapperFeature.DATAPACK_STRUCTURE) && seedMapperOptions.datapackEnabled;
        if (!datapackFeatureOn) {
            return "markers";
        }
        List<String> all = allDatapackLegendStructureIds();
        Set<String> disabled = seedMapperOptions.getDisabledDatapackStructures(currentSeedMapperWorldKey());
        List<String> enabled = new ArrayList<>();
        for (String id : all) {
            if (!disabled.contains(id)) {
                enabled.add(id);
            }
        }
        if (enabled.isEmpty()) {
            return "datapack structures";
        }
        int shown = Math.min(3, enabled.size());
        String head = String.join(", ", enabled.subList(0, shown));
        if (enabled.size() > shown) {
            return "datapack structures: " + head + " (+" + (enabled.size() - shown) + " more)";
        }
        return "datapack structures: " + head;
    }

    private boolean isMouseOverSeedMapperMarker(int mouseX, int mouseY) {
        for (SeedMapperMarkerHitbox hitbox : seedMapperMarkerHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void drawSeedMapperFeatureStrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        seedMapperIconHitboxes.clear();
        seedMapperStripLeft = -1;
        seedMapperStripRight = -1;
        seedMapperStripTop = -1;
        seedMapperStripBottom = -1;
        seedMapperTitleLeft = -1;
        seedMapperTitleRight = -1;
        seedMapperTitleTop = -1;
        seedMapperTitleBottom = -1;
        int currentDimension = getCurrentCubiomesDimension();
        int startX = 0;
        int y = this.top;
        int iconSize = 14;
        int gap = 3;
        int barHeight = iconSize + 6;
        int stripPad = 6;
        Component legendTitle = Component.translatable("options.seedmapper.tab");
        int titleX = startX + stripPad;
        int titleY = y + (barHeight - this.getFont().lineHeight) / 2;
        int titleWidth = this.getFont().width(legendTitle);
        seedMapperTitleLeft = titleX;
        seedMapperTitleRight = titleX + titleWidth;
        seedMapperTitleTop = titleY;
        seedMapperTitleBottom = titleY + this.getFont().lineHeight;

        int x = startX + titleWidth + stripPad + 8;
        int contentEnd = this.width - this.sideMargin - 6;
        int perPage = Math.max(1, (contentEnd - x) / (iconSize + gap));
        List<LegendEntry> visibleEntries = new ArrayList<>();
        for (SeedMapperFeature feature : SeedMapperFeature.values()) {
            if (feature == SeedMapperFeature.DATAPACK_STRUCTURE
                    || feature == SeedMapperFeature.END_PORTAL
                    || feature == SeedMapperFeature.END_BEACON) {
                continue;
            }
            if (!feature.availableInVersion(SeedMapperCompat.getMcVersion())) {
                continue;
            }
            if (featureMatchesDimension(feature, currentDimension) && isSeedMapperFeatureVisible(feature)) {
                visibleEntries.add(LegendEntry.feature(feature));
            }
        }
        if (seedMapperOptions.datapackEnabled && seedMapperOptions.isFeatureEnabled(SeedMapperFeature.DATAPACK_STRUCTURE)) {
            for (String structureId : allDatapackLegendStructureIds()) {
                visibleEntries.add(LegendEntry.datapack(structureId));
            }
        }
        LegendEntry[] all = visibleEntries.toArray(LegendEntry[]::new);
        seedMapperLegendMaxPage = Math.max(0, (all.length - 1) / perPage);
        if (seedMapperLegendPage > seedMapperLegendMaxPage) {
            seedMapperLegendPage = seedMapperLegendMaxPage;
        }

        int startIndex = seedMapperLegendPage * perPage;
        int endIndex = Math.min(all.length, startIndex + perPage);
        for (int i = startIndex; i < endIndex; i++) {
            LegendEntry entry = all[i];
            seedMapperIconHitboxes.add(new FeatureIconHitbox(entry.feature(), entry.datapackStructureId(), x, y + (barHeight - iconSize) / 2, iconSize));
            x += iconSize + gap;
        }

        legendArrowSize = 12;
        legendPrevY = y + (barHeight - legendArrowSize) / 2;
        legendNextY = legendPrevY;
        boolean multiPage = seedMapperLegendMaxPage > 0;
        int contentRight = Math.max(titleX + titleWidth, x - gap);
        if (multiPage) {
            Component pageText = Component.literal((seedMapperLegendPage + 1) + "/" + (seedMapperLegendMaxPage + 1));
            int pageWidth = this.getFont().width(pageText);
            int pageTextX = x + 4;
            legendPrevX = pageTextX + pageWidth + 8;
            legendNextX = legendPrevX + 14;

            int prevColor = seedMapperLegendPage > 0 ? 0xFFFFFFFF : 0x55FFFFFF;
            int nextColor = seedMapperLegendPage < seedMapperLegendMaxPage ? 0xFFFFFFFF : 0x55FFFFFF;
            int textY = y + (barHeight - this.getFont().lineHeight) / 2;
            graphics.text(this.getFont(), pageText, pageTextX, textY, 0xFFAAAAAA);
            graphics.text(this.getFont(), "<", legendPrevX, textY, prevColor);
            graphics.text(this.getFont(), ">", legendNextX, textY, nextColor);
            contentRight = legendNextX + legendArrowSize;
        } else {
            legendPrevX = -1;
            legendNextX = -1;
        }

        seedMapperStripLeft = startX;
        seedMapperStripTop = y;
        seedMapperStripRight = Math.max(seedMapperStripLeft + 40, contentRight + stripPad + 8);
        seedMapperStripBottom = y + barHeight;
        graphics.fill(seedMapperStripLeft, seedMapperStripTop, seedMapperStripRight, seedMapperStripBottom, 0xFF000000);
        boolean allLocationsEnabled = !seedMapperOptions.getEnabledFeaturesSnapshot().isEmpty();
        int titleColor = allLocationsEnabled ? 0xFFFFFFFF : 0xFF8A8A8A;
        graphics.text(this.getFont(), legendTitle, titleX, titleY, titleColor);
        if (isInSeedMapperTitle(mouseX, mouseY)) {
            graphics.requestCursor(CursorTypes.CROSSHAIR);
            Component tooltip = Component.literal(allLocationsEnabled
                    ? "Hide all SeedMapper locations"
                    : "Show all SeedMapper locations");
            renderTooltip(graphics, tooltip, mouseX, mouseY);
        }
        if (!seedMapperIconHitboxes.isEmpty()) {
            for (FeatureIconHitbox hitbox : seedMapperIconHitboxes) {
                SeedMapperFeature feature = hitbox.feature();
                boolean datapackStructure = hitbox.datapackStructureId() != null;
                boolean enabled = datapackStructure
                        ? seedMapperOptions.isDatapackStructureEnabled(currentSeedMapperWorldKey(), hitbox.datapackStructureId())
                        : isFeatureEnabledInPanel(feature);
                int color = enabled ? 0xFFFFFFFF : 0x55FFFFFF;
                if (datapackStructure) {
                    drawDatapackLegendIcon(graphics, hitbox.datapackStructureId(), hitbox.x(), hitbox.y(), iconSize, enabled);
                } else {
                    VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, feature.icon(), hitbox.x(), hitbox.y(), iconSize, iconSize, 0, 1, 0, 1, color);
                }
                if (mouseX >= hitbox.x() && mouseX <= hitbox.x() + iconSize && mouseY >= hitbox.y() && mouseY <= hitbox.y() + iconSize) {
                    graphics.requestCursor(CursorTypes.CROSSHAIR);
                    Component tooltip = datapackStructure
                            ? Component.literal(hitbox.datapackStructureId() + (enabled ? " (ON)" : " (OFF)") + " - manage in Datapack Settings")
                            : Component.translatable(feature.translationKey()).append(Component.literal(enabled ? " (ON)" : " (OFF)"));
                    renderTooltip(graphics, tooltip, mouseX, mouseY);
                }
            }
            if (multiPage) {
                Component pageText = Component.literal((seedMapperLegendPage + 1) + "/" + (seedMapperLegendMaxPage + 1));
                int pageTextX = x + 4;
                int textY = y + (barHeight - this.getFont().lineHeight) / 2;
                graphics.text(this.getFont(), pageText, pageTextX, textY, 0xFFAAAAAA);
                int prevColor = seedMapperLegendPage > 0 ? 0xFFFFFFFF : 0x55FFFFFF;
                int nextColor = seedMapperLegendPage < seedMapperLegendMaxPage ? 0xFFFFFFFF : 0x55FFFFFF;
                graphics.text(this.getFont(), "<", legendPrevX, textY, prevColor);
                graphics.text(this.getFont(), ">", legendNextX, textY, nextColor);
            }
        }
    }

    private boolean handleSeedMapperIconClick(int mouseX, int mouseY) {
        if (!seedMapperOptions.enabled || seedMapperIconHitboxes.isEmpty()) {
            return false;
        }
        if (seedMapperLegendMaxPage > 0 && legendPrevX >= 0
                && mouseX >= legendPrevX && mouseX <= legendPrevX + legendArrowSize && mouseY >= legendPrevY && mouseY <= legendPrevY + legendArrowSize) {
            if (seedMapperLegendPage > 0) {
                seedMapperLegendPage--;
            }
            return true;
        }
        if (seedMapperLegendMaxPage > 0 && legendNextX >= 0
                && mouseX >= legendNextX && mouseX <= legendNextX + legendArrowSize && mouseY >= legendNextY && mouseY <= legendNextY + legendArrowSize) {
            if (seedMapperLegendPage < seedMapperLegendMaxPage) {
                seedMapperLegendPage++;
            }
            return true;
        }
        for (FeatureIconHitbox hitbox : seedMapperIconHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                if (hitbox.datapackStructureId() != null) {
                    String worldKey = currentSeedMapperWorldKey();
                    boolean currentlyEnabled = seedMapperOptions.isDatapackStructureEnabled(worldKey, hitbox.datapackStructureId());
                    seedMapperOptions.setDatapackStructureEnabled(worldKey, hitbox.datapackStructureId(), !currentlyEnabled);
                    MapSettingsManager.instance.saveAll();
                    return true;
                }
                boolean ctrlDown = isCtrlDown();
                SeedMapperFeature clicked = hitbox.feature();
                if (isPortalFeature(clicked)) {
                    setPortalFeatureEnabled(clicked, !isFeatureEnabledInPanel(clicked));
                    MapSettingsManager.instance.saveAll();
                    return true;
                }
                if (ctrlDown) {
                    int currentHash = enabledFeatureSetHash();
                    if (seedMapperSavedToggles != null
                            && seedMapperIsolatedFeature == clicked
                            && currentHash == enabledFeatureSetHash(EnumSet.of(clicked))) {
                        if (enabledFeatureSetHash(seedMapperSavedToggles) == seedMapperIsolationBaseHash) {
                            seedMapperOptions.setEnabledFeatures(seedMapperSavedToggles);
                        }
                        seedMapperSavedToggles = null;
                        seedMapperIsolatedFeature = null;
                    } else {
                        if (seedMapperSavedToggles == null) {
                            seedMapperSavedToggles = seedMapperOptions.getEnabledFeaturesSnapshot();
                            seedMapperIsolationBaseHash = enabledFeatureSetHash(seedMapperSavedToggles);
                        }
                        seedMapperOptions.setOnlyFeatureEnabled(clicked);
                        seedMapperIsolatedFeature = clicked;
                        if (clicked == SeedMapperFeature.WORLD_SPAWN) {
                            centerOnWorldSpawn();
                        }
                    }
                } else {
                    seedMapperSavedToggles = null;
                    seedMapperIsolatedFeature = null;
                    seedMapperOptions.toggleFeature(clicked);
                }
                MapSettingsManager.instance.saveAll();
                return true;
            }
        }
        return false;
    }

    private boolean handleSeedMapperTitleClick(int mouseX, int mouseY) {
        if (!isInSeedMapperTitle(mouseX, mouseY)) {
            return false;
        }

        Set<SeedMapperFeature> enabledFeatures = seedMapperOptions.getEnabledFeaturesSnapshot();
        if (!enabledFeatures.isEmpty()) {
            seedMapperAllFeaturesSaved = EnumSet.copyOf(enabledFeatures);
            seedMapperOptions.setEnabledFeatures(Set.of());
        } else {
            Set<SeedMapperFeature> restored = seedMapperAllFeaturesSaved != null && !seedMapperAllFeaturesSaved.isEmpty()
                    ? EnumSet.copyOf(seedMapperAllFeaturesSaved)
                    : EnumSet.allOf(SeedMapperFeature.class);
            seedMapperOptions.setEnabledFeatures(restored);
        }

        seedMapperSavedToggles = null;
        seedMapperIsolatedFeature = null;
        clearSeedMapperLoadingState();
        MapSettingsManager.instance.saveAll();
        return true;
    }

    private boolean isSeedMapperFeatureVisible(SeedMapperFeature feature) {
        return true;
    }

    private List<String> visibleDatapackLegendStructureIds() {
        return allDatapackLegendStructureIds();
    }

    private List<String> allDatapackLegendStructureIds() {
        long seed;
        try {
            seed = resolveWorldMapSeed();
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        ArrayList<String> ids = new ArrayList<>();
        for (String id : SeedMapperImportedDatapackManager.importedStructureIds(seedMapperOptions.datapackCachePath, seed)) {
            ids.add(id);
        }
        return ids;
    }

    private void drawDatapackLegendIcon(GuiGraphicsExtractor graphics, String structureId, int x, int y, int iconSize, boolean enabled) {
        int color = SeedMapperImportedDatapackManager.colorForStructureId(structureId);
        int drawColor = enabled ? color : ((color & 0x00FFFFFF) | 0x77000000);
        if (SeedMapperImportedDatapackManager.usesPotionIcon()) {
            Identifier potion = SeedMapperImportedDatapackManager.iconForStructureId(structureId);
            Identifier overlay = SeedMapperImportedDatapackManager.iconOverlayForStructureId(structureId);
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, potion, x, y, iconSize, iconSize, 0, 1, 0, 1, enabled ? 0xFFFFFFFF : 0x99FFFFFF);
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, overlay, x, y, iconSize, iconSize, 0, 1, 0, 1, drawColor);
            return;
        }
        graphics.fill(x - 1, y - 1, x + iconSize + 1, y + iconSize + 1, 0xFF000000);
        graphics.fill(x, y, x + iconSize, y + iconSize, drawColor);
    }

    private boolean isPortalFeature(SeedMapperFeature feature) {
        return feature == SeedMapperFeature.NETHER_PORTAL
                || feature == SeedMapperFeature.END_PORTAL
                || feature == SeedMapperFeature.END_BEACON;
    }

    private boolean isFeatureEnabledInPanel(SeedMapperFeature feature) {
        if (feature == SeedMapperFeature.NETHER_PORTAL) {
            return mapOptions.showNetherPortalMarkers;
        }
        if (feature == SeedMapperFeature.END_PORTAL) {
            return mapOptions.showEndPortalMarkers;
        }
        if (feature == SeedMapperFeature.END_BEACON) {
            return mapOptions.showEndGatewayMarkers;
        }
        return seedMapperOptions.isFeatureEnabled(feature);
    }

    private void setPortalFeatureEnabled(SeedMapperFeature feature, boolean enabled) {
        if (feature == SeedMapperFeature.NETHER_PORTAL) {
            mapOptions.showNetherPortalMarkers = enabled;
        } else if (feature == SeedMapperFeature.END_PORTAL) {
            mapOptions.showEndPortalMarkers = enabled;
        } else if (feature == SeedMapperFeature.END_BEACON) {
            mapOptions.showEndGatewayMarkers = enabled;
        }
    }

    private void drawSeedMapperMarker(GuiGraphicsExtractor graphics, SeedMapperMarker marker, int mouseX, int mouseY) {
        float ptX = marker.blockX() + 0.5F;
        float ptZ = marker.blockZ() + 0.5F;

        boolean datapackStructure = marker.feature() == SeedMapperFeature.DATAPACK_STRUCTURE;
        boolean portalMarker = marker.feature() == SeedMapperFeature.NETHER_PORTAL
                || marker.feature() == SeedMapperFeature.END_PORTAL
                || marker.feature() == SeedMapperFeature.END_BEACON;
        int iconWidth;
        int iconHeight;
        if (portalMarker) {
            // Live portals use the same screen-space entity icon sizing as
            // containers, redstone, workstations, and spawners.
            int entitySize = Math.max(2, Math.round(ICON_WIDTH * (float) seedMapperOptions.mapEntityScale));
            iconWidth = entitySize;
            iconHeight = entitySize;
        } else {
            int baseSize = datapackStructure ? SeedMapperImportedDatapackManager.iconSizeForPersistentMap() : ICON_WIDTH;
            int scaledSize = Math.max(2, (int) Math.round(baseSize * seedMapperOptions.seedMapperWorldMapIconScale));
            iconWidth = scaledSize;
            iconHeight = scaledSize;
        }
        int x = this.width / 2;
        int y = this.height / 2;
        int borderX = this.centerX + iconWidth / 2;
        int borderY = this.centerY + iconHeight / 2;

        double wayX = this.mapCenterX - (this.oldNorth ? -ptZ : ptX);
        double wayY = this.mapCenterZ - (this.oldNorth ? ptX : ptZ);
        double dispX = wayX * mapToGui;
        double dispY = wayY * mapToGui;
        float locate = 0;
        float hypot = 0;
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().rotate(-locate);
        graphics.pose().translate(0.0F, -hypot);
        graphics.pose().rotate(locate);

        Vector2f guiVector = graphics.pose().transformPosition(new Vector2f(x, y));
        float screenX = guiVector.x();
        float screenY = guiVector.y();
        float iconLeft = screenX - iconWidth / 2.0F;
        float iconRight = screenX + iconWidth / 2.0F;
        float iconTop = screenY - iconHeight / 2.0F;
        if (iconTop <= getSeedMapperStripBottomY()
                && iconRight >= seedMapperStripLeft
                && iconLeft <= seedMapperStripRight) {
            graphics.pose().popMatrix();
            return;
        }
        // SeedMapper parity: never render offscreen icons on persistent map.
        if (screenX < iconWidth / 2.0F
                || screenX > this.width - iconWidth / 2.0F
                || screenY < this.top + iconHeight / 2.0F
                || screenY > this.bottom - iconHeight / 2.0F) {
            graphics.pose().popMatrix();
            return;
        }
        String worldKey = currentSeedMapperWorldKey();
        boolean completed = seedMapperOptions.isCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ());
        Waypoint linkedWaypoint = findWaypointForMarker(marker);
        if (linkedWaypoint != null && mapOptions.waypointsAllowed && options.showWaypoints) {
            graphics.pose().popMatrix();
            return;
        }
        seedMapperMarkerHitboxes.add(new SeedMapperMarkerHitbox(marker, worldKey, screenX, screenY, iconWidth, iconHeight));

        boolean isHovered = mouseX >= screenX - iconWidth / 2.0F && mouseX <= screenX + iconWidth / 2.0F
                && mouseY >= screenY - iconHeight / 2.0F && mouseY <= screenY + iconHeight / 2.0F;
        if (isHovered && popupOpen()) {
            graphics.requestCursor(CursorTypes.CROSSHAIR);
            Component tooltip = Component.translatable(marker.feature().translationKey())
                    .append(marker.label() == null || marker.label().isBlank() ? Component.empty() : Component.literal(" [" + marker.label() + "]"))
                    .append(Component.literal(" (X: " + marker.blockX() + ", Z: " + marker.blockZ() + ")"))
                    .append(Component.literal(completed ? " [Completed]" : ""));
            renderTooltip(graphics, tooltip, this.mouseX, this.mouseY);
        }

        int iconColor = datapackStructure
                ? SeedMapperImportedDatapackManager.colorForStructureId(marker.label())
                : 0xFFFFFFFF;
        if (datapackStructure) {
            drawDatapackMarker(graphics, x, y, iconWidth, iconHeight, iconColor);
        } else if (portalMarker) {
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, marker.feature().icon(),
                    x - iconWidth / 2.0F, y - iconHeight / 2.0F, iconWidth, iconHeight,
                    0.0F, 1.0F, 0.0F, 1.0F, iconColor);
        } else {
            Identifier icon = marker.feature().icon();
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, icon, x - iconWidth / 2.0F, y - iconHeight / 2.0F, iconWidth, iconHeight, 0, 1, 0, 1, iconColor);
        }
        if (linkedWaypoint != null) {
            drawIconStroke(graphics, Math.round(x - iconWidth / 2.0F), Math.round(y - iconHeight / 2.0F), iconWidth, iconHeight, linkedWaypoint.getUnifiedColor());
        }
        if (completed) {
            drawCompletedTick(graphics, Math.round(x - iconWidth / 2.0F), Math.round(y - iconHeight / 2.0F), iconWidth, iconHeight);
        }
        if (marker.feature() == SeedMapperFeature.ELYTRA
                && seedMapperOptions.elytraDetection
                && seedMapperOptions.isElytraMissing(worldKey, marker.blockX(), marker.blockZ())) {
            drawMissingElytraSlash(graphics, Math.round(x - iconWidth / 2.0F), Math.round(y - iconHeight / 2.0F), iconWidth, iconHeight);
        }
        if (marker.feature() == SeedMapperFeature.TREASURE_CLUSTER) {
            int count = parseClusterTreasureCount(marker.label());
            if (count > 0) {
                drawWaypointClusterBadge(graphics, x, y, count);
            }
        }
        graphics.pose().popMatrix();
    }

    private int parseClusterTreasureCount(String label) {
        if (label == null || label.isBlank()) {
            return 0;
        }
        int separator = label.indexOf(' ');
        String number = separator < 0 ? label : label.substring(0, separator);
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void drawMissingElytraSlash(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int inset = Math.max(1, Math.min(width, height) / 6);
        drawLine(graphics, x + inset, y + height - inset, x + width - inset, y + inset, 4, 0xFF5A0000);
        drawLine(graphics, x + inset, y + height - inset, x + width - inset, y + inset, 2, 0xFFFF2020);
    }

    private void drawDatapackMarker(GuiGraphicsExtractor graphics, int x, int y, int iconWidth, int iconHeight, int color) {
        float left = x - iconWidth / 2.0F;
        float top = y - iconHeight / 2.0F;
        if (SeedMapperImportedDatapackManager.usesPotionIcon()) {
            Identifier potion = SeedMapperImportedDatapackManager.iconForStructureId("");
            Identifier overlay = SeedMapperImportedDatapackManager.iconOverlayForStructureId("");
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, potion, left, top, iconWidth, iconHeight, 0, 1, 0, 1, 0xFFFFFFFF);
            VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, overlay, left, top, iconWidth, iconHeight, 0, 1, 0, 1, color);
            return;
        }

        graphics.fill(Math.round(left) - 1, Math.round(top) - 1, Math.round(left + iconWidth) + 1, Math.round(top + iconHeight) + 1, 0xFF000000);
        graphics.fill(Math.round(left), Math.round(top), Math.round(left + iconWidth), Math.round(top + iconHeight), color);
    }

    private Identifier getViewedDimensionIdentifier() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        return this.options.worldMapDimensionView.resolveIdentifier(currentLevel);
    }

    private DimensionContainer getViewedDimensionContainer() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        if (this.options.worldMapDimensionView == PersistentMapSettingsManager.WorldMapDimensionView.CURRENT && currentLevel != null) {
            return VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(currentLevel);
        }
        return VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByIdentifier(getViewedDimensionIdentifier());
    }

    private boolean isViewingCurrentDimension() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        return currentLevel != null && currentLevel.dimension().identifier().equals(getViewedDimensionIdentifier());
    }

    private double viewedCoordinateScale() {
        return coordinateScaleForDimension(getViewedDimensionContainer());
    }

    private double coordinateScaleForDimension(DimensionContainer dimension) {
        return dimension == null || dimension.type == null ? 1.0D : dimension.type.coordinateScale();
    }

    private double convertCurrentCoordinateToViewed(double coordinate) {
        Level currentLevel = GameVariableAccessShim.getWorld();
        if (currentLevel == null) {
            return coordinate;
        }
        double currentScale = currentLevel.dimensionType().coordinateScale();
        double targetScale = viewedCoordinateScale();
        if (currentScale == targetScale) {
            return coordinate;
        }
        return coordinate * currentScale / targetScale;
    }

    private boolean hasMeaningfulViewedPositionForCurrentPlayer() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        if (currentLevel == null) {
            return false;
        }
        Identifier currentDimension = currentLevel.dimension().identifier();
        Identifier viewedDimension = getViewedDimensionIdentifier();
        return currentDimension.equals(viewedDimension)
                || (!Level.END.identifier().equals(currentDimension) && !Level.END.identifier().equals(viewedDimension));
    }

    private void recenterForViewedDimension() {
        if (!hasMeaningfulViewedPositionForCurrentPlayer() || this.options.worldMapDimensionView == PersistentMapSettingsManager.WorldMapDimensionView.END) {
            centerAt(0.0D, 0.0D);
        } else {
            centerAt(convertCurrentCoordinateToViewed(GameVariableAccessShim.xCoordDouble()), convertCurrentCoordinateToViewed(GameVariableAccessShim.zCoordDouble()));
        }
        // Recenter and dimension buttons are GUI actions. Grabbing the mouse
        // here makes 26.3 treat the action as a screen transition and closes
        // the map on some clients.
        switchToMouseInput();
    }

    private boolean isWaypointVisibleInViewedDimension(Waypoint waypoint) {
        return waypoint != null && waypoint.inWorld && waypoint.isInDimension(getViewedDimensionContainer());
    }

    private int getWaypointXInViewedDimension(Waypoint waypoint) {
        return waypoint == null ? 0 : waypoint.getXInDimension(getViewedDimensionContainer());
    }

    private int getWaypointZInViewedDimension(Waypoint waypoint) {
        return waypoint == null ? 0 : waypoint.getZInDimension(getViewedDimensionContainer());
    }

    private PreviewBounds getVisibleWorldBounds() {
        if (this.oldNorth) {
            int minX = (int) Math.floor(this.mapCenterZ - this.centerY * this.guiToMap);
            int maxX = (int) Math.ceil(this.mapCenterZ + this.centerY * this.guiToMap);
            int minZ = (int) Math.floor(-this.mapCenterX - this.centerX * this.guiToMap);
            int maxZ = (int) Math.ceil(-this.mapCenterX + this.centerX * this.guiToMap);
            return new PreviewBounds(minX, maxX, minZ, maxZ);
        }
        int minX = (int) Math.floor(this.mapCenterX - this.centerX * this.guiToMap);
        int maxX = (int) Math.ceil(this.mapCenterX + this.centerX * this.guiToMap);
        int minZ = (int) Math.floor(this.mapCenterZ - this.centerY * this.guiToMap);
        int maxZ = (int) Math.ceil(this.mapCenterZ + this.centerY * this.guiToMap);
        return new PreviewBounds(minX, maxX, minZ, maxZ);
    }

    private ExportPlayerCoords getViewedExportPlayerCoords(PreviewBounds visibleBounds) {
        if (hasMeaningfulViewedPositionForCurrentPlayer()) {
            return new ExportPlayerCoords(
                    Mth.floor(convertCurrentCoordinateToViewed(GameVariableAccessShim.xCoordDouble())),
                    Mth.floor(convertCurrentCoordinateToViewed(GameVariableAccessShim.zCoordDouble()))
            );
        }
        return new ExportPlayerCoords(
                (visibleBounds.minX() + visibleBounds.maxX()) / 2,
                (visibleBounds.minZ() + visibleBounds.maxZ()) / 2
        );
    }

    private PreviewBounds getSeedPreviewRequestBounds(PreviewBounds visibleBounds) {
        long spanX = Math.max(1L, (long) visibleBounds.maxX() - visibleBounds.minX());
        long spanZ = Math.max(1L, (long) visibleBounds.maxZ() - visibleBounds.minZ());
        long padding = Math.max(this.options.getSeedMapPreviewPadding(), Math.max(spanX, spanZ) / 3);
        int snap = (int) Math.max(256L, Math.min(8192L, Long.highestOneBit(Math.max(256L, padding))));
        // Far beyond Minecraft's world border, retain headroom for grid alignment
        // instead of overflowing int endpoints during very wide zoom transitions.
        int minX = (int) Math.min(1_000_000_000L, Math.max(-1_000_000_000L, Math.floorDiv((long) visibleBounds.minX() - padding, snap) * snap));
        int maxX = (int) Math.max(-1_000_000_000L, Math.min(1_000_000_000L, Math.floorDiv((long) visibleBounds.maxX() + padding + snap - 1, snap) * snap));
        int minZ = (int) Math.min(1_000_000_000L, Math.max(-1_000_000_000L, Math.floorDiv((long) visibleBounds.minZ() - padding, snap) * snap));
        int maxZ = (int) Math.max(-1_000_000_000L, Math.min(1_000_000_000L, Math.floorDiv((long) visibleBounds.maxZ() + padding + snap - 1, snap) * snap));
        return new PreviewBounds(Math.min(minX, maxX), Math.max(minX, maxX), Math.min(minZ, maxZ), Math.max(minZ, maxZ));
    }

    private void ensureSeedPreviewTextureSize(int width, int height) {
        if (this.seedPreviewTexture != null && this.seedPreviewTexture.getWidth() == width && this.seedPreviewTexture.getHeight() == height) {
            return;
        }
        if (this.seedPreviewTexture != null) {
            minecraft.getTextureManager().release(seedPreviewTextureLocation);
        }
        this.seedPreviewTexture = new DynamicMutableTexture("Voxelmap Seed Preview", width, height, true);
        minecraft.getTextureManager().register(seedPreviewTextureLocation, this.seedPreviewTexture);
    }

    private int getSeedPreviewSampleQuartY(int dimension) {
        return getSeedPreviewSampleQuartY(dimension, this.seedMapperOptions.seedMapBiomeY);
    }

    private int getSeedPreviewSampleQuartY(int dimension, int biomeY) {
        return dimension == Cubiomes.DIM_END()
                ? 0
                : QuartPos.fromBlock(biomeY);
    }

    private int getSeedMapperGeneratorFlags() {
        return this.seedMapperOptions.largeBiomes ? Cubiomes.LARGE_BIOMES() : 0;
    }

    private String resolveSeedBiomeNameAt(int blockX, int blockZ) {
        long seed;
        try {
            seed = resolveWorldMapSeed();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            return "";
        }
        SeedBiomeNameKey key = new SeedBiomeNameKey(seed, dimension, blockX >> 2, blockZ >> 2,
                SeedMapperCompat.getMcVersion(), getSeedMapperGeneratorFlags(), getSeedPreviewSampleQuartY(dimension));
        String name = this.seedBiomeNames.get(key, () -> sampleSeedBiomeName(key));
        return name == null ? "" : name;
    }

    private String sampleSeedBiomeName(SeedBiomeNameKey key) {
        // Never acquire the native lock on the render thread: preview generation
        // can hold it for seconds at high resolutions.
        try {
            SeedMapperNative.ensureLoaded();
            synchronized (SeedMapperNative.cubiomesLock()) {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment generator = Generator.allocate(arena);
                    Cubiomes.setupGenerator(generator, key.mcVersion(), key.flags());
                    Cubiomes.applySeed(generator, key.dimension(), key.seed());
                    int biomeId = Cubiomes.getBiomeAt(generator, 4, key.quartX(), key.sampleY(), key.quartZ());
                    MemorySegment name = Cubiomes.biome2str(key.mcVersion(), biomeId);
                    return name != null && name.address() != 0L ? prettifyBiomeName(name.getString(0)) : "";
                }
            }
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String prettifyBiomeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        s = s.replace('_', ' ').trim();
        StringBuilder sb = new StringBuilder(s.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private int getCurrentCubiomesDimension() {
        return switch (this.options.worldMapDimensionView) {
            case CURRENT -> {
                Level currentLevel = GameVariableAccessShim.getWorld();
                if (currentLevel == null) {
                    yield Integer.MIN_VALUE;
                }
                if (DimensionManager.getEnvironment(currentLevel) == DimensionManager.Environment.NETHER) {
                    yield Cubiomes.DIM_NETHER();
                }
                if (DimensionManager.getEnvironment(currentLevel) == DimensionManager.Environment.END) {
                    yield Cubiomes.DIM_END();
                }
                yield Cubiomes.DIM_OVERWORLD();
            }
            case OVERWORLD -> Cubiomes.DIM_OVERWORLD();
            case NETHER -> Cubiomes.DIM_NETHER();
            case END -> Cubiomes.DIM_END();
        };
    }

    private boolean handleSeedMapperMarkerRightClick(int mouseX, int mouseY) {
        for (SeedMapperMarkerHitbox hitbox : seedMapperMarkerHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                selectedSeedMapperMarker = hitbox.marker();
                selectedSeedMapperWorldKey = hitbox.worldKey();
                selectedSeedMapperAssociatedWaypoint = findWaypointForMarker(selectedSeedMapperMarker);
                selectedSeedMapperWaypoint = findSelectedSeedMapperWaypoint(selectedSeedMapperMarker);
                if (selectedSeedMapperWaypoint == null) {
                    selectedSeedMapperWaypoint = createTransientStructureWaypoint(selectedSeedMapperMarker);
                }
                selectedWaypoint = selectedSeedMapperWaypoint;
                int mouseDirectX = (int) minecraft.mouseHandler.xpos();
                int mouseDirectY = (int) minecraft.mouseHandler.ypos();
                createStructurePopup(mouseX, mouseY, mouseDirectX, mouseDirectY);
                return true;
            }
        }
        return false;
    }

    private boolean handleSeedMapperMarkerLeftClick(int mouseX, int mouseY) {
        for (SeedMapperMarkerHitbox hitbox : seedMapperMarkerHitboxes) {
            if (!hitbox.contains(mouseX, mouseY)) {
                continue;
            }

            SeedMapperMarker marker = hitbox.marker();
            // Trial Chambers are checked before the chest-loot path: they are also
            // registered in FEATURE_TABLES, so gating the vault preview on the absence of
            // chest loot made this branch unreachable. A direct click opens the vault
            // preview; the chest loot stays available from the context menu.
            if (marker.feature() == SeedMapperFeature.TRIAL_CHAMBERS) {
                seedMapperLootWidgetOpenedAtMs = System.currentTimeMillis();
                seedMapperLootWidgetOpenedX = mouseX;
                seedMapperLootWidgetOpenedY = mouseY;
                openVaultLootWidgetAt(mouseX + 10, mouseY + 10);
                return true;
            }
            if (!SeedMapperLootService.hasPredictableLoot(marker.feature())) {
                // Nothing actionable for this feature. Keep scanning instead of
                // returning: markers can overlap, and the actionable one underneath
                // the cursor should still receive the click.
                continue;
            }

            long seed;
            try {
                seed = resolveWorldMapSeed();
            } catch (IllegalArgumentException ignored) {
                minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("SeedMapper",
                        "No seed is set for this world, so structure loot cannot be predicted."));
                return true;
            }

            int dimension = getCurrentCubiomesDimension();
            int generatorFlags = getSeedMapperGeneratorFlags();
            List<SeedMapperChestLootData> chestData;
            try {
                chestData = SeedMapperLootService.buildStructureChestLoot(
                        seed,
                        dimension,
                        SeedMapperCompat.getMcVersion(),
                        generatorFlags,
                        marker.feature(),
                        marker.blockX(),
                        marker.blockZ()
                );
            } catch (Throwable error) {
                // The cubiomes native library can fail to load or reject a call on an
                // unsupported platform. Report it in game rather than letting the
                // exception disappear into the GUI event loop with no visible effect.
                VoxelConstants.getLogger().error("SeedMapper loot lookup failed for {}", marker.feature().id(), error);
                minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("SeedMapper",
                        "Loot lookup failed for " + marker.feature().id() + ": " + error));
                return true;
            }
            if (chestData.isEmpty()) {
                minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("SeedMapper",
                        "No chest loot data available for " + marker.feature().id() + "."));
                return true;
            }

            int widgetX = Mth.clamp(mouseX + 10, 4, this.width - SeedMapperChestLootWidget.WIDTH - 4);
            int widgetY = Mth.clamp(mouseY + 10, this.top + 4, this.bottom - SeedMapperChestLootWidget.HEIGHT - 4);
            seedMapperChestLootWidget = new SeedMapperChestLootWidget(widgetX, widgetY, chestData);
            seedMapperLootWidgetOpenedAtMs = System.currentTimeMillis();
            seedMapperLootWidgetOpenedX = mouseX;
            seedMapperLootWidgetOpenedY = mouseY;
            return true;
        }
        return false;
    }

    /** Opens the vault-reward preview for the current world seed at the hovered map position. */
    private void openVaultLootWidgetAt(int guiX, int guiY) {
        long seed;
        try {
            seed = resolveWorldMapSeed();
        } catch (IllegalArgumentException ignored) {
            minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("SeedMapper",
                    "No seed is set for this world, so vault rewards cannot be predicted."));
            return;
        }
        int mcVersion = SeedMapperCompat.getMcVersion();
        List<SeedMapperVaultService.VaultPrediction> normalPredictions = SeedMapperVaultService.predict(
                seed, mcVersion, 0, false, 8);
        List<SeedMapperVaultService.VaultPrediction> ominousPredictions = SeedMapperVaultService.predict(
                seed, mcVersion, 0, true, 8);
        if (normalPredictions.isEmpty() && ominousPredictions.isEmpty()) {
            minecraft.gui.hud.getChat().addClientSystemMessage(
                    AppChatMessages.prefixed("SeedMapper", "Vault prediction unavailable for this version or seed."));
            return;
        }
        seedMapperChestLootWidget = null;
        int widgetX = Mth.clamp(guiX, 4, this.width - SeedMapperVaultLootWidget.WIDTH - 4);
        int widgetY = Mth.clamp(guiY, this.top + 4, this.bottom - SeedMapperVaultLootWidget.HEIGHT - 4);
        seedMapperVaultLootWidget = new SeedMapperVaultLootWidget(
                widgetX, widgetY, normalPredictions, ominousPredictions);
    }

    private void drawCompletedTick(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int size = Math.max(8, Math.min(width, height) - 4);
        int baseX = x + (width - size) / 2;
        int baseY = y + (height - size) / 2;
        int startX = baseX + size / 5;
        int startY = baseY + size * 3 / 5;
        int midX = baseX + size * 2 / 5;
        int midY = baseY + size * 4 / 5;
        int endX = baseX + size * 4 / 5;
        int endY = baseY + size / 5;
        drawLine(graphics, startX, startY, midX, midY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        drawLine(graphics, midX, midY, endX, endY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        drawLine(graphics, startX, startY, midX, midY, 1, COMPLETED_TICK_COLOR);
        drawLine(graphics, midX, midY, endX, endY, 1, COMPLETED_TICK_COLOR);
    }

    private void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            graphics.fill(x1 - thickness / 2, y1 - thickness / 2, x1 + thickness / 2 + 1, y1 + thickness / 2 + 1, color);
            return;
        }
        int radius = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + dx * i / steps;
            int py = y1 + dy * i / steps;
            graphics.fill(px - radius, py - radius, px + radius + 1, py + radius + 1, color);
        }
    }

    private boolean isCtrlDown() {
        long window = minecraft.getWindow().handle();
        return InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
    }

    private boolean isShiftDown() {
        long window = minecraft.getWindow().handle();
        return InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
    }

    private boolean isInTopHeader(int mouseX, int mouseY) {
        return mouseY >= 0 && mouseY < this.top;
    }

    private boolean isInSeedHeader(int mouseX, int mouseY) {
        int left = seedHeaderLeft;
        int right = seedHeaderRight;
        int top = seedHeaderTop;
        int bottom = seedHeaderBottom;
        if (left < 0 || right <= left || top < 0 || bottom <= top) {
            String seedTextValue = getWorldMapSeedText();
            if (seedTextValue.isEmpty()) {
                return false;
            }
            String seedText = "Seed: " + seedTextValue;
            int seedX = this.width - this.sideMargin - this.getFont().width(seedText);
            left = seedX - 2;
            right = seedX + this.getFont().width(seedText) + 2;
            top = 15;
            bottom = 16 + this.getFont().lineHeight + 1;
        }
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private boolean isCoordinateSubmitKey(KeyEvent keyEvent) {
        return keyEvent.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
                || keyEvent.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER;
    }

    private void commitCoordinateInputs() {
        try {
            int x = Integer.parseInt(this.coordinateXInput.getValue().trim());
            int z = Integer.parseInt(this.coordinateZInput.getValue().trim());
            this.centerAt(x, z);
            closeCoordinateInputs();
            this.switchToMouseInput();
        } catch (NumberFormatException ignored) {
            // The caller validates first, but keep an unexpected input event
            // from closing the map or throwing out of the GUI event loop.
        }
    }

    private boolean isInCoordinateLabel(int mouseX, int mouseY) {
        int left = this.coordinateLabelLeft;
        int right = this.coordinateLabelRight;
        int top = this.coordinateLabelTop;
        int bottom = this.coordinateLabelBottom;
        if (right <= left || bottom <= top) {
            String xText = "X: " + this.coordinateHoverX;
            String zText = "Z: " + this.coordinateHoverZ;
            int xTextX = this.sideMargin;
            int zTextX = this.sideMargin + 64;
            left = xTextX - 2;
            right = zTextX + this.getFont().width(zText) + 2;
            top = 15;
            bottom = 16 + this.getFont().lineHeight + 1;
        }
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private boolean isInSeedMapperStrip(int mouseX, int mouseY) {
        if (!seedMapperOptions.enabled || !mapOptions.worldmapAllowed) {
            return false;
        }
        if (seedMapperStripLeft < 0 || seedMapperStripRight <= seedMapperStripLeft) {
            return false;
        }
        return mouseX >= seedMapperStripLeft
                && mouseX <= seedMapperStripRight
                && mouseY >= seedMapperStripTop
                && mouseY <= seedMapperStripBottom;
    }

    private boolean isInSeedMapperTitle(int mouseX, int mouseY) {
        return seedMapperTitleLeft >= 0
                && seedMapperTitleRight > seedMapperTitleLeft
                && seedMapperTitleTop >= 0
                && seedMapperTitleBottom > seedMapperTitleTop
                && mouseX >= seedMapperTitleLeft
                && mouseX <= seedMapperTitleRight
                && mouseY >= seedMapperTitleTop
                && mouseY <= seedMapperTitleBottom;
    }

    private boolean isInWaypointSearchInput(int mouseX, int mouseY) {
        return this.waypointSearchInput != null
                && mouseX >= this.waypointSearchInput.getX()
                && mouseX <= this.waypointSearchInput.getX() + this.waypointSearchInput.getWidth()
                && mouseY >= this.waypointSearchInput.getY()
                && mouseY <= this.waypointSearchInput.getY() + this.waypointSearchInput.getHeight();
    }

    private int getSeedMapperStripBottomY() {
        if (!seedMapperOptions.enabled || !mapOptions.worldmapAllowed) {
            return Integer.MIN_VALUE;
        }
        return seedMapperStripBottom >= 0 ? seedMapperStripBottom : 56;
    }

    private void openCoordinateInputs(int x, int z) {
        this.editingCoordinates = true;
        this.lastEditingCoordinates = false;
        this.waypointSearchInput.setFocused(false);
        this.coordinateXInput.setVisible(true);
        this.coordinateZInput.setVisible(true);
        this.coordinateXInput.active = true;
        this.coordinateZInput.active = true;
        this.coordinateXInput.setValue(String.valueOf(x));
        this.coordinateZInput.setValue(String.valueOf(z));
        this.coordinateXInput.setTextColor(COORD_TEXT_COLOR_OK);
        this.coordinateZInput.setTextColor(COORD_TEXT_COLOR_OK);
        this.coordinateXInput.setTextColorUneditable(COORD_TEXT_COLOR_OK);
        this.coordinateZInput.setTextColorUneditable(COORD_TEXT_COLOR_OK);
        this.coordinateXInput.setFocused(true);
        this.coordinateZInput.setFocused(false);
        this.setFocused(this.coordinateXInput);
    }

    private void closeCoordinateInputs() {
        this.editingCoordinates = false;
        this.lastEditingCoordinates = false;
        this.coordinateXInput.setFocused(false);
        this.coordinateZInput.setFocused(false);
        this.coordinateXInput.setVisible(false);
        this.coordinateZInput.setVisible(false);
        this.coordinateXInput.active = false;
        this.coordinateZInput.active = false;
        this.setFocused(null);
    }

    private boolean isOverCoordinateInputs(int mouseX, int mouseY) {
        return isInCoordinateXInput(mouseX, mouseY) || isInCoordinateZInput(mouseX, mouseY);
    }

    private boolean isInCoordinateXInput(int mouseX, int mouseY) {
        return mouseX >= this.coordinateXInput.getX()
                && mouseX <= this.coordinateXInput.getX() + this.coordinateXInput.getWidth()
                && mouseY >= this.coordinateXInput.getY()
                && mouseY <= this.coordinateXInput.getY() + this.coordinateXInput.getHeight();
    }

    private boolean isInCoordinateZInput(int mouseX, int mouseY) {
        return mouseX >= this.coordinateZInput.getX()
                && mouseX <= this.coordinateZInput.getX() + this.coordinateZInput.getWidth()
                && mouseY >= this.coordinateZInput.getY()
                && mouseY <= this.coordinateZInput.getY() + this.coordinateZInput.getHeight();
    }

    private boolean featureMatchesDimension(SeedMapperFeature feature, int dimension) {
        if (feature == null) {
            return false;
        }

        if (dimension == Integer.MIN_VALUE) {
            return true;
        }

        if (feature == SeedMapperFeature.END_GATEWAY) {
            return dimension == com.github.cubiomes.Cubiomes.DIM_END();
        }

        if (feature == SeedMapperFeature.END_PORTAL) {
            return dimension != com.github.cubiomes.Cubiomes.DIM_NETHER();
        }

        if (feature == SeedMapperFeature.NETHER_PORTAL) {
            return dimension == com.github.cubiomes.Cubiomes.DIM_OVERWORLD()
                    || dimension == com.github.cubiomes.Cubiomes.DIM_NETHER();
        }

        if (feature == SeedMapperFeature.END_BEACON) {
            return dimension == com.github.cubiomes.Cubiomes.DIM_END();
        }

        return feature.availableInDimension(dimension);
    }

    private void createSeedMapperWaypoint(SeedMapperMarker marker) {
        if (marker == null || marker.feature() == null) {
            return;
        }

        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        DimensionContainer viewedDimension = getViewedDimensionContainer();
        if (viewedDimension != null) {
            dimensions.add(viewedDimension);
        }

        int y = terrainHighlightY(marker.blockX(), marker.blockZ());

        String name = Component.translatable(marker.feature().translationKey()).getString();
        if (marker.label() != null && !marker.label().isBlank()) {
            name = name + " [" + marker.label() + "]";
        }
        name = name + " (" + marker.blockX() + ", " + marker.blockZ() + ")";

        Waypoint waypoint = new Waypoint(
                name,
                marker.blockX(),
                marker.blockZ(),
                y,
                true,
                0.20F,
                0.85F,
                1.0F,
                "temple",
                waypointManager.getCurrentSubworldDescriptor(false),
                dimensions
        );
        waypointManager.addWaypoint(waypoint);
        minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("SeedMapper", "Waypoint created for " + name));
    }

    private Waypoint createTransientStructureWaypoint(SeedMapperMarker marker) {
        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        DimensionContainer viewedDimension = getViewedDimensionContainer();
        if (viewedDimension != null) {
            dimensions.add(viewedDimension);
        }
        int y = terrainHighlightY(marker.blockX(), marker.blockZ());
        return new Waypoint(
                displayMarkerName(marker),
                marker.blockX(),
                marker.blockZ(),
                y,
                true,
                0.20F,
                0.85F,
                1.0F,
                "temple",
                waypointManager.getCurrentSubworldDescriptor(false),
                dimensions
        );
    }

    private int terrainHighlightY(int x, int z) {
        Level level = VoxelConstants.getPlayer().level();
        if (isViewingCurrentDimension()) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            if (y < level.getMinY()) {
                y = this.persistentMap.getHeightAt(x, z, getViewedDimensionIdentifier());
            }
            return Math.max(y, 64);
        }
        int cachedHeight = this.persistentMap.getHeightAt(x, z, getViewedDimensionIdentifier());
        return cachedHeight == Short.MIN_VALUE ? 64 : Math.max(cachedHeight, 64);
    }

    private Waypoint findSeedMapperHighlightWaypoint(SeedMapperMarker marker) {
        if (marker == null) {
            return null;
        }

        String key = seedMapperHighlightKey(marker);
        String waypointName = seedMapperHighlightWaypoints.get(key);
        if (waypointName != null) {
            for (Waypoint waypoint : waypointManager.getWaypoints()) {
                if (waypointName.equals(waypoint.name)) {
                    return waypoint;
                }
            }
        }

        Waypoint highlighted = waypointManager.getHighlightedWaypoint();
        if (highlighted != null
                && getWaypointXInViewedDimension(highlighted) == marker.blockX()
                && getWaypointZInViewedDimension(highlighted) == marker.blockZ()
                && isSeedMapperHighlightName(highlighted.name)) {
            return highlighted;
        }

        return null;
    }

    private Waypoint findSelectedSeedMapperWaypoint(SeedMapperMarker marker) {
        if (marker == null) {
            return null;
        }

        Waypoint highlighted = waypointManager.getHighlightedWaypoint();
        if (isMarkerHighlighted(marker, highlighted)) {
            return highlighted;
        }

        Waypoint associated = findWaypointForMarker(marker);
        if (associated != null) {
            return associated;
        }

        return findSeedMapperHighlightWaypoint(marker);
    }

    private boolean isSeedMapperHighlightWaypoint(Waypoint waypoint) {
        return waypoint != null && isSeedMapperHighlightName(waypoint.name);
    }

    private boolean isMarkerHighlighted(SeedMapperMarker marker, Waypoint waypoint) {
        if (marker == null || waypoint == null) {
            return false;
        }

        return getWaypointXInViewedDimension(waypoint) == marker.blockX()
                && getWaypointZInViewedDimension(waypoint) == marker.blockZ()
                && waypoint.inWorld
                && waypoint.isInDimension(getViewedDimensionContainer());
    }

    private boolean isMarkerHighlighted(SeedMapperMarker marker) {
        if (marker == null) {
            return false;
        }

        if (isMarkerHighlighted(marker, waypointManager.getHighlightedWaypoint())) {
            return true;
        }

        String key = seedMapperHighlightKey(marker);
        String waypointName = seedMapperHighlightWaypoints.get(key);
        if (waypointName == null) {
            return false;
        }

        for (Waypoint waypoint : waypointManager.getWaypoints()) {
            if (waypointName.equals(waypoint.name)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSeedMapperHighlightName(String name) {
        return name != null && name.startsWith("SeedMapper Highlight ");
    }

    private String seedMapperHighlightKey(SeedMapperMarker marker) {
        return currentSeedMapperWorldKey() + "|" + marker.feature().id() + "|" + marker.blockX() + "|" + marker.blockZ();
    }

    private String seedMapperHighlightName(SeedMapperMarker marker) {
        return "SeedMapper Highlight " + displayMarkerName(marker);
    }

    private void rememberSeedMapperHighlight(SeedMapperMarker marker, String waypointName) {
        if (marker == null || waypointName == null) {
            return;
        }
        seedMapperHighlightWaypoints.put(seedMapperHighlightKey(marker), waypointName);
    }

    private void deleteSeedMapperHighlight(SeedMapperMarker marker) {
        if (marker == null) {
            return;
        }

        String key = seedMapperHighlightKey(marker);
        String waypointName = seedMapperHighlightWaypoints.remove(key);
        if (waypointName == null) {
            Waypoint waypoint = findSeedMapperHighlightWaypoint(marker);
            waypointName = waypoint != null ? waypoint.name : null;
        }
        if (waypointName == null) {
            return;
        }

        for (Waypoint waypoint : new ArrayList<>(waypointManager.getWaypoints())) {
            if (waypointName.equals(waypoint.name)) {
                waypointManager.deleteWaypoint(waypoint);
                break;
            }
        }
    }

    private String displayMarkerName(SeedMapperMarker marker) {
        String name = Component.translatable(marker.feature().translationKey()).getString();
        if (marker.label() != null && !marker.label().isBlank()) {
            name = name + " [" + marker.label() + "]";
        }
        return name + " (" + marker.blockX() + ", " + marker.blockZ() + ")";
    }

    private void toggleSeedMapperMarkerCompleted(SeedMapperMarker marker, String worldKey) {
        if (marker == null || worldKey == null) {
            return;
        }
        boolean completed = seedMapperOptions.isCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ());
        seedMapperOptions.setCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ(), !completed);
        MapSettingsManager.instance.saveAll();
    }

    private Waypoint findWaypointForMarker(SeedMapperMarker marker) {
        if (marker == null) {
            return null;
        }
        for (Waypoint waypoint : waypointManager.getWaypoints()) {
            if (!isWaypointVisibleInViewedDimension(waypoint)) continue;
            if (getWaypointXInViewedDimension(waypoint) == marker.blockX() && getWaypointZInViewedDimension(waypoint) == marker.blockZ() && isSeedMapperHighlightWaypoint(waypoint)) {
                return waypoint;
            }
        }
        for (Waypoint waypoint : waypointManager.getWaypoints()) {
            if (!isWaypointVisibleInViewedDimension(waypoint)) continue;
            if (getWaypointXInViewedDimension(waypoint) == marker.blockX() && getWaypointZInViewedDimension(waypoint) == marker.blockZ()) {
                return waypoint;
            }
        }
        return null;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private String getWaypointSearchQuery() {
        if (this.waypointSearchInput == null) {
            return "";
        }
        return TextUtils.scrubCodes(this.waypointSearchInput.getValue()).trim().toLowerCase(Locale.ROOT);
    }

    private boolean waypointMatchesSearch(Waypoint waypoint, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String name = waypoint.name == null ? "" : TextUtils.scrubCodes(waypoint.name);
        return name.toLowerCase(Locale.ROOT).contains(query);
    }

    private void drawQueuedWaypointLabels(GuiGraphicsExtractor graphics) {
        int layoutOptions = java.util.Objects.hash(options.clusterWaypointNames, top, bottom, width, height);
        if (layoutOptions != lastLabelOptions || !pendingWaypointLabels.equals(lastLabelInput)) {
            lastLabelOptions = layoutOptions;
            lastLabelInput = List.copyOf(pendingWaypointLabels);
            float centerX = getWidth() / 2.0F, centerY = top + (bottom - top) / 2.0F;
            pendingWaypointLabels.sort(Comparator.comparing(PendingWaypointLabel::highlighted).reversed()
                    .thenComparing(Comparator.comparing(PendingWaypointLabel::searchMatch).reversed())
                    .thenComparingInt(PendingWaypointLabel::labelWidth)
                    .thenComparingDouble(label -> label.distanceToCenter(centerX, centerY)));
            List<PlacedWaypointLabel> placed = new ArrayList<>();
            for (PendingWaypointLabel label : pendingWaypointLabels) {
                int row = options.clusterWaypointNames ? reserveWaypointLabel(label.centerX, label.iconCenterY, label.baseY, label.labelWidth,
                        label.labelHeight, WAYPOINT_LABEL_LINE_LIMIT) : 0;
                if (row >= 0) placed.add(new PlacedWaypointLabel(label, row));
                else if (options.clusterWaypointNames) registerWaypointCluster(label.clusterKey, label.centerX, label.iconCenterY);
            }
            lastLabelLayout = List.copyOf(placed);
            lastLabelClusters = java.util.Map.copyOf(waypointClusters);
        }
        for (PlacedWaypointLabel placed : lastLabelLayout) {
            PendingWaypointLabel label = placed.label();
            int row = placed.row(), step = label.labelHeight + 1;
            int y = (row & 1) == 0 ? Math.round(label.baseY + (row / 2) * step)
                    : Math.round(label.iconCenterY - ICON_HEIGHT / 2.0F - label.labelHeight - WAYPOINT_LABEL_PADDING - (row / 2) * step);
            graphics.text(getFont(), label.text, Math.round(label.centerX) - label.labelWidth / 2, y, label.color, true);
        }
        for (WaypointClusterData cluster : lastLabelClusters.values()) if (cluster.count > 1)
            drawWaypointClusterBadge(graphics, cluster.anchorX, cluster.anchorY, cluster.count);
        for (WaypointClusterData cluster : waypointIconBuckets.values()) if (cluster.count > 1)
            drawWaypointClusterBadge(graphics, cluster.anchorX, cluster.anchorY, cluster.count);
        pendingWaypointLabels.clear(); waypointLabelBounds.clear(); waypointLabelBuckets.clear(); waypointClusters.clear();
    }

    private int reserveWaypointLabel(float centerX, float iconCenterY, float topY, int labelWidth, int labelHeight, int maxRows) {
        int rows = Math.max(1, maxRows);
        for (int row = 0; row < rows; row++) {
            int left = Math.round(centerX - labelWidth / 2.0F) - WAYPOINT_LABEL_PADDING;
            int top;
            if ((row & 1) == 0) {
                top = Math.round(topY + (row / 2) * (labelHeight + 1.0F)) - WAYPOINT_LABEL_PADDING;
            } else {
                top = Math.round(iconCenterY - ICON_HEIGHT / 2.0F - labelHeight - WAYPOINT_LABEL_PADDING - (row / 2) * (labelHeight + 1.0F)) - WAYPOINT_LABEL_PADDING;
            }
            int right = left + labelWidth + WAYPOINT_LABEL_PADDING * 2;
            int bottom = top + labelHeight + WAYPOINT_LABEL_PADDING * 2;
            if (bottom > this.bottom) {
                continue;
            }
            WaypointLabelBounds candidate = new WaypointLabelBounds(left, top, right, bottom);
            if (!intersectsAnyWaypointLabel(candidate)) {
                this.waypointLabelBounds.add(candidate);
                for (int bx = Math.floorDiv(candidate.left, 64); bx <= Math.floorDiv(candidate.right, 64); bx++)
                    for (int by = Math.floorDiv(candidate.top, 64); by <= Math.floorDiv(candidate.bottom, 64); by++)
                        waypointLabelBuckets.computeIfAbsent(packXZ(bx, by), ignored -> new ArrayList<>()).add(candidate);
                return row;
            }
        }
        return -1;
    }

    private boolean intersectsAnyWaypointLabel(WaypointLabelBounds candidate) {
        for (int bx = Math.floorDiv(candidate.left, 64); bx <= Math.floorDiv(candidate.right, 64); bx++)
            for (int by = Math.floorDiv(candidate.top, 64); by <= Math.floorDiv(candidate.bottom, 64); by++) {
                List<WaypointLabelBounds> nearby = waypointLabelBuckets.get(packXZ(bx, by));
                if (nearby != null) for (WaypointLabelBounds bounds : nearby) if (bounds.intersects(candidate)) return true;
            }
        return false;
    }

    private void registerWaypointCluster(long clusterKey, float screenX, float screenY) {
        WaypointClusterData cluster = this.waypointClusters.get(clusterKey);
        if (cluster == null) {
            cluster = new WaypointClusterData(Math.round(screenX), Math.round(screenY));
            this.waypointClusters.put(clusterKey, cluster);
        }
        cluster.count++;
    }

    private void drawWaypointClusterBadge(GuiGraphicsExtractor graphics, float x, float y, int count) {
        String badgeText = String.valueOf(count);
        int badgeWidth = Math.max(12, this.getFont().width(badgeText) + 8);
        int badgeHeight = this.getFont().lineHeight + 4;
        int badgeX = Math.round(x + ICON_WIDTH / 2.0F - 2.0F);
        int badgeY = Math.round(y - ICON_HEIGHT / 2.0F - badgeHeight + 2.0F);
        graphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, 0xCC000000);
        graphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 1, 0xFFFFFFFF);
        graphics.fill(badgeX, badgeY + badgeHeight - 1, badgeX + badgeWidth, badgeY + badgeHeight, 0xFFFFFFFF);
        graphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeHeight, 0xFFFFFFFF);
        graphics.fill(badgeX + badgeWidth - 1, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, 0xFFFFFFFF);
        graphics.text(this.getFont(), badgeText, badgeX + 4, badgeY + 2, 0xFFFFFFFF);
    }

    private void drawIconStroke(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y, color);
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
        graphics.fill(x - 1, y, x, y + height, color);
        graphics.fill(x + width, y, x + width + 1, y + height, color);
    }

    private int enabledFeatureSetHash() {
        return enabledFeatureSetHash(seedMapperOptions.getEnabledFeaturesSnapshot());
    }

    private int enabledFeatureSetHash(Set<SeedMapperFeature> features) {
        int hash = 1;
        if (features != null) {
            for (SeedMapperFeature feature : features) {
                hash = 31 * hash + feature.ordinal();
            }
        }
        return hash;
    }

    private String currentSeedMapperWorldKey() {
        String world = waypointManager.getCurrentWorldName();
        String sub = waypointManager.getCurrentSubworldDescriptor(false);
        String dim = getViewedDimensionIdentifier().toString();
        return (world == null ? "unknown" : world) + "|" + (sub == null ? "" : sub) + "|" + dim;
    }

    private void centerOnWorldSpawn() {
        int dimension = getCurrentCubiomesDimension();
        if (dimension != com.github.cubiomes.Cubiomes.DIM_OVERWORLD()) {
            return;
        }
        long seed;
        try {
            seed = resolveWorldMapSeed();
        } catch (IllegalArgumentException ignored) {
            return;
        }
        List<SeedMapperMarker> markers = SeedMapperLocatorService.get().queryBlocking(
                seed,
                com.github.cubiomes.Cubiomes.DIM_OVERWORLD(),
                SeedMapperCompat.getMcVersion(),
                getSeedMapperGeneratorFlags(),
                -8192,
                8192,
                -8192,
                8192,
                seedMapperOptions,
                currentSeedMapperWorldKey()
        );
        for (SeedMapperMarker marker : markers) {
            if (marker.feature() == SeedMapperFeature.WORLD_SPAWN) {
                centerAt(marker.blockX(), marker.blockZ());
                return;
            }
        }
    }

    private record LegendEntry(SeedMapperFeature feature, String datapackStructureId) {
        private static LegendEntry feature(SeedMapperFeature feature) {
            return new LegendEntry(feature, null);
        }

        private static LegendEntry datapack(String structureId) {
            return new LegendEntry(SeedMapperFeature.DATAPACK_STRUCTURE, structureId);
        }
    }

    private record PreviewBounds(int minX, int maxX, int minZ, int maxZ) {
    }

    private record ExportPlayerCoords(int x, int z) {
    }

    private static final class WaypointLabelBounds {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private WaypointLabelBounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private boolean intersects(WaypointLabelBounds other) {
            return this.left < other.right
                    && this.right > other.left
                    && this.top < other.bottom
                    && this.bottom > other.top;
        }
    }

    private record PendingWaypointLabel(float centerX, float iconCenterY, float baseY, int labelWidth, int labelHeight, int color, String text, boolean searchActive, boolean searchMatch, boolean highlighted, long clusterKey) {
        private double distanceToCenter(float mapCenterX, float mapCenterY) {
            float dx = this.centerX - mapCenterX;
            float dy = this.iconCenterY - mapCenterY;
            return dx * dx + dy * dy;
        }
    }

    private static final class WaypointClusterData {
        private final int anchorX;
        private final int anchorY;
        private int count;

        private WaypointClusterData(int anchorX, int anchorY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    private record SeedPreviewQueryCacheKey(long seed, Identifier dimensionIdentifier, int dimension, int minX, int maxX, int minZ, int maxZ, int generatorFlags, int textureWidth, int textureHeight, int mcVersion, boolean terrainEnabled, int biomeY, int settingsHash) {
    }

    private record FeatureIconHitbox(SeedMapperFeature feature, String datapackStructureId, int x, int y, int size) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        }
    }

    private record SeedMapperMarkerHitbox(SeedMapperMarker marker, String worldKey, float centerX, float centerY, int width, int height) {
        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= centerX - width / 2.0F && mouseX <= centerX + width / 2.0F
                    && mouseY >= centerY - height / 2.0F && mouseY <= centerY + height / 2.0F;
        }
    }

    private record SeedMapperQueryCacheKey(long seed, int dimension, int generatorFlags, int minX, int maxX, int minZ, int maxZ, boolean lootOnly, int enabledFeatureHash, int datapackHash, int customSaltHash, String lootSearch, String datapackWorldKey) {
    }

    private record NewOldChunkRenderRect(float minX, float minZ, float maxX, float maxZ, int color) {
    }

    private record BuildNewOldChunkRectsResult(List<NewOldChunkRenderRect> rects, int visibleChunks, int generatedCells) {
    }

    public void renderBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, this.getWidth(), this.getHeight(), 0xFF000000);
    }

    protected void overlayBackground(GuiGraphicsExtractor graphics, int startY, int endY, int startAlpha, int endAlpha) {
        int colorBase = 0x404040;
        int colorStart = (startAlpha << 24) | colorBase;
        int colorEnd = (endAlpha << 24) | colorBase;
        float renderedTextureSize = 32.0F;
        VoxelMapGuiGraphics.blitFloatGradient(graphics, RenderPipelines.GUI_TEXTURED, VoxelConstants.getOptionsBackgroundTexture(), 0, startY, this.getWidth(), endY, 0, this.width / renderedTextureSize, 0, endY / renderedTextureSize, colorStart, colorEnd);
    }

    @Override
    public void tick() {
    }

    @Override
    public void removed() {
        persistentMap.suspendTerrainOverview();
        biomeViews.clear(); biomeDiskSource = null;
        trailTiles.clear();
        trailViews.clear(); areaViews.clear(); geometryViews.clear(); rasterLayers.clear();
        synchronized (this.closedLock) {
            this.closed = true;
            this.persistentMap.getRegions(0, -1, 0, -1);
            this.regions = NO_REGIONS;
        }
        if (this.seedPreviewFuture != null) {
            this.seedPreviewFuture.cancel(true);
            if (this.seedPreviewProgress != null) this.seedPreviewProgress.cancel();
            this.seedPreviewFuture = null;
        }
        minecraft.getTextureManager().release(seedPreviewTextureLocation);
        this.seedPreviewTexture = null;
        synchronized (this.seedPreviewLock) {
            this.seedPreviewDisplayedKey = null;
            this.seedPreviewPendingKey = null;
            this.seedPreviewPendingPixels = null;
            this.seedPreviewLoading = false;
            this.seedPreviewCache.clear();
        }
        VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().flushStorage();
    }

    private void createPopup(int x, int y, int directX, int directY) {
        selectedSeedMapperMarker = null;
        selectedSeedMapperWorldKey = null;
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        if (selectedPlot != null) {
            entries.add(new Popup.PopupEntry("Delete Plot", 17, true, true));
            boolean oppositeVisible = selectedPlot.showOppositeDimension() && isOppositeDimension(selectedPlot.dimension(), getViewedDimensionIdentifier().toString());
            entries.add(new Popup.PopupEntry(oppositeVisible ? "Hide in Opposite Dimension" : "Show in Opposite Dimension", 18, true, true));
            entries.add(new Popup.PopupEntry("Thickness: " + (selectedPlot.thickness() + 1), 19, true, true));
            entries.add(new Popup.PopupEntry("Color: " + new String[]{"Gold", "Cyan", "Green", "Pink", "Purple", "Orange"}[Math.floorMod(selectedPlot.color(), 6)], 20, true, true));
            entries.add(new Popup.PopupEntry("Duplicate Plot", 21, true, true));
            this.createPopup(x, y, directX, directY, 120, entries);
            return;
        }
        float cursorX = directX;
        float cursorY = directY - this.top * this.guiToDirectMouse;
        float cursorCoordX;
        float cursorCoordZ;
        if (this.oldNorth) {
            cursorCoordX = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
            cursorCoordZ = -(cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
        } else {
            cursorCoordX = cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
            cursorCoordZ = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
        }

        Popup.PopupEntry entry;
        if (selectedWaypoint != null && this.waypointManager.getWaypoints().contains(selectedWaypoint)) {
            entry = new Popup.PopupEntry(I18n.get("selectServer.edit"), 4, true, true);
            entries.add(entry);
            entry = new Popup.PopupEntry(I18n.get("selectServer.delete"), 5, true, true);
            entries.add(entry);
            entry = new Popup.PopupEntry(I18n.get(selectedWaypoint != this.waypointManager.getHighlightedWaypoint() ? "minimap.waypoints.highlight" : "minimap.waypoints.removeHighlight"), 1, true, true);
        } else {
            entry = new Popup.PopupEntry(I18n.get("minimap.waypoints.newWaypoint"), 0, true, mapOptions.waypointsAllowed);
            entries.add(entry);
            entry = new Popup.PopupEntry(I18n.get(selectedWaypoint == null ? "minimap.waypoints.highlight" : "minimap.waypoints.removeHighlight"), 1, true, mapOptions.waypointsAllowed);
        }
        entries.add(entry);
        addTransportMenuEntry(entries);
        entry = new Popup.PopupEntry(I18n.get("minimap.waypoints.share"), 2, false, true);
        entries.add(entry);
        entries.add(new Popup.PopupEntry("Plot", 16, true, true));
        entry = new Popup.PopupEntry("Export Visible SeedMap", 6, true, seedMapperOptions.enabled);
        entries.add(entry);
        entry = new Popup.PopupEntry("Recenter Map", 12, true, true);
        entries.add(entry);
        if (BaritoneHelper.isPresent()) {
            entries.add(new Popup.PopupEntry("Pathfind Here", 13, true, true));
        }

        this.createPopup(x, y, directX, directY, 60, entries);
        if (VoxelConstants.DEBUG) {
            persistentMap.debugLog((int) cursorCoordX, (int) cursorCoordZ, getViewedDimensionIdentifier());
        }
    }

    private void createStructurePopup(int x, int y, int directX, int directY) {
        if (selectedSeedMapperMarker == null) {
            return;
        }
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        boolean completed = selectedSeedMapperWorldKey != null
                && seedMapperOptions.isCompleted(selectedSeedMapperWorldKey, selectedSeedMapperMarker.feature(), selectedSeedMapperMarker.blockX(), selectedSeedMapperMarker.blockZ());
        boolean highlightActive = isMarkerHighlighted(selectedSeedMapperMarker);
        if (selectedSeedMapperAssociatedWaypoint != null && !highlightActive) {
            entries.add(new Popup.PopupEntry(I18n.get("selectServer.edit"), 4, true, true));
            entries.add(new Popup.PopupEntry(I18n.get("selectServer.delete"), 5, true, true));
        } else {
            entries.add(new Popup.PopupEntry("Create Waypoint", 7, true, true));
        }
        entries.add(new Popup.PopupEntry(I18n.get(highlightActive ? "minimap.waypoints.removeHighlight" : "minimap.waypoints.highlight"), 1, true, true));
        addTransportMenuEntry(entries);
        entries.add(new Popup.PopupEntry(I18n.get("minimap.waypoints.share"), 2, true, true));
        entries.add(new Popup.PopupEntry(completed ? "Mark Incomplete" : "Mark Complete", 8, true, true));
        if (SeedMapperLootService.hasPredictableLoot(selectedSeedMapperMarker.feature())) {
            entries.add(new Popup.PopupEntry("Open Loot", 9, true, true));
        }
        if (selectedSeedMapperMarker.feature() == SeedMapperFeature.TRIAL_CHAMBERS) {
            entries.add(new Popup.PopupEntry("Predict Vault Loot", 22, true, true));
        }
        if (BaritoneHelper.isPresent()
                && (selectedSeedMapperMarker.feature() == SeedMapperFeature.IRON_ORE_VEIN
                    || selectedSeedMapperMarker.feature() == SeedMapperFeature.COPPER_ORE_VEIN)) {
            entries.add(new Popup.PopupEntry("Mine with Baritone", 14, true, true));
        }
        this.createPopup(x, y, directX, directY, 110, entries);
    }

    private Waypoint getHoveredWaypoint() {
        if (!mapOptions.waypointsAllowed) {
            return null;
        }

        return hoverdWaypoint;
    }

    @Override
    public void popupAction(Popup popup, int action) {
        int mouseDirectX = popup.getClickedDirectX();
        int mouseDirectY = popup.getClickedDirectY();
        float cursorX = mouseDirectX;
        float cursorY = mouseDirectY - this.top * this.guiToDirectMouse;
        float cursorCoordX;
        float cursorCoordZ;
        if (this.oldNorth) {
            cursorCoordX = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
            cursorCoordZ = -(cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
        } else {
            cursorCoordX = cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
            cursorCoordZ = cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
        }

        int x = (int) Math.floor(cursorCoordX);
        int z = (int) Math.floor(cursorCoordZ);
        int y = this.persistentMap.getHeightAt(x, z, getViewedDimensionIdentifier());
        this.editClicked = false;
        this.addClicked = false;
        this.deleteClicked = false;
        switch (action) {
            case 16 -> {
                plotMode = true;
                plotStartSet = false;
                plotClickHandled = false;
                lastPlotInputMs = 0L;
                lastPlotInputX = Double.NaN;
                lastPlotInputY = Double.NaN;
                ignoreNextPlotRelease = true;
                ignoredPlotReleaseX = popup.getLastClickX();
                ignoredPlotReleaseY = popup.getLastClickY();
                editingPlot = null;
                placingDuplicatePlot = null;
                selectedPlot = null;
            }
            case 17 -> {
                if (selectedPlot != null) {
                    plotManager.remove(selectedPlot);
                    selectedPlot = null;
                }
            }
            case 18 -> {
                if (selectedPlot != null) {
                    plotManager.replace(selectedPlot, new PlotManager.Plot(selectedPlot.x1(), selectedPlot.z1(), selectedPlot.x2(), selectedPlot.z2(),
                            selectedPlot.dimension(), !selectedPlot.showOppositeDimension(), selectedPlot.thickness(), selectedPlot.color()));
                    selectedPlot = null;
                }
            }
            case 19 -> {
                if (selectedPlot != null) {
                    plotManager.replace(selectedPlot, new PlotManager.Plot(selectedPlot.x1(), selectedPlot.z1(), selectedPlot.x2(), selectedPlot.z2(),
                            selectedPlot.dimension(), selectedPlot.showOppositeDimension(), (selectedPlot.thickness() + 1) % 4, selectedPlot.color()));
                    selectedPlot = null;
                }
            }
            case 20 -> {
                if (selectedPlot != null) {
                    plotManager.replace(selectedPlot, new PlotManager.Plot(selectedPlot.x1(), selectedPlot.z1(), selectedPlot.x2(), selectedPlot.z2(),
                            selectedPlot.dimension(), selectedPlot.showOppositeDimension(), selectedPlot.thickness(), (selectedPlot.color() + 1) % 6));
                    selectedPlot = null;
                }
            }
            case 21 -> {
                if (selectedPlot != null) {
                    placingDuplicatePlot = selectedPlot;
                    selectedPlot = null;
                }
            }
            case 0 -> {
                if (selectedWaypoint != null) {
                    x = getWaypointXInViewedDimension(selectedWaypoint);
                    z = getWaypointZInViewedDimension(selectedWaypoint);
                }
                this.addClicked = true;
                float r;
                float g;
                float b;
                if (this.waypointManager.getWaypoints().isEmpty()) {
                    r = 0.0F;
                    g = 1.0F;
                    b = 0.0F;
                } else {
                    r = this.generator.nextFloat();
                    g = this.generator.nextFloat();
                    b = this.generator.nextFloat();
                }
                TreeSet<DimensionContainer> dimensions = new TreeSet<>();
                DimensionContainer viewedDimension = getViewedDimensionContainer();
                if (viewedDimension != null) {
                    dimensions.add(viewedDimension);
                }
                y = terrainHighlightY(x, z);
                this.newWaypoint = new Waypoint("", x, z, y, true, r, g, b, "", VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions);
                minecraft.gui.setScreen(new GuiAddWaypoint(this, this.newWaypoint, false));
            }
            case 1 -> {
                if (selectedSeedMapperMarker != null) {
                    if (isMarkerHighlighted(selectedSeedMapperMarker)) {
                        deleteSeedMapperHighlight(selectedSeedMapperMarker);
                        this.waypointManager.setHighlightedWaypoint(null, false);
                    } else {
                        TreeSet<DimensionContainer> dimensions2 = new TreeSet<>();
                        DimensionContainer viewedDimension = getViewedDimensionContainer();
                        if (viewedDimension != null) {
                            dimensions2.add(viewedDimension);
                        }
                        int markerX = selectedSeedMapperMarker.blockX();
                        int markerZ = selectedSeedMapperMarker.blockZ();
                        Waypoint highlightWaypoint = new Waypoint(seedMapperHighlightName(selectedSeedMapperMarker), markerX, markerZ, terrainHighlightY(markerX, markerZ), true, 1.0F, 0.0F, 0.0F, "target", VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions2);
                        this.waypointManager.addWaypoint(highlightWaypoint);
                        this.waypointManager.setHighlightedWaypoint(highlightWaypoint, false);
                        rememberSeedMapperHighlight(selectedSeedMapperMarker, highlightWaypoint.name);
                        selectedSeedMapperWaypoint = highlightWaypoint;
                        selectedWaypoint = highlightWaypoint;
                    }
                } else if (selectedWaypoint != null) {
                    this.waypointManager.setHighlightedWaypoint(selectedWaypoint, true);
                } else {
                    y = terrainHighlightY(x, z);
                    TreeSet<DimensionContainer> dimensions2 = new TreeSet<>();
                    DimensionContainer viewedDimension = getViewedDimensionContainer();
                    if (viewedDimension != null) {
                        dimensions2.add(viewedDimension);
                    }
                    Waypoint highlightWaypoint = new Waypoint("", x, z, y, true, 1.0F, 0.0F, 0.0F, "", VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions2);
                    this.waypointManager.setHighlightedWaypoint(highlightWaypoint, false);
                    selectedSeedMapperWaypoint = highlightWaypoint;
                    selectedWaypoint = highlightWaypoint;
                }
            }
            case 2 -> {
                if (selectedWaypoint != null) {
                    CommandUtils.sendWaypoint(selectedWaypoint);
                } else {
                    y = y > VoxelConstants.getPlayer().level().getMinY() ? y : 64;
                    CommandUtils.sendCoordinate(x, y, z);
                }
            }
            case 3 -> {
                if (selectedWaypoint == null) {
                    if (y < VoxelConstants.getPlayer().level().getMinY()) {
                        y = (!(VoxelConstants.getPlayer().level().dimensionType().hasCeiling()) ? VoxelConstants.getPlayer().level().getMaxY() : 64);
                    }
                    VoxelConstants.playerRunTeleportCommand(x, y, z);
                    break;
                }

                y = selectedWaypoint.getY() > VoxelConstants.getPlayer().level().getMinY() ? selectedWaypoint.getY() : (!(VoxelConstants.getPlayer().level().dimensionType().hasCeiling()) ? VoxelConstants.getPlayer().level().getMaxY() : 64);
                VoxelConstants.playerRunTeleportCommand(getWaypointXInViewedDimension(selectedWaypoint), y, getWaypointZInViewedDimension(selectedWaypoint));
            }
            case 4 -> {
                if (selectedWaypoint != null) {
                    this.editClicked = true;
                    minecraft.gui.setScreen(new GuiAddWaypoint(this, selectedWaypoint, true));
                }
            }
            case 5 -> {
                if (selectedWaypoint != null) {
                    pendingDeleteWaypoint = selectedWaypoint;
                    if (mapOptions.confirmWaypointDelete) {
                        createDeleteConfirmationPopup(popup);
                    } else {
                        deleteSelectedWaypoint();
                    }
                }
            }
            case 6 -> {
                exportVisibleSeedMap();
            }
            case 7 -> {
                if (selectedSeedMapperMarker != null) {
                    createSeedMapperWaypoint(selectedSeedMapperMarker);
                }
            }
            case 8 -> {
                if (selectedSeedMapperMarker != null && selectedSeedMapperWorldKey != null) {
                    toggleSeedMapperMarkerCompleted(selectedSeedMapperMarker, selectedSeedMapperWorldKey);
                }
            }
            case 9 -> {
                if (selectedSeedMapperMarker != null) {
                    seedMapperVaultLootWidget = null;
                    long seed;
                    try {
                        seed = resolveWorldMapSeed();
                    } catch (IllegalArgumentException ignored) {
                        break;
                    }

                    int dimension = getCurrentCubiomesDimension();
                    List<SeedMapperChestLootData> chestData = SeedMapperLootService.buildStructureChestLoot(
                            seed,
                            dimension,
                            SeedMapperCompat.getMcVersion(),
                            0,
                            selectedSeedMapperMarker.feature(),
                            selectedSeedMapperMarker.blockX(),
                            selectedSeedMapperMarker.blockZ()
                    );
                    if (chestData.isEmpty()) {
                        minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("SeedMapper", "No chest loot data available for this structure."));
                    } else {
                        int widgetX = Mth.clamp((int) popup.getClickedDirectX() / (int) this.guiToDirectMouse + 10, 4, this.width - SeedMapperChestLootWidget.WIDTH - 4);
                        int widgetY = Mth.clamp((int) (popup.getClickedDirectY() / this.guiToDirectMouse) + 10, this.top + 4, this.bottom - SeedMapperChestLootWidget.HEIGHT - 4);
                        seedMapperChestLootWidget = new SeedMapperChestLootWidget(widgetX, widgetY, chestData);
                    }
                }
            }
            case 10 -> deleteSelectedWaypoint();
            case 11 -> pendingDeleteWaypoint = null;
            case 12 -> {
                recenterForViewedDimension();
                clearPopups();
                if (minecraft.gui.screen() != this) {
                    minecraft.gui.setScreen(this);
                }
            }
            case 13 -> {
                if (BaritoneHelper.pathTo(x, z)) {
                    minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("Baritone", "Pathing to " + x + ", " + z));
                }
            }
            case 14 -> {
                if (selectedSeedMapperMarker != null) {
                    int oreType = selectedSeedMapperMarker.feature() == SeedMapperFeature.IRON_ORE_VEIN ? -1
                            : selectedSeedMapperMarker.feature() == SeedMapperFeature.COPPER_ORE_VEIN ? 1 : 0;
                    SeedMapperCommandHandler.mineOreVeinsAround(selectedSeedMapperMarker.blockX(), selectedSeedMapperMarker.blockZ(), 2, oreType);
                }
            }
            case 22 -> {
                if (selectedSeedMapperMarker != null && selectedSeedMapperMarker.feature() == SeedMapperFeature.TRIAL_CHAMBERS) {
                    int directX = popup.getClickedDirectX();
                    int directY = popup.getClickedDirectY();
                    openVaultLootWidgetAt((int) (directX / this.guiToDirectMouse) + 10,
                            (int) (directY / this.guiToDirectMouse) + 10);
                }
            }
            case 15 -> openTransportPopup(popup);
            default -> {
                if (action >= 1000) {
                    int shortcutIndex = action - 1000;
                    if (shortcutIndex < mapOptions.transportShortcuts.size()) {
                        if (selectedWaypoint != null) {
                            x = getWaypointXInViewedDimension(selectedWaypoint);
                            z = getWaypointZInViewedDimension(selectedWaypoint);
                            y = selectedWaypoint.getY() > VoxelConstants.getPlayer().level().getMinY()
                                    ? selectedWaypoint.getY() : terrainHighlightY(x, z);
                        } else if (y < VoxelConstants.getPlayer().level().getMinY()) {
                            y = terrainHighlightY(x, z);
                        }
                        VoxelConstants.playerRunTransportCommand(shortcutIndex, x, y, z);
                        clearPopups();
                    }
                } else {
                    VoxelConstants.getLogger().warn("unimplemented command");
                }
            }
        }

        if (action >= 7 && action <= 9) {
            selectedSeedMapperMarker = null;
            selectedSeedMapperWorldKey = null;
            selectedSeedMapperWaypoint = null;
            selectedSeedMapperAssociatedWaypoint = null;
        }

    }

    private void drawPlots(GuiGraphicsExtractor graphics, float cursorX, float cursorZ) {
        float previewThickness = Math.max(1.0F, 2.0F / Math.max(0.0000001F, this.mapToGui));
        for (PlotManager.Plot plot : plotManager.getPlots()) {
            if (!isPlotVisibleInViewedDimension(plot)) continue;
            double[] viewPlot = plotCoordinatesForView(plot);
            float thickness = Math.max(1.0F, (2.0F + plot.thickness()) / Math.max(0.0000001F, this.mapToGui));
            float x1 = (float) viewPlot[0];
            float z1 = (float) viewPlot[1];
            float x2 = (float) viewPlot[2];
            float z2 = (float) viewPlot[3];
            if (plot == editingPlot && editingPlotEndpoint == 2) {
                x2 = cursorX;
                z2 = cursorZ;
            }
            if (plot == editingPlot && editingPlotEndpoint == 1) {
                x1 = cursorX;
                z1 = cursorZ;
            }
            int color = new int[]{0xFFFFD21F, 0xFF4DD2FF, 0xFF66E36F, 0xFFFF66C4, 0xFFB980FF, 0xFFFF8033}[Math.floorMod(plot.color(), 6)];
            appendThickInterpolatedLine(x1, z1, x2, z2, thickness + 2.0F / Math.max(0.0000001F, this.mapToGui), 0xDD000000);
            appendThickInterpolatedLine(x1, z1, x2, z2, thickness, color);
        }
        if (plotStartSet) {
            float endpointSize = Math.min(128.0F, Math.max(2.0F, 6.0F / Math.max(0.0000001F, this.mapToGui)));
            float endpointHalf = endpointSize / 2.0F;
            appendExploredQuad((float) plotStartX - endpointHalf - 1.0F, (float) plotStartZ - endpointHalf - 1.0F,
                    (float) plotStartX + endpointHalf + 1.0F, (float) plotStartZ + endpointHalf + 1.0F, 0xFF000000);
            appendExploredQuad((float) plotStartX - endpointHalf, (float) plotStartZ - endpointHalf,
                    (float) plotStartX + endpointHalf, (float) plotStartZ + endpointHalf, 0xFFFFF27A);
            appendThickInterpolatedLine((float) plotStartX, (float) plotStartZ, cursorX, cursorZ, previewThickness, 0xFFFFF27A);
        }
        if (placingDuplicatePlot != null && isPlotVisibleInViewedDimension(placingDuplicatePlot)) {
            double[] original = plotCoordinatesForView(placingDuplicatePlot);
            float dx = cursorX - (float) ((original[0] + original[2]) / 2.0D);
            float dz = cursorZ - (float) ((original[1] + original[3]) / 2.0D);
            float x1 = (float) original[0] + dx;
            float z1 = (float) original[1] + dz;
            float x2 = (float) original[2] + dx;
            float z2 = (float) original[3] + dz;
            appendThickInterpolatedLine(x1, z1, x2, z2, previewThickness + 2.0F / Math.max(0.0000001F, this.mapToGui), 0xAA000000);
            appendThickInterpolatedLine(x1, z1, x2, z2, previewThickness, 0xAAFFFFFF);
        }
        flushExploredQuads(graphics);
    }

    private void handlePlotModeClick(double guiX, double guiY) {
        double[] mapPoint = mapPointFromGui(guiX, guiY);
        if (!plotStartSet) {
            plotStartX = mapPoint[0];
            plotStartZ = mapPoint[1];
            plotStartSet = true;
            return;
        }

        plotManager.add(new PlotManager.Plot(plotStartX, plotStartZ, mapPoint[0], mapPoint[1],
                getViewedDimensionIdentifier().toString(), false, 0, 0));
        plotStartSet = false;
        plotMode = false;
    }

    private void handlePlotInput(double guiX, double guiY) {
        long now = System.currentTimeMillis();
        if (now - lastPlotInputMs <= 250L
                && Math.abs(guiX - lastPlotInputX) <= 3.0D
                && Math.abs(guiY - lastPlotInputY) <= 3.0D) {
            plotClickHandled = true;
            return;
        }
        lastPlotInputMs = now;
        lastPlotInputX = guiX;
        lastPlotInputY = guiY;
        handlePlotModeClick(guiX, guiY);
        plotClickHandled = true;
    }

    private double[] mapPointFromGui(double guiX, double guiY) {
        float cursorX = (float) (guiX * this.guiToDirectMouse);
        float cursorY = (float) ((guiY - this.top) * this.guiToDirectMouse);
        if (this.oldNorth) {
            return new double[]{cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap),
                    -(cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap))};
        }
        return new double[]{cursorX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap),
                cursorY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap)};
    }

    private boolean isInMap(int x, int y) {
        return x >= 0 && x < this.width && y > this.top && y < this.bottom;
    }

    private PlotManager.Plot findPlotAt(double x, double z) {
        double threshold = Math.max(5.0 / Math.max(0.0001, this.mapToGui), 2.0);
        for (int i = plotManager.getPlots().size() - 1; i >= 0; i--) {
            PlotManager.Plot plot = plotManager.getPlots().get(i);
            if (!isPlotVisibleInViewedDimension(plot)) continue;
            double[] viewPlot = plotCoordinatesForView(plot);
            if (distanceToSegment(x, z, viewPlot[0], viewPlot[1], viewPlot[2], viewPlot[3]) <= threshold) return plot;
        }
        return null;
    }

    private int findPlotEndpointAt(double x, double z) {
        PlotManager.Plot plot = findPlotAt(x, z);
        if (plot == null) return 0;
        double[] viewPlot = plotCoordinatesForView(plot);
        double threshold = Math.max(8.0 / Math.max(0.0001, this.mapToGui), 3.0);
        double first = Math.hypot(x - viewPlot[0], z - viewPlot[1]);
        double second = Math.hypot(x - viewPlot[2], z - viewPlot[3]);
        if (first <= threshold || second <= threshold) return first <= second ? 1 : 2;
        return 0;
    }

    private double distanceToSegment(double x, double z, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0.0) return Math.hypot(x - x1, z - z1);
        double t = Mth.clamp((float) (((x - x1) * dx + (z - z1) * dz) / lengthSquared), 0.0F, 1.0F);
        return Math.hypot(x - (x1 + t * dx), z - (z1 + t * dz));
    }

    private boolean isPlotVisibleInViewedDimension(PlotManager.Plot plot) {
        String viewed = getViewedDimensionIdentifier().toString();
        return viewed.equals(plot.dimension()) || (plot.showOppositeDimension() && isOppositeDimension(plot.dimension(), viewed));
    }

    private boolean isOppositeDimension(String source, String viewed) {
        boolean sourceOverworld = "minecraft:overworld".equals(source);
        boolean sourceNether = "minecraft:the_nether".equals(source) || "minecraft:nether".equals(source);
        boolean viewedOverworld = "minecraft:overworld".equals(viewed);
        boolean viewedNether = "minecraft:the_nether".equals(viewed) || "minecraft:nether".equals(viewed);
        return (sourceOverworld && viewedNether) || (sourceNether && viewedOverworld);
    }

    /** Returns x1,z1,x2,z2 and the source-to-view coordinate multiplier. */
    private double[] plotCoordinatesForView(PlotManager.Plot plot) {
        String viewed = getViewedDimensionIdentifier().toString();
        double multiplier = 1.0D;
        if (!viewed.equals(plot.dimension())) {
            try {
                multiplier = plotSourceCoordinateScale(plot.dimension()) / viewedCoordinateScale();
            } catch (Exception ignored) {
            }
        }
        return new double[]{plot.x1() * multiplier, plot.z1() * multiplier,
                plot.x2() * multiplier, plot.z2() * multiplier, multiplier};
    }

    private double plotSourceCoordinateScale(String dimension) {
        if ("minecraft:overworld".equals(dimension)) return 1.0D;
        if ("minecraft:the_nether".equals(dimension) || "minecraft:nether".equals(dimension)) return 8.0D;
        try {
            DimensionContainer source = VoxelConstants.getVoxelMapInstance().getDimensionManager()
                    .getDimensionContainerByIdentifier(Identifier.parse(dimension));
            return coordinateScaleForDimension(source);
        } catch (Exception ignored) {
            return 1.0D;
        }
    }

    private void openTransportPopup(Popup source) {
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        for (int i = 0; i < mapOptions.transportShortcuts.size(); i++) {
            MapSettingsManager.TransportShortcut shortcut = mapOptions.transportShortcuts.get(i);
            if (shortcut.visible && shortcut.name != null && !shortcut.name.isBlank()) {
                entries.add(new Popup.PopupEntry(shortcut.name, 1000 + i, true, true));
            }
        }
        if (entries.isEmpty()) entries.add(new Popup.PopupEntry("No visible shortcuts", -1, false, false));
        // Anchor the submenu to the parent popup's right edge so its hover area
        // cannot overlap and steal highlighting from the parent menu.
        createPopup(source.getX() + source.getWidth() + 2, source.getY(), source.getClickedDirectX(), source.getClickedDirectY(), 150, entries);
    }

    private void addTransportMenuEntry(ArrayList<Popup.PopupEntry> entries) {
        if (mapOptions.transportShowAllInMainMenu) {
            for (int i = 0; i < mapOptions.transportShortcuts.size(); i++) {
                MapSettingsManager.TransportShortcut shortcut = mapOptions.transportShortcuts.get(i);
                if (shortcut.visible && shortcut.name != null && !shortcut.name.isBlank()) {
                    entries.add(new Popup.PopupEntry(shortcut.name, 1000 + i, true, true));
                }
            }
            return;
        }

        int visibleCount = 0;
        int visibleIndex = -1;
        for (int i = 0; i < mapOptions.transportShortcuts.size(); i++) {
            MapSettingsManager.TransportShortcut shortcut = mapOptions.transportShortcuts.get(i);
            if (shortcut.visible && shortcut.name != null && !shortcut.name.isBlank()) {
                visibleCount++;
                visibleIndex = i;
            }
        }

        if (visibleCount == 1) {
            entries.add(new Popup.PopupEntry(mapOptions.transportShortcuts.get(visibleIndex).name, 1000 + visibleIndex, true, true));
        } else {
            entries.add(new Popup.PopupEntry("Transport", 15, false, visibleCount > 0));
        }
    }

    @Override
    public boolean isEditing() {
        return this.editClicked;
    }

    @Override
    public void accept(boolean b) {
        if (this.deleteClicked) {
            this.deleteClicked = false;
            if (b) {
                deleteSelectedWaypoint();
            }
        }

        if (this.editClicked) {
            this.editClicked = false;
            if (b) {
                this.waypointManager.saveWaypoints();
            }
        }

        if (this.addClicked) {
            this.addClicked = false;
            if (b) {
                this.waypointManager.addWaypoint(this.newWaypoint);
            }
        }

        minecraft.gui.setScreen(this);
    }

    private void createDeleteConfirmationPopup(Popup source) {
        int popupX = source != null ? source.getX() + 1 : this.width / 2 - 45;
        int popupY = source != null ? source.getY() + 1 : this.height / 2 - 20;
        int directX = source != null ? source.getClickedDirectX() : (int) this.mouseX;
        int directY = source != null ? source.getClickedDirectY() : (int) this.mouseY;
        // Remove the previous context popup so the confirmation dialog receives all clicks.
        clearPopups();
        ArrayList<Popup.PopupEntry> entries = new ArrayList<>();
        entries.add(new Popup.PopupEntry("Confirm Delete?", -1, false, false));
        entries.add(new Popup.PopupEntry(I18n.get("selectServer.deleteButton"), 10, true, true));
        entries.add(new Popup.PopupEntry(I18n.get("gui.cancel"), 11, true, true));
        createPopup(popupX, popupY, directX, directY, 90, entries);
    }

    private void deleteSelectedWaypoint() {
        Waypoint toDelete = this.selectedWaypoint != null ? this.selectedWaypoint : this.pendingDeleteWaypoint;
        if (toDelete == null) {
            return;
        }

        this.waypointManager.deleteWaypoint(toDelete);
        if (this.selectedWaypoint == toDelete) {
            this.selectedWaypoint = null;
        }
        this.pendingDeleteWaypoint = null;
    }

    private int textWidth(String string) {
        Integer width = waypointTextWidths.get(string);
        if (width == null) {
            width = minecraft.font.width(string);
            waypointTextWidths.put(string, width);
            if (waypointTextWidths.size() > 4096) waypointTextWidths.remove(waypointTextWidths.keySet().iterator().next());
        }
        return width;
    }

    private int textWidth(Component text) {
        return minecraft.font.width(text);
    }

    private void write(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean shadow) {
        write(graphics, Component.nullToEmpty(text), x, y, color, shadow);
    }

    private void write(GuiGraphicsExtractor graphics, Component text, float x, float y, int color, boolean shadow) {
        graphics.text(minecraft.font, text, (int) x, (int) y, color, shadow);
    }

    private void writeCentered(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean shadow) {
        writeCentered(graphics, Component.nullToEmpty(text), x, y, color, shadow);
    }

    private void writeCentered(GuiGraphicsExtractor graphics, Component text, float x, float y, int color, boolean shadow) {
        graphics.text(minecraft.font, text, (int) x - (textWidth(text) / 2), (int) y, color, shadow);
    }

    public void exportVisibleSeedMap() {
        PreviewBounds visibleBounds = getVisibleWorldBounds();
        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            return;
        }
        ExportPlayerCoords exportPlayerCoords = getViewedExportPlayerCoords(visibleBounds);
        SeedMapperCommandHandler.exportBounds(
                visibleBounds.minX(),
                visibleBounds.maxX(),
                visibleBounds.minZ(),
                visibleBounds.maxZ(),
                "persistent_map_visible",
                dimension,
                currentSeedMapperWorldKey(),
                exportPlayerCoords.x(),
                exportPlayerCoords.z()
        );
    }
}

