package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.gui.GuiAddWaypoint;
import com.mamiyaotaru.voxelmap.gui.GuiMinimapOptions;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperLootViewer;
import com.mamiyaotaru.voxelmap.gui.GuiWaypoints;
import com.mamiyaotaru.voxelmap.gui.GuiWelcomeScreen;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.interfaces.AbstractMapData;
import com.mamiyaotaru.voxelmap.interfaces.IChangeObserver;
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperLocatorService;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperMarker;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperEspManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCompat;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperImportedDatapackManager;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.textures.TextureAtlas;
import com.mamiyaotaru.voxelmap.util.BiomeRepository;
import com.mamiyaotaru.voxelmap.util.BlockRepository;
import com.mamiyaotaru.voxelmap.util.CPULightmap;
import com.mamiyaotaru.voxelmap.util.ColorUtils;
import com.mamiyaotaru.voxelmap.util.Contact;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.DynamicMutableTexture;
import com.mamiyaotaru.voxelmap.util.FullMapData;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.MapChunkCache;
import com.mamiyaotaru.voxelmap.util.MapUtils;
import com.mamiyaotaru.voxelmap.util.MinimapContext;
import com.mamiyaotaru.voxelmap.util.MutableBlockPos;
import com.mamiyaotaru.voxelmap.util.MutableBlockPosCache;
import com.mamiyaotaru.voxelmap.util.RenderUtils;
import com.mamiyaotaru.voxelmap.util.ScaledDynamicMutableTexture;
import com.mamiyaotaru.voxelmap.util.VoxelMapCachedOrthoProjectionMatrixBuffer;
import com.mamiyaotaru.voxelmap.util.VoxelMapGuiGraphics;
import com.mamiyaotaru.voxelmap.util.VoxelMapRenderTarget;
import com.mamiyaotaru.voxelmap.util.VoxelMapRenderTypes;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.OutOfMemoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public class Map implements Runnable, IChangeObserver {

    private final Minecraft minecraft = Minecraft.getInstance();
    private final MapSettingsManager options;
    private final SeedMapperSettingsManager seedMapperOptions;
    private final ColorManager colorManager;
    private final WaypointManager waypointManager;
    private final MinimapContext minimapContext;
    private final Random generator = new Random();
    private long seedMapperLastMinimapQueryMs = 0L;
    private SeedMapperMinimapQueryKey seedMapperLastMinimapQueryKey;
    private List<SeedMapperMarker> seedMapperLastMinimapMarkers = List.of();

    // Map UI
    private static final float MAP_IMAGE_DEPTH = 0.0F;
    private static final float MAP_OVERLAY_DEPTH = 100.0F;
    private static final float MAP_TEXT_DEPTH = 200.0F;
    private static final String COMPLETED_TICK_GLYPH = "\u2714";
    private final Identifier resourceArrow = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/seedmapper/arrow.png");
    private final Identifier resourceSquareMapFrame = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/minimap/square_map_frame.png");
    private final Identifier resourceSquareMapStencil = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/minimap/square_map_stencil.png");
    private final Identifier resourceRoundMapFrame = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/minimap/round_map_frame.png");
    private final Identifier resourceRoundMapStencil = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/minimap/round_map_stencil.png");
    private final Identifier resourceNetherPortal = Identifier.fromNamespaceAndPath("minecraft", "textures/block/nether_portal.png");
    private final Identifier resourceEndPortalFrame = Identifier.fromNamespaceAndPath("minecraft", "textures/block/end_portal_frame_top.png");
    private final Identifier resourceBedrock = Identifier.fromNamespaceAndPath("minecraft", "textures/block/bedrock.png");
    private int scWidth;
    private int scHeight;
    private String message = "";
    private long messageTime;
    private static double minTablistOffset;
    private static float statusIconOffset = 0.0F;

    // Map Data
    private final int mapDataCount = 5;
    private final FullMapData[] mapData = new FullMapData[this.mapDataCount];
    private final MapChunkCache[] chunkCache = new MapChunkCache[this.mapDataCount];
    private DynamicMutableTexture[] mapImages;
    private Identifier[] mapResources;
    private final DynamicMutableTexture[] mapImagesFiltered = new DynamicMutableTexture[this.mapDataCount];
    private final DynamicMutableTexture[] mapImagesUnfiltered = new DynamicMutableTexture[this.mapDataCount];
    private final Identifier[] resourceMapImageFiltered = new Identifier[this.mapDataCount];
    private final Identifier[] resourceMapImageUnfiltered = new Identifier[this.mapDataCount];

    // Map Core Calculation
    private final int availableProcessors = Runtime.getRuntime().availableProcessors();
    private final boolean multicore = this.availableProcessors > 1;
    private final boolean threading = this.multicore;
    private Thread zCalc = new Thread(this, "Voxelmap LiveMap Calculation Thread");
    private int zCalcTicker;
    private final Object coordinateLock = new Object();
    private ClientLevel world;
    private int updateTimer;
    private boolean doFullRender = true;
    private boolean imageChanged = true;

    // Map Terrain Calculation
    private final int heightMapResetHeight = this.multicore ? 2 : 5;
    private final int heightMapResetTime = this.multicore ? 300 : 3000;
    private int heightMapFudge;
    private boolean lastBeneathRendering;
    private BlockState transparentBlockState;
    private BlockState surfaceBlockState;

    // Map Player Calculation
    private boolean zoomChanged;
    private int zoom;
    private double zoomScale = 1.0;
    private double zoomScaleAdjusted = 1.0;
    private int lastX;
    private int lastZ;
    private int lastY;
    private int lastImageX;
    private int lastImageZ;
    private float direction;
    private int rotationFactor;
    private boolean fullscreenMap;
    private boolean lastFullscreen;
    private Screen lastGuiScreen;

    // Map Light Calculation
    private boolean needLightmapRefresh = true;
    private int tickWithLightChange;
    private final int[] lightmapColors = new int[256];
    private final int[] lastLightmapValues = new int[16];
    private boolean needSkyColor;
    private int lastSkyColor;
    private double lastGamma;
    private float lastSunBrightness;
    private float lastLightning;
    private float lastNightVision;
    private boolean lastPaused = true;
    private boolean lastAboveHorizon = true;
    private int lastBiome;

    // Map Rendering
    private final MultiBufferSource.BufferSource renderBufferSource;
    private final Matrix4fStack renderMatrixStack = new Matrix4fStack(16);
    private final VoxelMapCachedOrthoProjectionMatrixBuffer mapProjection;
    private final VoxelMapRenderTarget hudRenderTarget; // Used for entire VoxelMap HUD rendering
    private final VoxelMapRenderTarget baseMapRenderTarget; // Used for minimap rendering before masking
    private final VoxelMapRenderTarget finalMapRenderTarget; // Used for minimap rendering after masking
    private float lastStableMinimapScaleProj = 1.0F;
    private float pendingMinimapScaleProj = 1.0F;
    private int pendingMinimapScaleProjFrames = 0;

    public Map() {
        this.options = VoxelConstants.getVoxelMapInstance().getMapOptions();
        this.seedMapperOptions = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        this.colorManager = VoxelConstants.getVoxelMapInstance().getColorManager();
        this.waypointManager = VoxelConstants.getVoxelMapInstance().getWaypointManager();
        this.minimapContext = new MinimapContext();
        ArrayList<KeyMapping> tempBindings = new ArrayList<>();
        tempBindings.addAll(Arrays.asList(minecraft.options.keyMappings));
        tempBindings.addAll(Arrays.asList(this.options.keyBindings));
        minecraft.options.keyMappings = tempBindings.toArray(new KeyMapping[0]);

        this.zCalc.start();

        for (int i = 0; i < this.mapDataCount; i++) {
            resourceMapImageFiltered[i] = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, String.format("map/filtered/%s", i));
            resourceMapImageUnfiltered[i] = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, String.format("map/unfiltered/%s", i));

            int resolution = 1 << (i + 5); // 32, 64, ...
            int chunks = (1 << (i + 1)) + 1; //3, 5, ...

            this.mapData[i] = new FullMapData(resolution, resolution);
            this.chunkCache[i] = new MapChunkCache(chunks, chunks, this);

            this.mapImagesFiltered[i] = new DynamicMutableTexture(String.format("voxelmap-map-%s", resolution), resolution, resolution, true);
            this.mapImagesFiltered[i].sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            minecraft.getTextureManager().register(resourceMapImageFiltered[i], this.mapImagesFiltered[i]);

            this.mapImagesUnfiltered[i] = new ScaledDynamicMutableTexture(String.format("voxelmap-map-unfiltered-%s", resolution), resolution, resolution, true);
            this.mapImagesUnfiltered[i].sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            minecraft.getTextureManager().register(resourceMapImageUnfiltered[i], this.mapImagesUnfiltered[i]);

        }

        if (this.options.filtering) {
            this.mapImages = this.mapImagesFiltered;
            this.mapResources = resourceMapImageFiltered;
        } else {
            this.mapImages = this.mapImagesUnfiltered;
            this.mapResources = resourceMapImageUnfiltered;
        }

        this.zoom = this.options.zoom;
        this.setZoomScale();

        this.renderBufferSource = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
        this.mapProjection = new VoxelMapCachedOrthoProjectionMatrixBuffer("VoxelMap Map To Screen Proj", -256.0F, 256.0F, 256.0F, -256.0F, 1000.0F, 21000.0F);

        final int fboTextureSize = 512;

        this.hudRenderTarget = new VoxelMapRenderTarget(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "render_target/voxelmap_gui"));
        this.hudRenderTarget.createBuffers(fboTextureSize, fboTextureSize);

        this.baseMapRenderTarget = new VoxelMapRenderTarget(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "render_target/voxelmap_base_map"));
        this.baseMapRenderTarget.createBuffers(fboTextureSize, fboTextureSize);

        this.finalMapRenderTarget = new VoxelMapRenderTarget(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "render_target/voxelmap_final_map"));
        this.finalMapRenderTarget.createBuffers(fboTextureSize, fboTextureSize);

        this.loadMapTextures();
    }

    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.loadMapTextures();
    }

    private void loadMapTextures() {
        boolean arrowFiltering = Boolean.parseBoolean(VoxelConstants.getVoxelMapInstance().getImageProperties().getProperty("minimap_arrow_filtering", "true"));
        FilterMode arrowFilterMode = arrowFiltering ? FilterMode.LINEAR : FilterMode.NEAREST;

        boolean frameFiltering = Boolean.parseBoolean(VoxelConstants.getVoxelMapInstance().getImageProperties().getProperty("minimap_frame_filtering", "true"));
        FilterMode frameFilterMode = frameFiltering ? FilterMode.LINEAR : FilterMode.NEAREST;

        try {
            DynamicTexture arrowTexture = new DynamicTexture(() -> "Minimap Arrow", TextureContents.load(Minecraft.getInstance().getResourceManager(), resourceArrow).image());
            arrowTexture.sampler = RenderSystem.getSamplerCache().getClampToEdge(arrowFilterMode);
            minecraft.getTextureManager().register(resourceArrow, arrowTexture);

            DynamicTexture squareMapTexture = new DynamicTexture(() -> "Minimap Square Map Frame", TextureContents.load(Minecraft.getInstance().getResourceManager(), resourceSquareMapFrame).image());
            squareMapTexture.sampler = RenderSystem.getSamplerCache().getClampToEdge(frameFilterMode);
            minecraft.getTextureManager().register(resourceSquareMapFrame, squareMapTexture);

            DynamicTexture roundMapTexture = new DynamicTexture(() -> "Minimap Round Map Frame", TextureContents.load(Minecraft.getInstance().getResourceManager(), resourceRoundMapFrame).image());
            roundMapTexture.sampler = RenderSystem.getSamplerCache().getClampToEdge(frameFilterMode);
            minecraft.getTextureManager().register(resourceRoundMapFrame, roundMapTexture);
        } catch (Exception exception) {
            VoxelConstants.getLogger().error("Failed getting map images " + exception.getLocalizedMessage(), exception);
        }
    }

    public void forceFullRender(boolean forceFullRender) {
        this.doFullRender = forceFullRender;
        VoxelConstants.getVoxelMapInstance().getSettingsAndLightingChangeNotifier().notifyOfChanges();
    }

    @Override
    public void run() {
        if (minecraft != null) {
            while (true) {
                if (this.world != null) {
                    if (!this.options.hide && this.options.minimapAllowed) {
                        try {
                            this.mapCalc(this.doFullRender);
                            if (!this.doFullRender) {
                                MutableBlockPos blockPos = MutableBlockPosCache.get();
                                this.chunkCache[this.zoom].centerChunks(blockPos.withXYZ(this.lastX, 0, this.lastZ));
                                MutableBlockPosCache.release(blockPos);
                                this.chunkCache[this.zoom].checkIfChunksChanged();
                            }
                        } catch (Exception exception) {
                            VoxelConstants.getLogger().error("Voxelmap LiveMap Calculation Thread", exception);
                        }
                    }

                    this.doFullRender = this.zoomChanged;
                    this.zoomChanged = false;
                }

                this.zCalcTicker = 0;
                synchronized (this.zCalc) {
                    try {
                        this.zCalc.wait(0L);
                    } catch (InterruptedException exception) {
                        VoxelConstants.getLogger().error("Voxelmap LiveMap Calculation Thread", exception);
                    }
                }
            }
        }

    }

    public void newWorld(ClientLevel world) {
        this.world = world;
        this.mapData[this.zoom].blank();
        this.doFullRender = true;
        VoxelConstants.getVoxelMapInstance().getSettingsAndLightingChangeNotifier().notifyOfChanges();
    }

    public void newWorldName() {
        String subworldName = this.waypointManager.getCurrentSubworldDescriptor(true);
        StringBuilder subworldNameBuilder = (new StringBuilder("§r")).append(I18n.get("worldmap.multiworld.newWorld")).append(":").append(" ");
        if (subworldName.isEmpty() && this.waypointManager.isMultiworld()) {
            subworldNameBuilder.append("???");
        } else if (!subworldName.isEmpty()) {
            subworldNameBuilder.append(subworldName);
        }

        this.showMessage(subworldNameBuilder.toString());
    }

    public void onTickInGame(GuiGraphicsExtractor graphics) {
        this.rotationFactor = this.options.oldNorth ? 90 : 0;

        if (minecraft.screen == null && this.options.welcome) {
            minecraft.setScreen(new GuiWelcomeScreen(null));
        }

        if (minecraft.screen == null && this.options.keyBindMenu.consumeClick()) {
            minecraft.setScreen(new GuiPersistentMap(null));
        }

        if (minecraft.screen == null && this.options.keyBindWaypointMenu.consumeClick()) {
            if (options.waypointsAllowed) {
                minecraft.setScreen(new GuiWaypoints(null));
            }
        }

        if (minecraft.screen == null && this.options.keyBindWaypoint.consumeClick()) {
            if (options.waypointsAllowed) {
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
                dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(VoxelConstants.getPlayer().level()));
                Waypoint newWaypoint = new Waypoint("", GameVariableAccessShim.xCoord(), GameVariableAccessShim.zCoord(), GameVariableAccessShim.yCoord(), true, r, g, b, "",
                        VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions);
                minecraft.setScreen(new GuiAddWaypoint(null, newWaypoint, false));
            }
        }

        if (minecraft.screen == null && this.options.keyBindMobToggle.consumeClick()) {
            VoxelConstants.getVoxelMapInstance().getRadarOptions().toggleBooleanValue(EnumOptionsMinimap.SHOW_RADAR);
            this.options.saveAll();
        }

        if (minecraft.screen == null && this.options.keyBindWaypointToggle.consumeClick()) {
            this.options.toggleIngameWaypoints();
        }

        if (minecraft.screen == null && this.options.keyBindZoom.consumeClick()) {
            this.cycleZoomLevel();
        }

        if (minecraft.screen == null && this.options.keyBindFullscreen.consumeClick()) {
            this.fullscreenMap = !this.fullscreenMap;
            this.doFullRender = true;
            this.imageChanged = true;
            this.showMessage(I18n.get("minimap.ui.zoomLevel", 2.0 / this.zoomScale));
        }

        if (minecraft.screen == null && this.options.keyBindMinimapToggle.consumeClick()) {
            this.options.toggleBooleanValue(EnumOptionsMinimap.HIDE_MINIMAP);
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperToggleOverlay.consumeClick()) {
            seedMapperOptions.enabled = !seedMapperOptions.enabled;
            this.options.saveAll();
            this.showMessage("SeedMapper Overlay: " + (seedMapperOptions.enabled ? "ON" : "OFF"));
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperBlockHighlightEsp.consumeClick()) {
            if (seedMapperOptions.espEnabled) {
                seedMapperOptions.espEnabled = false;
                this.options.saveAll();
                this.showMessage("SeedMapper Block Highlight ESP: OFF");
            } else {
                seedMapperOptions.espEnabled = true;
                this.options.saveAll();
                int chunks = Math.max(0, Math.min(8, seedMapperOptions.espDefaultChunks));
                String target = seedMapperOptions.espTarget == null ? "" : seedMapperOptions.espTarget.trim();
                if (!target.isBlank()) {
                    SeedMapperCommandHandler.handleChatCommand("seedmap highlight ore " + target + " " + chunks);
                }
                this.showMessage("SeedMapper Block Highlight ESP: ON");
            }
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperClearEsp.consumeClick()) {
            SeedMapperEspManager.clear();
            this.showMessage("SeedMapper ESP cleared");
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperOreVeinEsp.consumeClick()) {
            int chunks = Math.max(0, Math.min(8, seedMapperOptions.espDefaultChunks));
            seedMapperOptions.espEnabled = true;
            this.options.saveAll();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight orevein " + chunks);
            this.showMessage("SeedMapper ore vein ESP run");
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperCanyonEsp.consumeClick()) {
            int chunks = Math.max(0, Math.min(8, seedMapperOptions.espDefaultChunks));
            seedMapperOptions.espEnabled = true;
            this.options.saveAll();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight canyon " + chunks);
            this.showMessage("SeedMapper canyon ESP run");
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperCaveEsp.consumeClick()) {
            int chunks = Math.max(0, Math.min(8, seedMapperOptions.espDefaultChunks));
            seedMapperOptions.espEnabled = true;
            this.options.saveAll();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight cave " + chunks);
            this.showMessage("SeedMapper cave ESP run");
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperTerrainEsp.consumeClick()) {
            int chunks = Math.max(0, Math.min(8, seedMapperOptions.espDefaultChunks));
            seedMapperOptions.espEnabled = true;
            this.options.saveAll();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight terrain " + chunks);
            this.showMessage("SeedMapper terrain ESP run");
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperLootViewer.consumeClick()) {
            minecraft.setScreen(new GuiSeedMapperLootViewer(null));
        }

        if (minecraft.screen == null && this.options.keyBindSeedMapperSettings.consumeClick()) {
            minecraft.setScreen(new GuiMinimapOptions(null, 5));
        }

        this.checkForChanges();
        if (options.deathWaypointAllowed && minecraft.screen instanceof DeathScreen && !(this.lastGuiScreen instanceof DeathScreen)) {
            this.waypointManager.handleDeath();
        }

        this.lastGuiScreen = minecraft.screen;
        this.calculateCurrentLightAndSkyColor();
        if (this.threading) {
            if (!this.zCalc.isAlive()) {
                this.zCalc = new Thread(this, "Voxelmap LiveMap Calculation Thread");
                this.zCalc.start();
                this.zCalcTicker = 0;
            }

            if (!(minecraft.screen instanceof DeathScreen) && !(minecraft.screen instanceof OutOfMemoryScreen)) {
                ++this.zCalcTicker;
                if (this.zCalcTicker > 2000) {
                    this.zCalcTicker = 0;
                    Exception ex = new Exception();
                    ex.setStackTrace(this.zCalc.getStackTrace());
                    DebugRenderState.print();
                    VoxelConstants.getLogger().error("Voxelmap LiveMap Calculation Thread is hanging?", ex);
                }
                synchronized (this.zCalc) {
                    this.zCalc.notify();
                }
            }
        } else {
            if (!this.options.hide && this.options.minimapAllowed && this.world != null) {
                this.mapCalc(this.doFullRender);
                if (!this.doFullRender) {
                    MutableBlockPos blockPos = MutableBlockPosCache.get();
                    this.chunkCache[this.zoom].centerChunks(blockPos.withXYZ(this.lastX, 0, this.lastZ));
                    MutableBlockPosCache.release(blockPos);
                    this.chunkCache[this.zoom].checkIfChunksChanged();
                }
            }

            this.doFullRender = false;
        }

        boolean enabled = !minecraft.options.hideGui && (this.options.showUnderMenus || minecraft.screen == null) && !minecraft.debugEntries.isOverlayVisible();

        this.direction = GameVariableAccessShim.rotationYaw() + 180.0F;

        while (this.direction >= 360.0F) {
            this.direction -= 360.0F;
        }

        while (this.direction < 0.0F) {
            this.direction += 360.0F;
        }

        if (!this.message.isEmpty() && (System.currentTimeMillis() - this.messageTime > 3000L)) {
            this.message = "";
        }

        if (enabled && options.minimapAllowed) {
            this.drawMinimap(graphics);
        }

        if (enabled && options.waypointsAllowed && options.waypointCompass) {
            this.drawWaypointCompass(graphics);
        }

        this.updateTimer = this.updateTimer > 5000 ? 0 : this.updateTimer + 1;
    }

    private void drawWaypointCompass(GuiGraphicsExtractor graphics) {
        if (minecraft.player == null) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int centerX = Mth.clamp(Math.round(width * options.waypointCompassX / 100.0F), 80, Math.max(80, width - 80));
        int barWidth = Math.min(504, Math.max(180, width - 80));
        int barLeft = centerX - barWidth / 2;
        int barRight = centerX + barWidth / 2;
        int barY = Mth.clamp(Math.round(height * options.waypointCompassY / 100.0F), 2, Math.max(2, height - 36));
        int backgroundAlpha = Mth.clamp(Math.round(options.waypointCompassBackgroundOpacity * 255.0F / 100.0F), 0, 255);
        if (backgroundAlpha > 0) {
            graphics.fill(barLeft, barY + 6, barRight, barY + 15, backgroundAlpha << 24);
        }

        if (options.waypointCompassShowCoords) {
            String coords = "X: " + Mth.floor(playerX()) + " Y: " + Mth.floor(playerY()) + " Z: " + Mth.floor(playerZ());
            drawCompassText(graphics, coords, centerX, barY - 12, 0xFFFFFFFF, true);
        }

        int rendered = 0;
        double playerX = minecraft.player.getX();
        double playerY = minecraft.player.getY();
        double playerZ = minecraft.player.getZ();
        int maxDistance = options.waypointCompassIconRange;
        ArrayList<CompassWaypoint> compassWaypoints = new ArrayList<>();

        for (Waypoint waypoint : waypointManager.getWaypoints()) {
            if (!waypoint.isActive()) {
                continue;
            }

            double dx = waypoint.getXInCurrentDimension() + 0.5D - playerX;
            double dz = waypoint.getZInCurrentDimension() + 0.5D - playerZ;
            double distanceSq = dx * dx + dz * dz + Math.pow(waypoint.getY() + 0.5D - playerY, 2.0D);
            if (maxDistance > -1 && distanceSq > maxDistance * maxDistance) {
                continue;
            }

            double wayX = playerX - waypoint.getXInCurrentDimension() - 0.5D;
            double wayZ = playerZ - waypoint.getZInCurrentDimension() - 0.5D;
            float deltaYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(wayX, wayZ)) + this.direction);
            if (Math.abs(deltaYaw) > 90.0F) {
                continue;
            }

            compassWaypoints.add(new CompassWaypoint(waypoint, deltaYaw, (int) Math.sqrt(distanceSq)));
        }

        compassWaypoints.sort((first, second) -> {
            int angleCompare = Float.compare(Math.abs(first.deltaYaw()), Math.abs(second.deltaYaw()));
            return angleCompare != 0 ? angleCompare : Integer.compare(first.distance(), second.distance());
        });

        int maxRendered = Mth.clamp(options.waypointCompassMaxWaypoints, 1, 64);
        for (CompassWaypoint compassWaypoint : compassWaypoints) {
            Waypoint waypoint = compassWaypoint.waypoint();
            int x = centerX - Math.round(compassWaypoint.deltaYaw() / 90.0F * (barWidth / 2.0F));
            String label = waypoint.name;
            String distanceLabel = compassWaypoint.distance() + "m";
            int textWidth = minecraft.font.width(label);
            int textX = Mth.clamp(x, barLeft + textWidth / 2 + 2, barRight - textWidth / 2 - 2);
            int color = withOpacity(waypoint.getUnifiedColor(), options.waypointCompassTextOpacity);
            drawCompassText(graphics, "♦", x, barY + 6, color, false);
            drawCompassText(graphics, label, textX, barY + 22, color, true);
            drawCompassText(graphics, distanceLabel, textX, barY + 32, color, true);

            if (++rendered >= maxRendered) {
                break;
            }
        }
    }

    private record CompassWaypoint(Waypoint waypoint, float deltaYaw, int distance) {
    }

    private double playerX() {
        return minecraft.player == null ? 0.0D : minecraft.player.getX();
    }

    private double playerY() {
        return minecraft.player == null ? 0.0D : minecraft.player.getY();
    }

    private double playerZ() {
        return minecraft.player == null ? 0.0D : minecraft.player.getZ();
    }

    private void drawCompassText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean outline) {
        if (outline && options.waypointCompassTextOutline) {
            int outlineColor = withOpacity(0xFF000000, options.waypointCompassOutlineOpacity);
            graphics.centeredText(minecraft.font, Component.literal(text), x - 1, y, outlineColor);
            graphics.centeredText(minecraft.font, Component.literal(text), x + 1, y, outlineColor);
            graphics.centeredText(minecraft.font, Component.literal(text), x, y - 1, outlineColor);
            graphics.centeredText(minecraft.font, Component.literal(text), x, y + 1, outlineColor);
        }
        graphics.centeredText(minecraft.font, Component.literal(text), x, y, color);
    }

    private int withOpacity(int color, int opacity) {
        int alpha = Mth.clamp(Math.round(opacity * 255.0F / 100.0F), 0, 255);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void cycleZoomLevel() {
        if (--this.zoom < 0) {
            this.zoom = this.mapDataCount - 1;
        }
        this.setZoomLevel(this.zoom);
        this.showMessage(I18n.get("minimap.ui.zoomLevel", 2.0 / this.zoomScale));

    }

    public void setZoomLevel(int zoom) {
        int clampedZoom = Mth.clamp(zoom, 0, this.mapDataCount - 1);
        if (this.zoom == clampedZoom && this.options.zoom == clampedZoom) {
            return;
        }

        this.zoom = clampedZoom;
        this.options.zoom = clampedZoom;
        this.options.saveAll();
        this.zoomChanged = true;
        this.setZoomScale();
        this.doFullRender = true;
    }

    private void setZoomScale() {
        this.zoomScale = Math.pow(2.0, this.zoom) / 2.0;
        if (this.options.squareMap && this.options.rotates) {
            this.zoomScaleAdjusted = this.zoomScale / 1.4142F;
        } else {
            this.zoomScaleAdjusted = this.zoomScale;
        }

    }

    public void calculateCurrentLightAndSkyColor() {
        try {
            if (this.world != null) {
                if (this.needLightmapRefresh && VoxelConstants.getElapsedTicks() != this.tickWithLightChange && !minecraft.isPaused() || this.options.realTimeTorches) {
                    this.needLightmapRefresh = false;
                    CPULightmap lightmap = CPULightmap.getInstance();
                    lightmap.setup();
                    for (int blockLight = 0; blockLight < 16; blockLight++) {
                        for (int skyLight = 0; skyLight < 16; skyLight++) {
                            this.lightmapColors[blockLight + skyLight * 16] = lightmap.getLight(blockLight, skyLight);
                        }
                    }
                }

                boolean lightChanged = false;
                if (minecraft.options.gamma().get() != this.lastGamma) {
                    lightChanged = true;
                    this.lastGamma = minecraft.options.gamma().get();
                }

                float sunBrightness = 1 - (this.world.getSkyDarken() / 15f);
                if (Math.abs(this.lastSunBrightness - sunBrightness) > 0.01 || sunBrightness == 1.0 && sunBrightness != this.lastSunBrightness || sunBrightness == 0.0 && sunBrightness != this.lastSunBrightness) {
                    lightChanged = true;
                    this.needSkyColor = true;
                    this.lastSunBrightness = sunBrightness;
                }

                float nightVision = 0.0F;
                if (VoxelConstants.getPlayer().hasEffect(MobEffects.NIGHT_VISION)) {
                    int duration = VoxelConstants.getPlayer().getEffect(MobEffects.NIGHT_VISION).getDuration();
                    nightVision = duration > 200 ? 1.0F : 0.7F + Mth.sin((duration - 1.0F) * (float) Math.PI * 0.2F) * 0.3F;
                }

                if (this.lastNightVision != nightVision) {
                    this.lastNightVision = nightVision;
                    lightChanged = true;
                }

                int lastLightningBolt = this.world.getSkyFlashTime();
                if (this.lastLightning != lastLightningBolt) {
                    this.lastLightning = lastLightningBolt;
                    lightChanged = true;
                }

                if (this.lastPaused != minecraft.isPaused()) {
                    this.lastPaused = !this.lastPaused;
                    lightChanged = true;
                }

                boolean scheduledUpdate = (this.updateTimer - 50) % 50 == 0;
                if (lightChanged || scheduledUpdate) {
                    this.tickWithLightChange = VoxelConstants.getElapsedTicks();
                    this.needLightmapRefresh = true;
                }

                boolean aboveHorizon = VoxelConstants.getPlayer().getEyePosition(0.0F).y >= this.world.getLevelData().getHorizonHeight(this.world);
                if (this.world.dimension().identifier().toString().toLowerCase().contains("ether")) {
                    aboveHorizon = true;
                }

                if (aboveHorizon != this.lastAboveHorizon) {
                    this.needSkyColor = true;
                    this.lastAboveHorizon = aboveHorizon;
                }

                MutableBlockPos blockPos = MutableBlockPosCache.get();
                int biomeID = this.world.registryAccess().lookupOrThrow(Registries.BIOME).getId(this.world.getBiome(blockPos.withXYZ(GameVariableAccessShim.xCoord(), GameVariableAccessShim.yCoord(), GameVariableAccessShim.zCoord())).value());
                MutableBlockPosCache.release(blockPos);
                if (biomeID != this.lastBiome) {
                    this.needSkyColor = true;
                    this.lastBiome = biomeID;
                }

                if (this.needSkyColor || scheduledUpdate) {
                    this.colorManager.setSkyColor(this.getSkyColor());
                }
            }
        } catch (NullPointerException ignore) {

        }
    }

    private int getSkyColor() {
        this.needSkyColor = false;
        boolean aboveHorizon = this.lastAboveHorizon;
        Vector4f color = new Vector4f();
        minecraft.gameRenderer.fogRenderer.computeFogColor(minecraft.gameRenderer.getMainCamera(), 0.0F, this.world, minecraft.options.renderDistance().get(), minecraft.gameRenderer.getBossOverlayWorldDarkening(0.0F), color);
        int r = (int) (color.x * 255.0F);
        int g = (int) (color.y * 255.0F);
        int b = (int) (color.z * 255.0F);
        if (!aboveHorizon) {
            return 0x0A000000 | (r << 16) | (g << 8) | b;
        } else {
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    public int[] getLightmapArray() {
        return this.lightmapColors;
    }

    public int getLightmapColor(int skyLight, int blockLight) {
        if (this.lightmapColors == null) {
            return 0;
        }
        return ARGB.toABGR(this.lightmapColors[blockLight + skyLight * 16]);
    }

    public void drawMinimap(GuiGraphicsExtractor graphics) {
        int scScaleOrig = 1;

        while (minecraft.getWindow().getWidth() / (scScaleOrig + 1) >= 320 && minecraft.getWindow().getHeight() / (scScaleOrig + 1) >= 240) {
            ++scScaleOrig;
        }

        int safeSizeModifier = Mth.clamp(this.options.sizeModifier, -1, 4);
        int scScale = Math.max(1, scScaleOrig + (this.fullscreenMap ? 0 : safeSizeModifier));
        double scaledWidthD = (double) minecraft.getWindow().getWidth() / scScale;
        double scaledHeightD = (double) minecraft.getWindow().getHeight() / scScale;
        this.scWidth = Mth.ceil(scaledWidthD);
        this.scHeight = Mth.ceil(scaledHeightD);
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        float scaleProj = (float) (scScale) / guiScale;
        if (!this.fullscreenMap) {
            // Allow larger minimap sizes from the "Map Size" setting.
            scaleProj = Mth.clamp(scaleProj, 0.5F, 4.0F);
            scaleProj = stabilizeMinimapScaleProjection(scaleProj);
        }
        final float finalScaleProj = scaleProj;

        int mapX;
        if (this.options.mapCorner != 0 && this.options.mapCorner != 3) {
            mapX = this.scWidth - 37;
        } else {
            mapX = 37;
        }

        int mapY;
        if (this.options.mapCorner != 0 && this.options.mapCorner != 1) {
            mapY = this.scHeight - 37;
        } else {
            mapY = 37;
        }

        float statusIconOffset = 0.0F;
        if (options.moveMapBelowStatusEffectIcons) {
            if (this.options.mapCorner == 1 && !VoxelConstants.getPlayer().getActiveEffects().isEmpty()) {

                for (MobEffectInstance statusEffectInstance : VoxelConstants.getPlayer().getActiveEffects()) {
                    if (statusEffectInstance.showIcon()) {
                        if (statusEffectInstance.getEffect().value().isBeneficial()) {
                            statusIconOffset = Math.max(statusIconOffset, 24.0F);
                        } else {
                            statusIconOffset = 50.0F;
                        }
                    }
                }
                int scHeight = minecraft.getWindow().getGuiScaledHeight();
                float resFactor = (float) this.scHeight / scHeight;
                mapY += (int) (statusIconOffset * resFactor);
            }
        }
        Map.statusIconOffset = statusIconOffset;

        this.minimapContext.updateVars(
                GameVariableAccessShim.xCoordDouble(),
                GameVariableAccessShim.yCoordDouble(),
                GameVariableAccessShim.zCoordDouble(),
                (this.options.rotates && !this.fullscreenMap) ? this.direction : -this.rotationFactor,
                this.zoomScale,
                this.zoomScaleAdjusted
        );

        // Hard reset every frame so transient push/pop mismatches can't accumulate into giant minimap transforms.
        renderMatrixStack.clear();
        renderMatrixStack.identity();

        int mapY2 = mapY;
        RenderUtils.renderWithFullscreenProjection(hudRenderTarget, () -> {
            renderMatrixStack.pushMatrix();
            try {
                if (!this.options.hide) {
                    if (this.fullscreenMap) {
                        this.renderMapFull(renderMatrixStack, scWidth, scHeight, finalScaleProj);
                        this.drawArrow(renderMatrixStack, scWidth / 2, scHeight / 2, finalScaleProj);
                    } else {
                        this.renderMap(renderMatrixStack, mapX, mapY2, scScale, finalScaleProj);
                        this.drawArrow(renderMatrixStack, mapX, mapY2, finalScaleProj);
                        this.drawDirections(renderMatrixStack, mapX, mapY2, finalScaleProj);
                    }
                }
                this.showCoords(renderMatrixStack, mapX, mapY2, finalScaleProj);
            } finally {
                renderMatrixStack.popMatrix();
                renderMatrixStack.clear();
                renderMatrixStack.identity();
                renderBufferSource.endBatch();
            }
        });

        VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED, hudRenderTarget.colorTextureId, 0.0F, 0.0F, RenderUtils.getGuiWidth(), RenderUtils.getGuiHeight(), 0.0F, 1.0F, 0.0F, 1.0F, 0xFFFFFFFF);
    }

    private float stabilizeMinimapScaleProjection(float rawScaleProj) {
        if (!Float.isFinite(rawScaleProj)) {
            return this.lastStableMinimapScaleProj;
        }

        float immediateDeltaThreshold = 0.35F;
        int persistFramesRequired = 3;

        float delta = Math.abs(rawScaleProj - this.lastStableMinimapScaleProj);
        if (delta <= immediateDeltaThreshold) {
            this.lastStableMinimapScaleProj = rawScaleProj;
            this.pendingMinimapScaleProj = rawScaleProj;
            this.pendingMinimapScaleProjFrames = 0;
            return this.lastStableMinimapScaleProj;
        }

        if (Math.abs(rawScaleProj - this.pendingMinimapScaleProj) <= 0.01F) {
            this.pendingMinimapScaleProjFrames++;
        } else {
            this.pendingMinimapScaleProj = rawScaleProj;
            this.pendingMinimapScaleProjFrames = 1;
        }

        // Accept only if the jump persists, so one-frame spikes are ignored.
        if (this.pendingMinimapScaleProjFrames >= persistFramesRequired) {
            this.lastStableMinimapScaleProj = this.pendingMinimapScaleProj;
            this.pendingMinimapScaleProjFrames = 0;
        }

        return this.lastStableMinimapScaleProj;
    }

    private void checkForChanges() {
        boolean changed = false;
        if (this.colorManager.checkForChanges()) {
            changed = true;
        }

        if (this.options.isChanged()) {
            if (this.options.filtering) {
                this.mapImages = this.mapImagesFiltered;
                this.mapResources = resourceMapImageFiltered;
            } else {
                this.mapImages = this.mapImagesUnfiltered;
                this.mapResources = resourceMapImageUnfiltered;
            }

            changed = true;
            this.setZoomScale();
        }

        if (changed) {
            this.doFullRender = true;
            VoxelConstants.getVoxelMapInstance().getSettingsAndLightingChangeNotifier().notifyOfChanges();
        }

    }

    private void mapCalc(boolean full) {
        int currentX = GameVariableAccessShim.xCoord();
        int currentZ = GameVariableAccessShim.zCoord();
        int currentY = GameVariableAccessShim.yCoord();
        int offsetX = currentX - this.lastX;
        int offsetZ = currentZ - this.lastZ;
        int offsetY = currentY - this.lastY;
        int zoom = this.zoom;
        int multi = (int) Math.pow(2.0, zoom);
        ClientLevel world = this.world;
        boolean needHeightAndID;
        boolean needHeightMap = false;
        boolean needLight = false;
        boolean skyColorChanged = false;
        int skyColor = this.colorManager.getAirColor();
        if (this.lastSkyColor != skyColor) {
            skyColorChanged = true;
            this.lastSkyColor = skyColor;
        }

        if (this.options.dynamicLighting) {
            int torchOffset = this.options.realTimeTorches ? 8 : 0;
            for (int t = 0; t < 16; ++t) {
                int newValue = getLightmapColor(t, torchOffset);
                if (this.lastLightmapValues[t] != newValue) {
                    needLight = true;
                    this.lastLightmapValues[t] = newValue;
                }
            }
        }

        if (offsetY != 0) {
            ++this.heightMapFudge;
        } else if (this.heightMapFudge != 0) {
            ++this.heightMapFudge;
        }

        if (full || Math.abs(offsetY) >= this.heightMapResetHeight || this.heightMapFudge > this.heightMapResetTime) {
            if (this.lastY != currentY) {
                needHeightMap = true;
            }

            this.lastY = currentY;
            this.heightMapFudge = 0;
        }

        if (Math.abs(offsetX) > 32 * multi || Math.abs(offsetZ) > 32 * multi) {
            full = true;
        }

        boolean nether = false;
        boolean caves = false;
        boolean netherPlayerInOpen;
        MutableBlockPos blockPos = MutableBlockPosCache.get();
        blockPos.setXYZ(this.lastX, Math.max(Math.min(GameVariableAccessShim.yCoord(), world.getMaxY() - 1), world.getMinY()), this.lastZ);
        if (VoxelConstants.getPlayer().level().dimensionType().hasCeiling()) {

            netherPlayerInOpen = world.getChunk(blockPos).getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX() & 15, blockPos.getZ() & 15) <= currentY;
            nether = currentY < 126;
            if (this.options.cavesAllowed && this.options.showCaves && currentY >= 126 && !netherPlayerInOpen) {
                caves = true;
            }
        } else if (world.dimensionType().cardinalLightType() == CardinalLighting.Type.NETHER && !VoxelConstants.getClientWorld().dimensionType().hasSkyLight()) {
            boolean endPlayerInOpen = world.getChunk(blockPos).getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX() & 15, blockPos.getZ() & 15) <= currentY;
            if (this.options.cavesAllowed && this.options.showCaves && !endPlayerInOpen) {
                caves = true;
            }
        } else if (this.options.cavesAllowed && this.options.showCaves && world.getBrightness(LightLayer.SKY, blockPos) <= 0) {
            caves = true;
        }
        MutableBlockPosCache.release(blockPos);

        boolean beneathRendering = caves || nether;
        if (this.lastBeneathRendering != beneathRendering) {
            full = true;
        }

        this.lastBeneathRendering = beneathRendering;
        needHeightAndID = needHeightMap && (nether || caves);
        int color24;
        synchronized (this.coordinateLock) {
            if (!full) {
                this.mapImages[zoom].moveY(offsetZ);
                this.mapImages[zoom].moveX(offsetX);
            }

            this.lastX = currentX;
            this.lastZ = currentZ;
        }
        int startX = currentX - 16 * multi;
        int startZ = currentZ - 16 * multi;
        if (!full) {
            this.mapData[zoom].moveZ(offsetZ);
            this.mapData[zoom].moveX(offsetX);

            for (int imageY = offsetZ > 0 ? 32 * multi - 1 : -offsetZ - 1; imageY >= (offsetZ > 0 ? 32 * multi - offsetZ : 0); --imageY) {
                for (int imageX = 0; imageX < 32 * multi; ++imageX) {
                    color24 = this.getPixelColor(true, true, true, true, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY);
                    this.mapImages[zoom].setRGB(imageX, imageY, color24);
                }
            }

            for (int imageY = 32 * multi - 1; imageY >= 0; --imageY) {
                for (int imageX = offsetX > 0 ? 32 * multi - offsetX : 0; imageX < (offsetX > 0 ? 32 * multi : -offsetX); ++imageX) {
                    color24 = this.getPixelColor(true, true, true, true, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY);
                    this.mapImages[zoom].setRGB(imageX, imageY, color24);
                }
            }
        }

        if (full || this.options.heightmap && needHeightMap || needHeightAndID || this.options.dynamicLighting && needLight || skyColorChanged) {
            for (int imageY = 32 * multi - 1; imageY >= 0; --imageY) {
                for (int imageX = 0; imageX < 32 * multi; ++imageX) {
                    color24 = this.getPixelColor(full, full || needHeightAndID, full, full || needLight || needHeightAndID, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY);
                    this.mapImages[zoom].setRGB(imageX, imageY, color24);
                }
            }
        }

        if ((full || offsetX != 0 || offsetZ != 0 || !this.lastFullscreen) && this.fullscreenMap && this.options.biomeOverlay != 0) {
            this.mapData[zoom].segmentBiomes();
            this.mapData[zoom].findCenterOfSegments(!this.options.oldNorth);
        }

        this.lastFullscreen = this.fullscreenMap;
        if (full || offsetX != 0 || offsetZ != 0 || needHeightMap || needLight || skyColorChanged) {
            this.imageChanged = true;
        }

        if (needLight || skyColorChanged) {
            VoxelConstants.getVoxelMapInstance().getSettingsAndLightingChangeNotifier().notifyOfChanges();
        }

    }

    @Override
    public void handleChangeInWorld(int chunkX, int sectionY, int chunkZ) {
        try {
            this.chunkCache[this.zoom].registerChangeAt(chunkX, chunkZ);
        } catch (Exception e) {
            VoxelConstants.getLogger().warn(e);
        }
    }

    @Override
    public void processChunk(LevelChunk chunk) {
        VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().processChunk(chunk);
        VoxelConstants.getVoxelMapInstance().getPortalMarkersManager().processChunk(chunk);
        this.rectangleCalc(chunk.getPos().x() * 16, chunk.getPos().z() * 16, chunk.getPos().x() * 16 + 15, chunk.getPos().z() * 16 + 15);
    }

    private void rectangleCalc(int left, int top, int right, int bottom) {
        boolean nether = false;
        boolean caves = false;
        boolean netherPlayerInOpen;
        MutableBlockPos blockPos = MutableBlockPosCache.get();
        blockPos.setXYZ(this.lastX, Math.max(Math.min(GameVariableAccessShim.yCoord(), world.getMaxY()), world.getMinY()), this.lastZ);
        int currentY = GameVariableAccessShim.yCoord();
        if (VoxelConstants.getPlayer().level().dimensionType().hasCeiling()) {
            netherPlayerInOpen = this.world.getChunk(blockPos).getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX() & 15, blockPos.getZ() & 15) <= currentY;
            nether = currentY < 126;
            if (this.options.cavesAllowed && this.options.showCaves && currentY >= 126 && !netherPlayerInOpen) {
                caves = true;
            }
        } else if (world.dimensionType().cardinalLightType() == CardinalLighting.Type.NETHER && !world.dimensionType().hasSkyLight()) {
            boolean endPlayerInOpen = this.world.getChunk(blockPos).getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX() & 15, blockPos.getZ() & 15) <= currentY;
            if (this.options.cavesAllowed && this.options.showCaves && !endPlayerInOpen) {
                caves = true;
            }
        } else if (this.options.cavesAllowed && this.options.showCaves && this.world.getBrightness(LightLayer.SKY, blockPos) <= 0) {
            caves = true;
        }
        MutableBlockPosCache.release(blockPos);

        int zoom = this.zoom;
        int startX = this.lastX;
        int startZ = this.lastZ;
        ClientLevel world = this.world;
        int multi = (int) Math.pow(2.0, zoom);
        startX -= 16 * multi;
        startZ -= 16 * multi;
        left = left - startX - 1;
        right = right - startX + 1;
        top = top - startZ - 1;
        bottom = bottom - startZ + 1;
        left = Math.max(0, left);
        right = Math.min(32 * multi - 1, right);
        top = Math.max(0, top);
        bottom = Math.min(32 * multi - 1, bottom);
        int color24;

        for (int imageY = bottom; imageY >= top; --imageY) {
            for (int imageX = left; imageX <= right; ++imageX) {
                color24 = this.getPixelColor(true, true, true, true, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY);
                this.mapImages[zoom].setRGB(imageX, imageY, color24);
            }
        }

        this.imageChanged = true;
    }

    private int getPixelColor(boolean needBiome, boolean needHeightAndID, boolean needTint, boolean needLight, boolean nether, boolean caves, ClientLevel world, int zoom, int multi, int startX, int startZ, int imageX, int imageY) {
        int blockX = startX + imageX;
        int blockZ = startZ + imageY;
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        LevelChunk loadedChunk = world.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (loadedChunk == null || loadedChunk.isEmpty()) {
            // Use deterministic fallback color for unloaded chunks so texture
            // rows/columns don't keep stale copied data during moveX/moveY.
            return MapUtils.doSlimeAndGrid(this.colorManager.getAirColor(), world, blockX, blockZ);
        }

        int surfaceHeight = Short.MIN_VALUE;
        int seafloorHeight = Short.MIN_VALUE;
        int transparentHeight = Short.MIN_VALUE;
        int foliageHeight = Short.MIN_VALUE;
        int surfaceColor;
        int seafloorColor = 0;
        int transparentColor = 0;
        int foliageColor = 0;
        this.surfaceBlockState = null;
        this.transparentBlockState = BlockRepository.air.defaultBlockState();
        BlockState foliageBlockState = BlockRepository.air.defaultBlockState();
        BlockState seafloorBlockState = BlockRepository.air.defaultBlockState();
        boolean surfaceBlockChangeForcedTint = false;
        boolean transparentBlockChangeForcedTint = false;
        boolean foliageBlockChangeForcedTint = false;
        boolean seafloorBlockChangeForcedTint = false;
        int surfaceBlockStateID;
        int transparentBlockStateID;
        int foliageBlockStateID;
        int seafloorBlockStateID;
        MutableBlockPos blockPos = MutableBlockPosCache.get();
        MutableBlockPos tempBlockPos = MutableBlockPosCache.get();
        blockPos.withXYZ(startX + imageX, 64, startZ + imageY);
        int color24;
        Biome biome;
        if (needBiome) {
            // int chunkX = SectionPos.blockToSectionCoord(blockPos.getX());
            // int chunkZ = SectionPos.blockToSectionCoord(blockPos.getZ());
            // if (world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null) { // TODO 1.21.5 testen
            biome = world.getBiome(blockPos).value();
            // } else {
            // biome = null;
            // }

            this.mapData[zoom].setBiome(imageX, imageY, biome);
        } else {
            biome = this.mapData[zoom].getBiome(imageX, imageY);
        }

        if (this.options.biomeOverlay == 1) {
            if (biome != null) {
                color24 = ARGB.toABGR(BiomeRepository.getBiomeColor(biome) | 0xFF000000);
            } else {
                color24 = 0;
            }

        } else {
            boolean solid = false;
            if (needHeightAndID) {
                if (!nether && !caves) {
                    LevelChunk chunk = loadedChunk;
                    transparentHeight = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX() & 15, blockPos.getZ() & 15) + 1;
                    this.transparentBlockState = world.getBlockState(blockPos.withXYZ(startX + imageX, transparentHeight - 1, startZ + imageY));
                    FluidState fluidState = this.transparentBlockState.getFluidState();
                    if (fluidState != Fluids.EMPTY.defaultFluidState()) {
                        this.transparentBlockState = fluidState.createLegacyBlock();
                    }

                    surfaceHeight = transparentHeight;
                    this.surfaceBlockState = this.transparentBlockState;
                    VoxelShape voxelShape;
                    boolean hasOpacity = this.surfaceBlockState.getLightDampening() > 0;
                    if (!hasOpacity && this.surfaceBlockState.canOcclude() && this.surfaceBlockState.useShapeForLightOcclusion()) {
                        voxelShape = this.surfaceBlockState.getFaceOcclusionShape(Direction.DOWN);
                        hasOpacity = Shapes.faceShapeOccludes(voxelShape, Shapes.empty());
                        voxelShape = this.surfaceBlockState.getFaceOcclusionShape(Direction.UP);
                        hasOpacity = hasOpacity || Shapes.faceShapeOccludes(Shapes.empty(), voxelShape);
                    }

                    while (!hasOpacity && surfaceHeight > world.getMinY()) {
                        foliageBlockState = this.surfaceBlockState;
                        --surfaceHeight;
                        this.surfaceBlockState = world.getBlockState(blockPos.withXYZ(startX + imageX, surfaceHeight - 1, startZ + imageY));
                        fluidState = this.surfaceBlockState.getFluidState();
                        if (fluidState != Fluids.EMPTY.defaultFluidState()) {
                            this.surfaceBlockState = fluidState.createLegacyBlock();
                        }

                        hasOpacity = this.surfaceBlockState.getLightDampening() > 0;
                        if (!hasOpacity && this.surfaceBlockState.canOcclude() && this.surfaceBlockState.useShapeForLightOcclusion()) {
                            voxelShape = this.surfaceBlockState.getFaceOcclusionShape(Direction.DOWN);
                            hasOpacity = Shapes.faceShapeOccludes(voxelShape, Shapes.empty());
                            voxelShape = this.surfaceBlockState.getFaceOcclusionShape(Direction.UP);
                            hasOpacity = hasOpacity || Shapes.faceShapeOccludes(Shapes.empty(), voxelShape);
                        }
                    }

                    if (surfaceHeight == transparentHeight) {
                        transparentHeight = Short.MIN_VALUE;
                        this.transparentBlockState = BlockRepository.air.defaultBlockState();
                        foliageBlockState = world.getBlockState(blockPos.withXYZ(startX + imageX, surfaceHeight, startZ + imageY));
                    }

                    if (foliageBlockState.getBlock() == Blocks.SNOW) {
                        this.surfaceBlockState = foliageBlockState;
                        foliageBlockState = BlockRepository.air.defaultBlockState();
                    }

                    if (foliageBlockState == this.transparentBlockState) {
                        foliageBlockState = BlockRepository.air.defaultBlockState();
                    }

                    if (foliageBlockState != null && !(foliageBlockState.getBlock() instanceof AirBlock)) {
                        foliageHeight = surfaceHeight + 1;
                    } else {
                        foliageHeight = Short.MIN_VALUE;
                    }

                    Block material = this.surfaceBlockState.getBlock();
                    if (material == Blocks.WATER || material == Blocks.ICE) {
                        seafloorHeight = surfaceHeight;

                        for (seafloorBlockState = world.getBlockState(blockPos.withXYZ(startX + imageX, surfaceHeight - 1, startZ + imageY)); seafloorBlockState.getLightDampening() < 5 && !(seafloorBlockState.getBlock() instanceof LeavesBlock)
                                && seafloorHeight > world.getMinY() + 1; seafloorBlockState = world.getBlockState(blockPos.withXYZ(startX + imageX, seafloorHeight - 1, startZ + imageY))) {
                            material = seafloorBlockState.getBlock();
                            if (transparentHeight == Short.MIN_VALUE && material != Blocks.ICE && material != Blocks.WATER && Heightmap.Types.MOTION_BLOCKING.isOpaque().test(seafloorBlockState)) {
                                transparentHeight = seafloorHeight;
                                this.transparentBlockState = seafloorBlockState;
                            }

                            if (foliageHeight == Short.MIN_VALUE && seafloorHeight != transparentHeight && this.transparentBlockState != seafloorBlockState && material != Blocks.ICE && material != Blocks.WATER && !(material instanceof AirBlock) && material != Blocks.BUBBLE_COLUMN) {
                                foliageHeight = seafloorHeight;
                                foliageBlockState = seafloorBlockState;
                            }

                            --seafloorHeight;
                        }

                        if (seafloorBlockState.getBlock() == Blocks.WATER) {
                            seafloorBlockState = BlockRepository.air.defaultBlockState();
                        }
                    }
                } else {
                    surfaceHeight = this.getNetherHeight(startX + imageX, startZ + imageY);
                    this.surfaceBlockState = world.getBlockState(blockPos.withXYZ(startX + imageX, surfaceHeight - 1, startZ + imageY));
                    surfaceBlockStateID = BlockRepository.getStateId(this.surfaceBlockState);
                    foliageHeight = surfaceHeight + 1;
                    blockPos.setXYZ(startX + imageX, foliageHeight - 1, startZ + imageY);
                    foliageBlockState = world.getBlockState(blockPos);
                    Block material = foliageBlockState.getBlock();
                    if (material != Blocks.SNOW && !(material instanceof AirBlock) && material != Blocks.LAVA && material != Blocks.WATER) {
                        foliageBlockStateID = BlockRepository.getStateId(foliageBlockState);
                    } else {
                        foliageHeight = Short.MIN_VALUE;
                    }
                }

                surfaceBlockStateID = BlockRepository.getStateId(this.surfaceBlockState);
                if (this.options.biomes && this.surfaceBlockState != this.mapData[zoom].getBlockstate(imageX, imageY)) {
                    surfaceBlockChangeForcedTint = true;
                }

                this.mapData[zoom].setHeight(imageX, imageY, surfaceHeight);
                this.mapData[zoom].setBlockstateID(imageX, imageY, surfaceBlockStateID);
                if (this.options.biomes && this.transparentBlockState != this.mapData[zoom].getTransparentBlockstate(imageX, imageY)) {
                    transparentBlockChangeForcedTint = true;
                }

                this.mapData[zoom].setTransparentHeight(imageX, imageY, transparentHeight);
                transparentBlockStateID = BlockRepository.getStateId(this.transparentBlockState);
                this.mapData[zoom].setTransparentBlockstateID(imageX, imageY, transparentBlockStateID);
                if (this.options.biomes && foliageBlockState != this.mapData[zoom].getFoliageBlockstate(imageX, imageY)) {
                    foliageBlockChangeForcedTint = true;
                }

                this.mapData[zoom].setFoliageHeight(imageX, imageY, foliageHeight);
                foliageBlockStateID = BlockRepository.getStateId(foliageBlockState);
                this.mapData[zoom].setFoliageBlockstateID(imageX, imageY, foliageBlockStateID);
                if (this.options.biomes && seafloorBlockState != this.mapData[zoom].getOceanFloorBlockstate(imageX, imageY)) {
                    seafloorBlockChangeForcedTint = true;
                }

                this.mapData[zoom].setOceanFloorHeight(imageX, imageY, seafloorHeight);
                seafloorBlockStateID = BlockRepository.getStateId(seafloorBlockState);
                this.mapData[zoom].setOceanFloorBlockstateID(imageX, imageY, seafloorBlockStateID);
            } else {
                surfaceHeight = this.mapData[zoom].getHeight(imageX, imageY);
                surfaceBlockStateID = this.mapData[zoom].getBlockstateID(imageX, imageY);
                this.surfaceBlockState = BlockRepository.getStateById(surfaceBlockStateID);
                transparentHeight = this.mapData[zoom].getTransparentHeight(imageX, imageY);
                transparentBlockStateID = this.mapData[zoom].getTransparentBlockstateID(imageX, imageY);
                this.transparentBlockState = BlockRepository.getStateById(transparentBlockStateID);
                foliageHeight = this.mapData[zoom].getFoliageHeight(imageX, imageY);
                foliageBlockStateID = this.mapData[zoom].getFoliageBlockstateID(imageX, imageY);
                foliageBlockState = BlockRepository.getStateById(foliageBlockStateID);
                seafloorHeight = this.mapData[zoom].getOceanFloorHeight(imageX, imageY);
                seafloorBlockStateID = this.mapData[zoom].getOceanFloorBlockstateID(imageX, imageY);
                seafloorBlockState = BlockRepository.getStateById(seafloorBlockStateID);
            }

            if (surfaceHeight == Short.MIN_VALUE) {
                surfaceHeight = this.lastY + 1;
                solid = true;
            }

            if (this.surfaceBlockState.getBlock() == Blocks.LAVA) {
                solid = false;
            }

            if (this.options.biomes) {
                surfaceColor = this.colorManager.getBlockColor(blockPos, surfaceBlockStateID, biome);
                int tint;
                if (!needTint && !surfaceBlockChangeForcedTint) {
                    tint = this.mapData[zoom].getBiomeTint(imageX, imageY);
                } else {
                    tint = this.colorManager.getBiomeTint(this.mapData[zoom], world, this.surfaceBlockState, surfaceBlockStateID, blockPos.withXYZ(startX + imageX, surfaceHeight - 1, startZ + imageY), tempBlockPos, startX, startZ);
                    this.mapData[zoom].setBiomeTint(imageX, imageY, tint);
                }

                if (tint != -1) {
                    surfaceColor = ColorUtils.colorMultiplier(surfaceColor, tint);
                }
            } else {
                surfaceColor = this.colorManager.getBlockColorWithDefaultTint(blockPos, surfaceBlockStateID);
            }

            surfaceColor = this.applyHeight(surfaceColor, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY, surfaceHeight, solid, 1);
            int light;
            if (needLight) {
                light = this.getLight(surfaceColor, this.surfaceBlockState, world, startX + imageX, startZ + imageY, surfaceHeight, solid);
                this.mapData[zoom].setLight(imageX, imageY, light);
            } else {
                light = this.mapData[zoom].getLight(imageX, imageY);
            }

            if (light == 0) {
                surfaceColor = 0;
            } else if (light != 255) {
                surfaceColor = ColorUtils.colorMultiplier(surfaceColor, light);
            }

            if (this.options.waterTransparency && seafloorHeight != Short.MIN_VALUE) {
                if (!this.options.biomes) {
                    seafloorColor = this.colorManager.getBlockColorWithDefaultTint(blockPos, seafloorBlockStateID);
                } else {
                    seafloorColor = this.colorManager.getBlockColor(blockPos, seafloorBlockStateID, biome);
                    int tint;
                    if (!needTint && !seafloorBlockChangeForcedTint) {
                        tint = this.mapData[zoom].getOceanFloorBiomeTint(imageX, imageY);
                    } else {
                        tint = this.colorManager.getBiomeTint(this.mapData[zoom], world, seafloorBlockState, seafloorBlockStateID, blockPos.withXYZ(startX + imageX, seafloorHeight - 1, startZ + imageY), tempBlockPos, startX, startZ);
                        this.mapData[zoom].setOceanFloorBiomeTint(imageX, imageY, tint);
                    }

                    if (tint != -1) {
                        seafloorColor = ColorUtils.colorMultiplier(seafloorColor, tint);
                    }
                }

                seafloorColor = this.applyHeight(seafloorColor, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY, seafloorHeight, solid, 0);
                int seafloorLight;
                if (needLight) {
                    seafloorLight = this.getLight(seafloorColor, seafloorBlockState, world, startX + imageX, startZ + imageY, seafloorHeight, solid);
                    blockPos.setXYZ(startX + imageX, seafloorHeight, startZ + imageY);
                    BlockState blockStateAbove = world.getBlockState(blockPos);
                    Block materialAbove = blockStateAbove.getBlock();
                    if (this.options.dynamicLighting && materialAbove == Blocks.ICE) {
                        int multiplier = minecraft.options.ambientOcclusion().get() ? 200 : 120;
                        seafloorLight = ColorUtils.colorMultiplier(seafloorLight, 0xFF000000 | multiplier << 16 | multiplier << 8 | multiplier);
                    }

                    this.mapData[zoom].setOceanFloorLight(imageX, imageY, seafloorLight);
                } else {
                    seafloorLight = this.mapData[zoom].getOceanFloorLight(imageX, imageY);
                }

                if (seafloorLight == 0) {
                    seafloorColor = 0;
                } else if (seafloorLight != 255) {
                    seafloorColor = ColorUtils.colorMultiplier(seafloorColor, seafloorLight);
                }
            }

            if (this.options.blockTransparency) {
                if (transparentHeight != Short.MIN_VALUE && this.transparentBlockState != null && this.transparentBlockState != BlockRepository.air.defaultBlockState()) {
                    if (this.options.biomes) {
                        transparentColor = this.colorManager.getBlockColor(blockPos, transparentBlockStateID, biome);
                        int tint;
                        if (!needTint && !transparentBlockChangeForcedTint) {
                            tint = this.mapData[zoom].getTransparentBiomeTint(imageX, imageY);
                        } else {
                            tint = this.colorManager.getBiomeTint(this.mapData[zoom], world, this.transparentBlockState, transparentBlockStateID, blockPos.withXYZ(startX + imageX, transparentHeight - 1, startZ + imageY), tempBlockPos, startX, startZ);
                            this.mapData[zoom].setTransparentBiomeTint(imageX, imageY, tint);
                        }

                        if (tint != -1) {
                            transparentColor = ColorUtils.colorMultiplier(transparentColor, tint);
                        }
                    } else {
                        transparentColor = this.colorManager.getBlockColorWithDefaultTint(blockPos, transparentBlockStateID);
                    }

                    transparentColor = this.applyHeight(transparentColor, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY, transparentHeight, solid, 3);
                    int transparentLight;
                    if (needLight) {
                        transparentLight = this.getLight(transparentColor, this.transparentBlockState, world, startX + imageX, startZ + imageY, transparentHeight, solid);
                        this.mapData[zoom].setTransparentLight(imageX, imageY, transparentLight);
                    } else {
                        transparentLight = this.mapData[zoom].getTransparentLight(imageX, imageY);
                    }

                    if (transparentLight == 0) {
                        transparentColor = 0;
                    } else if (transparentLight != 255) {
                        transparentColor = ColorUtils.colorMultiplier(transparentColor, transparentLight);
                    }
                }

                if (foliageHeight != Short.MIN_VALUE && foliageBlockState != null && foliageBlockState != BlockRepository.air.defaultBlockState()) {
                    if (!this.options.biomes) {
                        foliageColor = this.colorManager.getBlockColorWithDefaultTint(blockPos, foliageBlockStateID);
                    } else {
                        foliageColor = this.colorManager.getBlockColor(blockPos, foliageBlockStateID, biome);
                        int tint;
                        if (!needTint && !foliageBlockChangeForcedTint) {
                            tint = this.mapData[zoom].getFoliageBiomeTint(imageX, imageY);
                        } else {
                            tint = this.colorManager.getBiomeTint(this.mapData[zoom], world, foliageBlockState, foliageBlockStateID, blockPos.withXYZ(startX + imageX, foliageHeight - 1, startZ + imageY), tempBlockPos, startX, startZ);
                            this.mapData[zoom].setFoliageBiomeTint(imageX, imageY, tint);
                        }

                        if (tint != -1) {
                            foliageColor = ColorUtils.colorMultiplier(foliageColor, tint);
                        }
                    }

                    foliageColor = this.applyHeight(foliageColor, nether, caves, world, zoom, multi, startX, startZ, imageX, imageY, foliageHeight, solid, 2);
                    int foliageLight;
                    if (needLight) {
                        foliageLight = this.getLight(foliageColor, foliageBlockState, world, startX + imageX, startZ + imageY, foliageHeight, solid);
                        this.mapData[zoom].setFoliageLight(imageX, imageY, foliageLight);
                    } else {
                        foliageLight = this.mapData[zoom].getFoliageLight(imageX, imageY);
                    }

                    if (foliageLight == 0) {
                        foliageColor = 0;
                    } else if (foliageLight != 255) {
                        foliageColor = ColorUtils.colorMultiplier(foliageColor, foliageLight);
                    }
                }
            }

            if (seafloorColor != 0 && seafloorHeight > Short.MIN_VALUE) {
                color24 = seafloorColor;
                if (foliageColor != 0 && foliageHeight <= surfaceHeight) {
                    color24 = ColorUtils.colorAdder(foliageColor, seafloorColor);
                }

                if (transparentColor != 0 && transparentHeight <= surfaceHeight) {
                    color24 = ColorUtils.colorAdder(transparentColor, color24);
                }

                color24 = ColorUtils.colorAdder(surfaceColor, color24);
            } else {
                color24 = surfaceColor;
            }

            if (foliageColor != 0 && foliageHeight > surfaceHeight) {
                color24 = ColorUtils.colorAdder(foliageColor, color24);
            }

            if (transparentColor != 0 && transparentHeight > surfaceHeight) {
                color24 = ColorUtils.colorAdder(transparentColor, color24);
            }

            if (this.options.biomeOverlay == 2) {
                int bc = 0;
                if (biome != null) {
                    bc = ARGB.toABGR(BiomeRepository.getBiomeColor(biome));
                }

                bc = 2130706432 | bc;
                color24 = ColorUtils.colorAdder(bc, color24);
            }

        }
        MutableBlockPosCache.release(blockPos);
        MutableBlockPosCache.release(tempBlockPos);
        return MapUtils.doSlimeAndGrid(color24, world, startX + imageX, startZ + imageY);
    }

    private int getBlockHeight(boolean nether, boolean caves, Level world, int x, int z) {
        if (!world.hasChunk(x >> 4, z >> 4)) {
            return Short.MIN_VALUE;
        }
        MutableBlockPos blockPos = MutableBlockPosCache.get();
        int playerHeight = GameVariableAccessShim.yCoord();
        blockPos.setXYZ(x, playerHeight, z);
        LevelChunk chunk = world.getChunkSource().getChunk(x >> 4, z >> 4, false);
        if (chunk == null || chunk.isEmpty()) {
            MutableBlockPosCache.release(blockPos);
            return Short.MIN_VALUE;
        }
        int height = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX() & 15, blockPos.getZ() & 15) + 1;
        BlockState blockState = world.getBlockState(blockPos.withXYZ(x, height - 1, z));
        FluidState fluidState = this.transparentBlockState.getFluidState();
        if (fluidState != Fluids.EMPTY.defaultFluidState()) {
            blockState = fluidState.createLegacyBlock();
        }

        while (blockState.getLightDampening() == 0 && height > world.getMinY()) {
            --height;
            blockState = world.getBlockState(blockPos.withXYZ(x, height - 1, z));
            fluidState = this.surfaceBlockState.getFluidState();
            if (fluidState != Fluids.EMPTY.defaultFluidState()) {
                blockState = fluidState.createLegacyBlock();
            }
        }
        MutableBlockPosCache.release(blockPos);
        return (nether || caves) && height > playerHeight ? this.getNetherHeight(x, z) : height;
    }

    private int getNetherHeight(int x, int z) {
        MutableBlockPos blockPos = MutableBlockPosCache.get();
        int y = this.lastY;
        blockPos.setXYZ(x, y, z);
        BlockState blockState = this.world.getBlockState(blockPos);
        if (blockState.getLightDampening() == 0 && blockState.getBlock() != Blocks.LAVA) {
            while (y > world.getMinY()) {
                --y;
                blockPos.setXYZ(x, y, z);
                blockState = this.world.getBlockState(blockPos);
                if (blockState.getLightDampening() > 0 || blockState.getBlock() == Blocks.LAVA) {
                    MutableBlockPosCache.release(blockPos);
                    return y + 1;
                }
            }
            MutableBlockPosCache.release(blockPos);
            return y;
        } else {
            while (y <= this.lastY + 10 && y < world.getMaxY()) {
                ++y;
                blockPos.setXYZ(x, y, z);
                blockState = this.world.getBlockState(blockPos);
                if (blockState.getLightDampening() == 0 && blockState.getBlock() != Blocks.LAVA) {
                    MutableBlockPosCache.release(blockPos);
                    return y;
                }
            }
            MutableBlockPosCache.release(blockPos);
            return Short.MIN_VALUE;
        }
    }

    private int getSeafloorHeight(Level world, int x, int z, int height) {
        MutableBlockPos blockPos = MutableBlockPosCache.get();
        for (BlockState blockState = world.getBlockState(blockPos.withXYZ(x, height - 1, z)); blockState.getLightDampening() < 5 && !(blockState.getBlock() instanceof LeavesBlock) && height > world.getMinY() + 1; blockState = world.getBlockState(blockPos.withXYZ(x, height - 1, z))) {
            --height;
        }
        MutableBlockPosCache.release(blockPos);
        return height;
    }

    private int getTransparentHeight(boolean nether, boolean caves, Level world, int x, int z, int height) {
        MutableBlockPos blockPos = MutableBlockPosCache.get();
        int transHeight;
        if (!caves && !nether) {
            transHeight = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos.withXYZ(x, height, z)).getY();
            if (transHeight <= height) {
                transHeight = Short.MIN_VALUE;
            }
        } else {
            transHeight = Short.MIN_VALUE;
        }

        BlockState blockState = world.getBlockState(blockPos.withXYZ(x, transHeight - 1, z));
        Block material = blockState.getBlock();
        if (transHeight == height + 1 && material == Blocks.SNOW) {
            transHeight = Short.MIN_VALUE;
        }

        if (material == Blocks.BARRIER) {
            ++transHeight;
            blockState = world.getBlockState(blockPos.withXYZ(x, transHeight - 1, z));
            material = blockState.getBlock();
            if (material instanceof AirBlock) {
                transHeight = Short.MIN_VALUE;
            }
        }
        MutableBlockPosCache.release(blockPos);
        return transHeight;
    }

    private int applyHeight(int color24, boolean nether, boolean caves, Level world, int zoom, int multi, int startX, int startZ, int imageX, int imageY, int height, boolean solid, int layer) {
        if (color24 != this.colorManager.getAirColor() && color24 != 0 && (this.options.heightmap || this.options.slopemap) && !solid) {
            int heightComp = -1;
            int diff;
            double sc = 0.0;
            if (!this.options.slopemap) {
                diff = height - this.lastY;
                sc = Math.log10(Math.abs(diff) / 8.0 + 1.0) / 1.8;
                if (diff < 0) {
                    sc = 0.0 - sc;
                }
            } else {
                if (imageX > 0 && imageY < 32 * multi - 1) {
                    if (layer == 0) {
                        heightComp = this.mapData[zoom].getOceanFloorHeight(imageX - 1, imageY + 1);
                    }

                    if (layer == 1) {
                        heightComp = this.mapData[zoom].getHeight(imageX - 1, imageY + 1);
                    }

                    if (layer == 2) {
                        heightComp = height;
                    }

                    if (layer == 3) {
                        heightComp = this.mapData[zoom].getTransparentHeight(imageX - 1, imageY + 1);
                        if (heightComp == Short.MIN_VALUE) {
                            Block block = BlockRepository.getStateById(this.mapData[zoom].getTransparentBlockstateID(imageX, imageY)).getBlock();
                            if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
                                heightComp = this.mapData[zoom].getHeight(imageX - 1, imageY + 1);
                            }
                        }
                    }
                } else {
                    if (layer == 0) {
                        int baseHeight = this.getBlockHeight(nether, caves, world, startX + imageX - 1, startZ + imageY + 1);
                        heightComp = this.getSeafloorHeight(world, startX + imageX - 1, startZ + imageY + 1, baseHeight);
                    }

                    if (layer == 1) {
                        heightComp = this.getBlockHeight(nether, caves, world, startX + imageX - 1, startZ + imageY + 1);
                    }

                    if (layer == 2) {
                        heightComp = height;
                    }

                    if (layer == 3) {
                        int baseHeight = this.getBlockHeight(nether, caves, world, startX + imageX - 1, startZ + imageY + 1);
                        heightComp = this.getTransparentHeight(nether, caves, world, startX + imageX - 1, startZ + imageY + 1, baseHeight);
                        if (heightComp == Short.MIN_VALUE) {
                            MutableBlockPos blockPos = MutableBlockPosCache.get();
                            BlockState blockState = world.getBlockState(blockPos.withXYZ(startX + imageX, height - 1, startZ + imageY));
                            MutableBlockPosCache.release(blockPos);
                            Block block = blockState.getBlock();
                            if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
                                heightComp = baseHeight;
                            }
                        }
                    }
                }

                if (heightComp == Short.MIN_VALUE) {
                    heightComp = height;
                }

                diff = heightComp - height;
                if (diff != 0) {
                    sc = diff > 0 ? 1.0 : -1.0;
                    sc /= 8.0;
                }

                if (this.options.heightmap) {
                    diff = height - this.lastY;
                    double heightsc = Math.log10(Math.abs(diff) / 8.0 + 1.0) / 3.0;
                    sc = diff > 0 ? sc + heightsc : sc - heightsc;
                }
            }

            int alpha = color24 >> 24 & 0xFF;
            int r = color24 >> 16 & 0xFF;
            int g = color24 >> 8 & 0xFF;
            int b = color24 & 0xFF;
            if (sc > 0.0) {
                r += (int) (sc * (255 - r));
                g += (int) (sc * (255 - g));
                b += (int) (sc * (255 - b));
            } else if (sc < 0.0) {
                sc = Math.abs(sc);
                r -= (int) (sc * r);
                g -= (int) (sc * g);
                b -= (int) (sc * b);
            }

            color24 = alpha * 16777216 + r * 65536 + g * 256 + b;
        }

        return color24;
    }

    private int getLight(int color24, BlockState blockState, Level world, int x, int z, int height, boolean solid) {
        int combinedLight = 0xffffffff;
        if (solid) {
            combinedLight = 0;
        } else if (color24 != this.colorManager.getAirColor() && color24 != 0 && this.options.dynamicLighting) {
            MutableBlockPos blockPos = MutableBlockPosCache.get();
            blockPos.setXYZ(x, Math.max(Math.min(height, world.getMaxY()), world.getMinY()), z);
            int blockLight = world.getBrightness(LightLayer.BLOCK, blockPos);
            int skyLight = world.getBrightness(LightLayer.SKY, blockPos);
            if (blockState.getBlock() == Blocks.LAVA || blockState.getBlock() == Blocks.MAGMA_BLOCK) {
                blockLight = 14;
            }
            MutableBlockPosCache.release(blockPos);
            combinedLight = getLightmapColor(skyLight, blockLight);
        }

        return ARGB.toABGR(combinedLight);
    }


    private void renderMap(Matrix4fStack matrixStack, int x, int y, int scScale, float scaleProj) {
        matrixStack.pushMatrix();
        try {
            matrixStack.scale(scaleProj, scaleProj, 1.0F);

            synchronized (this.coordinateLock) {
                if (this.imageChanged) {
                    this.imageChanged = false;
                    this.mapImages[this.zoom].upload();
                    this.lastImageX = this.lastX;
                    this.lastImageZ = this.lastZ;
                }
            }

            renderBufferSource.endBatch();

            RenderUtils.renderWithCustomProjection(baseMapRenderTarget, mapProjection.getBuffer(), -2000.0F, () -> {
                float scale = 1.0F;
                if (this.options.squareMap && this.options.rotates) {
                    // Keep world coverage effectively unchanged while adding a minimal overscan
                    // to prevent edge gaps at extreme rotation/zoom-out.
                    scale = (float) Math.sqrt(2.0D) + 0.0025F;
                }
                float multi = (float) (1.0 / this.zoomScale);
                float percentX = (float) (GameVariableAccessShim.xCoordDouble() - this.lastImageX) * multi;
                float percentY = (float) (GameVariableAccessShim.zCoordDouble() - this.lastImageZ) * multi;

                matrixStack.pushMatrix();
                matrixStack.identity();

                matrixStack.pushMatrix();
                if (!options.rotates) {
                    matrixStack.rotate(Axis.ZP.rotationDegrees(rotationFactor));
                } else {
                    matrixStack.rotate(Axis.ZP.rotationDegrees(-direction));
                }
                matrixStack.scale(scale, scale, 1.0F);
                matrixStack.translate(-percentX * 512.0F / 64.0F, -percentY * 512.0F / 64.0F, 0.0F);

                RenderType liveMapRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(mapResources[zoom]);
                VertexConsumer liveMapBuffer = renderBufferSource.getBuffer(liveMapRenderType);
                RenderUtils.drawTexturedModalRect(matrixStack, liveMapBuffer, -256.0F, -256.0F, -10.0F, 512.0F, 512.0F, 0xFFFFFFFF);
                matrixStack.popMatrix();

                matrixStack.pushMatrix();
                matrixStack.identity();
                matrixStack.scale(512.0F / 64.0F, 512.0F / 64.0F, 1.0F);
                try {
                    drawChunkOverlayMinimap(matrixStack, 0, 0, GameVariableAccessShim.xCoordDouble(), GameVariableAccessShim.zCoordDouble());
                } catch (Exception exception) {
                    VoxelConstants.getLogger().error("VoxelMap minimap chunk overlay render", exception);
                } finally {
                    renderBufferSource.endBatch();
                }
                matrixStack.popMatrix();

                if (VoxelConstants.getVoxelMapInstance().getRadar() != null) {
                    VoxelConstants.getVoxelMapInstance().getRadar().onTickInGame(matrixStack, minimapContext);
                    VoxelConstants.getVoxelMapInstance().getRadar().renderMapMobs(matrixStack, renderBufferSource, Contact.DisplayState.BELOW_FRAME, 0, 0, scScale, 512.0F / 64.0F);
                }

                matrixStack.popMatrix();
                renderBufferSource.endBatch();
            });

            RenderUtils.renderWithCustomProjection(finalMapRenderTarget, mapProjection.getBuffer(), -2000.0F, () -> {
                matrixStack.pushMatrix();
                matrixStack.identity();

                RenderType stencilRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(this.options.squareMap ? resourceSquareMapStencil : resourceRoundMapStencil);
                VertexConsumer stencilBuffer = renderBufferSource.getBuffer(stencilRenderType);
                if (this.options.squareMap) {
                    RenderUtils.drawTexturedModalRect(matrixStack, stencilBuffer, -256.0F, -256.0F, MAP_IMAGE_DEPTH, 512.0F, 512.0F, 0xFFFFFFFF);
                } else {
                    RenderUtils.drawTexturedModalRect(matrixStack, stencilBuffer, -256.0F, -256.0F, MAP_IMAGE_DEPTH, 512.0F, 512.0F, 0xFFFFFFFF);
                }

                RenderType maskedMapRenderType = VoxelMapRenderTypes.GUI_TEXTURED_MASKED_NO_DEPTH_TEST.apply(baseMapRenderTarget.colorTextureId);
                VertexConsumer maskedMapBuffer = renderBufferSource.getBuffer(maskedMapRenderType);
                RenderUtils.drawTexturedModalRect(matrixStack, maskedMapBuffer, -256.0F, -256.0F, 0.0F, 512.0F, 512.0F, 0xFFFFFFFF);

                matrixStack.popMatrix();
                renderBufferSource.endBatch();
            });

            double guiScale = (double) minecraft.getWindow().getWidth() / this.scWidth;
            minTablistOffset = guiScale * 63;

            RenderType finalMapRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(finalMapRenderTarget.colorTextureId);
            VertexConsumer finalMapBuffer = renderBufferSource.getBuffer(finalMapRenderType);
            RenderUtils.drawTexturedModalRect(matrixStack, finalMapBuffer, x - 32.0F, y - 32.0F, MAP_IMAGE_DEPTH, 64.0F, 64.0F, 0xFFFFFFFF);

            RenderType frameRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(options.squareMap ? resourceSquareMapFrame : resourceRoundMapFrame);
            VertexConsumer frameBuffer = renderBufferSource.getBuffer(frameRenderType);
            RenderUtils.drawTexturedModalRect(matrixStack, frameBuffer, x - 32.0F, y - 32.0F, MAP_OVERLAY_DEPTH, 64.0F, 64.0F, 0xFFFFFFFF);

            double lastXDouble = GameVariableAccessShim.xCoordDouble();
            double lastZDouble = GameVariableAccessShim.zCoordDouble();
            TextureAtlas textureAtlas = VoxelConstants.getVoxelMapInstance().getWaypointManager().getTextureAtlas();

            if (options.waypointsAllowed) {
                for (Waypoint waypoint : waypointManager.getWaypoints()) {
                    boolean isHighlighted = waypointManager.isHighlightedWaypoint(waypoint);
                    if (waypoint.isActive() || isHighlighted) {
                        double distanceSq = waypoint.getDistanceSqToEntity(minecraft.getCameraEntity());
                        boolean isOutOfRange = options.maxWaypointDisplayDistance >= 0 && distanceSq >= (options.maxWaypointDisplayDistance * options.maxWaypointDisplayDistance);
                        if (!isOutOfRange || isHighlighted) {
                            drawWaypoint(matrixStack, x, y, waypoint, textureAtlas, null, isHighlighted, -1, lastXDouble, lastZDouble);
                        }
                    }
                }

                Waypoint highlightedPoint = waypointManager.getHighlightedWaypoint();
                if (highlightedPoint != null) {
                    drawWaypoint(matrixStack, x, y, highlightedPoint, textureAtlas, textureAtlas.getAtlasSprite("marker/target"), true, 0xFFFF0000, lastXDouble, lastZDouble);
                }
            }

            drawPortalMarkersMinimap(matrixStack, x, y, lastXDouble, lastZDouble);
            drawSeedMapperMinimapMarkers(matrixStack, x, y, lastXDouble, lastZDouble);
            renderBufferSource.endBatch();
        } finally {
            matrixStack.popMatrix();
        }
    }


    private void drawWaypoint(Matrix4fStack matrixStack, int x, int y, Waypoint waypoint, TextureAtlas textureAtlas, Sprite icon, boolean isHighlighted, int color, double baseX, double baseZ) {
        if (isHighlighted && options.autoHideHighlightsWhenNear) {
            double dx = baseX - waypoint.getXInCurrentDimension() - 0.5D;
            double dz = baseZ - waypoint.getZInCurrentDimension() - 0.5D;
            double maxDistance = options.autoHideHighlightsNearDistance;
            if (dx * dx + dz * dz <= maxDistance * maxDistance) {
                return;
            }
        }

        boolean uprightIcon = icon != null;

        double wayX = baseX - waypoint.getXInCurrentDimension() - 0.5;
        double wayY = baseZ - waypoint.getZInCurrentDimension() - 0.5;
        float locate = (float) Math.toDegrees(Math.atan2(wayX, wayY));
        float hypot = (float) (Math.sqrt(wayX * wayX + wayY * wayY) / zoomScaleAdjusted);
        boolean far;
        if (this.options.rotates) {
            locate += this.direction;
        } else {
            locate -= this.rotationFactor;
        }

        if (this.options.squareMap) {
            double radLocate = Math.toRadians(locate);
            double dispX = hypot * Math.cos(radLocate);
            double dispY = hypot * Math.sin(radLocate);
            far = Math.abs(dispX) > 28.5 || Math.abs(dispY) > 28.5;
            if (far) {
                hypot = (float) (hypot / Math.max(Math.abs(dispX), Math.abs(dispY)) * 30.0);
            }
        } else {
            far = hypot >= 31.0f;
            if (far) {
                hypot = 34.0f;
            }
        }

        RenderType waypointRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(textureAtlas.getIdentifier());
        VertexConsumer waypointBuffer = renderBufferSource.getBuffer(waypointRenderType);

        int iconColor = color == -1 ? waypoint.getUnifiedColor(!waypoint.enabled && isHighlighted ? 0.3F : 1.0F) : color;
        if (far) {
            if (icon == null) {
                icon = textureAtlas.getAtlasSprite("marker/" + waypoint.imageSuffix);
                if (icon == textureAtlas.getMissingImage()) {
                    icon = textureAtlas.getAtlasSprite("marker/arrow");
                }
            }

            try {
                matrixStack.pushMatrix();
                matrixStack.translate(x, y, 0.0F);
                matrixStack.rotate(Axis.ZP.rotationDegrees(-locate));
                if (uprightIcon) {
                    matrixStack.translate(0.0F, -hypot, 0.0F);
                    matrixStack.rotate(Axis.ZP.rotationDegrees(locate));
                    matrixStack.translate(-x, -y, 0.0F);
                } else {
                    matrixStack.translate(-x, -y, 0.0F);
                    matrixStack.translate(0.0F, -hypot, 0.0F);
                }

                RenderUtils.drawTexturedModalRect(matrixStack, waypointBuffer, icon, x - 4.0F, y - 4.0F, MAP_OVERLAY_DEPTH, 8.0F, 8.0F, iconColor);
            } catch (Exception var40) {
                this.showMessage("Error: marker overlay not found!");
            } finally {
                matrixStack.popMatrix();
            }
        } else {
            if (icon == null) {
                icon = textureAtlas.getAtlasSprite("selectable/" + waypoint.imageSuffix);
                if (icon == textureAtlas.getMissingImage()) {
                    icon = textureAtlas.getAtlasSprite(WaypointManager.fallbackIconLocation);
                }
            }

            try {
                matrixStack.pushMatrix();
                matrixStack.rotate(Axis.ZP.rotationDegrees(-locate));
                matrixStack.translate(0.0F, -hypot, 0.0F);
                matrixStack.rotate(Axis.ZP.rotationDegrees(locate));

                RenderUtils.drawTexturedModalRect(matrixStack, waypointBuffer, icon, x - 4.0F, y - 4.0F, MAP_OVERLAY_DEPTH, 8.0F, 8.0F, iconColor);
            } catch (Exception var42) {
                this.showMessage("Error: waypoint overlay not found!");
            } finally {
                matrixStack.popMatrix();
            }
        }
    }

    private void drawSeedMapperMinimapMarkers(Matrix4fStack matrixStack, int x, int y, double baseX, double baseZ) {
        if (!seedMapperOptions.enabled) {
            return;
        }

        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            return;
        }

        long seed;
        try {
            seed = seedMapperOptions.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        int centerX = Mth.floor(baseX);
        int centerZ = Mth.floor(baseZ);
        int queryRadius = Math.max(256, (int) Math.ceil(40.0D * zoomScaleAdjusted)) + 64;
        int keySnap = Math.max(64, queryRadius / 4);
        int queryCenterX = Math.floorDiv(centerX, keySnap) * keySnap;
        int queryCenterZ = Math.floorDiv(centerZ, keySnap) * keySnap;
        int generatorFlags = 0;
        int mcVersion = SeedMapperCompat.getMcVersion();
        String datapackWorldKey = currentSeedMapperWorldKey();
        SeedMapperMinimapQueryKey key = new SeedMapperMinimapQueryKey(seed, dimension, mcVersion, generatorFlags, queryCenterX, queryCenterZ, queryRadius, seedMapperOptions.showLootableOnly, seedMapperOptions.getDatapackMarkerHash(), datapackWorldKey);
        long now = System.currentTimeMillis();
        long minIntervalMs = 750L;
        boolean shouldRefresh = seedMapperLastMinimapQueryKey == null
                || !seedMapperLastMinimapQueryKey.equals(key)
                || now - seedMapperLastMinimapQueryMs >= minIntervalMs;
        if (shouldRefresh) {
            SeedMapperLocatorService.QueryResult result = SeedMapperLocatorService.get().queryWithStatus(
                    seed,
                    dimension,
                    mcVersion,
                    generatorFlags,
                    queryCenterX - queryRadius,
                    queryCenterX + queryRadius,
                    queryCenterZ - queryRadius,
                    queryCenterZ + queryRadius,
                    seedMapperOptions,
                    datapackWorldKey
            );
            if (result.exact() || seedMapperLastMinimapMarkers.isEmpty()) {
                seedMapperLastMinimapMarkers = result.markers();
                seedMapperLastMinimapQueryKey = key;
            }
            seedMapperLastMinimapQueryMs = now;
        }
        List<SeedMapperMarker> markers = seedMapperLastMinimapMarkers;

        boolean lowDetail = zoomScaleAdjusted > 2.0D;
        double maxBlockDistance = (options.squareMap ? 42.0D : 34.0D) * zoomScaleAdjusted + 8.0D;
        int denseDrawn = 0;
        int totalDrawn = 0;
        int scanned = 0;
        int maxScanned = lowDetail ? 1400 : 2200;
        String worldKey = currentSeedMapperWorldKey();
        for (SeedMapperMarker marker : markers) {
            if (++scanned > maxScanned || totalDrawn >= 220) {
                break;
            }
            double dx = Math.abs(baseX - marker.blockX() - 0.5D);
            double dz = Math.abs(baseZ - marker.blockZ() - 0.5D);
            if (dx > maxBlockDistance || dz > maxBlockDistance) {
                continue;
            }
            if (lowDetail && (marker.feature() == com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature.SLIME_CHUNK
                    || marker.feature() == com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature.IRON_ORE_VEIN
                    || marker.feature() == com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature.COPPER_ORE_VEIN)) {
                if (denseDrawn >= 40) {
                    continue;
                }
                denseDrawn++;
            }
            drawSeedMapperMinimapMarker(matrixStack, x, y, marker, baseX, baseZ, worldKey);
            totalDrawn++;
        }
    }

    private void drawSeedMapperMinimapMarker(Matrix4fStack matrixStack, int x, int y, SeedMapperMarker marker, double baseX, double baseZ, String worldKey) {
        double wayX = baseX - marker.blockX() - 0.5;
        double wayY = baseZ - marker.blockZ() - 0.5;
        float locate = (float) Math.toDegrees(Math.atan2(wayX, wayY));
        float hypot = (float) (Math.sqrt(wayX * wayX + wayY * wayY) / zoomScaleAdjusted);
        if (this.options.rotates) {
            locate += this.direction;
        } else {
            locate -= this.rotationFactor;
        }

        boolean far;
        if (this.options.squareMap) {
            double radLocate = Math.toRadians(locate);
            double dispX = hypot * Math.sin(radLocate);
            double dispY = -hypot * Math.cos(radLocate);
            far = Math.abs(dispX) > 28.5 || Math.abs(dispY) > 28.5;
            if (far) {
                return;
            }
        } else {
            far = hypot >= 31.0f;
            if (far) {
                return;
            }
        }

        boolean datapackStructure = marker.feature() == com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature.DATAPACK_STRUCTURE;
        int iconColor = datapackStructure
                ? SeedMapperImportedDatapackManager.colorForStructureId(marker.label())
                : 0xFFFFFFFF;
        float iconSize = datapackStructure
                ? SeedMapperImportedDatapackManager.iconSizeForMinimap()
                : 8.0F;

        try {
            matrixStack.pushMatrix();
            matrixStack.rotate(Axis.ZP.rotationDegrees(-locate));
            matrixStack.translate(0.0F, -hypot, 0.0F);
            matrixStack.rotate(Axis.ZP.rotationDegrees(locate));
            if (datapackStructure) {
                drawDatapackMinimapMarker(matrixStack, renderBufferSource, x, y, iconSize, iconColor);
            } else {
                Identifier icon = marker.feature().icon();
                RenderType markerRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(icon);
                VertexConsumer markerBuffer = renderBufferSource.getBuffer(markerRenderType);
                RenderUtils.drawTexturedModalRect(matrixStack, markerBuffer, x - iconSize / 2.0F, y - iconSize / 2.0F, MAP_OVERLAY_DEPTH, iconSize, iconSize, iconColor);
            }
            if (seedMapperOptions.isCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ())) {
                float tickScale = 0.30F;
                int tickWidth = this.minecraft.font.width(COMPLETED_TICK_GLYPH);
                int tickHeight = this.minecraft.font.lineHeight;
                matrixStack.pushMatrix();
                matrixStack.scale(tickScale, tickScale, 1.0F);
                float drawX = (x - tickWidth / 2.0F) / tickScale;
                float drawY = (y - tickHeight / 2.0F + 0.5F) / tickScale;
                RenderUtils.drawString(matrixStack, renderBufferSource, COMPLETED_TICK_GLYPH, drawX, drawY, MAP_TEXT_DEPTH + 1.0F, 0xFF22C84A, false);
                matrixStack.popMatrix();
            }
        } catch (Exception ignored) {
        } finally {
            matrixStack.popMatrix();
        }
    }

    private void drawDatapackMinimapMarker(Matrix4fStack matrixStack, MultiBufferSource.BufferSource renderBufferSource, float x, float y, float iconSize, int color) {
        if (SeedMapperImportedDatapackManager.usesPotionIcon()) {
            Identifier potion = SeedMapperImportedDatapackManager.iconForStructureId("");
            Identifier overlay = SeedMapperImportedDatapackManager.iconOverlayForStructureId("");
            RenderType potionRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(potion);
            RenderType overlayRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(overlay);
            VertexConsumer potionBuffer = renderBufferSource.getBuffer(potionRenderType);
            VertexConsumer overlayBuffer = renderBufferSource.getBuffer(overlayRenderType);
            RenderUtils.drawTexturedModalRect(matrixStack, potionBuffer, x - iconSize / 2.0F, y - iconSize / 2.0F, MAP_OVERLAY_DEPTH, iconSize, iconSize, 0xFFFFFFFF);
            RenderUtils.drawTexturedModalRect(matrixStack, overlayBuffer, x - iconSize / 2.0F, y - iconSize / 2.0F, MAP_OVERLAY_DEPTH, iconSize, iconSize, color);
            return;
        }

        float outerHalf = iconSize / 2.0F + 1.0F;
        float innerHalf = iconSize / 2.0F;
        VertexConsumer buffer = renderBufferSource.getBuffer(VoxelMapRenderTypes.WAYPOINT_TEXT_BACKGROUND);
        float borderRed = ARGB.redFloat(0xFF000000);
        float borderGreen = ARGB.greenFloat(0xFF000000);
        float borderBlue = ARGB.blueFloat(0xFF000000);
        buffer.addVertex(matrixStack, x - outerHalf, y - outerHalf, MAP_OVERLAY_DEPTH).setColor(borderRed, borderGreen, borderBlue, 1.0F);
        buffer.addVertex(matrixStack, x - outerHalf, y + outerHalf, MAP_OVERLAY_DEPTH).setColor(borderRed, borderGreen, borderBlue, 1.0F);
        buffer.addVertex(matrixStack, x + outerHalf, y + outerHalf, MAP_OVERLAY_DEPTH).setColor(borderRed, borderGreen, borderBlue, 1.0F);
        buffer.addVertex(matrixStack, x + outerHalf, y - outerHalf, MAP_OVERLAY_DEPTH).setColor(borderRed, borderGreen, borderBlue, 1.0F);

        float red = ARGB.redFloat(color);
        float green = ARGB.greenFloat(color);
        float blue = ARGB.blueFloat(color);
        float alpha = ARGB.alphaFloat(color);
        buffer.addVertex(matrixStack, x - innerHalf, y - innerHalf, MAP_OVERLAY_DEPTH + 0.01F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrixStack, x - innerHalf, y + innerHalf, MAP_OVERLAY_DEPTH + 0.01F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrixStack, x + innerHalf, y + innerHalf, MAP_OVERLAY_DEPTH + 0.01F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrixStack, x + innerHalf, y - innerHalf, MAP_OVERLAY_DEPTH + 0.01F).setColor(red, green, blue, alpha);
    }

    private String currentSeedMapperWorldKey() {
        String world = waypointManager.getCurrentWorldName();
        String sub = waypointManager.getCurrentSubworldDescriptor(false);
        Level level = GameVariableAccessShim.getWorld();
        String dim = level == null ? "unknown" : level.dimension().identifier().toString();
        return (world == null ? "unknown" : world) + "|" + (sub == null ? "" : sub) + "|" + dim;
    }

    private int getCurrentCubiomesDimension() {
        Level currentLevel = GameVariableAccessShim.getWorld();
        if (currentLevel == null) {
            return Integer.MIN_VALUE;
        }
        if (currentLevel.dimension() == Level.NETHER) {
            return com.github.cubiomes.Cubiomes.DIM_NETHER();
        }
        if (currentLevel.dimension() == Level.END) {
            return com.github.cubiomes.Cubiomes.DIM_END();
        }
        return com.github.cubiomes.Cubiomes.DIM_OVERWORLD();
    }

    private record SeedMapperMinimapQueryKey(long seed, int dimension, int mcVersion, int generatorFlags, int centerX, int centerZ, int radius, boolean lootOnly, int datapackHash, String datapackWorldKey) {
    }

    private void drawChunkOverlayMinimap(Matrix4fStack matrixStack, int x, int y, double baseX, double baseZ) {
        RadarSettingsManager radarSettings = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        int centerChunkX = Mth.floor(baseX) >> 4;
        int centerChunkZ = Mth.floor(baseZ) >> 4;
        // Cover full minimap footprint at extreme zoom-out. Chunk overlays need a slightly
        // larger sampling radius than entity markers because rotated square maps and texture
        // overscan can otherwise leave a 1-2 chunk border near the frame.
        double footprintBlocks = this.options.squareMap ? 44.0D : 34.0D;
        if (this.options.squareMap && this.options.rotates) {
            footprintBlocks += 6.0D;
        }
        int radius = Math.max(4, (int) Math.ceil(footprintBlocks * zoomScaleAdjusted / 16.0D) + 2);

        if (radarSettings.showExploredChunks) {
            List<ChunkPos> exploredChunks = new ArrayList<>(VoxelConstants.getVoxelMapInstance().getExploredChunksManager().getExploredChunksInRange(centerChunkX, centerChunkZ, radius));
            for (ChunkPos chunk : exploredChunks) {
                drawChunkSquare(matrixStack, x, y, baseX, baseZ, chunk, radarSettings.getExploredChunksColorRgb(), radarSettings.exploredChunksOpacity / 100.0F);
            }
        }

        if (radarSettings.showNewerNewChunks) {
            NewerNewChunksManager manager = VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager();

            List<ChunkPos> oldChunks = new ArrayList<>(manager.getOldChunksInRange(centerChunkX, centerChunkZ, radius));
            for (ChunkPos chunk : oldChunks) {
                drawChunkSquare(matrixStack, x, y, baseX, baseZ, chunk, radarSettings.getNewerNewChunksOldColorRgb(), radarSettings.newerNewChunksOldOpacity / 100.0F);
            }

            List<ChunkPos> newChunks = new ArrayList<>(manager.getNewChunksInRange(centerChunkX, centerChunkZ, radius));
            for (ChunkPos chunk : newChunks) {
                drawChunkSquare(matrixStack, x, y, baseX, baseZ, chunk, radarSettings.getNewerNewChunksNewColorRgb(), radarSettings.newerNewChunksNewOpacity / 100.0F);
            }

            List<ChunkPos> blockUpdateChunks = new ArrayList<>(manager.getBlockUpdateChunksInRange(centerChunkX, centerChunkZ, radius));
            for (ChunkPos chunk : blockUpdateChunks) {
                int color = switch (radarSettings.newerNewChunksDetectMode) {
                    case 1 -> radarSettings.getNewerNewChunksOldColorRgb();
                    case 2 -> radarSettings.getNewerNewChunksBlockColorRgb();
                    default -> radarSettings.getNewerNewChunksNewColorRgb();
                };
                float opacity = switch (radarSettings.newerNewChunksDetectMode) {
                    case 1 -> radarSettings.newerNewChunksOldOpacity / 100.0F;
                    case 2 -> radarSettings.newerNewChunksBlockOpacity / 100.0F;
                    default -> radarSettings.newerNewChunksNewOpacity / 100.0F;
                };
                drawChunkSquare(matrixStack, x, y, baseX, baseZ, chunk, color, opacity);
            }
        }
    }

    private void drawPortalMarkersMinimap(Matrix4fStack matrixStack, int x, int y, double baseX, double baseZ) {
        boolean showNether = options.showNetherPortalMarkers;
        boolean showEnd = options.showEndPortalMarkers;
        boolean showEndBeacon = options.showEndGatewayMarkers;
        if (!showNether && !showEnd && !showEndBeacon) {
            return;
        }

        int centerX = Mth.floor(baseX);
        int centerZ = Mth.floor(baseZ);
        int radius = Math.max(128, (int) Math.ceil(44.0D * zoomScaleAdjusted));
        for (PortalMarkersManager.PortalMarker marker : VoxelConstants.getVoxelMapInstance().getPortalMarkersManager()
                .getMarkersInRange(centerX, centerZ, radius, showNether, showEnd, showEndBeacon)) {
            Identifier icon = switch (marker.type()) {
                case NETHER -> resourceNetherPortal;
                case END -> resourceEndPortalFrame;
                case END_BEACON -> resourceBedrock;
            };
            drawPortalMarkerMinimap(matrixStack, x, y, marker.pos(), baseX, baseZ, icon);
        }
    }

    private void drawPortalMarkerMinimap(Matrix4fStack matrixStack, int x, int y, BlockPos markerPos, double baseX, double baseZ, Identifier icon) {
        double wayX = baseX - markerPos.getX() - 0.5D;
        double wayY = baseZ - markerPos.getZ() - 0.5D;
        float locate = (float) Math.toDegrees(Math.atan2(wayX, wayY));
        float hypot = (float) (Math.sqrt(wayX * wayX + wayY * wayY) / zoomScaleAdjusted);
        if (this.options.rotates) {
            locate += this.direction;
        } else {
            locate -= this.rotationFactor;
        }

        if (this.options.squareMap) {
            double radLocate = Math.toRadians(locate);
            double dispX = hypot * Math.sin(radLocate);
            double dispY = -hypot * Math.cos(radLocate);
            if (Math.abs(dispX) > 28.5D || Math.abs(dispY) > 28.5D) {
                return;
            }
        } else if (hypot >= 31.0F) {
            return;
        }

        float iconSize = 8.0F;
        try {
            matrixStack.pushMatrix();
            matrixStack.rotate(Axis.ZP.rotationDegrees(-locate));
            matrixStack.translate(0.0F, -hypot, 0.0F);
            matrixStack.rotate(Axis.ZP.rotationDegrees(locate));
            RenderType markerRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(icon);
            VertexConsumer markerBuffer = renderBufferSource.getBuffer(markerRenderType);
            RenderUtils.drawTexturedModalRect(matrixStack, markerBuffer, x - iconSize / 2.0F, y - iconSize / 2.0F, MAP_OVERLAY_DEPTH, iconSize, iconSize, 0xFFFFFFFF);
        } catch (Exception ignored) {
        } finally {
            matrixStack.popMatrix();
        }
    }

    private void drawChunkSquare(Matrix4fStack matrixStack, int x, int y, double baseX, double baseZ, ChunkPos chunk, int rgb, float alpha) {
        double minX = chunk.getMinBlockX();
        double minZ = chunk.getMinBlockZ();
        double maxX = minX + 16.0D;
        double maxZ = minZ + 16.0D;

        float[] p0 = projectChunkPoint(baseX, baseZ, minX, minZ);
        float[] p1 = projectChunkPoint(baseX, baseZ, minX, maxZ);
        float[] p2 = projectChunkPoint(baseX, baseZ, maxX, maxZ);
        float[] p3 = projectChunkPoint(baseX, baseZ, maxX, minZ);

        if (!Float.isFinite(p0[0]) || !Float.isFinite(p0[1])
                || !Float.isFinite(p1[0]) || !Float.isFinite(p1[1])
                || !Float.isFinite(p2[0]) || !Float.isFinite(p2[1])
                || !Float.isFinite(p3[0]) || !Float.isFinite(p3[1])) {
            return;
        }

        final float hardLimit = 96.0F;
        if (Math.abs(p0[0]) > hardLimit || Math.abs(p0[1]) > hardLimit
                || Math.abs(p1[0]) > hardLimit || Math.abs(p1[1]) > hardLimit
                || Math.abs(p2[0]) > hardLimit || Math.abs(p2[1]) > hardLimit
                || Math.abs(p3[0]) > hardLimit || Math.abs(p3[1]) > hardLimit) {
            return;
        }

        if (isChunkOutsideMinimap(p0, p1, p2, p3)) {
            return;
        }

        int color = ((int) (alpha * 255.0F) << 24) | (rgb & 0x00FFFFFF);
        float red = ARGB.redFloat(color);
        float green = ARGB.greenFloat(color);
        float blue = ARGB.blueFloat(color);
        float overlayAlpha = ARGB.alphaFloat(color);
        VertexConsumer overlayBuffer = renderBufferSource.getBuffer(VoxelMapRenderTypes.WAYPOINT_TEXT_BACKGROUND);
        overlayBuffer.addVertex(matrixStack, x + p0[0], y + p0[1], MAP_OVERLAY_DEPTH - 1.0F).setColor(red, green, blue, overlayAlpha);
        overlayBuffer.addVertex(matrixStack, x + p1[0], y + p1[1], MAP_OVERLAY_DEPTH - 1.0F).setColor(red, green, blue, overlayAlpha);
        overlayBuffer.addVertex(matrixStack, x + p2[0], y + p2[1], MAP_OVERLAY_DEPTH - 1.0F).setColor(red, green, blue, overlayAlpha);
        overlayBuffer.addVertex(matrixStack, x + p3[0], y + p3[1], MAP_OVERLAY_DEPTH - 1.0F).setColor(red, green, blue, overlayAlpha);
    }

    private float[] projectChunkPoint(double baseX, double baseZ, double worldX, double worldZ) {
        double wayX = baseX - worldX;
        double wayZ = baseZ - worldZ;
        float locate = (float) Math.toDegrees(Math.atan2(wayX, wayZ));
        float hypot = (float) (Math.sqrt(wayX * wayX + wayZ * wayZ) / zoomScaleAdjusted);
        if (this.options.rotates) {
            locate += this.direction;
        } else {
            locate -= this.rotationFactor;
        }

        double radLocate = Math.toRadians(locate);
        return new float[] {
                (float) (-hypot * Math.sin(radLocate)),
                (float) (-hypot * Math.cos(radLocate))
        };
    }

    private boolean isChunkOutsideMinimap(float[] p0, float[] p1, float[] p2, float[] p3) {
        if (this.options.squareMap) {
            return quadOutsideSquare(p0, p1, p2, p3);
        }
        float radius = 31.5F;
        float radiusSq = radius * radius;
        return quadOutsideCircle(p0, p1, p2, p3, radiusSq);
    }

    private static boolean quadOutsideSquare(float[] p0, float[] p1, float[] p2, float[] p3) {
        float min = -31.5F;
        float max = 31.5F;
        float[][] points = new float[][] {p0, p1, p2, p3};

        boolean allLeft = true;
        boolean allRight = true;
        boolean allTop = true;
        boolean allBottom = true;
        for (float[] p : points) {
            allLeft &= p[0] < min;
            allRight &= p[0] > max;
            allTop &= p[1] < min;
            allBottom &= p[1] > max;
        }

        return allLeft || allRight || allTop || allBottom;
    }

    private static boolean outsideCircle(float[] point, float radiusSq) {
        return point[0] * point[0] + point[1] * point[1] > radiusSq;
    }

    private static boolean quadOutsideCircle(float[] p0, float[] p1, float[] p2, float[] p3, float radiusSq) {
        if (!outsideCircle(p0, radiusSq) || !outsideCircle(p1, radiusSq) || !outsideCircle(p2, radiusSq) || !outsideCircle(p3, radiusSq)) {
            return false;
        }

        float[][] points = new float[][] {p0, p1, p2, p3};
        if (polygonContainsOrigin(points)) {
            return false;
        }

        for (int i = 0; i < points.length; i++) {
            float[] a = points[i];
            float[] b = points[(i + 1) % points.length];
            if (segmentIntersectsCircle(a, b, radiusSq)) {
                return false;
            }
        }

        return true;
    }

    private static boolean polygonContainsOrigin(float[][] points) {
        boolean inside = false;
        for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
            float xi = points[i][0];
            float yi = points[i][1];
            float xj = points[j][0];
            float yj = points[j][1];
            boolean crosses = (yi > 0.0F) != (yj > 0.0F);
            if (crosses) {
                float xAtYAxis = (xj - xi) * (-yi) / (yj - yi) + xi;
                if (xAtYAxis > 0.0F) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static boolean segmentIntersectsCircle(float[] a, float[] b, float radiusSq) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float lenSq = dx * dx + dy * dy;
        if (lenSq == 0.0F) {
            return !outsideCircle(a, radiusSq);
        }
        float t = -(a[0] * dx + a[1] * dy) / lenSq;
        t = Mth.clamp(t, 0.0F, 1.0F);
        float closestX = a[0] + t * dx;
        float closestY = a[1] + t * dy;
        return closestX * closestX + closestY * closestY <= radiusSq;
    }


    private void drawArrow(Matrix4fStack matrixStack, int x, int y, float scaleProj) {
        final float arrowSize = 6.0F;
        matrixStack.pushMatrix();
        matrixStack.scale(scaleProj, scaleProj, 1.0F);

        matrixStack.translate(x, y, 0.0F);
        matrixStack.rotate(Axis.ZP.rotationDegrees(this.options.rotates && !this.fullscreenMap ? 0.0F : this.direction + this.rotationFactor));
        matrixStack.translate(-x, -y, 0.0F);

        RenderType renderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(resourceArrow);
        VertexConsumer buffer = renderBufferSource.getBuffer(renderType);
        RenderUtils.drawTexturedModalRect(matrixStack, buffer, x - arrowSize / 2.0F, y - arrowSize / 2.0F, MAP_OVERLAY_DEPTH, arrowSize, arrowSize, 0xFFFFFFFF);

        matrixStack.popMatrix();
    }

    private void renderMapFull(Matrix4fStack matrixStack, int scWidth, int scHeight, float scaleProj) {
        synchronized (this.coordinateLock) {
            if (this.imageChanged) {
                this.imageChanged = false;
                this.mapImages[this.zoom].upload();
                this.lastImageX = this.lastX;
                this.lastImageZ = this.lastZ;
            }
        }
        matrixStack.pushMatrix();
        matrixStack.scale(scaleProj, scaleProj, 1.0F);
        matrixStack.translate(scWidth / 2.0F, scHeight / 2.0F, 0.0F);
        matrixStack.rotate(Axis.ZP.rotationDegrees(rotationFactor));
        matrixStack.translate(-(scWidth / 2.0F), -(scHeight / 2.0F), 0.0F);
        int mapSize = Math.max(256, Mth.ceil(Mth.sqrt((float) (scWidth * scWidth + scHeight * scHeight))) + 24);
        int left = scWidth / 2 - mapSize / 2;
        int top = scHeight / 2 - mapSize / 2;
        RenderType mapRenderType = VoxelMapRenderTypes.GUI_TEXTURED_LEQUAL_DEPTH_TEST.apply(mapResources[zoom]);
        VertexConsumer mapBuffer = renderBufferSource.getBuffer(mapRenderType);
        RenderUtils.drawTexturedModalRect(matrixStack, mapBuffer, left, top, MAP_IMAGE_DEPTH, mapSize, mapSize, 0xFFFFFFFF);
        matrixStack.popMatrix();

        if (this.options.biomeOverlay != 0) {
            double factor = Math.pow(2.0, 3 - this.zoom);
            float mapScale = mapSize / 256.0F;
            int minimumSize = (int) Math.pow(2.0, this.zoom);
            minimumSize *= minimumSize;
            ArrayList<AbstractMapData.BiomeLabel> labels = this.mapData[this.zoom].getBiomeLabels();
            matrixStack.pushMatrix();

            for (AbstractMapData.BiomeLabel o : labels) {
                if (o.segmentSize > minimumSize) {
                    String name = o.name;
                    float x = (float) (o.x * factor);
                    float z = (float) (o.z * factor);
                    if (this.options.oldNorth) {
                        RenderUtils.drawCenteredString(matrixStack, renderBufferSource, name, (left + mapSize) - z * mapScale, top + x * mapScale - 3.0F, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
                    } else {
                        RenderUtils.drawCenteredString(matrixStack, renderBufferSource, name, left + x * mapScale, top + z * mapScale - 3.0F, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
                    }
                }
            }

            matrixStack.popMatrix();
        }
    }

    private void drawDirections(Matrix4fStack matrixStack, int x, int y, float scaleProj) {
        if (this.options.showFacingDegrees && this.options.showFacingCardinal) {
            return;
        }
        float scale = 0.5F * this.options.radarTextScale;
        float rotate;
        if (this.options.rotates) {
            rotate = -this.direction - 90.0F - this.rotationFactor;
        } else {
            rotate = -90.0F;
        }

        float distance;
        if (this.options.squareMap) {
            if (this.options.rotates) {
                float tempdir = this.direction % 90.0F;
                tempdir = 45.0F - Math.abs(45.0F - tempdir);
                distance = 33.5F / scale / Mth.cos(Math.toRadians(tempdir));
            } else {
                distance = 33.5F / scale;
            }
        } else {
            distance = 32.5F / scale;
        }

        matrixStack.pushMatrix();
        matrixStack.scale(scaleProj, scaleProj, 1.0F);
        matrixStack.scale(scale, scale, 1.0F);

        matrixStack.pushMatrix();
        matrixStack.translate(distance * Mth.sin(-(rotate - 90.0F) * Mth.DEG_TO_RAD), distance * Mth.cos(-(rotate - 90.0F) * Mth.DEG_TO_RAD), 0.0F);
        RenderUtils.drawCenteredString(matrixStack, renderBufferSource, "N", x / scale, y / scale - 4.0F, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
        matrixStack.popMatrix();
        matrixStack.pushMatrix();
        matrixStack.translate(distance * Mth.sin(-(rotate) * Mth.DEG_TO_RAD), distance * Mth.cos(-(rotate) * Mth.DEG_TO_RAD), 0.0F);
        RenderUtils.drawCenteredString(matrixStack, renderBufferSource, "E", x / scale, y / scale - 4.0F, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
        matrixStack.popMatrix();
        matrixStack.pushMatrix();
        matrixStack.translate(distance * Mth.sin(-(rotate + 90.0F) * Mth.DEG_TO_RAD), distance * Mth.cos(-(rotate + 90.0F) * Mth.DEG_TO_RAD), 0.0F);
        RenderUtils.drawCenteredString(matrixStack, renderBufferSource, "S", x / scale, y / scale - 4.0F, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
        matrixStack.popMatrix();
        matrixStack.pushMatrix();
        matrixStack.translate(distance * Mth.sin(-(rotate + 180.0F) * Mth.DEG_TO_RAD), distance * Mth.cos(-(rotate + 180.0F) * Mth.DEG_TO_RAD), 0.0F);
        RenderUtils.drawCenteredString(matrixStack, renderBufferSource, "W", x / scale, y / scale - 4.0F, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
        matrixStack.popMatrix();

        matrixStack.popMatrix();
    }

    private void showCoords(Matrix4fStack matrixStack, int x, int y, float scaleProj) {
        if (!this.options.hide && !this.fullscreenMap) {
            int displayLineCount = 0;
            if (this.options.showFacingDegrees) {
                displayLineCount++;
            }
            if (this.options.coordsMode == 1) {
                displayLineCount += 2;
            } else if (this.options.coordsMode == 2) {
                displayLineCount++;
            }
            if (this.options.showBiome) {
                displayLineCount++;
            }
            if (!this.message.isEmpty()) {
                displayLineCount++;
            }
            if (displayLineCount == 0) {
                return;
            }

            int textStart;
            int lineHeight = 10;
            int belowStart = y + 32 + 4;
            int belowEnd = belowStart + lineHeight * (displayLineCount - 1) + 9;
            boolean placeAbove = belowEnd > this.scHeight - 15;
            if (placeAbove) {
                textStart = y - 32 - 4 - 9 - lineHeight * (displayLineCount - 1);
                if (textStart < 5) {
                    textStart = 5;
                }
            } else {
                textStart = belowStart;
            }
            int lineCount = 0;
            float scale = 0.5F * this.options.radarTextScale;
            matrixStack.pushMatrix();
            matrixStack.scale(scale * scaleProj, scale * scaleProj, 1.0F);

            String coords;
            if (this.options.showFacingDegrees) {
                int heading = normalizeHeading((int) (this.direction + this.rotationFactor));
                coords = this.options.showFacingCardinal
                        ? heading + "° " + getCompassAbbreviation(heading)
                        : heading + "°";
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, coords, x / scale, textStart / scale + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
                lineCount++;
            }

            if (this.options.coordsMode == 1) {
                coords = this.dCoord(GameVariableAccessShim.xCoord()) + ", " + this.dCoord(GameVariableAccessShim.zCoord());
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, coords, x / scale, textStart / scale + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true); // X, Z
                lineCount++;

                coords = this.dCoord(GameVariableAccessShim.yCoord());
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, coords, x / scale, textStart / scale + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true); // Y
                lineCount++;
            } else if (this.options.coordsMode == 2) {
                coords = GameVariableAccessShim.xCoord() + ", " + GameVariableAccessShim.yCoord() + ", " + GameVariableAccessShim.zCoord();
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, coords, x / scale, textStart / scale + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true); // X, Z
                lineCount++;
            }

            if (this.options.showBiome) {
                coords = BiomeRepository.getName(this.lastBiome);
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, coords, x / scale, textStart / scale + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true); // BIOME
                lineCount++;
            }

            if (!this.message.isEmpty()) {
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, this.message, x / scale, textStart / scale + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true); // WORLD NAME
                lineCount++;
            }

            matrixStack.popMatrix();
        } else {
            int textStart = 5;
            int lineCount = 0;
            int lineHeight = 10;

            if (this.options.coordsMode != 0) {
                int heading = (int) (this.direction + this.rotationFactor);
                if (heading > 360) {
                    heading -= 360;
                }
                String ns = "";
                String ew = "";
                if (heading > 360 - 67.5 || heading <= 67.5) {
                    ns = "north";
                } else if (heading > 180 - 67.5 && heading <= 180 + 67.5) {
                    ns = "south";
                }
                if (heading > 90 - 67.5 && heading <= 90 + 67.5) {
                    ew = "east";
                } else if (heading > 270 - 67.5 && heading <= 270 + 67.5) {
                    ew = "west";
                }

                String direction = I18n.get("minimap.ui." + ns + ew);
                String stats = "(" + this.dCoord(GameVariableAccessShim.xCoord()) + ", " + this.dCoord(GameVariableAccessShim.yCoord()) + ", " + this.dCoord(GameVariableAccessShim.zCoord()) + ") " + heading + "' " + direction;
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, stats, (this.scWidth * scaleProj / 2.0F), textStart + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
                lineCount++;
            }
            if (!this.message.isEmpty()) {
                RenderUtils.drawCenteredString(matrixStack, renderBufferSource, this.message, (this.scWidth * scaleProj / 2.0F), textStart + lineHeight * lineCount, MAP_TEXT_DEPTH, 0xFFFFFFFF, true);
                lineCount++;
            }
        }
    }

    private String dCoord(int paramInt1) {
        if (paramInt1 < 0) {
            return "-" + Math.abs(paramInt1);
        } else {
            return paramInt1 > 0 ? "+" + paramInt1 : Integer.toString(paramInt1);
        }
    }

    private static int normalizeHeading(int heading) {
        int normalized = heading % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized;
    }

    private static String getCompassAbbreviation(int heading) {
        String[] directions = { "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW" };
        int index = (int) Math.round(heading / 22.5D) & 15;
        return directions[index];
    }

    private void showMessage(String str) {
        this.message = str;
        this.messageTime = System.currentTimeMillis();
    }

    public static double getMinTablistOffset() {
        return minTablistOffset;
    }

    public static float getStatusIconOffset() {
        return statusIconOffset;
    }
}
