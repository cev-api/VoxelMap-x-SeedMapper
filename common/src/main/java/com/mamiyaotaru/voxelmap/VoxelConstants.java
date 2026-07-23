package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.persistent.ThreadManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperEspRenderer;
import com.mamiyaotaru.voxelmap.util.BiomeRepository;
import com.mamiyaotaru.voxelmap.util.CommandUtils;
import com.mamiyaotaru.voxelmap.util.MessageUtils;
import com.mamiyaotaru.voxelmap.util.ModrinthUpdateChecker;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class VoxelConstants {
    private static final Logger LOGGER = LogManager.getLogger("VoxelMap");
    private static final VoxelMap VOXELMAP_INSTANCE = new VoxelMap();
    public static final String MOD_ID = "voxelmap";
    public static final boolean DEBUG = false;

    private static final Identifier OPTIONS_BACKGROUND_TEXTURE = Identifier.parse("textures/block/dirt.png");
    private static final Identifier CHECK_MARKER_TEXTURE = Identifier.parse("textures/gui/sprites/container/beacon/confirm.png");
    private static final Identifier CROSS_MARKER_TEXTURE = Identifier.parse("textures/gui/sprites/container/beacon/cancel.png");

    private static String modVersion = null;
    private static int elapsedTicks;
    private static Events events;
    private static PacketBridge packetBridge;
    private static ModApiBridge modApiBridge;

    private VoxelConstants() {}

    @NotNull
    public static Minecraft getMinecraft() { return Minecraft.getInstance(); }

    public static boolean isSinglePlayer() { return getMinecraft().isLocalServer(); }

    public static boolean isRealmServer() {
        ClientPacketListener playNetworkHandler = getMinecraft().getConnection();
        ServerData serverInfo = playNetworkHandler != null ? getMinecraft().getConnection().getServerData() : null;
        return serverInfo != null && serverInfo.isRealm();
    }

    public static boolean hasVulkanMod() { return modApiBridge != null && modApiBridge.isModEnabled("vulkanmod"); }

    public static boolean isVulkanRenderer() {
        if (hasVulkanMod()) {
            return true;
        }

        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return false;
        }

        String backendName = device.getDeviceInfo().backendName();
        return backendName != null && backendName.toLowerCase(Locale.ROOT).contains("vulkan");
    }

    @NotNull
    public static Logger getLogger() { return LOGGER; }

    @NotNull
    public static Optional<IntegratedServer> getIntegratedServer() { return Optional.ofNullable(getMinecraft().getSingleplayerServer()); }

    @NotNull
    public static Optional<Level> getWorldByKey(ResourceKey<Level> key) { return getIntegratedServer().map(integratedServer -> integratedServer.getLevel(key)); }

    @NotNull
    public static ClientLevel getClientWorld() { return (ClientLevel) getPlayer().level(); }

    @NotNull
    public static LocalPlayer getPlayer() {
        LocalPlayer player = getMinecraft().player;

        if (player == null) {
            String error = "Attempted to fetch player entity while not in-game!";

            getLogger().fatal(error);
            throw new IllegalStateException(error);
        }

        return player;
    }

    @NotNull
    public static VoxelMap getVoxelMapInstance() { return VOXELMAP_INSTANCE; }

    static void tick() { elapsedTicks = elapsedTicks == Integer.MAX_VALUE ? 1 : elapsedTicks + 1; }

    public static int getElapsedTicks() { return elapsedTicks; }

    static { elapsedTicks = 0; }

    public static Identifier getOptionsBackgroundTexture() {
        return OPTIONS_BACKGROUND_TEXTURE;
    }

    public static Identifier getCheckMarkerTexture() {
        return CHECK_MARKER_TEXTURE;
    }

    public static Identifier getCrossMarkerTexture() {
        return CROSS_MARKER_TEXTURE;
    }

    public static void clientTick() {
        VoxelConstants.getVoxelMapInstance().onTick();

    }

    public static void renderOverlay(GuiGraphicsExtractor graphics) {
        try {
            VoxelConstants.getVoxelMapInstance().onTickInGame(graphics);
        } catch (RuntimeException e) {
            VoxelConstants.getLogger().log(org.apache.logging.log4j.Level.ERROR, "Error while render overlay", e);
        }
    }

    public static Component getModifiedChatMessage(Component chat) {
        Component resolvedShare = com.mamiyaotaru.voxelmap.chunksync.ChunkShareChat.maybeResolveIncoming(chat);
        if (resolvedShare != null) {
            return resolvedShare;
        }
        return CommandUtils.checkForWaypoints(chat);
    }

    public static boolean onSendChatMessage(String message) {
        if (handleUpdateCheckerCommand(message)) {
            return false;
        }
        if (SeedMapperCommandHandler.handleChatCommand(message)) {
            return false;
        }
        if (message.startsWith("newWaypoint")) {
            CommandUtils.waypointClicked(message);
            return false;
        } else if (message.startsWith("ztp")) {
            CommandUtils.teleport(message);
            return false;
        } else {
            return true;
        }
    }

    public static void onRenderWaypoints(float gameTimeDeltaPartialTick, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Camera camera) {
        try {
            VoxelConstants.getVoxelMapInstance().getWaypointManager().renderWaypoints(gameTimeDeltaPartialTick, poseStack, submitNodeCollector, camera);
            SeedMapperEspRenderer.render(gameTimeDeltaPartialTick, poseStack, submitNodeCollector, camera);
        } catch (RuntimeException e) {
            VoxelConstants.getLogger().log(org.apache.logging.log4j.Level.ERROR, "Error while render waypoints", e);
        }
    }

    public static void onShutDown() {
        VoxelConstants.getLogger().info("Saving all world maps");
        VoxelConstants.getVoxelMapInstance().getPersistentMap().purgeCachedRegions();
        VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().flushStorage();
        VoxelConstants.getVoxelMapInstance().getExploredChunksManager().flushStorage();
        VoxelConstants.getVoxelMapInstance().getMapOptions().saveAll();
        BiomeRepository.saveBiomeColors();
        long shutdownStart = System.currentTimeMillis();
        final long maxWaitMs = 500L;
        while (ThreadManager.executorService.getQueue().size() + ThreadManager.executorService.getActiveCount() > 0
                && System.currentTimeMillis() - shutdownStart < maxWaitMs) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VoxelConstants.getLogger().warn("Interrupted while waiting for world map tasks during shutdown; continuing.");
                break;
            }
        }
        int remaining = ThreadManager.executorService.getQueue().size() + ThreadManager.executorService.getActiveCount();
        if (remaining > 0) {
            VoxelConstants.getLogger().warn("Continuing shutdown with {} world map task(s) still pending.", remaining);
        }
    }

    public static void playerRunTeleportCommand(double x, double y, double z) {
        playerRunTransportCommand(0, x, y, z);
    }

    public static void playerRunTransportCommand(int shortcutIndex, double x, double y, double z) {
        MapSettingsManager mapSettingsManager = VoxelConstants.getVoxelMapInstance().getMapOptions();
        if (shortcutIndex < 0 || shortcutIndex >= mapSettingsManager.transportShortcuts.size()) return;
        MapSettingsManager.TransportShortcut shortcut = mapSettingsManager.transportShortcuts.get(shortcutIndex);
        String cmd = shortcut.command == null ? "" : shortcut.command.trim();
        if (shortcutIndex == 0 && mapSettingsManager.serverTeleportCommand != null) {
            cmd = mapSettingsManager.serverTeleportCommand.trim();
        }
        cmd = cmd.replace("%p", VoxelConstants.getPlayer().getName().getString())
                .replace("%x", formatTransportCoordinate(x))
                .replace("%z", formatTransportCoordinate(z));
        if (mapSettingsManager.transportExcludeY) {
            cmd = cmd.replace("%y", "");
        } else {
            cmd = cmd.replace("%y", formatTransportCoordinate(y));
        }
        if (!cmd.isBlank()) {
            // Dot-prefixed commands are handled by the client command/chat
            // integration (the same path used when the player types them).
            // Sending them with sendCommand makes the server interpret the
            // leading dot as part of an unknown server command.
            if (shortcut.clientCommand || cmd.startsWith(".") || cmd.startsWith("!")) {
                // Run through ChatScreen's input handler so client mods (for
                // example Wurst) receive the exact same interception path as
                // manually typed commands. The temporary screen is never
                // installed, so the current map screen remains open.
                new ChatScreen("", false).handleChatInput(cmd, false);
            } else {
                if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
                VoxelConstants.getPlayer().connection.sendCommand(cmd);
            }
        }
    }

    private static String formatTransportCoordinate(double coordinate) {
        return String.valueOf((int) Math.floor(coordinate));
    }

    public static int moveScoreboard(int bottomX, int entriesHeight) {
        MapSettingsManager mapSettingsManager = VoxelConstants.getVoxelMapInstance().getMapOptions();
        double unscaledHeight = Map.getMinTablistOffset(); // / scaleFactor;
        if (mapSettingsManager.hide || !mapSettingsManager.minimapAllowed || mapSettingsManager.mapCorner != 1 || !mapSettingsManager.moveScoreboardBelowMap || !Double.isFinite(unscaledHeight)) {
            return bottomX;
        }
        double scaleFactor = Minecraft.getInstance().getWindow().getGuiScale(); // 1x 2x 3x, ...
        double mapHeightScaled = unscaledHeight * 1.37 / scaleFactor; // * 1.37 because unscaledHeight is just the map without the text around it

        int fontHeight = Minecraft.getInstance().font.lineHeight; // height of the title line
        float statusIconOffset = Map.getStatusIconOffset();
        int statusIconOffsetInt = Float.isFinite(statusIconOffset) ? (int) statusIconOffset : 0;
        int minBottom = (int) (mapHeightScaled + entriesHeight + fontHeight + statusIconOffsetInt);

        return Math.max(bottomX, minBottom);
    }

    public static void setEvents(Events events) {
        VoxelConstants.events = events;
        VoxelConstants.getVoxelMapInstance().onEventsSet(events);
    }

    public static Events getEvents() {
        return events;
    }

    public static void setPacketBridge(PacketBridge packetBridge) {
        VoxelConstants.packetBridge = packetBridge;
    }

    public static PacketBridge getPacketBridge() {
        return packetBridge;
    }

    public static void setModApiBride(ModApiBridge modApiBridge) {
        VoxelConstants.modApiBridge = modApiBridge;
    }

    public static ModApiBridge getModApiBridge() {
        return modApiBridge;
    }

    public static void setModVersion(String modVersion) {
        VoxelConstants.modVersion = modVersion;
    }

    public static String getModVersion() {
        return modVersion;
    }
    private static boolean handleUpdateCheckerCommand(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        String[] args = normalized.split("\\s+");
        if (args.length < 2) {
            return false;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (!(root.equals("voxelmap") || root.equals("vmap")) || !sub.equals("updatechecker")) {
            return false;
        }

        MapSettingsManager options = VoxelConstants.getVoxelMapInstance().getMapOptions();
        if (args.length == 2 || "status".equalsIgnoreCase(args[2])) {
            MessageUtils.chatInfo("Update checker is currently " + (options.updateNotifier ? "ON" : "OFF") + ".");
            MessageUtils.chatInfo("Usage: /voxelmap updatechecker <on|off|toggle|status|check>");
            return true;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "on" -> options.updateNotifier = true;
            case "off" -> options.updateNotifier = false;
            case "toggle" -> options.updateNotifier = !options.updateNotifier;
            case "check" -> {
                String projectId = ModrinthUpdateChecker.getBuildProperty("modrinthId", "cVrDroCh");
                String version = ModrinthUpdateChecker.getBuildProperty("forkVersion", VoxelConstants.getModVersion());
                if (version == null || version.isBlank()) {
                    MessageUtils.chatInfo("Update check failed: local version is unknown.");
                    return true;
                }
                String mcVersion = net.minecraft.SharedConstants.getCurrentVersion().name();
                MessageUtils.chatInfo("Checking Modrinth for updates...");
                new ModrinthUpdateChecker(projectId, VoxelConstants.getModApiBridge().getModLoader(), mcVersion)
                        .checkUpdates(version, result -> {
                            if (result == null || result.latestVersion() == null) {
                                MessageUtils.chatInfo("Update check failed: no compatible versions found.");
                                return;
                            }
                            int cmp = ModrinthUpdateChecker.compareVersions(ModrinthUpdateChecker.getRawVersion(version), result.latestVersion());
                            if (cmp >= 0) {
                                MessageUtils.chatInfo("No update available. Current version: " + version);
                            } else {
                                MessageUtils.chatInfo("Update available: " + result.latestVersion());
                            }
                        });
                return true;
            }
            default -> {
                MessageUtils.chatInfo("Unknown value '" + args[2] + "'. Use on, off, toggle, status, or check.");
                return true;
            }
        }

        options.saveAll();
        MessageUtils.chatInfo("Update checker is now " + (options.updateNotifier ? "ON" : "OFF") + ".");
        return true;
    }
}
