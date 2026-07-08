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
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperOptions;
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
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCompat;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperImportedDatapackManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperNative;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.textures.TextureAtlas;
import com.mamiyaotaru.voxelmap.util.BackgroundImageInfo;
import com.mamiyaotaru.voxelmap.util.BiomeMapData;
import com.mamiyaotaru.voxelmap.util.BiomeRepository;
import com.mamiyaotaru.voxelmap.util.CellGrid;
import com.mamiyaotaru.voxelmap.util.ColorUtils;
import com.mamiyaotaru.voxelmap.util.CommandUtils;
import com.mamiyaotaru.voxelmap.util.AppChatMessages;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.DynamicMutableTexture;
import com.mamiyaotaru.voxelmap.util.EasingUtils;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.ImageUtils;
import com.mamiyaotaru.voxelmap.util.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.util.TextUtils;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private boolean lastStill;
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
    private float scScale = 1.0F;
    private float guiToMap = 2.0F;
    private float mapToGui = 0.5F;
    private float mouseDirectToMap = 1.0F;
    private float guiToDirectMouse = 2.0F;
    private static boolean gotSkin;
    private boolean closed;
    private CachedRegion[] regions = new CachedRegion[0];
    BackgroundImageInfo backGroundImageInfo;
    private final BiomeMapData biomeMapData = new BiomeMapData(760, 360);
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
    private PopupGuiButton buttonRealmView;
    private PopupGuiButton buttonSeedPreview;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final Identifier voxelmapSkinLocation = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "persistentmap/playerskin");
    private final Identifier crosshairResource = Identifier.parse("textures/gui/sprites/hud/crosshair.png");
    private final Identifier seedMapperDirectionArrowResource = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/seedmapper/arrow.png");
    private final Identifier seedPreviewTextureLocation = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "persistentmap/seedpreview");
    private final List<FeatureIconHitbox> seedMapperIconHitboxes = new ArrayList<>();
    private final List<SeedMapperMarkerHitbox> seedMapperMarkerHitboxes = new ArrayList<>();
    private final Set<Long> visibleSeedMapperMarkerCoords = new HashSet<>();
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
    private static final int SEED_PREVIEW_MIN_TEXTURE_WIDTH = 256;
    private static final int SEED_PREVIEW_MIN_TEXTURE_HEIGHT = 128;
    private static final int SEED_PREVIEW_SAMPLE_BLOCK_Y = 64;
    private static final int SEED_PREVIEW_CONTOUR_INTERVAL = 16;
    private static final long SEED_PREVIEW_REQUEST_INTERVAL_MOVING_MS = 125L;
    private static final long SEED_PREVIEW_REQUEST_INTERVAL_STILL_MS = 50L;
    private DynamicMutableTexture seedPreviewTexture;
    private SeedPreviewQueryCacheKey seedPreviewDisplayedKey;
    private SeedPreviewQueryCacheKey seedPreviewPendingKey;
    private int[] seedPreviewPendingPixels;
    private Future<?> seedPreviewFuture;
    private long seedPreviewLastRequestMs = 0L;
    private boolean seedPreviewLoading = false;
    private boolean seedPreviewDrewThisFrame = false;
    private long seedPreviewLoadingStartedMs = 0L;
    private static final long SEED_PREVIEW_LOADING_DEBOUNCE_MS = 500L;
    private int seedBiomeNameQuartX = Integer.MIN_VALUE;
    private int seedBiomeNameQuartZ = Integer.MIN_VALUE;
    private long seedBiomeNameSeed = 0L;
    private int seedBiomeNameDimension = Integer.MIN_VALUE;
    private String seedBiomeNameCached = "";
    private final Object seedPreviewLock = new Object();
    private int seedPreviewCacheLimit = 8;
    private final LinkedHashMap<SeedPreviewQueryCacheKey, int[]> seedPreviewCache =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<SeedPreviewQueryCacheKey, int[]> eldest) {
                    return size() > Math.max(1, seedPreviewCacheLimit);
                }
            };
    private static final int SEED_PREVIEW_WORKER_THREADS =
            Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() - 1, 8));
    private static final ExecutorService seedPreviewSampler =
            Executors.newFixedThreadPool(SEED_PREVIEW_WORKER_THREADS, r -> {
                Thread t = new Thread(r, "Voxelmap SeedPreview Sampler");
                t.setDaemon(true);
                return t;
            });
    private final Map<Integer, Integer> seedPreviewBiomeColorCache = new HashMap<>();
    private final Map<Integer, Boolean> seedPreviewOceanicCache = new HashMap<>();
    private long exploredLinesLastQueryMs = 0L;
    private long exploredLinesLastDataVersion = Long.MIN_VALUE;
    private ExploredLinesQueryCacheKey exploredLinesLastQueryKey;
    private List<ChunkPos> exploredLinesLastResult = List.of();
    private CellGrid exploredLinesLastCellResult = new CellGrid(0, 0, 0, 0);
    private ExploredLineRenderCacheKey exploredLineRenderCacheKey;
    private ExploredLineMesher.Result exploredLineMesh = ExploredLineMesher.Result.EMPTY;
    private float[] exploredQuadCoords = new float[4096];
    private int[] exploredQuadColors = new int[1024];
    private int exploredQuadCount = 0;
    private final List<PlayerLayerStatusHitbox> playerLayerStatusHitboxes = new ArrayList<>();
    private final List<WaypointLabelBounds> waypointLabelBounds = new ArrayList<>();
    private final List<PendingWaypointLabel> pendingWaypointLabels = new ArrayList<>();
    private final Map<Long, WaypointClusterData> waypointClusters = new HashMap<>();
    private NewOldChunkOverlayRenderCacheKey newOldChunkOverlayRenderCacheKey;
    private List<NewOldChunkRenderRect> newOldChunkOldRects = List.of();
    private List<NewOldChunkRenderRect> newOldChunkNewRects = List.of();
    private long newOldChunkLastMotionMs = 0L;
    private long newOldChunkCacheHits = 0L;
    private long newOldChunkCacheMisses = 0L;
    private long newOldChunkLastDebugLogMs = 0L;
    private int newOldChunkLastOldReturned = 0;
    private int newOldChunkLastNewReturned = 0;
    private int newOldChunkLastVisibleOld = 0;
    private int newOldChunkLastVisibleNew = 0;
    private int newOldChunkLastCells = 0;
    private long newOldChunkLastRebuildTimeMs = 0L;
    private SeedMapperChestLootWidget seedMapperChestLootWidget;
    private Set<SeedMapperFeature> seedMapperAllFeaturesSaved;
    private boolean currentDragging;
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

        DynamicTexture texture = new DynamicTexture(() -> "Voxelmap player", ImageUtils.nativeImageFromBufferedImage(skinImage));
        texture.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        minecraft.getTextureManager().register(voxelmapSkinLocation, texture);
    }

    @Override
    public void init() {
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
        this.screenTitle = I18n.get("worldmap.title");
        this.buildWorldName();
        this.leftMouseButtonDown = false;
        this.sideMargin = 10;
        this.buttonCount = 6;
        this.buttonSeparation = 4;
        this.buttonWidth = (this.width - this.sideMargin * 2 - this.buttonSeparation * (this.buttonCount - 1)) / this.buttonCount;
        this.buttonWaypoints = new PopupGuiButton(this.sideMargin, this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("options.minimap.waypoints"), button -> minecraft.gui.setScreen(new GuiWaypoints(this)), this);
        this.addRenderableWidget(this.buttonWaypoints);
        this.multiworldButtonName = Component.translatable(VoxelConstants.isRealmServer() ? "menu.online" : "options.worldmap.multiworld");
        this.multiworldButtonNameRed = (Component.translatable(VoxelConstants.isRealmServer() ? "menu.online" : "options.worldmap.multiworld")).withStyle(ChatFormatting.RED);
        if (!minecraft.hasSingleplayerServer() && !VoxelConstants.getVoxelMapInstance().getWaypointManager().receivedAutoSubworldName()) {
            this.addRenderableWidget(this.buttonMultiworld = new PopupGuiButton(this.sideMargin + (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, this.multiworldButtonName, button -> minecraft.gui.setScreen(new GuiSubworldsSelect(this)), this));
        }

        this.buttonRealmView = this.addRenderableWidget(new PopupGuiButton(this.sideMargin + 2 * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("worldmap.realm.button", getDisplayedWorldMapDimensionName()), button -> cycleWorldMapDimensionView(), this));
        this.buttonSeedPreview = this.addRenderableWidget(new PopupGuiButton(this.sideMargin + 3 * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("worldmap.seedpreview.button", I18n.get(this.seedMapperOptions.worldMapSeedPreview ? "options.on" : "options.off")), button -> toggleSeedPreview(), this));
        this.addRenderableWidget(new PopupGuiButton(this.sideMargin + 4 * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("menu.options"), button -> minecraft.gui.setScreen(new GuiMinimapOptions(this)), this));
        this.addRenderableWidget(new PopupGuiButton(this.sideMargin + 5 * (this.buttonWidth + this.buttonSeparation), this.getHeight() - 26, this.buttonWidth, 20, Component.translatable("gui.done"), button -> this.onClose(), this));
        refreshWorldMapControlLabels();
        this.coordinateXInput = new EditBox(this.getFont(), this.sideMargin, 10, 68, 20, Component.literal("X"));
        this.coordinateZInput = new EditBox(this.getFont(), this.sideMargin + 74, 10, 68, 20, Component.literal("Z"));
        this.coordinateXInput.setMaxLength(12);
        this.coordinateZInput.setMaxLength(12);
        this.coordinateXInput.setHint(Component.literal("X"));
        this.coordinateZInput.setHint(Component.literal("Z"));
        this.coordinateXInput.setVisible(false);
        this.coordinateZInput.setVisible(false);
        this.coordinateXInput.active = false;
        this.coordinateZInput.active = false;
        this.addRenderableWidget(this.coordinateXInput);
        this.addRenderableWidget(this.coordinateZInput);
        this.top = 32;
        this.bottom = this.getHeight() - 32;
        this.waypointSearchInput = new EditBox(this.getFont(), this.getWidth() - this.sideMargin - WAYPOINT_SEARCH_BOX_WIDTH, this.getHeight() - 50, WAYPOINT_SEARCH_BOX_WIDTH, 20, Component.translatable("worldmap.waypointSearch"));
        this.waypointSearchInput.setMaxLength(48);
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
        this.lastStill = false;
        this.timeAtLastTick = System.currentTimeMillis();
        ensureSeedPreviewTextureSize(resolveSeedPreviewTextureWidth(), resolveSeedPreviewTextureHeight());
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
                this.seedPreviewFuture.cancel(false);
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
        MapSettingsManager.instance.saveAll();
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        this.timeOfLastMouseInput = System.currentTimeMillis();
        this.switchToMouseInput();
        float mouseDirectX = (float) minecraft.mouseHandler.xpos();
        float mouseDirectY = (float) minecraft.mouseHandler.ypos();
        if (amount != 0.0) {
            if (amount > 0.0) {
                this.zoomGoal *= 1.26F;
            } else if (amount < 0.0) {
                this.zoomGoal /= 1.26F;
            }

            this.zoomStart = this.zoom;
            this.zoomGoal = this.bindZoom(this.zoomGoal);
            this.timeOfZoom = System.currentTimeMillis();
            this.zoomDirectX = mouseDirectX;
            this.zoomDirectY = mouseDirectY;
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        currentDragging = false;
        int mouseX = (int) mouseButtonEvent.x();
        int mouseY = (int) mouseButtonEvent.y();

        if (isInTopHeader(mouseX, mouseY) || isInSeedMapperStrip(mouseX, mouseY)) {
            return true;
        }

        if (mouseButtonEvent.button() == 1 && handleSeedMapperMarkerRightClick(mouseX, mouseY)) {
            return true;
        }

        selectedWaypoint = getHoveredWaypoint();
        if (mouseButtonEvent.button() == 1 && (selectedWaypoint != null || (mouseY > this.top && mouseY < this.bottom))) {
            this.timeOfLastKBInput = 0L;
            int mouseDirectX = (int) minecraft.mouseHandler.xpos();
            int mouseDirectY = (int) minecraft.mouseHandler.ypos();
            if (mapOptions.worldmapAllowed) {
                this.createPopup((int) mouseButtonEvent.x(), (int) mouseButtonEvent.y(), mouseDirectX, mouseDirectY);
            }
        }

        return super.mouseReleased(mouseButtonEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        int mouseX = (int) mouseButtonEvent.x();
        int mouseY = (int) mouseButtonEvent.y();
        if (mouseButtonEvent.button() == 0 && handlePlayerLayerStatusClick(mouseX, mouseY)) {
            return true;
        }
        if (mouseButtonEvent.button() == 0) {
            long now = System.currentTimeMillis();
            boolean closeInTime = now - this.lastMapLeftClickMs <= 300L;
            boolean closeInSpace = Math.abs(mouseX - this.lastMapLeftClickX) <= 10
                    && Math.abs(mouseY - this.lastMapLeftClickY) <= 10;
            doubleClick = closeInTime && closeInSpace;
            this.lastMapLeftClickMs = now;
            this.lastMapLeftClickX = mouseX;
            this.lastMapLeftClickY = mouseY;
        }

        if (seedMapperChestLootWidget != null) {
            if (seedMapperChestLootWidget.mouseClicked(mouseButtonEvent, doubleClick)) {
                return true;
            }
            if (seedMapperChestLootWidget.isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {
                return true;
            }
            if (mouseButtonEvent.button() == 0) {
                seedMapperChestLootWidget = null;
            }
        }

        if (mouseButtonEvent.button() == 0 && this.editingCoordinates && this.popupOpen()) {
            if (isInCoordinateXInput(mouseX, mouseY)) {
                this.coordinateZInput.setFocused(false);
                this.coordinateXInput.setFocused(true);
                this.setFocused(this.coordinateXInput);
                return super.mouseClicked(mouseButtonEvent, doubleClick);
            }

            if (isInCoordinateZInput(mouseX, mouseY)) {
                this.coordinateXInput.setFocused(false);
                this.coordinateZInput.setFocused(true);
                this.setFocused(this.coordinateZInput);
                return super.mouseClicked(mouseButtonEvent, doubleClick);
            }

            closeCoordinateInputs();
        }

        if (mouseButtonEvent.button() == 0
                && mapOptions.worldmapAllowed
                && options.showCoordinates
                && !this.editingCoordinates
                && mouseX >= this.coordinateLabelLeft
                && mouseX <= this.coordinateLabelRight
                && mouseY >= this.coordinateLabelTop
                && mouseY <= this.coordinateLabelBottom) {
            openCoordinateInputs(this.coordinateHoverX, this.coordinateHoverZ);
            return true;
        }

        if (mapOptions.worldmapAllowed && isInSeedHeader(mouseX, mouseY)) {
            if (mouseButtonEvent.button() == 0) {
                minecraft.gui.setScreen(new GuiSeedMapperOptions(this));
            }
            return true;
        }

        if (this.waypointSearchInput != null && isInWaypointSearchInput(mouseX, mouseY)) {
            if (mouseButtonEvent.button() == 0) {
                this.waypointSearchInput.setFocused(true);
                this.setFocused(this.waypointSearchInput);
            }
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        // Popup must consume clicks before map/marker handlers to prevent click-through.
        if (!this.popupOpen()) {
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        if (isInSeedMapperStrip(mouseX, mouseY)) {
            if (mouseButtonEvent.button() == 0 && handleSeedMapperTitleClick(mouseX, mouseY)) {
                return true;
            }
            if (mouseButtonEvent.button() == 0 && handleSeedMapperIconClick(mouseX, mouseY)) {
                return true;
            }
            return true;
        }

        if (isInTopHeader(mouseX, mouseY)) {
            return true;
        }

        if (mouseButtonEvent.button() == 0 && handleSeedMapperIconClick(mouseX, mouseY)) {
            return true;
        }
        if (mouseButtonEvent.button() == 0 && handleSeedMapperMarkerLeftClick(mouseX, mouseY)) {
            return true;
        }
        if (mouseButtonEvent.button() == 0) {
            currentDragging = true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick) || mouseButtonEvent.button() == 1;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (this.waypointSearchInput != null && this.waypointSearchInput.isFocused()) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
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
                this.zoomGoal /= 1.26F;
            }

            this.zoomStart = this.zoom;
            this.zoomGoal = this.bindZoom(this.zoomGoal);
            this.timeOfZoom = System.currentTimeMillis();
            this.zoomDirectX = (minecraft.getWindow().getWidth() / 2f);
            this.zoomDirectY = (minecraft.getWindow().getHeight() - minecraft.getWindow().getHeight() / 2f);
            this.switchToKeyboardInput();
        }

        this.clearPopups();
        if (this.editingCoordinates) {
            if (!this.coordinateXInput.isFocused() && !this.coordinateZInput.isFocused()) {
                this.coordinateXInput.setFocused(true);
                this.setFocused(this.coordinateXInput);
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeCoordinateInputs();
                return true;
            }

            if (keyEvent.key() == GLFW.GLFW_KEY_TAB) {
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
            if ((keyEvent.key() == 257 || keyEvent.key() == 335) && isGood) {
                int x = Integer.parseInt(this.coordinateXInput.getValue().trim());
                int z = Integer.parseInt(this.coordinateZInput.getValue().trim());
                this.centerAt(x, z);
                closeCoordinateInputs();
                this.switchToKeyboardInput();
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
            keyEvent = new KeyEvent(GLFW.GLFW_KEY_ESCAPE, -1, -1);
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

    private void switchToMouseInput() {
        this.timeOfLastKBInput = 0L;
        if (!this.mouseCursorShown) {
            GLFW.glfwSetInputMode(minecraft.getWindow().handle(), 208897, 212993);
        }

        this.mouseCursorShown = true;
    }

    private void switchToKeyboardInput() {
        this.timeOfLastKBInput = System.currentTimeMillis();
        this.mouseCursorShown = false;
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), 208897, 212995);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.pose().pushMatrix();
        this.waypointLabelBounds.clear();
        this.pendingWaypointLabels.clear();
        this.waypointClusters.clear();
        this.buttonWaypoints.active = mapOptions.waypointsAllowed;
        refreshWorldMapControlLabels();
        this.zoomGoal = this.bindZoom(this.zoomGoal);
        if (this.mouseX != mouseX || this.mouseY != mouseY) {
            this.timeOfLastMouseInput = System.currentTimeMillis();
            this.switchToMouseInput();
        }

        this.mouseX = mouseX;
        this.mouseY = mouseY;
        float mouseDirectX = (float) minecraft.mouseHandler.xpos();
        float mouseDirectY = (float) minecraft.mouseHandler.ypos();
        if (this.zoom != this.zoomGoal) {
            float previousZoom = this.zoom;
            long timeSinceZoom = System.currentTimeMillis() - this.timeOfZoom;
            if (timeSinceZoom < 700.0F) {
                this.zoom = EasingUtils.easeOutExpo(this.zoomStart, this.zoomGoal, timeSinceZoom, 700.0F);
            } else {
                this.zoom = this.zoomGoal;
            }

            float scaledZoom = this.zoom;
            if (minecraft.getWindow().getWidth() > 1600) {
                scaledZoom = this.zoom * minecraft.getWindow().getWidth() / 1600.0F;
            }

            float zoomDelta = this.zoom / previousZoom;
            float zoomOffsetX = this.centerX * this.guiToDirectMouse - this.zoomDirectX;
            float zoomOffsetY = (this.top + this.centerY) * this.guiToDirectMouse - this.zoomDirectY;
            float zoomDeltaX = zoomOffsetX - zoomOffsetX * zoomDelta;
            float zoomDeltaY = zoomOffsetY - zoomOffsetY * zoomDelta;
            this.mapCenterX += zoomDeltaX / scaledZoom;
            this.mapCenterZ += zoomDeltaY / scaledZoom;
        }

        this.options.zoom = this.zoomGoal;
        float scaledZoom = this.zoom;
        if (minecraft.getWindow().getScreenWidth() > 1600) {
            scaledZoom = this.zoom * minecraft.getWindow().getScreenWidth() / 1600.0F;
        }

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
        boolean farZoomPerformanceMode = isFarZoomPerformanceMode();
        int exploredLeftRegion = left - 1;
        int exploredRightRegion = right + 1;
        int exploredTopRegion = top - 1;
        int exploredBottomRegion = bottom + 1;
        // Keep full visible explored-line bounds in performance mode to avoid "windowed" clipping while panning.

        synchronized (this.closedLock) {
            if (this.closed) {
                return;
            }
            if (!farZoomPerformanceMode && mapOptions.worldmapAllowed) {
                this.regions = this.persistentMap.getRegions(left - 1, right + 1, top - 1, bottom + 1, viewedDimension);
            } else {
                this.regions = new CachedRegion[0];
            }
        }

        this.backGroundImageInfo = this.waypointManager.getBackgroundImageInfo();
        if (this.backGroundImageInfo != null && !farZoomPerformanceMode) {
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
            if (!farZoomPerformanceMode) {
                drawSeedPreview(graphics, visibleBounds);
                for (CachedRegion region : this.regions) {
                    Identifier resource = region.getTextureLocation(this.zoom);
                    if (resource != null) {
                        graphics.blit(RenderPipelines.GUI_TEXTURED, resource, region.getX() * 256, region.getZ() * 256, 0, 0, region.getWidth(), region.getWidth(), region.getWidth(), region.getWidth());
                    }
                }
            }
            drawExploredChunkLinesWorldMap(graphics, exploredLeftRegion, exploredRightRegion, exploredTopRegion, exploredBottomRegion);
            drawNewOldChunkOverlayWorldMap(graphics, exploredLeftRegion, exploredRightRegion, exploredTopRegion, exploredBottomRegion);

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

            if (this.oldNorth) {
                graphics.pose().rotate(-90.0F * Mth.DEG_TO_RAD);
            }

            graphics.pose().scale(this.guiToMap, this.guiToMap);
            graphics.pose().translate(-(this.centerX - this.mapCenterX * this.mapToGui), -((this.top + this.centerY) - this.mapCenterZ * this.mapToGui));
            if (!farZoomPerformanceMode && mapOptions.biomeOverlay != 0) {
                float biomeScaleX = this.mapPixelsX / 760.0F;
                float biomeScaleY = this.mapPixelsY / 360.0F;
                boolean still = !this.leftMouseButtonDown;
                still = still && this.zoom == this.zoomGoal;
                still = still && this.deltaX == 0.0F && this.deltaY == 0.0F;
                still = still && ThreadManager.executorService.getActiveCount() == 0;
                if (still && !this.lastStill) {
                    int column;
                    if (this.oldNorth) {
                        column = (int) Math.floor(Math.floor(this.mapCenterZ - this.centerY * this.guiToMap) / 256.0) - (left - 1);
                    } else {
                        column = (int) Math.floor(Math.floor(this.mapCenterX - this.centerX * this.guiToMap) / 256.0) - (left - 1);
                    }

                    for (int x = 0; x < this.biomeMapData.getWidth(); ++x) {
                        for (int z = 0; z < this.biomeMapData.getHeight(); ++z) {
                            float floatMapX;
                            float floatMapZ;
                            if (this.oldNorth) {
                                floatMapX = z * biomeScaleY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
                                floatMapZ = -(x * biomeScaleX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap));
                            } else {
                                floatMapX = x * biomeScaleX * this.mouseDirectToMap + (this.mapCenterX - this.centerX * this.guiToMap);
                                floatMapZ = z * biomeScaleY * this.mouseDirectToMap + (this.mapCenterZ - this.centerY * this.guiToMap);
                            }

                            int mapX = (int) Math.floor(floatMapX);
                            int mapZ = (int) Math.floor(floatMapZ);
                            int regionX = (int) Math.floor(mapX / 256.0F) - (left - 1);
                            int regionZ = (int) Math.floor(mapZ / 256.0F) - (top - 1);
                            if (!this.oldNorth && regionX != column || this.oldNorth && regionZ != column) {
                                this.persistentMap.compress();
                            }

                            column = !this.oldNorth ? regionX : regionZ;
                            CachedRegion region = this.regions[regionZ * (right + 1 - (left - 1) + 1) + regionX];
                            Biome biome = null;
                            if (region.getMapData() != null && region.isLoaded() && !region.isEmpty()) {
                                int inRegionX = mapX - region.getX() * region.getWidth();
                                int inRegionZ = mapZ - region.getZ() * region.getWidth();
                                int height = region.getMapData().getHeight(inRegionX, inRegionZ);
                                int light = region.getMapData().getLight(inRegionX, inRegionZ);
                                if (height != Short.MIN_VALUE || light != 0) {
                                    biome = region.getMapData().getBiome(inRegionX, inRegionZ);
                                }
                            }

                            this.biomeMapData.setBiome(x, z, biome);
                        }
                    }

                    this.persistentMap.compress();
                    this.biomeMapData.segmentBiomes();
                    this.biomeMapData.findCenterOfSegments(true);
                }

                this.lastStill = still;
                boolean displayStill = !this.leftMouseButtonDown;
                displayStill = displayStill && this.zoom == this.zoomGoal;
                displayStill = displayStill && this.deltaX == 0.0F && this.deltaY == 0.0F;
                if (displayStill) {
                    int minimumSize = (int) (20.0F * this.scScale / biomeScaleX);
                    minimumSize *= minimumSize;
                    ArrayList<AbstractMapData.BiomeLabel> labels = this.biomeMapData.getBiomeLabels();
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

        if (!farZoomPerformanceMode && mapOptions.worldmapAllowed) {
            drawSeedMapperFeatureStrip(graphics, mouseX, mouseY);
        }
        if (!farZoomPerformanceMode) {
            drawSeedMapperMarkers(graphics, mouseX, mouseY);
            drawSeedMapperLoadingStatus(graphics);
            drawSeedPreviewLoadingStatus(graphics);
        }
        drawPlayerLayerStatuses(graphics);

        Waypoint currentlyHovered = null;
        boolean showWaypointsInThisMode = !farZoomPerformanceMode || this.options.isShowWaypointsInPerformanceModeEnabled();
        if (showWaypointsInThisMode && mapOptions.waypointsAllowed && options.showWaypoints) {
            TextureAtlas textureAtlas = VoxelConstants.getVoxelMapInstance().getWaypointManager().getTextureAtlas();
            for (Waypoint waypoint : waypointManager.getWaypoints()) {
                if (!isWaypointVisibleInViewedDimension(waypoint)) continue;
                if (hasVisibleSeedMapperMarkerAt(getWaypointXInViewedDimension(waypoint), getWaypointZInViewedDimension(waypoint))) continue;

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

        drawQueuedWaypointLabels(graphics);

        if (mapOptions.worldmapAllowed) {
            graphics.centeredText(this.getFont(), this.screenTitle, this.getWidth() / 2, 16, 0xFFFFFFFF);
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
            if (this.zoom != this.zoomGoal) {
                String zoomText = String.format(java.util.Locale.ROOT, "Zoom: %.3fx", this.zoom);
                graphics.text(this.getFont(), zoomText, this.sideMargin, this.getHeight() - 38, 0xFFFFFFFF);
            }
            if (farZoomPerformanceMode) {
                String perfText = "Performance Mode: Explored chunk lines only";
                int perfWidth = this.getFont().width(perfText);
                graphics.text(this.getFont(), perfText, this.getWidth() - this.sideMargin - perfWidth, 28, 0xFFAAAAAA);
            }

            if (this.subworldName != null && !this.subworldName.equals(VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true))
                    || VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true) != null && !VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(true).equals(this.subworldName)) {
                this.buildWorldName();
            }

            int worldNameY = showSeedHeader ? 28 : 16;
            graphics.text(this.getFont(), this.worldNameDisplay, this.getWidth() - this.sideMargin - this.worldNameDisplayLength, worldNameY, 0xFFFFFF);
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
                graphics.tooltip(this.getFont(), tooltip, seedMapperChestLootWidget.getPendingTooltipX(), seedMapperChestLootWidget.getPendingTooltipY(), DefaultTooltipPositioner.INSTANCE, null);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawExploredChunkLinesWorldMap(GuiGraphicsExtractor graphics, int leftRegion, int rightRegion, int topRegion, int bottomRegion) {
        this.exploredQuadCount = 0;
        // Clear up front so an early return (overlay off / zero alpha) leaves no stale player-layer labels.
        this.playerLayerStatusHitboxes.clear();
        if (!radarOptions.showExploredChunks) {
            return;
        }

        int minChunkX = leftRegion * 16;
        int maxChunkX = rightRegion * 16 + 15;
        int minChunkZ = topRegion * 16;
        int maxChunkZ = bottomRegion * 16 + 15;

        int centerChunkX = (minChunkX + maxChunkX) >> 1;
        int centerChunkZ = (minChunkZ + maxChunkZ) >> 1;
        int radius = Math.max(maxChunkX - centerChunkX, maxChunkZ - centerChunkZ) + 2;

        boolean farZoomPerformanceMode = isFarZoomPerformanceMode();
        boolean literalLineMode = options.isLiteralLineModeEnabled();
        int alpha = Mth.clamp((int) Math.round((radarOptions.exploredChunksOpacity / 100.0D) * 255.0D), 0, 255);
        if (farZoomPerformanceMode) {
            alpha = Math.max(alpha, 210);
        }
        if (literalLineMode) {
            alpha = Math.max(alpha, 235);
        }
        if (alpha <= 0) {
            return;
        }

        int color = (alpha << 24) | (radarOptions.getExploredChunksColorRgb() & 0x00FFFFFF);
        float thicknessMultiplier = options.getChunkLineThickness();
        float targetScreenThickness = (literalLineMode ? 1.25F : (farZoomPerformanceMode ? 2.4F : 1.25F)) * thicknessMultiplier;
        if (literalLineMode) {
            // Prevent a zoom boundary from making the line effectively sub-pixel and "disappearing".
            targetScreenThickness = Math.max(0.8F, targetScreenThickness);
        }
        float lineThickness = literalLineMode
                ? Math.max(0.15F, targetScreenThickness / Math.max(0.0001F, this.mapToGui))
                : Math.max(1.0F, targetScreenThickness / Math.max(0.0001F, this.mapToGui));
        boolean mapInMotion = currentDragging
                || Math.abs(this.deltaX) > 0.01F
                || Math.abs(this.deltaY) > 0.01F
                || this.zoom != this.zoomGoal;
        boolean ultraLowDetail = this.mapToGui < 0.20F;
        int decimationMask = 0;
        if (!literalLineMode && mapInMotion) {
            if (this.mapToGui < 0.12F) {
                decimationMask = 0x7; // keep about 1/8 while moving
            } else if (this.mapToGui < 0.18F) {
                decimationMask = 0x3; // keep about 1/4 while moving
            } else if (this.mapToGui < 0.28F) {
                decimationMask = 0x1; // keep about 1/2 while moving
            }
        }
        if (!literalLineMode && !mapInMotion && this.mapToGui < 0.10F) {
            decimationMask = Math.max(decimationMask, 0x3); // keep about 1/4 while still
        }
        if (!literalLineMode && this.mapToGui < 0.06F && !farZoomPerformanceMode) {
            decimationMask = Math.max(decimationMask, 0x1F); // keep about 1/32 at extreme zoom-out
        }
        int maxDraw = Integer.MAX_VALUE;
        if (!literalLineMode) {
            maxDraw = mapInMotion ? 3000 : 12000;
            if (ultraLowDetail) {
                maxDraw = Math.min(maxDraw, mapInMotion ? 700 : 5000);
            }
            if (farZoomPerformanceMode) {
                maxDraw = Math.min(maxDraw, mapInMotion ? 6000 : 12000);
            } else if (this.mapToGui < 0.10F) {
                maxDraw = Math.min(maxDraw, mapInMotion ? 1200 : 3000);
            } else if (this.mapToGui < 0.06F) {
                maxDraw = Math.min(maxDraw, mapInMotion ? 800 : 1800);
            }
        }
        int maxScanned = Integer.MAX_VALUE;
        if (!literalLineMode) {
            if (farZoomPerformanceMode) {
                maxScanned = mapInMotion ? 250_000 : 500_000;
            } else if (this.mapToGui < 0.10F) {
                maxScanned = 120_000;
            } else if (this.mapToGui < 0.06F) {
                maxScanned = 80_000;
            }
        }
        int scanned = 0;
        int drawn = 0;
        int querySnap;
        if (literalLineMode) {
            if (this.mapToGui < 0.012F) {
                querySnap = mapInMotion ? 4096 : 2048;
            } else if (this.mapToGui < 0.02F) {
                querySnap = mapInMotion ? 1024 : 512;
            } else if (this.mapToGui < 0.05F) {
                querySnap = mapInMotion ? 512 : 256;
            } else {
                querySnap = mapInMotion ? 256 : 128;
            }
        } else {
            querySnap = mapInMotion ? 64 : 32;
        }
        int queryMinChunkX = Math.floorDiv(minChunkX, querySnap) * querySnap;
        int queryMaxChunkX = Math.floorDiv(maxChunkX + querySnap - 1, querySnap) * querySnap;
        int queryMinChunkZ = Math.floorDiv(minChunkZ, querySnap) * querySnap;
        int queryMaxChunkZ = Math.floorDiv(maxChunkZ + querySnap - 1, querySnap) * querySnap;
        ExploredLinesQueryCacheKey queryKey = new ExploredLinesQueryCacheKey(queryMinChunkX, queryMaxChunkX, queryMinChunkZ, queryMaxChunkZ, querySnap, getViewedDimensionIdentifier());
        long now = System.currentTimeMillis();
        long minIntervalMs;
        if (literalLineMode) {
            if (this.mapToGui < 0.012F) {
                minIntervalMs = mapInMotion ? 1500L : 3000L;
            } else if (this.mapToGui < 0.02F) {
                minIntervalMs = mapInMotion ? 900L : 1800L;
            } else if (this.mapToGui < 0.05F) {
                minIntervalMs = mapInMotion ? 600L : 1200L;
            } else {
                minIntervalMs = mapInMotion ? 350L : 700L;
            }
        } else {
            minIntervalMs = mapInMotion ? 180L : 350L;
        }
        boolean vectorLineMode = literalLineMode || mapInMotion || farZoomPerformanceMode || this.mapToGui < 0.16F;
        int cellChunkSize = 1;
        if (literalLineMode) {
            float chunkScreenSize = 16.0F * this.mapToGui;
            cellChunkSize = Math.max(1, (int) Math.ceil(0.45F / Math.max(0.0001F, chunkScreenSize)));
        } else if (vectorLineMode) {
            if (this.mapToGui < 0.006F) {
                cellChunkSize = mapInMotion ? 256 : 128;
            } else if (this.mapToGui < 0.012F) {
                cellChunkSize = mapInMotion ? 128 : 64;
            } else if (this.mapToGui < 0.03F) {
                cellChunkSize = mapInMotion ? 64 : 32;
            } else if (this.mapToGui < 0.06F) {
                cellChunkSize = mapInMotion ? 16 : 8;
            } else if (this.mapToGui < 0.10F) {
                cellChunkSize = mapInMotion ? 4 : 2;
            } else {
                cellChunkSize = mapInMotion ? 2 : 1;
            }
        }

        com.mamiyaotaru.voxelmap.ExploredChunksManager exploredChunksManager = VoxelConstants.getVoxelMapInstance().getExploredChunksManager();
        Identifier viewedDimension = getViewedDimensionIdentifier();
        long exploredDataVersion = exploredChunksManager.getDataVersion(viewedDimension);
        boolean queryKeyChanged = exploredLinesLastQueryKey == null || !exploredLinesLastQueryKey.equals(queryKey);
        boolean queryIntervalElapsed = now - exploredLinesLastQueryMs >= minIntervalMs;
        boolean dataChanged = exploredLinesLastDataVersion != exploredDataVersion;
        boolean refreshChunks = exploredLinesLastResult.isEmpty() || queryKeyChanged || (dataChanged && queryIntervalElapsed);
        if (vectorLineMode) {
            ExploredLineRenderCacheKey renderCacheKey = new ExploredLineRenderCacheKey(queryKey, cellChunkSize, exploredDataVersion);
            if (!renderCacheKey.equals(exploredLineRenderCacheKey) || dataChanged) {
                CellGrid previousCells = exploredLinesLastCellResult;
                ExploredLineMesher.Result previousMesh = exploredLineMesh;
                CellGrid queriedCells = exploredChunksManager.getExploredCellsInRange(centerChunkX, centerChunkZ, radius, cellChunkSize, viewedDimension);
                if (exploredChunksManager.consumeStorageLoadIncomplete()) {
                    exploredLinesLastCellResult = previousCells;
                    exploredLineMesh = previousMesh;
                    exploredLineRenderCacheKey = null;
                } else {
                    exploredLinesLastCellResult = queriedCells;
                    rebuildExploredLineRenderCache(exploredLinesLastCellResult, cellChunkSize, literalLineMode);
                    exploredLineRenderCacheKey = renderCacheKey;
                    exploredLinesLastQueryKey = queryKey;
                    exploredLinesLastQueryMs = now;
                    exploredLinesLastDataVersion = exploredDataVersion;
                }
            }
            ExploredLineMesher.Result mesh = exploredLineMesh;
            float[] segmentCoords = mesh.segments();
            int segmentCount = mesh.segmentCount();
            for (int i = 0; i < segmentCount; i++) {
                int o = i << 2;
                appendThickInterpolatedLine(segmentCoords[o], segmentCoords[o + 1], segmentCoords[o + 2], segmentCoords[o + 3], lineThickness, color);
            }
            if (literalLineMode) {
                float cellWorldSize = cellChunkSize * 16.0F;
                float nodeHalf = Math.max(lineThickness * 0.55F, cellWorldSize * 0.04F);
                boolean ultraFarLiteral = this.mapToGui < 0.03F;
                float[] nodeCoords = mesh.nodeCoords();
                boolean[] nodeLinked = mesh.nodeLinked();
                int nodeCount = mesh.nodeCount();
                for (int i = 0; i < nodeCount; i++) {
                    if (!ultraFarLiteral || !nodeLinked[i]) {
                        float nx = nodeCoords[i << 1];
                        float nz = nodeCoords[(i << 1) + 1];
                        appendExploredQuad(nx - nodeHalf, nz - nodeHalf, nx + nodeHalf, nz + nodeHalf, color);
                    }
                }
            }
            renderPlayerExploredLayers(graphics, exploredChunksManager, centerChunkX, centerChunkZ, radius, cellChunkSize, lineThickness, color, viewedDimension);
            flushExploredQuads(graphics);
            return;
        }

        if (refreshChunks) {
            exploredLinesLastResult = new ArrayList<>(exploredChunksManager.getExploredChunksInRange(centerChunkX, centerChunkZ, radius, viewedDimension));
            exploredLinesLastQueryKey = queryKey;
            exploredLinesLastQueryMs = now;
            exploredLinesLastDataVersion = exploredDataVersion;
        }

        for (ChunkPos chunk : exploredLinesLastResult) {
            if (++scanned >= maxScanned) {
                break;
            }
            if (drawn >= maxDraw) {
                break;
            }
            int chunkX = chunk.x();
            int chunkZ = chunk.z();
            if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                continue;
            }
            if (decimationMask != 0) {
                int hash = (chunkX * 73428767) ^ (chunkZ * 912931);
                if ((hash & decimationMask) != 0) {
                    continue;
                }
            }
            float minX = chunk.getMinBlockX();
            float minZ = chunk.getMinBlockZ();
            float maxX = minX + 16.0F;
            float maxZ = minZ + 16.0F;

            appendExploredQuad(minX, minZ, maxX, minZ + lineThickness, color);
            appendExploredQuad(minX, maxZ - lineThickness, maxX, maxZ, color);
            appendExploredQuad(minX, minZ, minX + lineThickness, maxZ, color);
            appendExploredQuad(maxX - lineThickness, minZ, maxX, maxZ, color);
            drawn++;
        }

        renderPlayerExploredLayers(graphics, exploredChunksManager, centerChunkX, centerChunkZ, radius, cellChunkSize, lineThickness, color, viewedDimension);
        flushExploredQuads(graphics);
    }

    private void drawNewOldChunkOverlayWorldMap(GuiGraphicsExtractor graphics, int leftRegion, int rightRegion, int topRegion, int bottomRegion) {
        if (!options.showNewOldChunks || !radarOptions.showNewerNewChunks) {
            return;
        }
        // At extreme zoom-out these overlays are barely legible and can still add frame cost.
        // Hard-stop rendering at and below 0.020x world map zoom.
        if (this.zoom <= 0.020F) {
            return;
        }

        int minChunkX = leftRegion * 16;
        int maxChunkX = rightRegion * 16 + 15;
        int minChunkZ = topRegion * 16;
        int maxChunkZ = bottomRegion * 16 + 15;

        boolean farZoomPerformanceMode = isFarZoomPerformanceMode();
        boolean mapInMotion = currentDragging
                || Math.abs(this.deltaX) > 0.01F
                || Math.abs(this.deltaY) > 0.01F
                || this.zoom != this.zoomGoal;
        long now = System.currentTimeMillis();
        if (mapInMotion) {
            newOldChunkLastMotionMs = now;
        }
        boolean movingLod = mapInMotion || now - newOldChunkLastMotionMs < 250L;

        int oldAlpha = Mth.clamp((int) Math.round((radarOptions.newerNewChunksOldOpacity / 100.0D) * 255.0D), 0, 255);
        int newAlpha = Mth.clamp((int) Math.round((radarOptions.newerNewChunksNewOpacity / 100.0D) * 255.0D), 0, 255);
        if (oldAlpha <= 0 && newAlpha <= 0) {
            return;
        }

        int oldColor = (oldAlpha << 24) | (radarOptions.getNewerNewChunksOldColorRgb() & 0x00FFFFFF);
        int newColor = (newAlpha << 24) | (radarOptions.getNewerNewChunksNewColorRgb() & 0x00FFFFFF);

        int cellChunkSize = getNewOldChunkCellChunkSize(movingLod, farZoomPerformanceMode);
        int maxDraw = getNewOldChunkMaxDraw(movingLod, farZoomPerformanceMode);
        int zoomBucket = getNewOldChunkZoomBucket();
        int querySnap = Math.max(cellChunkSize, movingLod ? 16 : 8);
        int queryMinChunkX = Math.floorDiv(minChunkX, querySnap) * querySnap;
        int queryMaxChunkX = Math.floorDiv(maxChunkX + querySnap, querySnap) * querySnap - 1;
        int queryMinChunkZ = Math.floorDiv(minChunkZ, querySnap) * querySnap;
        int queryMaxChunkZ = Math.floorDiv(maxChunkZ + querySnap, querySnap) * querySnap - 1;

        NewerNewChunksManager manager = VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager();
        Identifier viewedDimensionOverlay = getViewedDimensionIdentifier();
        NewOldChunkOverlayRenderCacheKey cacheKey = new NewOldChunkOverlayRenderCacheKey(
                queryMinChunkX, queryMaxChunkX, queryMinChunkZ, queryMaxChunkZ,
                zoomBucket, cellChunkSize, oldColor, newColor, farZoomPerformanceMode, movingLod,
                maxDraw, manager.getDataVersion(viewedDimensionOverlay), manager.getLoadedWorldKey(viewedDimensionOverlay));
        if (!cacheKey.equals(newOldChunkOverlayRenderCacheKey)) {
            List<NewOldChunkRenderRect> previousOldRects = newOldChunkOldRects;
            List<NewOldChunkRenderRect> previousNewRects = newOldChunkNewRects;
            rebuildNewOldChunkOverlayRenderCache(manager, cacheKey, viewedDimensionOverlay);
            long rebuiltDataVersion = manager.getDataVersion(viewedDimensionOverlay);
            if (manager.consumeStorageLoadIncomplete()) {
                newOldChunkOverlayRenderCacheKey = null;
                newOldChunkOldRects = previousOldRects;
                newOldChunkNewRects = previousNewRects;
            } else {
                newOldChunkOverlayRenderCacheKey = rebuiltDataVersion == cacheKey.dataVersion()
                        ? cacheKey
                        : new NewOldChunkOverlayRenderCacheKey(cacheKey.minChunkX(), cacheKey.maxChunkX(), cacheKey.minChunkZ(), cacheKey.maxChunkZ(),
                                cacheKey.zoomBucket(), cacheKey.cellChunkSize(), cacheKey.oldColor(), cacheKey.newColor(),
                                cacheKey.farZoomPerformanceMode(), cacheKey.movingLod(), cacheKey.maxDraw(), rebuiltDataVersion, cacheKey.worldKey());
            }
            newOldChunkCacheMisses++;
        } else {
            newOldChunkCacheHits++;
        }

        // Submit all rects as a single batched GUI element (old first, then new on top) instead of one
        // element per rect, avoiding the O(N^2) strata sort in the GUI renderer (same fix as explored lines).
        int rectCount = newOldChunkOldRects.size() + newOldChunkNewRects.size();
        if (rectCount > 0) {
            float[] coords = new float[rectCount * 4];
            int[] colors = new int[rectCount];
            int i = 0;
            for (NewOldChunkRenderRect rect : newOldChunkOldRects) {
                int o = i << 2;
                coords[o] = rect.minX();
                coords[o + 1] = rect.minZ();
                coords[o + 2] = rect.maxX();
                coords[o + 3] = rect.maxZ();
                colors[i++] = rect.color();
            }
            for (NewOldChunkRenderRect rect : newOldChunkNewRects) {
                int o = i << 2;
                coords[o] = rect.minX();
                coords[o + 1] = rect.minZ();
                coords[o + 2] = rect.maxX();
                coords[o + 3] = rect.maxZ();
                colors[i++] = rect.color();
            }
            VoxelMapGuiGraphics.fillRectsBatched(graphics, coords, colors, rectCount);
        }

        renderPlayerNewOldOverlays(graphics, manager, queryMinChunkX, queryMaxChunkX, queryMinChunkZ, queryMaxChunkZ,
                cellChunkSize, oldColor, newColor, maxDraw, viewedDimensionOverlay);

        maybeLogNewOldChunkOverlayDebug(manager);
    }

    private void renderPlayerNewOldOverlays(GuiGraphicsExtractor graphics, NewerNewChunksManager manager,
            int queryMinChunkX, int queryMaxChunkX, int queryMinChunkZ, int queryMaxChunkZ,
            int cellChunkSize, int oldColor, int newColor, int maxDraw, Identifier viewedDimension) {
        java.util.Set<String> slugs = manager.playerLayerSlugs();
        if (slugs.isEmpty()) {
            return;
        }
        int centerChunkX = (queryMinChunkX + queryMaxChunkX) >> 1;
        int centerChunkZ = (queryMinChunkZ + queryMaxChunkZ) >> 1;
        int radius = Math.max(queryMaxChunkX - centerChunkX, queryMaxChunkZ - centerChunkZ) + 2;
        int effectiveCell = Math.max(1, cellChunkSize);

        java.util.ArrayList<NewOldChunkRenderRect> rects = new java.util.ArrayList<>();
        for (String slug : slugs) {
            if (!ChunkSharePlayerSettings.isEnabled(slug)) {
                continue;
            }
            NewerNewChunksManager.NewOldCellsSnapshot snap =
                    manager.getPlayerNewOldCellsInRange(slug, centerChunkX, centerChunkZ, radius, effectiveCell, viewedDimension);
            // greedyMesh consumes the grid; the snapshot grids are freshly built per call so that's fine.
            rects.addAll(greedyMeshNewOldChunkCells(snap.oldCells(), effectiveCell,
                    ChunkSharePlayerSettings.colorFor(slug, oldColor), maxDraw));
            rects.addAll(greedyMeshNewOldChunkCells(snap.newCells(), effectiveCell,
                    ChunkSharePlayerSettings.colorFor(slug, newColor), maxDraw));
        }
        if (rects.isEmpty()) {
            return;
        }
        float[] coords = new float[rects.size() << 2];
        int[] colors = new int[rects.size()];
        int i = 0;
        for (NewOldChunkRenderRect rect : rects) {
            int o = i << 2;
            coords[o] = rect.minX();
            coords[o + 1] = rect.minZ();
            coords[o + 2] = rect.maxX();
            coords[o + 3] = rect.maxZ();
            colors[i++] = rect.color();
        }
        VoxelMapGuiGraphics.fillRectsBatched(graphics, coords, colors, rects.size());
    }

    private void rebuildNewOldChunkOverlayRenderCache(NewerNewChunksManager manager, NewOldChunkOverlayRenderCacheKey cacheKey, Identifier viewedDimension) {
        long start = System.nanoTime();
        int centerChunkX = (cacheKey.minChunkX() + cacheKey.maxChunkX()) >> 1;
        int centerChunkZ = (cacheKey.minChunkZ() + cacheKey.maxChunkZ()) >> 1;
        int radius = Math.max(cacheKey.maxChunkX() - centerChunkX, cacheKey.maxChunkZ() - centerChunkZ) + 2;

        int remainingDraw = cacheKey.maxDraw();
        int effectiveCell = Math.max(1, cacheKey.cellChunkSize());
        NewerNewChunksManager.NewOldCellsSnapshot snapshot = manager.getNewOldCellsInRange(centerChunkX, centerChunkZ, radius, effectiveCell, viewedDimension);
        newOldChunkLastOldReturned = snapshot.oldChunks();
        newOldChunkLastNewReturned = snapshot.newChunks();
        BuildNewOldChunkRectsResult oldResult = buildNewOldChunkRectsFromCells(snapshot.oldCells(), cacheKey.oldColor(), effectiveCell, remainingDraw, newOldChunkLastOldReturned);
        remainingDraw = Math.max(0, remainingDraw - oldResult.rects().size());
        BuildNewOldChunkRectsResult newResult = buildNewOldChunkRectsFromCells(snapshot.newCells(), cacheKey.newColor(), effectiveCell, remainingDraw, newOldChunkLastNewReturned);

        newOldChunkOldRects = oldResult.rects();
        newOldChunkNewRects = newResult.rects();
        newOldChunkLastVisibleOld = oldResult.visibleChunks();
        newOldChunkLastVisibleNew = newResult.visibleChunks();
        newOldChunkLastCells = oldResult.generatedCells() + newResult.generatedCells();
        newOldChunkLastRebuildTimeMs = (System.nanoTime() - start) / 1_000_000L;
    }

    private BuildNewOldChunkRectsResult buildNewOldChunkRectsFromCells(CellGrid cells, int color, int cellChunkSize, int maxDraw, int visibleChunks) {
        if (maxDraw <= 0 || cells.isEmpty()) {
            return new BuildNewOldChunkRectsResult(List.of(), visibleChunks, 0);
        }
        int cellCount = 0;
        for (boolean b : cells.cells) {
            if (b) {
                cellCount++;
            }
        }
        return new BuildNewOldChunkRectsResult(greedyMeshNewOldChunkCells(cells, cellChunkSize, color, maxDraw), visibleChunks, cellCount);
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

    private int getNewOldChunkMaxQueryResults(int cellChunkSize, int maxDraw) {
        if (cellChunkSize <= 1 && maxDraw == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (maxDraw == Integer.MAX_VALUE) {
            return 120_000;
        }
        return Math.max(8_000, Math.min(120_000, maxDraw * Math.max(4, cellChunkSize)));
    }

    private int getNewOldChunkZoomBucket() {
        if (this.mapToGui < 0.03F) {
            return 0;
        }
        if (this.mapToGui < 0.06F) {
            return 1;
        }
        if (this.mapToGui < 0.10F) {
            return 2;
        }
        if (this.mapToGui < 0.18F) {
            return 3;
        }
        return 4;
    }

    private int getExploredVectorMaxCells(boolean mapInMotion) {
        if (this.mapToGui < 0.006F) {
            return mapInMotion ? 700 : 1200;
        }
        if (this.mapToGui < 0.012F) {
            return mapInMotion ? 1000 : 1800;
        }
        if (this.mapToGui < 0.03F) {
            return mapInMotion ? 1800 : 3000;
        }
        if (this.mapToGui < 0.06F) {
            return mapInMotion ? 2500 : 4500;
        }
        return mapInMotion ? 3500 : 6000;
    }

    private int getExploredVectorMaxSegments(boolean mapInMotion) {
        if (this.mapToGui < 0.006F) {
            return mapInMotion ? 160 : 260;
        }
        if (this.mapToGui < 0.012F) {
            return mapInMotion ? 220 : 380;
        }
        if (this.mapToGui < 0.03F) {
            return mapInMotion ? 360 : 700;
        }
        return mapInMotion ? 650 : 1200;
    }

    private void maybeLogNewOldChunkOverlayDebug(NewerNewChunksManager manager) {
        if (!VoxelConstants.DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - newOldChunkLastDebugLogMs < 5000L) {
            return;
        }
        newOldChunkLastDebugLogMs = now;
        VoxelConstants.getLogger().info("World map new/old overlay: hits={} misses={} oldReturned={} newReturned={} oldVisible={} newVisible={} cells={} rects={} rebuildMs={} storageFiles={} storageDecoded={} storageMissing={} storageMs={} migrationMs={} version={}",
                newOldChunkCacheHits, newOldChunkCacheMisses,
                newOldChunkLastOldReturned, newOldChunkLastNewReturned,
                newOldChunkLastVisibleOld, newOldChunkLastVisibleNew,
                newOldChunkLastCells, newOldChunkOldRects.size() + newOldChunkNewRects.size(),
                newOldChunkLastRebuildTimeMs,
                manager.getLastLoadedRegionFiles(), manager.getLastDecodedChunks(), manager.getLastSkippedMissingRegionFiles(),
                manager.getLastStorageLoadTimeMs(), manager.getLastMigrationTimeMs(), manager.getDataVersion());
    }

    private int drawChunkSquaresWorldMap(GuiGraphicsExtractor graphics, List<ChunkPos> chunks, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, int color, int cellChunkSize, int maxDraw, int startDrawn) {
        int drawn = startDrawn;
        if (cellChunkSize > 1) {
            java.util.HashSet<Long> cells = new java.util.HashSet<>();
            for (ChunkPos chunk : chunks) {
                int chunkX = chunk.x();
                int chunkZ = chunk.z();
                if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                    continue;
                }
                int cellX = Math.floorDiv(chunkX, cellChunkSize);
                int cellZ = Math.floorDiv(chunkZ, cellChunkSize);
                cells.add(chunkKey(cellX, cellZ));
            }

            float worldCellSize = 16.0F * cellChunkSize;
            for (long key : cells) {
                if (drawn >= maxDraw) {
                    break;
                }
                int cellX = (int) (key >> 32);
                int cellZ = (int) key;
                float minX = cellX * worldCellSize;
                float minZ = cellZ * worldCellSize;
                VoxelMapGuiGraphics.fillGradient(graphics, minX, minZ, minX + worldCellSize, minZ + worldCellSize, color, color, color, color);
                drawn++;
            }
            return drawn;
        }

        for (ChunkPos chunk : chunks) {
            if (drawn >= maxDraw) {
                break;
            }
            int chunkX = chunk.x();
            int chunkZ = chunk.z();
            if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                continue;
            }

            float minX = chunk.getMinBlockX();
            float minZ = chunk.getMinBlockZ();
            VoxelMapGuiGraphics.fillGradient(graphics, minX, minZ, minX + 16.0F, minZ + 16.0F, color, color, color, color);
            drawn++;
        }
        return drawn;
    }

    private long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void rebuildExploredLineRenderCache(CellGrid chunkSet, int cellChunkSize, boolean buildNodes) {
        exploredLineMesh = ExploredLineMesher.build(
                chunkSet, cellChunkSize, buildNodes, exploredLineMesh.segmentCount(), exploredLineMesh.nodeCount());
    }

    private void clearExploredLineCaches() {
        exploredLinesLastQueryMs = 0L;
        exploredLinesLastDataVersion = Long.MIN_VALUE;
        exploredLinesLastQueryKey = null;
        exploredLinesLastResult = List.of();
        exploredLinesLastCellResult = new CellGrid(0, 0, 0, 0);
        exploredLineRenderCacheKey = null;
        exploredLineMesh = ExploredLineMesher.Result.EMPTY;
    }

    private static int playerLayerColor(String slug, int alphaSource) {
        return ChunkSharePlayerSettings.colorFor(slug, alphaSource);
    }

    /** Draws each imported player's explored lattice and their old/new area outline, in the player's colour. */
    private void renderPlayerExploredLayers(GuiGraphicsExtractor graphics, com.mamiyaotaru.voxelmap.ExploredChunksManager mgr,
            int centerChunkX, int centerChunkZ, int radius, int cellChunkSize, float lineThickness, int selfColor,
            Identifier viewedDimension) {
        java.util.Set<String> slugs = mgr.playerLayerSlugs();
        if (slugs.isEmpty()) {
            return;
        }
        for (String slug : slugs) {
            if (!ChunkSharePlayerSettings.isEnabled(slug)) {
                continue;
            }
            int layerColor = playerLayerColor(slug, selfColor);

            CellGrid cells = mgr.getPlayerExploredCellsInRange(slug, centerChunkX, centerChunkZ, radius, cellChunkSize, viewedDimension);
            ExploredLineMesher.Result mesh = ExploredLineMesher.build(cells, cellChunkSize, false, 0, 0);
            float[] seg = mesh.segments();
            int segCount = mesh.segmentCount();
            for (int i = 0; i < segCount; i++) {
                int o = i << 2;
                appendThickInterpolatedLine(seg[o], seg[o + 1], seg[o + 2], seg[o + 3], lineThickness, layerColor);
            }
        }
    }

    private void drawPlayerLayerStatuses(GuiGraphicsExtractor graphics) {
        this.playerLayerStatusHitboxes.clear();
        java.util.Set<String> slugs = new java.util.LinkedHashSet<>();
        slugs.addAll(VoxelConstants.getVoxelMapInstance().getExploredChunksManager().playerLayerSlugs());
        slugs.addAll(VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().playerLayerSlugs());
        int y = this.top + 5;
        for (String slug : slugs) {
            boolean enabled = ChunkSharePlayerSettings.isEnabled(slug);
            String text = enabled ? "Chunk Share Active: " + layerDisplayName(slug) : "Chunk Share: " + layerDisplayName(slug);
            int color = enabled ? playerLayerColor(slug, 0xFF000000) : 0xFFFFFFFF;
            int x = this.width - this.sideMargin - this.getFont().width(text);
            graphics.text(this.getFont(), text, x, y, color, true);
            this.playerLayerStatusHitboxes.add(new PlayerLayerStatusHitbox(slug, x, y - 1, this.width - this.sideMargin, y + 10));
            y += 11;
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
        float step = Math.max(Math.max(1.0F, thickness * 0.45F), 0.35F / Math.max(0.0001F, this.mapToGui));
        if (this.mapToGui < 0.006F) {
            step = Math.max(step, 10.0F / Math.max(0.0001F, this.mapToGui));
        } else if (this.mapToGui < 0.012F) {
            step = Math.max(step, 7.0F / Math.max(0.0001F, this.mapToGui));
        } else if (this.mapToGui < 0.03F) {
            step = Math.max(step, 4.0F / Math.max(0.0001F, this.mapToGui));
        }
        int steps = Math.max(1, (int) Math.ceil(length / step));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float px = x1 + dx * t;
            float pz = z1 + dz * t;
            appendExploredQuad(px - half, pz - half, px + half, pz + half, color);
        }
    }

    private boolean isFarZoomPerformanceMode() {
        return this.mapToGui < this.options.getPerformanceModeThreshold();
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
        float locate = (float) Math.atan2(wayX, wayY);
        float hypot = (float) Math.sqrt(wayX * wayX + wayY * wayY) * mapToGui;

        double dispX = hypot * Math.sin(locate);
        double dispY = hypot * Math.cos(locate);
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
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
            graphics.pose().rotate(-locate);
            graphics.pose().translate(0.0F, -hypot);
            graphics.pose().rotate(locate);
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
        float locate = (float) Math.atan2(wayX, wayY);
        float hypot = (float) Math.sqrt(wayX * wayX + wayY * wayY) * mapToGui;

        double dispX = hypot * Math.sin(locate);
        double dispY = hypot * Math.cos(locate);
        boolean far = Math.abs(dispX) > borderX || Math.abs(dispY) > borderY;
        if (far) {
            if (!options.showDistantWaypoints) {
                return false;
            }

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
            graphics.pose().rotate(-locate);
            graphics.pose().translate(0.0F, -hypot);
            graphics.pose().rotate(locate);
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

        String searchQuery = getWaypointSearchQuery();
        boolean searchActive = !searchQuery.isEmpty();
        boolean searchMatch = !searchActive || waypointMatchesSearch(waypoint, searchQuery);

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

        boolean showLabel = options.showWaypointNames && searchMatch;
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
        PreviewBounds requestBounds = getSeedPreviewRequestBounds(visibleBounds, mapInMotion);
        int requestTextureWidth;
        int requestTextureHeight;
        if (mapInMotion && this.seedPreviewDisplayedKey != null) {
            requestTextureWidth = this.seedPreviewDisplayedKey.textureWidth();
            requestTextureHeight = this.seedPreviewDisplayedKey.textureHeight();
        } else {
            requestTextureWidth = resolveSeedPreviewTextureWidth();
            requestTextureHeight = resolveSeedPreviewTextureHeight();
        }
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
                this.options.getSeedMapPreviewSettingsHash()
        );
        synchronized (this.seedPreviewLock) {
            this.seedPreviewCacheLimit = this.options.getSeedMapPreviewCacheSize();
            boolean needNew = this.seedPreviewDisplayedKey == null
                    || !canReuseSeedPreviewForRequest(this.seedPreviewDisplayedKey, requestKey);
            boolean allowRequeue = !mapInMotion
                    || this.options.seedMapPreviewUpdateWhileMoving
                    || this.seedPreviewDisplayedKey == null;
            if (needNew && allowRequeue) {
                int[] cached = this.seedPreviewCache.get(requestKey);
                if (cached != null) {
                    this.seedPreviewPendingPixels = cached;
                    this.seedPreviewPendingKey = requestKey;
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

        float previewSpanX = Math.max(1.0F, this.seedPreviewDisplayedKey.maxX() - this.seedPreviewDisplayedKey.minX());
        float previewSpanZ = Math.max(1.0F, this.seedPreviewDisplayedKey.maxZ() - this.seedPreviewDisplayedKey.minZ());
        float minU = (overlapMinX - this.seedPreviewDisplayedKey.minX()) / previewSpanX;
        float maxU = (overlapMaxX - this.seedPreviewDisplayedKey.minX()) / previewSpanX;
        float minV = (overlapMinZ - this.seedPreviewDisplayedKey.minZ()) / previewSpanZ;
        float maxV = (overlapMaxZ - this.seedPreviewDisplayedKey.minZ()) / previewSpanZ;
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
        this.seedPreviewFuture = ThreadManager.executorService.submit(() -> {
            int[] pixels = buildSeedPreviewPixels(requestKey);
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
                && displayedKey.settingsHash() == requestKey.settingsHash();
    }

    private boolean canReuseSeedPreviewForRequest(SeedPreviewQueryCacheKey displayedKey, SeedPreviewQueryCacheKey requestKey) {
        return displayedKey.seed() == requestKey.seed()
                && displayedKey.dimensionIdentifier().equals(requestKey.dimensionIdentifier())
                && displayedKey.dimension() == requestKey.dimension()
                && displayedKey.generatorFlags() == requestKey.generatorFlags()
                && displayedKey.textureWidth() == requestKey.textureWidth()
                && displayedKey.textureHeight() == requestKey.textureHeight()
                && displayedKey.mcVersion() == requestKey.mcVersion()
                && displayedKey.terrainEnabled() == requestKey.terrainEnabled()
                && displayedKey.settingsHash() == requestKey.settingsHash()
                && displayedKey.minX() <= requestKey.minX()
                && displayedKey.maxX() >= requestKey.maxX()
                && displayedKey.minZ() <= requestKey.minZ()
                && displayedKey.maxZ() >= requestKey.maxZ();
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
            boolean terrain = requestKey.terrainEnabled() && requestKey.dimension() == Cubiomes.DIM_OVERWORLD();
            int[] biomeIds = new int[width * height];
            int[] heights = terrain ? new int[width * height] : null;
            AtomicBoolean terrainOk = new AtomicBoolean(true);
            sampleSeedPreviewBands(requestKey, biomeIds, heights, terrainOk);
            int[] effectiveHeights = (heights != null && terrainOk.get()) ? heights : null;
            for (int y = 0; y < height; y++) {
                int rowOffset = y * width;
                for (int x = 0; x < width; x++) {
                    int idx = rowOffset + x;
                    int color = resolveSeedPreviewColor(biomeIds[idx], requestKey.mcVersion());
                    if (effectiveHeights != null) {
                        color = applySeedPreviewTerrainStyle(color, biomeIds[idx], effectiveHeights, width, x, y);
                    }
                    pixels[idx] = ColorUtils.premultiplyWithAlpha(color);
                }
            }
        } catch (RuntimeException ex) {
            VoxelConstants.getLogger().warn("Failed generating SeedMapper world-map preview", ex);
        }
        return pixels;
    }

    private void warmupCubiomes(SeedPreviewQueryCacheKey requestKey) {
        synchronized (SeedMapperNative.cubiomesLock()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment generator = Generator.allocate(arena);
                Cubiomes.setupGenerator(generator, requestKey.mcVersion(), requestKey.generatorFlags());
                Cubiomes.applySeed(generator, requestKey.dimension(), requestKey.seed());
                Cubiomes.getBiomeAt(generator, 4, 0, getSeedPreviewSampleQuartY(requestKey.dimension()), 0);
            }
            if (requestKey.terrainEnabled() && requestKey.dimension() == Cubiomes.DIM_OVERWORLD()) {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment params = TerrainNoise.allocate(arena);
                    if (Cubiomes.setupTerrainNoise(params, requestKey.mcVersion(), requestKey.generatorFlags()) != 0
                            && Cubiomes.initTerrainNoise(params, requestKey.seed(), requestKey.dimension()) != 0) {
                        Cubiomes.samplePreliminarySurfaceLevel(params, 0, 0);
                    }
                }
            }
        }
    }

    private void sampleSeedPreviewBands(SeedPreviewQueryCacheKey requestKey, int[] biomeIds, int[] heights, AtomicBoolean terrainOk) {
        int width = requestKey.textureWidth();
        int height = requestKey.textureHeight();
        int sampleY = getSeedPreviewSampleQuartY(requestKey.dimension());
        double spanX = Math.max(1.0D, requestKey.maxX() - requestKey.minX());
        double spanZ = Math.max(1.0D, requestKey.maxZ() - requestKey.minZ());
        boolean terrain = heights != null;
        int bands = Math.max(1, Math.min(SEED_PREVIEW_WORKER_THREADS, height));
        List<Future<?>> futures = new ArrayList<>(bands);
        for (int b = 0; b < bands; b++) {
            final int y0 = b * height / bands;
            final int y1 = (b + 1) * height / bands;
            futures.add(seedPreviewSampler.submit(() -> {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment generator = Generator.allocate(arena);
                    Cubiomes.setupGenerator(generator, requestKey.mcVersion(), requestKey.generatorFlags());
                    Cubiomes.applySeed(generator, requestKey.dimension(), requestKey.seed());
                    MemorySegment terrainParams = null;
                    if (terrain) {
                        terrainParams = TerrainNoise.allocate(arena);
                        if (Cubiomes.setupTerrainNoise(terrainParams, requestKey.mcVersion(), requestKey.generatorFlags()) == 0
                                || Cubiomes.initTerrainNoise(terrainParams, requestKey.seed(), requestKey.dimension()) == 0) {
                            terrainOk.set(false);
                            terrainParams = null;
                        }
                    }
                    for (int y = y0; y < y1; y++) {
                        int blockZ = Mth.floor(requestKey.minZ() + (y + 0.5D) * spanZ / height);
                        int quartZ = blockZ >> 2;
                        int rowOffset = y * width;
                        for (int x = 0; x < width; x++) {
                            int blockX = Mth.floor(requestKey.minX() + (x + 0.5D) * spanX / width);
                            biomeIds[rowOffset + x] = Cubiomes.getBiomeAt(generator, 4, blockX >> 2, sampleY, quartZ);
                            if (terrainParams != null) {
                                heights[rowOffset + x] = Cubiomes.samplePreliminarySurfaceLevel(terrainParams, blockX, blockZ);
                            }
                        }
                    }
                }
            }));
        }
        awaitSeedPreviewBands(futures);
    }

    private void awaitSeedPreviewBands(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while sampling SeedMap preview", e);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                throw new RuntimeException("SeedMap preview sampling band failed", cause != null ? cause : e);
            }
        }
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
        visibleSeedMapperMarkerCoords.clear();
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

        // Portal markers are detected from world/chunks and must not depend on seed availability.
        if (showPortalMarkers) {
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

        boolean lowDetail = mapToGui < 0.35F;
        boolean ultraLowDetail = mapToGui < 0.20F;
        if (!mapInMotion && !lowDetail && markers.size() < 2000) {
            final double priorityX = GameVariableAccessShim.xCoordDouble();
            final double priorityZ = GameVariableAccessShim.zCoordDouble();
            markers.sort(Comparator.comparingDouble(marker -> {
                double dx = marker.blockX() - priorityX;
                double dz = marker.blockZ() - priorityZ;
                return dx * dx + dz * dz;
            }));
        }

        int markerLimit = Math.max(200, seedMapperOptions.worldMapMarkerLimit);
        if (mapInMotion) {
            if (ultraLowDetail) {
                markerLimit = Math.min(markerLimit, 180);
            } else if (lowDetail) {
                markerLimit = Math.min(markerLimit, 600);
            }
        }
        int maxTotal = markerLimit;
        if (mapInMotion) {
            maxTotal = Math.min(maxTotal, 1200);
            if (ultraLowDetail) {
                maxTotal = Math.min(maxTotal, 120);
            }
        }
        int maxDense = Integer.MAX_VALUE;
        int denseDrawn = 0;
        int totalDrawn = 0;
        int scanned = 0;
        int maxScanned = mapInMotion ? Math.min(Math.max(2500, markerLimit), 6000) : Integer.MAX_VALUE;
        if (mapInMotion && ultraLowDetail) {
            maxScanned = Math.min(maxScanned, 1500);
        }
        int decimationMask = 0;
        if (mapInMotion) {
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
        String summary = seedMapperLoadingSummary == null || seedMapperLoadingSummary.isBlank()
                ? "structures"
                : seedMapperLoadingSummary;
        graphics.text(this.getFont(), "Loading SeedMapper: " + summary, this.sideMargin + 2, this.bottom - 14, 0xFFE0E0E0);
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
        graphics.text(this.getFont(), "Loading SeedMap terrain...", this.sideMargin + 2, y, 0xFFE0E0E0);
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
            if (feature == SeedMapperFeature.DATAPACK_STRUCTURE) {
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
        graphics.fill(seedMapperStripLeft, seedMapperStripTop, seedMapperStripRight, seedMapperStripBottom, 0xA0000000);
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
        int iconWidth = datapackStructure
                ? SeedMapperImportedDatapackManager.iconSizeForPersistentMap()
                : (portalMarker ? ICON_WIDTH + 4 : ICON_WIDTH);
        int iconHeight = datapackStructure
                ? SeedMapperImportedDatapackManager.iconSizeForPersistentMap()
                : (portalMarker ? ICON_HEIGHT + 4 : ICON_HEIGHT);
        int x = this.width / 2;
        int y = this.height / 2;
        int borderX = this.centerX + iconWidth / 2;
        int borderY = this.centerY + iconHeight / 2;

        double wayX = this.mapCenterX - (this.oldNorth ? -ptZ : ptX);
        double wayY = this.mapCenterZ - (this.oldNorth ? ptX : ptZ);
        float locate = (float) Math.atan2(wayX, wayY);
        float hypot = (float) Math.sqrt(wayX * wayX + wayY * wayY) * mapToGui;

        double dispX = hypot * Math.sin(locate);
        double dispY = hypot * Math.cos(locate);
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
        visibleSeedMapperMarkerCoords.add(packXZ(marker.blockX(), marker.blockZ()));
        graphics.pose().popMatrix();
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
        switchToKeyboardInput();
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

    private int resolveSeedPreviewTextureWidth() {
        int viewportWidth = Math.max(1, Math.round((this.centerX * 2) * this.guiToDirectMouse));
        float downsampleFactor = this.guiToMap <= 4.0F ? 1.0F : Mth.clamp(4.0F / this.guiToMap, 0.25F, 1.0F);
        int targetWidth = Mth.ceil(viewportWidth * downsampleFactor);
        int maxWidth = Math.max(SEED_PREVIEW_MIN_TEXTURE_WIDTH, this.options.getSeedMapPreviewResolution());
        return quantizeSeedPreviewTextureSize(targetWidth, SEED_PREVIEW_MIN_TEXTURE_WIDTH, maxWidth);
    }

    private int resolveSeedPreviewTextureHeight() {
        int viewportHeight = Math.max(1, Math.round((this.centerY * 2) * this.guiToDirectMouse));
        float downsampleFactor = this.guiToMap <= 4.0F ? 1.0F : Mth.clamp(4.0F / this.guiToMap, 0.25F, 1.0F);
        int targetHeight = Mth.ceil(viewportHeight * downsampleFactor);
        int maxHeight = Math.max(SEED_PREVIEW_MIN_TEXTURE_HEIGHT, this.options.getSeedMapPreviewResolution());
        return quantizeSeedPreviewTextureSize(targetHeight, SEED_PREVIEW_MIN_TEXTURE_HEIGHT, maxHeight);
    }

    private int quantizeSeedPreviewTextureSize(int value, int min, int max) {
        int clamped = Mth.clamp(value, min, max);
        return Math.max(min, ((clamped + 63) / 64) * 64);
    }

    private PreviewBounds getSeedPreviewRequestBounds(PreviewBounds visibleBounds, boolean mapInMotion) {
        int spanX = Math.max(1, visibleBounds.maxX() - visibleBounds.minX());
        int spanZ = Math.max(1, visibleBounds.maxZ() - visibleBounds.minZ());
        int span = Math.max(spanX, spanZ);
        int padding = Math.max(this.options.getSeedMapPreviewPadding(), span / (mapInMotion ? 3 : 4));
        int snap = Math.max(256, Math.min(8192, Integer.highestOneBit(Math.max(256, padding))));
        int minX = Math.floorDiv(visibleBounds.minX() - padding, snap) * snap;
        int maxX = Math.floorDiv(visibleBounds.maxX() + padding + snap - 1, snap) * snap;
        int minZ = Math.floorDiv(visibleBounds.minZ() - padding, snap) * snap;
        int maxZ = Math.floorDiv(visibleBounds.maxZ() + padding + snap - 1, snap) * snap;
        return new PreviewBounds(minX, maxX, minZ, maxZ);
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
        return dimension == Cubiomes.DIM_END() ? 0 : QuartPos.fromBlock(SEED_PREVIEW_SAMPLE_BLOCK_Y);
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
        int quartX = blockX >> 2;
        int quartZ = blockZ >> 2;
        if (quartX == this.seedBiomeNameQuartX && quartZ == this.seedBiomeNameQuartZ
                && seed == this.seedBiomeNameSeed && dimension == this.seedBiomeNameDimension) {
            return this.seedBiomeNameCached;
        }

        String name = "";
        try {
            SeedMapperNative.ensureLoaded();
            int mcVersion = SeedMapperCompat.getMcVersion();
            int generatorFlags = getSeedMapperGeneratorFlags();
            int sampleY = getSeedPreviewSampleQuartY(dimension);
            synchronized (SeedMapperNative.cubiomesLock()) {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment generator = Generator.allocate(arena);
                    Cubiomes.setupGenerator(generator, mcVersion, generatorFlags);
                    Cubiomes.applySeed(generator, dimension, seed);
                    int biomeId = Cubiomes.getBiomeAt(generator, 4, quartX, sampleY, quartZ);
                    MemorySegment biomeNameSegment = Cubiomes.biome2str(mcVersion, biomeId);
                    if (biomeNameSegment != null && biomeNameSegment.address() != 0L) {
                        name = prettifyBiomeName(biomeNameSegment.getString(0));
                    }
                }
            }
        } catch (RuntimeException ex) {
            name = "";
        }

        this.seedBiomeNameQuartX = quartX;
        this.seedBiomeNameQuartZ = quartZ;
        this.seedBiomeNameSeed = seed;
        this.seedBiomeNameDimension = dimension;
        this.seedBiomeNameCached = name;
        return name;
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
                if (currentLevel.dimension() == Level.NETHER) {
                    yield Cubiomes.DIM_NETHER();
                }
                if (currentLevel.dimension() == Level.END) {
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
            if (!marker.feature().lootable()) {
                return false;
            }

            long seed;
            try {
                seed = resolveWorldMapSeed();
            } catch (IllegalArgumentException ignored) {
                return true;
            }

            int dimension = getCurrentCubiomesDimension();
            int generatorFlags = getSeedMapperGeneratorFlags();
            List<SeedMapperChestLootData> chestData = SeedMapperLootService.buildStructureChestLoot(
                    seed,
                    dimension,
                    SeedMapperCompat.getMcVersion(),
                    generatorFlags,
                    marker.feature(),
                    marker.blockX(),
                    marker.blockZ()
            );
            if (chestData.isEmpty()) {
                minecraft.gui.hud.getChat().addClientSystemMessage(AppChatMessages.prefixed("SeedMapper", "No chest loot data available for this structure."));
                return true;
            }

            int widgetX = Mth.clamp(mouseX + 10, 4, this.width - SeedMapperChestLootWidget.WIDTH - 4);
            int widgetY = Mth.clamp(mouseY + 10, this.top + 4, this.bottom - SeedMapperChestLootWidget.HEIGHT - 4);
            seedMapperChestLootWidget = new SeedMapperChestLootWidget(widgetX, widgetY, chestData);
            return true;
        }
        return false;
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
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean isShiftDown() {
        long window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean isInTopHeader(int mouseX, int mouseY) {
        return mouseY >= 0 && mouseY < this.top;
    }

    private boolean isInSeedHeader(int mouseX, int mouseY) {
        return seedHeaderLeft >= 0
                && seedHeaderRight > seedHeaderLeft
                && seedHeaderTop >= 0
                && seedHeaderBottom > seedHeaderTop
                && mouseX >= seedHeaderLeft
                && mouseX <= seedHeaderRight
                && mouseY >= seedHeaderTop
                && mouseY <= seedHeaderBottom;
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

    private boolean hasVisibleSeedMapperMarkerAt(int x, int z) {
        return visibleSeedMapperMarkerCoords.contains(packXZ(x, z));
    }

    private long packXZ(int x, int z) {
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
        if (this.pendingWaypointLabels.isEmpty()) {
            return;
        }

        float mapCenterX = this.getWidth() / 2.0F;
        float mapCenterY = this.top + (this.bottom - this.top) / 2.0F;
        Comparator<PendingWaypointLabel> comparator = Comparator
                .comparing(PendingWaypointLabel::searchActive).reversed()
                .thenComparing(Comparator.comparing(PendingWaypointLabel::searchMatch).reversed())
                .thenComparing(Comparator.comparing(PendingWaypointLabel::highlighted).reversed())
                .thenComparingInt(PendingWaypointLabel::labelWidth)
                .thenComparingDouble(label -> label.distanceToCenter(mapCenterX, mapCenterY));
        this.pendingWaypointLabels.sort(comparator);

        for (PendingWaypointLabel label : this.pendingWaypointLabels) {
            int rows = options.clusterWaypointNames ? WAYPOINT_LABEL_LINE_LIMIT : 1;
            int row = reserveWaypointLabel(label.centerX, label.iconCenterY, label.baseY, label.labelWidth, label.labelHeight, rows);
            if (row >= 0) {
                int drawX = Math.round(label.centerX) - label.labelWidth / 2;
                int lineStep = label.labelHeight + 1;
                int drawY;
                if ((row & 1) == 0) {
                    drawY = Math.round(label.baseY + (row / 2) * lineStep);
                } else {
                    drawY = Math.round(label.iconCenterY - ICON_HEIGHT / 2.0F - label.labelHeight - WAYPOINT_LABEL_PADDING - (row / 2) * lineStep);
                }
                graphics.text(this.getFont(), label.text, drawX, drawY, label.color, true);
            } else if (options.clusterWaypointNames) {
                registerWaypointCluster(label.clusterKey, label.centerX, label.iconCenterY);
            }
        }

        for (WaypointClusterData cluster : this.waypointClusters.values()) {
            if (cluster.count > 1) {
                drawWaypointClusterBadge(graphics, cluster.anchorX, cluster.anchorY, cluster.count);
            }
        }

        this.pendingWaypointLabels.clear();
        this.waypointLabelBounds.clear();
        this.waypointClusters.clear();
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
                return row;
            }
        }
        return -1;
    }

    private boolean intersectsAnyWaypointLabel(WaypointLabelBounds candidate) {
        for (WaypointLabelBounds bounds : this.waypointLabelBounds) {
            if (bounds.intersects(candidate)) {
                return true;
            }
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

    private record SeedPreviewQueryCacheKey(long seed, Identifier dimensionIdentifier, int dimension, int minX, int maxX, int minZ, int maxZ, int generatorFlags, int textureWidth, int textureHeight, int mcVersion, boolean terrainEnabled, int settingsHash) {
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

    private record SeedMapperQueryCacheKey(long seed, int dimension, int generatorFlags, int minX, int maxX, int minZ, int maxZ, boolean lootOnly, int enabledFeatureHash, int datapackHash, String lootSearch, String datapackWorldKey) {
    }

    private record ExploredLinesQueryCacheKey(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ, int snap, Identifier viewedDimension) {
    }

    private record ExploredLineRenderCacheKey(ExploredLinesQueryCacheKey queryKey, int cellChunkSize, long dataVersion) {
    }

    private record NewOldChunkOverlayRenderCacheKey(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
            int zoomBucket, int cellChunkSize, int oldColor, int newColor, boolean farZoomPerformanceMode,
            boolean movingLod, int maxDraw, long dataVersion, String worldKey) {
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
        synchronized (this.closedLock) {
            this.closed = true;
            this.persistentMap.getRegions(0, -1, 0, -1);
            this.regions = new CachedRegion[0];
        }
        if (this.seedPreviewFuture != null) {
            this.seedPreviewFuture.cancel(false);
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
        entry = new Popup.PopupEntry(I18n.get("minimap.waypoints.teleportTo"), 3, true, true);
        entries.add(entry);
        entry = new Popup.PopupEntry(I18n.get("minimap.waypoints.share"), 2, true, true);
        entries.add(entry);
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
        entries.add(new Popup.PopupEntry(I18n.get("minimap.waypoints.teleportTo"), 3, true, true));
        entries.add(new Popup.PopupEntry(I18n.get("minimap.waypoints.share"), 2, true, true));
        entries.add(new Popup.PopupEntry(completed ? "Mark Incomplete" : "Mark Complete", 8, true, true));
        if (selectedSeedMapperMarker.feature().lootable()) {
            entries.add(new Popup.PopupEntry("Open Loot", 9, true, true));
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
            default -> VoxelConstants.getLogger().warn("unimplemented command");
        }

        if (action >= 7 && action <= 9) {
            selectedSeedMapperMarker = null;
            selectedSeedMapperWorldKey = null;
            selectedSeedMapperWaypoint = null;
            selectedSeedMapperAssociatedWaypoint = null;
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
        return minecraft.font.width(string);
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

