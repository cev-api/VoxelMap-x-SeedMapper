package com.mamiyaotaru.voxelmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mamiyaotaru.voxelmap.textures.IIconCreator;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.textures.TextureAtlas;
import com.mamiyaotaru.voxelmap.util.BackgroundImageInfo;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.MessageUtils;
import com.mamiyaotaru.voxelmap.util.TextUtils;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import com.mamiyaotaru.voxelmap.util.WaypointContainer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.RealmsServerList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class WaypointManager {
    public final MapSettingsManager options;
    final TextureAtlas textureAtlas;
    final TextureAtlas textureAtlasChooser;
    private boolean loaded;
    private boolean needSave;
    private ArrayList<Waypoint> wayPts = new ArrayList<>();
    private Waypoint highlightedWaypoint;
    private String worldName = "";
    private String currentSubWorldName = "";
    private String currentSubworldDescriptor = "";
    private String currentSubworldDescriptorNoCodes = "";
    private boolean multiworld;
    private boolean gotAutoSubworldName;
    private DimensionContainer currentDimension;
    private final TreeSet<String> knownSubworldNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final HashSet<String> oldNorthWorldNames = new HashSet<>();
    private final HashMap<String, String> worldSeeds = new HashMap<>();
    private BackgroundImageInfo backgroundImageInfo;
    private WaypointContainer waypointContainer;
    private File settingsFile;
    private Long lastNewWorldNameTime = 0L;
    private final Object waypointLock = new Object();
    public static final String fallbackIconLocation = "selectable/point";
    public static final Identifier resourceTextureAtlasWaypoints = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "atlas/waypoints");
    public static final Identifier resourceTextureAtlasWaypointChooser = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "atlas/waypoint-chooser");
    public final Minecraft minecraft = Minecraft.getInstance();
    public static final String coordinateHighlightName = "§t§a§r§g§e§t";

    public WaypointManager() {
        this.options = VoxelConstants.getVoxelMapInstance().getMapOptions();
        this.textureAtlas = new TextureAtlas("waypoints", resourceTextureAtlasWaypoints);
        this.textureAtlas.setFilter(true, false);
        this.textureAtlasChooser = new TextureAtlas("chooser", resourceTextureAtlasWaypointChooser);
        this.textureAtlasChooser.setFilter(true, false);
        this.waypointContainer = new WaypointContainer(this.options);
    }

    public void onResourceManagerReload(ResourceManager resourceManager) {
        List<Identifier> images = new ArrayList<>();
        IIconCreator iconCreator = textureAtlas -> {

            Map<Identifier, Resource> resourceMap = VoxelConstants.getMinecraft().getResourceManager().listResources("images/waypoints", asset -> asset.getPath().endsWith(".png"));
            for (Identifier candidate : resourceMap.keySet()) {
                if (candidate.getNamespace().equals(VoxelConstants.MOD_ID)) {
                    images.add(candidate);
                }
            }

            for (Identifier Identifier : images) {
                Sprite icon = textureAtlas.registerIconForResource(Identifier);
                String name = Identifier.toString();
                textureAtlas.registerMaskedIcon(toSimpleName(name), icon);
            }

            Sprite markerIcon = textureAtlas.registerIconForResource(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/waypoints/marker/arrow.png"));
            textureAtlas.registerMaskedIcon(toSimpleName(markerIcon.getIconName().toString()), markerIcon);

            Sprite targetIcon = textureAtlas.registerIconForResource(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/waypoints/marker/target.png"));
            textureAtlas.registerMaskedIcon(toSimpleName(targetIcon.getIconName().toString()), markerIcon);

        };

        this.textureAtlas.loadTextureAtlas(iconCreator);
        this.textureAtlasChooser.reset();

        images.sort(Comparator.comparingInt((Identifier id) -> {
            if (toSimpleName(id.toString()).equals(fallbackIconLocation)) {
                return 0;
            }
            return 1;
        }).thenComparing(Identifier::compareTo));

        for (Identifier Identifier : images) {
            String name = Identifier.toString();
            if (name.toLowerCase().contains("waypoints/selectable")) {
                Sprite icon = this.textureAtlasChooser.registerIconForResource(Identifier);
                this.textureAtlasChooser.registerMaskedIcon(toSimpleName(name), icon);
                this.textureAtlasChooser.stitchNew();
            }
        }

//      I couldn't find a better way to make stitch sorted :(
//      this.textureAtlasChooser.stitch();

        boolean useFiltering = Boolean.parseBoolean(VoxelConstants.getVoxelMapInstance().getImageProperties().getProperty("waypoint_icon_filtering", "true"));
        this.textureAtlas.setFilter(useFiltering, false);
        this.textureAtlasChooser.setFilter(useFiltering, false);
    }

    public static String toSimpleName(String name) {
        String prefix = "voxelmap:images/waypoints/";
        if (!name.startsWith(prefix)) {
            return name;
        }

        return name.substring(prefix.length()).replace(".png", "");
    }

    public TextureAtlas getTextureAtlas() {
        return this.textureAtlas;
    }

    public TextureAtlas getTextureAtlasChooser() {
        return this.textureAtlasChooser;
    }

    public ArrayList<Waypoint> getWaypoints() {
        return this.wayPts;
    }

    public WaypointContainer getWaypointContainer() {
        return this.waypointContainer;
    }

    public ImportResult importXaeroWaypoints() {
        File xaeroMinimapDir = new File(minecraft.gameDirectory, "xaero/minimap");
        if (!xaeroMinimapDir.isDirectory()) {
            return new ImportResult(0, 0, "No Xaero minimap folder found");
        }

        List<File> worldDirs = findMatchingWorldDirectories(xaeroMinimapDir, "Multiplayer_");
        if (worldDirs.isEmpty()) {
            return new ImportResult(0, 0, "No Xaero waypoints for this server");
        }

        int imported = 0;
        int skipped = 0;
        for (File worldDir : worldDirs) {
            try (var paths = Files.walk(worldDir.toPath())) {
                List<Path> files = paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .toList();
                for (Path file : files) {
                    ImportCounts counts = importXaeroFile(file.toFile());
                    imported += counts.imported;
                    skipped += counts.skipped;
                }
            } catch (IOException exception) {
                VoxelConstants.getLogger().error("Failed scanning Xaero waypoint folder " + worldDir.getPath(), exception);
            }
        }

        if (imported > 0) {
            saveWaypoints();
            waypointContainer.refreshRenderables();
        }

        return new ImportResult(imported, skipped, imported == 0 ? "No new Xaero waypoints found" : "Imported " + imported + " Xaero waypoints");
    }

    public ImportResult importWurstWaypoints() {
        File wurstWaypointDir = new File(minecraft.gameDirectory, "wurst/waypoints");
        if (!wurstWaypointDir.isDirectory()) {
            return new ImportResult(0, 0, "No Wurst waypoint folder found");
        }

        List<File> files = findMatchingFiles(wurstWaypointDir, ".json");
        if (files.isEmpty()) {
            return new ImportResult(0, 0, "No Wurst waypoint file for this server");
        }

        int imported = 0;
        int skipped = 0;
        for (File file : files) {
            ImportCounts counts = importWurstFile(file);
            imported += counts.imported;
            skipped += counts.skipped;
        }

        if (imported > 0) {
            saveWaypoints();
            waypointContainer.refreshRenderables();
        }

        return new ImportResult(imported, skipped, imported == 0 ? "No new Wurst waypoints found" : "Imported " + imported + " Wurst waypoints");
    }

    public void newWorld(Level world) {
        if (world == null) {
            this.currentDimension = null;
        } else {
            String mapName;
            if (VoxelConstants.getMinecraft().hasSingleplayerServer()) {
                mapName = this.getMapName();
            } else {
                mapName = this.getServerName();
                if (mapName != null) {
                    mapName = mapName.toLowerCase();
                }
            }

            if (!this.worldName.equals(mapName) && mapName != null && !mapName.isEmpty()) {
                this.currentDimension = null;
                this.worldName = mapName;
                VoxelConstants.getVoxelMapInstance().getDimensionManager().populateDimensions(world);
                this.loadWaypoints();
            }

            VoxelConstants.getVoxelMapInstance().getDimensionManager().enteredWorld(world);
            DimensionContainer dim = VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(world);
            this.enteredDimension(dim);
            this.setSubWorldDescriptor("");
        }

    }

    public String getMapName() {
        Optional<IntegratedServer> integratedServer = VoxelConstants.getIntegratedServer();

        if (integratedServer.isEmpty()) {
            String error = "Tried fetching map name on a non-integrated server!";

            VoxelConstants.getLogger().fatal(error);
            throw new IllegalStateException(error);
        }

        return integratedServer.get().getWorldPath(LevelResource.ROOT).normalize().toFile().getName();
    }

    public String getServerName() {
        String serverName = "";

        try {
            ServerData serverData = VoxelConstants.getMinecraft().getCurrentServer();
            if (serverData != null) {
                boolean isOnLAN = serverData.isLan();
                boolean isRealm = VoxelConstants.isRealmServer();
                if (isOnLAN) {
                    VoxelConstants.getLogger().warn("LAN server detected!");
                    serverName = serverData.name;
                } else if (isRealm) {
                    VoxelConstants.getLogger().info("Server is a Realm.");
                    RealmsClient realmsClient = RealmsClient.getOrCreate(Minecraft.getInstance());
                    RealmsServerList realmsServerList = realmsClient.listRealms();
                    for (RealmsServer realmsServer : realmsServerList.servers()) {
                        if (realmsServer.name.equals(serverData.name)) {
                            serverName = "Realm_" + realmsServer.id + "." + realmsServer.ownerUUID;
                            break;
                        }
                    }
                } else {
                    serverName = serverData.ip;
                }
            } else if (VoxelConstants.isRealmServer()) {
                VoxelConstants.getLogger().warn("ServerData was null, and detected as realm server.");
                User session = VoxelConstants.getMinecraft().getUser();
                serverName = session.getSessionId();
                VoxelConstants.getLogger().info(serverName);
            } else {
                ClientPacketListener netHandler = VoxelConstants.getMinecraft().getConnection();
                Connection networkManager = netHandler.getConnection();
                InetSocketAddress socketAddress = (InetSocketAddress) networkManager.getRemoteAddress();
                serverName = socketAddress.getHostString() + ":" + socketAddress.getPort();
            }
        } catch (Exception var6) {
            VoxelConstants.getLogger().error("error getting ServerData", var6);
        }

        return serverName;
    }

    public String getCurrentWorldName() {
        return this.worldName;
    }

    public void handleDeath() {
        HashSet<Waypoint> toDel = new HashSet<>();

        for (Waypoint pt : this.wayPts) {
            if (pt.name.equals("Latest Death")) {
                pt.name = "Previous Death";
            }

            if (pt.name.startsWith("Previous Death")) {
                if (this.options.deathpoints == 2) {
                    int num = 0;

                    try {
                        if (pt.name.length() > 15) {
                            num = Integer.parseInt(pt.name.substring(15));
                        }
                    } catch (NumberFormatException ignored) {}

                    pt.red -= (pt.red - 0.5F) / 8.0F;
                    pt.green -= (pt.green - 0.5F) / 8.0F;
                    pt.blue -= (pt.blue - 0.5F) / 8.0F;
                    pt.name = "Previous Death " + (num + 1);
                } else {
                    toDel.add(pt);
                }
            }
        }

        if (this.options.deathpoints != 2 && (!(toDel.isEmpty()))) {
            for (Waypoint pt : toDel) {
                this.deleteWaypoint(pt);
            }
        }

        if (this.options.deathpoints != 0) {
            TreeSet<DimensionContainer> dimensions = new TreeSet<>();
            dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(VoxelConstants.getPlayer().level()));
            double dimensionScale = VoxelConstants.getPlayer().level().dimensionType().coordinateScale();
            this.addWaypoint(new Waypoint("Latest Death", (int) (GameVariableAccessShim.xCoord() * dimensionScale), (int) (GameVariableAccessShim.zCoord() * dimensionScale), GameVariableAccessShim.yCoord() - 1, true, 1.0F, 1.0F, 1.0F, "Skull", this.getCurrentSubworldDescriptor(false), dimensions));
        }

    }

    private void enteredDimension(DimensionContainer dimension) {
        this.highlightedWaypoint = null;
        if (dimension == this.currentDimension) {
            this.multiworld = true;
        }

        this.currentDimension = dimension;
        synchronized (this.waypointLock) {
            for (Waypoint pt : this.wayPts) {
                pt.inDimension = pt.dimensions.isEmpty() || pt.dimensions.contains(dimension);
            }

            this.waypointContainer = new WaypointContainer(this.options);
            this.waypointContainer.refreshRenderables();
        }

        this.loadBackgroundMapImage();
    }

    public void setOldNorth(boolean oldNorth) {
        String oldNorthWorldName;
        if (this.knownSubworldNames.isEmpty()) {
            oldNorthWorldName = "all";
        } else {
            oldNorthWorldName = this.getCurrentSubworldDescriptor(false);
        }

        if (oldNorth) {
            this.oldNorthWorldNames.add(oldNorthWorldName);
        } else {
            this.oldNorthWorldNames.remove(oldNorthWorldName);
        }

        this.saveWaypoints();
    }

    public TreeSet<String> getKnownSubworldNames() {
        return this.knownSubworldNames;
    }

    public boolean receivedAutoSubworldName() {
        return this.gotAutoSubworldName;
    }

    public boolean isMultiworld() {
        return this.multiworld || VoxelConstants.isRealmServer();
    }

    public synchronized void setSubworldName(String name, boolean fromServer) {
        boolean notNull = !name.isEmpty();
        if (notNull || System.currentTimeMillis() - this.lastNewWorldNameTime > 2000L) {
            if (notNull) {
                if (fromServer) {
                    this.gotAutoSubworldName = true;
                }

                if (!name.equals(this.currentSubWorldName)) {
                    VoxelConstants.getLogger().info("New world name: " + TextUtils.scrubCodes(name));
                }

                this.lastNewWorldNameTime = System.currentTimeMillis();
            }

            this.currentSubWorldName = name;
            this.setSubWorldDescriptor(this.currentSubWorldName);
        }

    }

    private void setSubWorldDescriptor(String descriptor) {
        boolean serverSaysOldNorth = false;
        if (descriptor.endsWith("§o§n")) {
            descriptor = descriptor.substring(0, descriptor.length() - 4);
            serverSaysOldNorth = true;
        }

        this.currentSubworldDescriptor = descriptor;
        this.currentSubworldDescriptorNoCodes = TextUtils.scrubCodes(this.currentSubworldDescriptor);
        this.newSubworldName(this.currentSubworldDescriptorNoCodes);
        String currentSubWorldDescriptorScrubbed = TextUtils.scrubName(this.currentSubworldDescriptorNoCodes);
        synchronized (this.waypointLock) {
            for (Waypoint pt : this.wayPts) {
                pt.inWorld = currentSubWorldDescriptorScrubbed.isEmpty() || Objects.equals(pt.world, "") || currentSubWorldDescriptorScrubbed.equals(pt.world);
            }
        }

        if (serverSaysOldNorth) {
            if (this.currentSubworldDescriptorNoCodes.isEmpty()) {
                this.oldNorthWorldNames.add("all");
            } else {
                this.oldNorthWorldNames.add(this.currentSubworldDescriptorNoCodes);
            }
        }

        VoxelConstants.getVoxelMapInstance().getMapOptions().oldNorth = this.oldNorthWorldNames.contains(this.currentSubworldDescriptorNoCodes);
    }

    private void newSubworldName(String name) {
        if (name != null && !name.isEmpty()) {
            this.multiworld = true;
            if (this.knownSubworldNames.add(name)) {
                if (this.loaded) {
                    this.saveWaypoints();
                } else {
                    this.needSave = true;
                }
            }

            this.loadBackgroundMapImage();
        }
    }

    public void changeSubworldName(String oldName, String newName) {
        if (!newName.equals(oldName) && this.knownSubworldNames.remove(oldName)) {
            this.knownSubworldNames.add(newName);
            synchronized (this.waypointLock) {
                for (Waypoint pt : this.wayPts) {
                    if (pt.world.equals(oldName)) {
                        pt.world = newName;
                    }
                }
            }

            VoxelConstants.getVoxelMapInstance().getPersistentMap().renameSubworld(oldName, newName);
            String worldName = this.getCurrentWorldName();
            String worldNamePathPart = TextUtils.scrubNameFile(worldName);
            String subWorldNamePathPart = TextUtils.scrubNameFile(oldName) + "/";
            File oldCachedRegionFileDir = new File(minecraft.gameDirectory, "/mods/mamiyaotaru/voxelmap/cache/" + worldNamePathPart + "/" + subWorldNamePathPart);
            if (oldCachedRegionFileDir.exists() && oldCachedRegionFileDir.isDirectory()) {
                subWorldNamePathPart = TextUtils.scrubNameFile(newName) + "/";
                File newCachedRegionFileDir = new File(minecraft.gameDirectory, "/mods/mamiyaotaru/voxelmap/cache/" + worldNamePathPart + "/" + subWorldNamePathPart);
                boolean success = oldCachedRegionFileDir.renameTo(newCachedRegionFileDir);
                if (!success) {
                    VoxelConstants.getLogger().warn("Failed renaming " + oldCachedRegionFileDir.getPath() + " to " + newCachedRegionFileDir.getPath());
                }
            }

            if (oldName.equals(this.getCurrentSubworldDescriptor(false))) {
                this.setSubworldName(newName, false);
            }

            this.saveWaypoints();
        }

    }

    public void deleteSubworld(String name) {
        if (this.knownSubworldNames.remove(name)) {
            synchronized (this.waypointLock) {
                for (Waypoint pt : this.wayPts) {
                    if (pt.world.equals(name)) {
                        pt.world = "";
                        pt.inWorld = true;
                    }
                }
            }

            this.saveWaypoints();
            this.lastNewWorldNameTime = 0L;
            this.setSubworldName("", false);
        }

        if (this.knownSubworldNames.isEmpty()) {
            this.multiworld = false;
        }

    }

    public String getCurrentSubworldDescriptor(boolean withCodes) {
        return withCodes ? this.currentSubworldDescriptor : this.currentSubworldDescriptorNoCodes;
    }

    public String getWorldSeed() {
        String key = "all";
        if (!this.knownSubworldNames.isEmpty()) {
            key = this.getCurrentSubworldDescriptor(false);
        }

        String seed = this.worldSeeds.get(key);
        if (seed == null) {
            seed = "";
        }

        return seed;
    }

    public void setWorldSeed(String newSeed) {
        String worldName = "all";
        if (!this.knownSubworldNames.isEmpty()) {
            worldName = this.getCurrentSubworldDescriptor(false);
        }

        this.worldSeeds.put(worldName, newSeed);
        this.saveWaypoints();
    }

    public void saveWaypoints() {
        String worldNameSave = this.getCurrentWorldName();
        if (worldNameSave.endsWith(":25565")) {
            int portSepLoc = worldNameSave.lastIndexOf(':');
            if (portSepLoc != -1) {
                worldNameSave = worldNameSave.substring(0, portSepLoc);
            }
        }

        worldNameSave = TextUtils.scrubNameFile(worldNameSave);
        File saveDir = new File(minecraft.gameDirectory, "/voxelmap/");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        this.settingsFile = new File(saveDir, worldNameSave + ".points");

        try {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(this.settingsFile), StandardCharsets.UTF_8));
            String knownSubworldsString = this.knownSubworldNames.stream().map(subworldName -> TextUtils.scrubName(subworldName) + ",").collect(Collectors.joining());

            out.println("subworlds:" + knownSubworldsString);
            String oldNorthWorldsString = this.oldNorthWorldNames.stream().map(oldNorthWorldName -> TextUtils.scrubName(oldNorthWorldName) + ",").collect(Collectors.joining());

            out.println("oldNorthWorlds:" + oldNorthWorldsString);
            String seedsString = this.worldSeeds.entrySet().stream().map(entry -> TextUtils.scrubName(entry.getKey()) + "#" + entry.getValue() + ",").collect(Collectors.joining());

            out.println("seeds:" + seedsString);

            for (Waypoint pt : this.wayPts) {
                if (!(!pt.name.isEmpty() && pt.name.charAt(0) == '^')) {
                    StringBuilder dimensionsString = new StringBuilder();

                    for (DimensionContainer dimension : pt.dimensions) {
                        dimensionsString.append(dimension.getStorageName()).append("#");
                    }

                    if (dimensionsString.toString().isEmpty()) {
                        dimensionsString.append(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByIdentifier(BuiltinDimensionTypes.OVERWORLD.identifier()).getStorageName());
                    }

                    out.println("name:" + TextUtils.scrubName(pt.name) + ",x:" + pt.x + ",z:" + pt.z + ",y:" + pt.y + ",enabled:" + pt.enabled + ",beacon:" + pt.showBeacon + ",red:" + pt.red + ",green:" + pt.green + ",blue:" + pt.blue + ",suffix:" + pt.imageSuffix + ",world:" + TextUtils.scrubName(pt.world) + ",dimensions:" + dimensionsString);
                }
            }

            out.close();
        } catch (FileNotFoundException var12) {
            MessageUtils.chatInfo("§C" + I18n.get("minimap.waypoints.errorSavingWaypoints"));
            VoxelConstants.getLogger().error(var12);
        }

    }

    private void loadWaypoints() {
        this.loaded = false;
        this.multiworld = false;
        this.gotAutoSubworldName = false;
        this.currentDimension = null;
        this.setSubWorldDescriptor("");
        this.knownSubworldNames.clear();
        this.oldNorthWorldNames.clear();
        this.worldSeeds.clear();
        synchronized (this.waypointLock) {
            boolean loaded;
            this.wayPts = new ArrayList<>();
            String worldNameStandard = this.getCurrentWorldName();
            if (worldNameStandard.endsWith(":25565")) {
                int portSepLoc = worldNameStandard.lastIndexOf(':');
                if (portSepLoc != -1) {
                    worldNameStandard = worldNameStandard.substring(0, portSepLoc);
                }
            }

            worldNameStandard = TextUtils.scrubNameFile(worldNameStandard);
            loaded = this.loadWaypointsExtensible(worldNameStandard);
            if (!loaded) {
                MessageUtils.chatInfo("§E" + I18n.get("minimap.waypoints.noWaypointsExist"));
            }
        }

        this.loaded = true;
        if (this.needSave) {
            this.needSave = false;
            this.saveWaypoints();
        }

        this.multiworld = this.multiworld || !this.knownSubworldNames.isEmpty();
    }

    private boolean loadWaypointsExtensible(String worldNameStandard) {
        File settingsFileNew = new File(minecraft.gameDirectory, "/voxelmap/" + worldNameStandard + ".points");
        File settingsFileOld = new File(minecraft.gameDirectory, "/mods/mamiyaotaru/voxelmap/" + worldNameStandard + ".points");
        if (!settingsFileOld.exists() && !settingsFileNew.exists()) {
            return false;
        } else {
            if (!settingsFileOld.exists()) {
                this.settingsFile = settingsFileNew;
            } else if (!settingsFileNew.exists()) {
                this.settingsFile = settingsFileOld;
            } else {
                this.settingsFile = settingsFileNew;
            }

            if (this.settingsFile.exists()) {
                try {
                    Properties properties = new Properties();
                    FileReader fr = new FileReader(this.settingsFile);
                    properties.load(fr);
                    String subWorldsS = properties.getProperty("subworlds", "");
                    String[] subWorlds = subWorldsS.split(",");

                    for (String subWorld : subWorlds) {
                        if (!subWorld.isEmpty()) {
                            this.knownSubworldNames.add(TextUtils.descrubName(subWorld));
                        }
                    }

                    String oldNorthWorldsS = properties.getProperty("oldNorthWorlds", "");
                    String[] oldNorthWorlds = oldNorthWorldsS.split(",");

                    for (String oldNorthWorld : oldNorthWorlds) {
                        if (!oldNorthWorld.isEmpty()) {
                            this.oldNorthWorldNames.add(TextUtils.descrubName(oldNorthWorld));
                        }
                    }

                    String worldSeedsS = properties.getProperty("seeds", "");
                    String[] worldSeedPairs = worldSeedsS.split(",");

                    for (String pair : worldSeedPairs) {
                        String[] worldSeedPair = pair.split("#");
                        if (worldSeedPair.length == 2) {
                            this.worldSeeds.put(worldSeedPair[0], worldSeedPair[1]);
                        }
                    }

                    fr.close();
                } catch (IOException exception) {
                    VoxelConstants.getLogger().error(exception);
                }

                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(this.settingsFile), StandardCharsets.UTF_8));

                    String sCurrentLine;
                    while ((sCurrentLine = in.readLine()) != null) {
                        try {
                            String[] pairs = sCurrentLine.split(",");
                            if (pairs.length > 1) {
                                String name = "";
                                int x = 0;
                                int z = 0;
                                int y = -1;
                                boolean enabled = false;
                                boolean beacon = false;
                                float red = 0.5F;
                                float green = 0.0F;
                                float blue = 0.0F;
                                String suffix = "";
                                String world = "";
                                TreeSet<DimensionContainer> dimensions = new TreeSet<>();

                                for (String pair : pairs) {
                                    int splitIndex = pair.indexOf(':');
                                    if (splitIndex != -1) {
                                        String key = pair.substring(0, splitIndex).toLowerCase().trim();
                                        String value = pair.substring(splitIndex + 1).trim();
                                        switch (key) {
                                            case "name" -> name = TextUtils.descrubName(value);
                                            case "x" -> x = Integer.parseInt(value);
                                            case "z" -> z = Integer.parseInt(value);
                                            case "y" -> y = Integer.parseInt(value);
                                            case "enabled" -> enabled = Boolean.parseBoolean(value);
                                            case "beacon" -> beacon = Boolean.parseBoolean(value);
                                            case "red" -> red = Float.parseFloat(value);
                                            case "green" -> green = Float.parseFloat(value);
                                            case "blue" -> blue = Float.parseFloat(value);
                                            case "suffix" -> suffix = value;
                                            case "world" -> world = TextUtils.descrubName(value);
                                            case "dimensions" -> {
                                                String[] dimensionStrings = value.split("#");
                                                for (String dimensionString : dimensionStrings) {
                                                    String convertOldFormat = dimensionString.equals("1") ? "the_end" : dimensionString.equals("-1") ? "the_nether" : dimensionString.equals("0") ? "overworld" : dimensionString;
                                                    dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByIdentifier(convertOldFormat));
                                                }
                                                if (dimensions.isEmpty()) {
                                                    dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByIdentifier(BuiltinDimensionTypes.OVERWORLD.identifier()));
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!name.isEmpty()) {
                                    this.loadWaypoint(name, x, z, y, enabled, beacon, red, green, blue, suffix, world, dimensions);
                                    if (!world.isEmpty()) {
                                        this.knownSubworldNames.add(TextUtils.descrubName(world));
                                    }
                                }
                            }
                        } catch (Exception exception) {
                            VoxelConstants.getLogger().error(exception);
                        }
                    }

                    in.close();
                    return true;
                } catch (IOException var25) {
                    MessageUtils.chatInfo("§C" + I18n.get("minimap.waypoints.errorLoadingWaypoints"));
                    VoxelConstants.getLogger().error("waypoint load error: " + var25.getLocalizedMessage(), var25);
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    private void loadWaypoint(String name, int x, int z, int y, boolean enabled, boolean beacon, float red, float green, float blue, String suffix, String world, TreeSet<DimensionContainer> dimensions) {
        Waypoint newWaypoint = new Waypoint(name, x, z, y, enabled, red, green, blue, suffix, world, dimensions);
        newWaypoint.showBeacon = beacon;
        if (!this.wayPts.contains(newWaypoint)) {
            this.wayPts.add(newWaypoint);
        }

    }

    private ImportCounts importXaeroFile(File file) {
        int imported = 0;
        int skipped = 0;
        TreeSet<DimensionContainer> dimensions = dimensionsForXaeroPath(file.toPath());

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("waypoint:")) {
                    continue;
                }

                String[] fields = line.split(":", -1);
                if (fields.length < 9) {
                    ++skipped;
                    continue;
                }

                try {
                    String name = humanizeXaeroName(fields[1]);
                    int x = Integer.parseInt(fields[3]);
                    int y = Integer.parseInt(fields[4]);
                    int z = Integer.parseInt(fields[5]);
                    float[] color = xaeroColor(Integer.parseInt(fields[6]));
                    boolean enabled = !Boolean.parseBoolean(fields[7]);
                    String suffix = xaeroIcon(fields[1], fields[8]);
                    if (addImportedWaypoint(new Waypoint(name, x, z, y, enabled, color[0], color[1], color[2], suffix, getCurrentSubworldDescriptor(false), new TreeSet<>(dimensions)))) {
                        ++imported;
                    } else {
                        ++skipped;
                    }
                } catch (RuntimeException exception) {
                    ++skipped;
                    VoxelConstants.getLogger().warn("Skipping invalid Xaero waypoint in " + file.getPath() + ": " + line);
                }
            }
        } catch (IOException exception) {
            VoxelConstants.getLogger().error("Failed importing Xaero waypoints from " + file.getPath(), exception);
        }

        return new ImportCounts(imported, skipped);
    }

    private ImportCounts importWurstFile(File file) {
        int imported = 0;
        int skipped = 0;

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                return new ImportCounts(0, 0);
            }

            JsonArray waypointArray = rootElement.getAsJsonObject().getAsJsonArray("waypoints");
            if (waypointArray == null) {
                return new ImportCounts(0, 0);
            }

            for (JsonElement element : waypointArray) {
                if (!element.isJsonObject()) {
                    ++skipped;
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                JsonObject pos = object.getAsJsonObject("pos");
                if (pos == null) {
                    ++skipped;
                    continue;
                }

                try {
                    String name = getString(object, "name", "Wurst Waypoint");
                    String icon = wurstIcon(getString(object, "icon", ""));
                    int color = getInt(object, "color", -1);
                    float[] rgb = rgbFromPackedColor(color);
                    boolean enabled = getBoolean(object, "visible", true);
                    int x = getInt(pos, "x", 0);
                    int y = getInt(pos, "y", 0);
                    int z = getInt(pos, "z", 0);
                    TreeSet<DimensionContainer> dimensions = dimensionsForWurstName(getString(object, "dimension", ""));
                    if (addImportedWaypoint(new Waypoint(name, x, z, y, enabled, rgb[0], rgb[1], rgb[2], icon, getCurrentSubworldDescriptor(false), dimensions))) {
                        ++imported;
                    } else {
                        ++skipped;
                    }
                } catch (RuntimeException exception) {
                    ++skipped;
                    VoxelConstants.getLogger().warn("Skipping invalid Wurst waypoint in " + file.getPath());
                }
            }
        } catch (IOException | RuntimeException exception) {
            VoxelConstants.getLogger().error("Failed importing Wurst waypoints from " + file.getPath(), exception);
        }

        return new ImportCounts(imported, skipped);
    }

    private boolean addImportedWaypoint(Waypoint waypoint) {
        synchronized (waypointLock) {
            if (this.wayPts.contains(waypoint)) {
                return false;
            }

            waypoint.inWorld = waypoint.world.isEmpty() || TextUtils.scrubName(getCurrentSubworldDescriptor(false)).equals(waypoint.world);
            waypoint.inDimension = waypoint.dimensions.isEmpty() || waypoint.dimensions.contains(this.currentDimension);
            this.wayPts.add(waypoint);
            return true;
        }
    }

    private List<File> findMatchingWorldDirectories(File root, String prefix) {
        String serverKey = normalizedServerKey();
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return List.of();
        }

        List<File> matches = new ArrayList<>();
        for (File child : children) {
            String name = child.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                name = name.substring(prefix.length());
            }

            if (serverKey.equals(normalizeServerCandidate(name))) {
                matches.add(child);
            }
        }

        return matches;
    }

    private List<File> findMatchingFiles(File root, String extension) {
        String serverKey = normalizedServerKey();
        File[] children = root.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(extension));
        if (children == null) {
            return List.of();
        }

        List<File> matches = new ArrayList<>();
        for (File child : children) {
            String name = child.getName();
            name = name.substring(0, name.length() - extension.length());
            if (serverKey.equals(normalizeServerCandidate(name))) {
                matches.add(child);
            }
        }

        return matches;
    }

    private String normalizedServerKey() {
        return normalizeServerCandidate(getCurrentWorldName());
    }

    private String normalizeServerCandidate(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":25565")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        int portSeparator = normalized.lastIndexOf(':');
        if (portSeparator > -1 && normalized.indexOf(':') == portSeparator) {
            String possiblePort = normalized.substring(portSeparator + 1);
            if (possiblePort.chars().allMatch(Character::isDigit)) {
                normalized = normalized.substring(0, portSeparator);
            }
        }
        return normalized.replaceAll("[^a-z0-9.\\-]", "");
    }

    private TreeSet<DimensionContainer> dimensionsForXaeroPath(Path path) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("dim%-1") || normalized.contains("minecraft$the_nether") || normalized.contains("minecraft$nether")) {
            return singleDimension(Level.NETHER.identifier());
        }
        if (normalized.contains("dim%1") || normalized.contains("minecraft$the_end") || normalized.contains("minecraft$end")) {
            return singleDimension(Level.END.identifier());
        }
        return singleDimension(Level.OVERWORLD.identifier());
    }

    private TreeSet<DimensionContainer> dimensionsForWurstName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.contains("nether")) {
            return singleDimension(Level.NETHER.identifier());
        }
        if (normalized.contains("end")) {
            return singleDimension(Level.END.identifier());
        }
        return singleDimension(Level.OVERWORLD.identifier());
    }

    private TreeSet<DimensionContainer> singleDimension(Identifier identifier) {
        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByIdentifier(identifier));
        return dimensions;
    }

    private String humanizeXaeroName(String rawName) {
        String name = rawName == null || rawName.isBlank() ? "Xaero Waypoint" : rawName;
        if (name.startsWith("gui.xaero_")) {
            name = name.substring("gui.xaero_".length());
        }
        name = name.replace('_', ' ').trim();
        if (name.isEmpty()) {
            return "Xaero Waypoint";
        }

        StringBuilder builder = new StringBuilder();
        for (String part : name.split(" ")) {
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private String xaeroIcon(String name, String type) {
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lowerName.contains("death") || "1".equals(type) || "2".equals(type)) {
            return "skull";
        }
        return "point";
    }

    private String wurstIcon(String icon) {
        return switch ((icon == null ? "" : icon).toLowerCase(Locale.ROOT)) {
            case "diamond" -> "diamond";
            case "star" -> "star";
            case "home", "house" -> "house";
            case "skull", "death" -> "skull";
            case "pickaxe" -> "pickaxe";
            case "portal", "fire" -> "fire";
            default -> "point";
        };
    }

    private float[] xaeroColor(int index) {
        int[] colors = {
                0xFF5555, 0x55FF55, 0x5555FF, 0xFFFF55,
                0xFF55FF, 0x55FFFF, 0xFFFFFF, 0xAAAAAA,
                0xAA0000, 0x00AA00, 0x0000AA, 0xFFAA00,
                0xAA00AA, 0x00AAAA, 0x000000, 0x555555
        };
        return rgbFromPackedColor(colors[Math.floorMod(index, colors.length)]);
    }

    private float[] rgbFromPackedColor(int color) {
        int rgb = color & 0xFFFFFF;
        return new float[] {
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F
        };
    }

    private String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private int getInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    private boolean getBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }

    public void deleteWaypoint(Waypoint point) {
        this.wayPts.remove(point);
        this.saveWaypoints();
        if (point == this.highlightedWaypoint) {
            this.setHighlightedWaypoint(null, false);
        }

        this.waypointContainer.refreshRenderables();
    }

    public void deleteWaypoints(List<Waypoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }

        this.wayPts.removeAll(points);
        if (points.contains(this.highlightedWaypoint)) {
            this.highlightedWaypoint = null;
        }

        this.saveWaypoints();
        this.waypointContainer.refreshRenderables();
    }

    public void addWaypoint(Waypoint newWaypoint) {
        this.wayPts.add(newWaypoint);
        this.saveWaypoints();
        if (this.highlightedWaypoint != null && this.highlightedWaypoint.getX() == newWaypoint.getX() && this.highlightedWaypoint.getZ() == newWaypoint.getZ()) {
            this.setHighlightedWaypoint(newWaypoint, false);
        }

        this.waypointContainer.refreshRenderables();
    }

    public boolean addAutoPortalWaypoint(PortalMarkersManager.PortalType type, BlockPos pos) {
        if (type == null || pos == null) {
            return false;
        }

        if (!this.options.waypointsAllowed || !this.options.autoPortalWaypoints) {
            return false;
        }

        synchronized (this.waypointLock) {
            String prefix = type == PortalMarkersManager.PortalType.END ? "End Portal" : "Nether Portal";
            if (this.hasPortalWaypointNearby(prefix, pos, 3) || this.hasWaypointAt(pos)) {
                return false;
            }

            String icon = type == PortalMarkersManager.PortalType.END ? "diamond" : "fire";
            int nextNumber = this.nextPortalWaypointIndex(prefix);
            String name = prefix + " " + nextNumber;
            TreeSet<DimensionContainer> dimensions = new TreeSet<>();
            if (this.currentDimension != null) {
                dimensions.add(this.currentDimension);
            }

            Waypoint waypoint = new Waypoint(name, pos.getX(), pos.getZ(), pos.getY(), true, 0.72F, 0.24F, 1.0F, icon, this.getCurrentSubworldDescriptor(false), dimensions);
            waypoint.showBeacon = false;
            this.wayPts.add(waypoint);
            return true;
        }
    }

    private boolean hasWaypointAt(BlockPos pos) {
        for (Waypoint waypoint : this.wayPts) {
            if (waypoint.x == pos.getX() && waypoint.y == pos.getY() && waypoint.z == pos.getZ()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPortalWaypointNearby(String prefix, BlockPos pos, int distance) {
        int maxSq = distance * distance;
        for (Waypoint waypoint : this.wayPts) {
            if (!waypoint.name.startsWith(prefix)) {
                continue;
            }
            int dx = waypoint.x - pos.getX();
            int dz = waypoint.z - pos.getZ();
            if (dx * dx + dz * dz <= maxSq) {
                return true;
            }
        }
        return false;
    }

    private int nextPortalWaypointIndex(String prefix) {
        int highest = 0;
        String base = prefix + " ";
        for (Waypoint waypoint : this.wayPts) {
            String name = waypoint.name;
            if (name.equals(prefix)) {
                highest = Math.max(highest, 1);
                continue;
            }
            if (!name.startsWith(base)) {
                continue;
            }
            String tail = name.substring(base.length()).trim();
            try {
                highest = Math.max(highest, Integer.parseInt(tail));
            } catch (NumberFormatException ignored) {
            }
        }
        return highest + 1;
    }

    public void setHighlightedWaypoint(Waypoint waypoint, boolean toggle) {
        if (toggle && waypoint == this.highlightedWaypoint) {
            this.highlightedWaypoint = null;
        } else {
            if (waypoint != null && !this.wayPts.contains(waypoint)) {
                waypoint.name = coordinateHighlightName;
                waypoint.red = 1.0F;
                waypoint.blue = 0.0F;
                waypoint.green = 0.0F;
            }

            this.highlightedWaypoint = waypoint;
        }

        this.waypointContainer.refreshRenderables();
    }

    public Waypoint getHighlightedWaypoint() {
        return this.highlightedWaypoint;
    }

    public boolean isHighlightedWaypoint(Waypoint waypoint) {
        return waypoint == highlightedWaypoint;
    }

    public boolean isWaypointHighlight(Waypoint waypoint) {
        if (isHighlightedWaypoint(waypoint)) {
            return wayPts.contains(waypoint);
        }

        return false;
    }

    public boolean isCoordinateHighlight(Waypoint waypoint) {
        if (isHighlightedWaypoint(waypoint)) {
            return waypoint.name.equals(coordinateHighlightName);
        }

        return false;
    }

    public void renderWaypoints(float gameTimeDeltaPartialTick, PoseStack poseStack, BufferSource bufferSource, Camera camera) {
        if (this.waypointContainer == null) {
            return;
        }

        if (!options.waypointsAllowed && !options.highlightTracerEnabled) {
            return;
        }

        this.waypointContainer.renderWaypoints(gameTimeDeltaPartialTick, poseStack, bufferSource, camera);
    }

    private void loadBackgroundMapImage() {
        if (this.backgroundImageInfo != null) {
            this.backgroundImageInfo.unregister();
            this.backgroundImageInfo = null;
        }

        try {
            String path = this.getCurrentWorldName();
            String subworldDescriptor = this.getCurrentSubworldDescriptor(false);
            if (subworldDescriptor != null && !subworldDescriptor.isEmpty()) {
                path = path + "/" + subworldDescriptor;
            }
            path = path + "/" + this.currentDimension.getStorageName();
            String tempPath = "images/backgroundmaps/" + path + "/map.png";
            Identifier identifier = Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, tempPath);

            Minecraft.getInstance().getResourceManager().getResourceOrThrow(identifier); // check if it exists

            InputStream is = VoxelConstants.getMinecraft().getResourceManager().getResource(identifier).get().open();
            Image image = ImageIO.read(is);
            is.close();
            BufferedImage mapImage = new BufferedImage(image.getWidth(null), image.getHeight(null), 2);
            Graphics gfx = mapImage.createGraphics();
            gfx.drawImage(image, 0, 0, null);
            gfx.dispose();
            is = VoxelConstants.getMinecraft().getResourceManager().getResource(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "images/backgroundmaps/" + path + "/map.txt")).get().open();
            InputStreamReader isr = new InputStreamReader(is);
            Properties mapProperties = new Properties();
            mapProperties.load(isr);
            String left = mapProperties.getProperty("left");
            String right = mapProperties.getProperty("right");
            String top = mapProperties.getProperty("top");
            String bottom = mapProperties.getProperty("bottom");
            String width = mapProperties.getProperty("width");
            String height = mapProperties.getProperty("height");
            String scale = mapProperties.getProperty("scale");
            if (left != null && top != null && width != null && height != null) {
                this.backgroundImageInfo = new BackgroundImageInfo(identifier, mapImage, Integer.parseInt(left), Integer.parseInt(top), Integer.parseInt(width), Integer.parseInt(height));
            } else if (left != null && top != null && scale != null) {
                this.backgroundImageInfo = new BackgroundImageInfo(identifier, mapImage, Integer.parseInt(left), Integer.parseInt(top), Float.parseFloat(scale));
            } else if (left != null && top != null && right != null && bottom != null) {
                int widthInt = Integer.parseInt(right) - Integer.parseInt(left);
                this.backgroundImageInfo = new BackgroundImageInfo(identifier, mapImage, Integer.parseInt(left), Integer.parseInt(top), widthInt, widthInt);
            }

            isr.close();
        } catch (Exception ignore) {
        }
    }

    public BackgroundImageInfo getBackgroundImageInfo() {
        return this.backgroundImageInfo;
    }

    private record ImportCounts(int imported, int skipped) {
    }

    public record ImportResult(int imported, int skipped, String message) {
    }
}
