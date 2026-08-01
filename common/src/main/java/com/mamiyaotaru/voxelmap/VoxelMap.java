package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.interfaces.AbstractRadar;
import com.mamiyaotaru.voxelmap.interfaces.IReloadListener;
import com.mamiyaotaru.voxelmap.multiloader.Events;
import com.mamiyaotaru.voxelmap.multiloader.MultiLoaderManager;
import com.mamiyaotaru.voxelmap.multiloader.PackRegistrar;
import com.mamiyaotaru.voxelmap.persistent.PersistentMap;
import com.mamiyaotaru.voxelmap.persistent.PersistentMapSettingsManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.persistent.ThreadManager;
import com.mamiyaotaru.voxelmap.persistent.VoxelMapDataStore;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.util.BiomeRepository;
import com.mamiyaotaru.voxelmap.util.DimensionManager;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.MapUtils;
import com.mamiyaotaru.voxelmap.util.ModrinthUpdateChecker;
import com.mamiyaotaru.voxelmap.util.WorldUpdateListener;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoxelMap implements PreparableReloadListener {
    private static final Pattern SEED_IN_CHAT = Pattern.compile("(-?\\d{5,20})");
    private static final boolean SHOW_UNDER_MENUS = true;
    private static final boolean IS_FAIR = false;

    private boolean initialized = false;

    private MapSettingsManager mapOptions;
    private RadarSettingsManager radarOptions;
    private PersistentMapSettingsManager persistentMapOptions;
    private SeedMapperSettingsManager seedMapperOptions;

    private Map map;
    private PersistentMap persistentMap;
    private Radar radar;
    private RadarSimple radarSimple;
    private SettingsAndLightingChangeNotifier settingsAndLightingChangeNotifier;
    private WorldUpdateListener worldUpdateListener;
    private ExploredChunksManager exploredChunksManager;
    private NewerNewChunksManager newerNewChunksManager;
    private PortalMarkersManager portalMarkersManager;

    private ColorManager colorManager;
    private WaypointManager waypointManager;
    private DimensionManager dimensionManager;

    private ClientLevel world;
    private String worldName = "";
    private String passMessage;
    private Properties imageProperties;

    private final VoxelMapDataStore dataStore = new VoxelMapDataStore();
    private final ArrayDeque<Runnable> executionQueue = new ArrayDeque<>();
    private final ArrayDeque<Runnable> runOnWorldSet = new ArrayDeque<>();
    private final ArrayList<IReloadListener> reloadListeners = new ArrayList<>();


    VoxelMap() {}

    private void lateInit(boolean showUnderMenus, boolean isFair) {
        mapOptions = new MapSettingsManager();
        radarOptions = new RadarSettingsManager();
        persistentMapOptions = new PersistentMapSettingsManager();
        seedMapperOptions = new SeedMapperSettingsManager();

        mapOptions.showUnderMenus = showUnderMenus;
        radarOptions.forceCpuRendering = VoxelConstants.hasVulkanMod();
        radarOptions.radarAllowed = !isFair;
        radarOptions.radarMobsAllowed = !isFair;
        radarOptions.radarPlayersAllowed = !isFair;

        mapOptions.addSubSettingsManager(radarOptions);
        mapOptions.addSubSettingsManager(persistentMapOptions);
        mapOptions.addSubSettingsManager(seedMapperOptions);
        mapOptions.loadAll();

        colorManager = new ColorManager();
        waypointManager = new WaypointManager();
        dimensionManager = new DimensionManager();
        exploredChunksManager = new ExploredChunksManager();
        newerNewChunksManager = new NewerNewChunksManager();
        portalMarkersManager = new PortalMarkersManager();

        try {
            if (radarOptions.radarAllowed && (radarOptions.radarMobsAllowed || radarOptions.radarPlayersAllowed)) {
                radar = new Radar();
                radarSimple = new RadarSimple();
            }
        } catch (RuntimeException e) {
            VoxelConstants.getLogger().error("Failed creating radar {}", e.getLocalizedMessage(), e);
            radarOptions.radarAllowed = false;
            radarOptions.radarMobsAllowed = false;
            radarOptions.radarPlayersAllowed = false;
            radar = null;
            radarSimple = null;
        }

        map = new Map();
        persistentMap = new PersistentMap();
        settingsAndLightingChangeNotifier = new SettingsAndLightingChangeNotifier();
        worldUpdateListener = new WorldUpdateListener();
        worldUpdateListener.addListener(map);
        worldUpdateListener.addListener(persistentMap);

        addReloadListener(map);
        addReloadListener(waypointManager);
        addReloadListener(radar);
        addReloadListener(radarSimple);
        addReloadListener(colorManager);

        initialized = true;
    }

    public boolean isRunning() {
        return initialized;
    }

    public synchronized void execute(Runnable task) {
        if (!initialized) {
            executionQueue.addLast(task);
        } else {
            task.run();
        }
    }

    public synchronized void runOnWorldSet(Runnable task) {
        if (world == null) {
            runOnWorldSet.addLast(task);
        } else {
            task.run();
        }
    }

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor executor, PreparationBarrier preparationBarrier, Executor executor2) {
        return preparationBarrier.wait((Object) Unit.INSTANCE).thenRunAsync(() -> apply(sharedState.resourceManager()), executor2);
    }

    private void apply(ResourceManager resourceManager) {
        execute(() -> {
            imageProperties = new Properties();
            Identifier location = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "configs/images.properties");
            resourceManager.getResource(location).ifPresent((resource) -> {
                try (InputStream inputStream = resource.open()) {
                    imageProperties.load(inputStream);
                } catch (Exception ignored) {
                }
            });

            BiomeRepository.loadBiomeColors();

            for (IReloadListener listener : reloadListeners) {
                if (listener == null) continue;
                listener.onResourceManagerReload(resourceManager);
            }
        });
    }

    public void addReloadListener(IReloadListener listener) {
        reloadListeners.add(listener);
    }

    public void registerPacks(PackRegistrar registrar) {
        registrar.registerPack(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "voxelmap_legacy"), Component.translatable("resourcePack.minimap.voxelmapLegacy.title"));
    }

    public void onEventsSet(Events events) {
        events.initEvents(this);
    }

    public void onTickInGame(GuiGraphicsExtractor graphics) {
        if (!initialized) return;

        com.mamiyaotaru.voxelmap.integration.BaritoneOreMiner.getInstance().tick();
        map.onTickInGame(graphics);
        if (passMessage != null) {
            VoxelConstants.getMinecraft().gui.hud.getChat().addClientSystemMessage(Component.literal(passMessage));
            passMessage = null;
        }

    }

    public void onTick() {
        if (!initialized) {
            lateInit(SHOW_UNDER_MENUS, IS_FAIR);
            return;
        }

        while (!executionQueue.isEmpty()) {
            executionQueue.removeFirst().run();
        }

        ClientLevel newWorld = GameVariableAccessShim.getWorld();
        if (world != newWorld) {
            if (world != null) {
                com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerDetection.flushPersistence();
            }
            world = newWorld;
            waypointManager.newWorld(world);
            persistentMap.newWorld(world);
            map.newWorld(world);
            if (world != null) {
                MapUtils.reset();

                // send "new" world_id packet
                MultiLoaderManager.getPacketBridge().sendWorldIDPacket("");

                if (!worldName.equals(waypointManager.getCurrentWorldName())) {
                    worldName = waypointManager.getCurrentWorldName();
                }

                while (!runOnWorldSet.isEmpty()) {
                    runOnWorldSet.removeFirst().run();
                }
            }
        }

        VoxelConstants.tick();
        exploredChunksManager.onTick();
        newerNewChunksManager.onTick();
        portalMarkersManager.onTick();
        com.mamiyaotaru.voxelmap.seedmapper.SeedMapperElytraDetection.tick();
        com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerDetection.tick();
        persistentMap.onTick();
    }

    public void checkPermissionMessages(Component message) {
        String msg = message.getString().replaceAll("§r", "");
        SeedMapperCommandHandler.handlePotentialLocateResult(msg);
        tryAutoApplySeedcrackerSeed(msg);

        execute(() -> {
            if (msg.contains("§3 §6 §3 §6 §3 §6 §d")) {
                mapOptions.cavesAllowed = false;
                VoxelConstants.getLogger().info("Server disabled cavemapping.");
            }

            if (msg.contains("§3 §6 §3 §6 §3 §6 §e")) {
                radarOptions.radarAllowed = false;
                radarOptions.radarPlayersAllowed = false;
                radarOptions.radarMobsAllowed = false;
                VoxelConstants.getLogger().info("Server disabled radar.");
            }

            if (msg.contains("§3 §6 §3 §6 §3 §6 §f")) {
                mapOptions.cavesAllowed = true;
                VoxelConstants.getLogger().info("Server enabled cavemapping.");
            }

            if (msg.contains("§3 §6 §3 §6 §3 §6 §0")) {
                radarOptions.radarAllowed = true;
                radarOptions.radarPlayersAllowed = true;
                radarOptions.radarMobsAllowed = true;
                VoxelConstants.getLogger().info("Server enabled radar.");
            }
        });
    }

    public MapSettingsManager getMapOptions() {
        return mapOptions;
    }

    public RadarSettingsManager getRadarOptions() {
        return radarOptions;
    }

    public PersistentMapSettingsManager getPersistentMapOptions() {
        return persistentMapOptions;
    }

    private void tryAutoApplySeedcrackerSeed(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        String lower = msg.toLowerCase();
        if (!(lower.contains("seedcracker") || lower.contains("cracked seed") || lower.contains("seed found"))) {
            return;
        }
        Matcher matcher = SEED_IN_CHAT.matcher(msg);
        if (!matcher.find()) {
            return;
        }
        String seed = matcher.group(1);
        setWorldSeed(seed);
        if (seedMapperOptions != null && (seedMapperOptions.manualSeed == null || seedMapperOptions.manualSeed.isBlank())) {
            seedMapperOptions.manualSeed = seed;
            seedMapperOptions.putSavedSeed(seedMapperOptions.getCurrentServerKey(), seed);
        }
    }

    public SeedMapperSettingsManager getSeedMapperOptions() {
        return seedMapperOptions;
    }

    public Map getMap() {
        return map;
    }

    public PersistentMap getPersistentMap() {
        return persistentMap;
    }
    public AbstractRadar getRadar() {
        if (radarOptions.showRadar) {
            if (radarOptions.radarMode == 1) {
                return radarSimple;
            }

            if (radarOptions.radarMode == 2) {
                return radar;
            }
        }

        return null;
    }

    public Radar getFullRadar() {
        return radar;
    }

    public SettingsAndLightingChangeNotifier getSettingsAndLightingChangeNotifier() {
        return settingsAndLightingChangeNotifier;
    }

    public ColorManager getColorManager() {
        return colorManager;
    }

    public WaypointManager getWaypointManager() {
        return waypointManager;
    }

    public DimensionManager getDimensionManager() {
        return dimensionManager;
    }

    public VoxelMapDataStore getDataStore() {
        return dataStore;
    }

    public ExploredChunksManager getExploredChunksManager() {
        return exploredChunksManager;
    }

    public NewerNewChunksManager getNewerNewChunksManager() {
        return newerNewChunksManager;
    }

    public PortalMarkersManager getPortalMarkersManager() {
        return portalMarkersManager;
    }

    public Properties getImageProperties() {
        return imageProperties;
    }

    public synchronized void newSubWorldName(String name, boolean fromServer) {
        runOnWorldSet(() -> {
            waypointManager.setSubworldName(name, fromServer);
            map.newWorldName();
        });
    }

    public String getWorldSeed() {
        if (!initialized) return "";
        return waypointManager.getWorldSeed().isEmpty() ? VoxelConstants.getWorldByKey(Level.OVERWORLD).map(value -> Long.toString(((ServerLevel) value).getSeed())).orElse("") : waypointManager.getWorldSeed();
    }

    public void setWorldSeed(String newSeed) {
        if (!initialized) return;
        waypointManager.setWorldSeed(newSeed);
    }

    public void sendPlayerMessageOnMainThread(String s) {
        passMessage = s;
    }

    public WorldUpdateListener getWorldUpdateListener() {
        return worldUpdateListener;
    }

    public void clearServerSettings() {
        execute(() -> {
            radarOptions.radarAllowed = true;
            radarOptions.radarPlayersAllowed = true;
            radarOptions.radarMobsAllowed = true;
            mapOptions.cavesAllowed = true;
            mapOptions.serverTeleportCommand = null;

            mapOptions.worldmapAllowed = true;
            mapOptions.minimapAllowed = true;
            mapOptions.waypointsAllowed = true;
            mapOptions.deathWaypointAllowed = true;
        });
    }

    public void onJoinServer() {
        if (seedMapperOptions != null) {
            seedMapperOptions.loadSavedSeedForCurrentServer();
            if (seedMapperOptions.datapackAutoload) {
                String serverKey = seedMapperOptions.getCurrentServerKey();
                String savedUrl = seedMapperOptions.getDatapackSavedUrlsSnapshot().get(serverKey);
                String savedPath = seedMapperOptions.getDatapackSavedCachePathsSnapshot().get(serverKey);
                if (savedUrl != null && !savedUrl.isBlank()) {
                    seedMapperOptions.datapackUrl = savedUrl;
                }
                if (savedPath != null && !savedPath.isBlank()) {
                    seedMapperOptions.datapackCachePath = savedPath;
                    seedMapperOptions.datapackEnabled = true;
                    seedMapperOptions.setFeatureEnabled(com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature.DATAPACK_STRUCTURE, true);
                }
            }
        }
        if (getRadar() != null) {
            getRadar().onJoinServer();
        }
        ModrinthUpdateChecker.checkUpdates();
    }

    public void onDisconnect() {
        com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerDetection.flushPersistence();
        clearServerSettings();
    }

    public void onConfigurationInit() {
        clearServerSettings();
    }

    public void onClientStarted() {
    }

    public void onClientStopping() {
        try {
            VoxelConstants.onShutDown();
        } catch (RuntimeException e) {
            VoxelConstants.getLogger().warn("Error during VoxelMap shutdown preparation; continuing.", e);
        }
        if (map != null) {
            map.shutdown();
        }
        VoxelConstants.onShutDown();
        ThreadManager.flushSaveQueue();
        ThreadManager.shutdownCalculationQueue();
    }
}
