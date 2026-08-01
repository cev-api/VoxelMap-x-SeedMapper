package com.mamiyaotaru.voxelmap.gui.settings;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.chunksync.ChunkShareConfig;
import com.mamiyaotaru.voxelmap.chunksync.ChunkShareTransport;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommandHandler;
import com.mamiyaotaru.voxelmap.gui.GuiChunkSyncLayers;
import com.mamiyaotaru.voxelmap.gui.GuiColorEditScreen;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperCrackingMods;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperDatapackOptions;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperEspProfiles;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperLocator;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperLootViewer;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperMarkerOptions;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperSavedSeeds;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.interfaces.ISettingsManager;
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;
import com.mamiyaotaru.voxelmap.persistent.GuiTransportShortcutsOptions;
import com.mamiyaotaru.voxelmap.persistent.PersistentMapSettingsManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandTree;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCompat;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperDatapackManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperEspTarget;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperFeature;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperMarkerOption;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import com.mamiyaotaru.voxelmap.util.MessageUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VoxelMapSettings {
    private VoxelMapSettings() {
    }

    public static List<SettingsCategory> create(Runnable openEntityTypeDialog, Supplier<Screen> host) {
        VoxelMap voxelMap = VoxelConstants.getVoxelMapInstance();
        MapSettingsManager map = voxelMap.getMapOptions();
        RadarSettingsManager radar = voxelMap.getRadarOptions();
        PersistentMapSettingsManager world = voxelMap.getPersistentMapOptions();
        SeedMapperSettingsManager seed = voxelMap.getSeedMapperOptions();

        return List.of(
                general(map),
                minimap(voxelMap, map, seed, host),
                worldMap(map, world, host),
                waypoints(map),
                radar(radar, map, seed, openEntityTypeDialog, host),
                chunks(map, radar, world, host),
                sharing(host),
                seedmapper(seed, host),
                new SettingsCategory("controls", "options.voxelmap.category.controls", List.of(), SettingsCategory.SpecialView.KEY_BINDINGS),
                advanced(voxelMap, map, radar));
    }

    private static SettingsGroup minimapExtras(VoxelMap voxelMap, MapSettingsManager map, SeedMapperSettingsManager seed) {
        return group("options.voxelmap.group.hudScale",
                SettingsOption.slider("minimap.zoom", "options.voxelmap.minimap.zoom", null,
                        () -> (double) map.zoom, value -> voxelMap.getMap().setZoomLevel((int) Math.round(value)),
                        0, 4, 1, value -> Component.literal(formatMinimapZoomFactor((int) Math.round(value))),
                        () -> map.minimapAllowed, serverDisabled(), 0),
                SettingsOption.choice("minimap.facingDegrees", "options.minimap.showFacingDegrees", null,
                        () -> !map.showFacingDegrees ? 0 : map.showFacingCardinal ? 2 : 1,
                        value -> changed(map, ignored -> {
                            map.showFacingDegrees = value != 0;
                            map.showFacingCardinal = value == 2;
                        }, value),
                        List.of(value(0, "options.off"), value(1, "options.minimap.showFacingDegrees.degreesOnly"), value(2, "options.minimap.showFacingDegrees.degreesAndCardinal"))),
                slider("minimap.radarTextScale", "options.voxelmap.minimap.radarTextScale", map,
                        () -> (double) map.radarTextScale, value -> map.radarTextScale = value.floatValue(), 0.5, 2.0, 0.01,
                        value -> Component.literal(String.format(Locale.ROOT, "%.2fx", value)), () -> true, Component::empty, 0),
                SettingsOption.toggle("minimap.borderMarkers", "options.voxelmap.minimap.borderMarkers", null,
                        () -> seed.showDistant, value -> changed(map, ignored -> seed.showDistant = value, value),
                        () -> map.minimapAllowed, serverDisabled(), 0),
                SettingsOption.slider("minimap.borderRange", "options.voxelmap.minimap.borderRange", null,
                        () -> (double) seed.minimapDistantMarkerRange,
                        value -> changed(map, ignored -> seed.minimapDistantMarkerRange = Math.max(128, ((int) Math.round(value / 128.0D)) * 128), value),
                        128, 32768, 128, value -> Component.literal((((int) Math.round(value / 128.0D)) * 128) + " blocks"),
                        () -> map.minimapAllowed && seed.showDistant, requires("options.voxelmap.requires.borderMarkers"), 1));
    }

    private static SettingsGroup highlights(MapSettingsManager map, Supplier<Screen> host) {
        return group("options.voxelmap.group.highlights",
                toggle("highlights.autoRemove", "options.voxelmap.highlights.autoRemove", map,
                        () -> map.autoHideHighlightsWhenNear, value -> map.autoHideHighlightsWhenNear = value),
                slider("highlights.removeRadius", "options.voxelmap.highlights.removeRadius", map,
                        () -> (double) map.autoHideHighlightsNearDistance, value -> map.autoHideHighlightsNearDistance = value.floatValue(),
                        1, 64, 1, value -> Component.literal(value.intValue() + " blocks"),
                        () -> map.autoHideHighlightsWhenNear, requires("options.voxelmap.requires.autoRemove"), 1),
                toggle("tracer.enabled", "options.voxelmap.tracer.enabled", map,
                        () -> map.highlightTracerEnabled, value -> map.highlightTracerEnabled = value),
                slider("tracer.thickness", "options.voxelmap.tracer.thickness", map,
                        () -> (double) map.highlightTracerThickness, value -> map.highlightTracerThickness = value.floatValue(),
                        1, 6, 0.1, value -> Component.literal(String.format(Locale.ROOT, "%.1f", value)),
                        () -> map.highlightTracerEnabled, requires("options.voxelmap.requires.tracer"), 1),
                colorText("tracer.color", "options.voxelmap.tracer.color", map, () -> map.highlightTracerColor,
                        value -> map.highlightTracerColor = value, () -> map.highlightTracerEnabled, requires("options.voxelmap.requires.tracer")),
                colorPick("tracer.pick", "options.voxelmap.tracer.pick", host, "options.voxelmap.tracer.color",
                        () -> map.highlightTracerColor, value -> {
                            map.highlightTracerColor = value;
                            map.markChanged();
                        }, () -> map.highlightTracerEnabled, requires("options.voxelmap.requires.tracer")));
    }

    private static SettingsOption<String> colorText(String id, String nameKey, MapSettingsManager saveVia, Supplier<String> getter, Consumer<String> setter,
            java.util.function.BooleanSupplier enabled, Supplier<Component> reason) {
        return SettingsOption.text(id, nameKey, null, getter,
                value -> {
                    if (value.matches("#?[0-9a-fA-F]{6}")) {
                        changed(saveVia, ignored -> setter.accept(value.startsWith("#") ? value : "#" + value), value);
                    }
                },
                enabled, reason);
    }

    private static SettingsOption<Void> colorPick(String id, String nameKey, Supplier<Screen> host, String titleKey, Supplier<String> getter, Consumer<String> setter,
            java.util.function.BooleanSupplier enabled, Supplier<Component> reason) {
        return SettingsOption.action(id, nameKey, null, Component.translatable("options.voxelmap.action.open"),
                () -> VoxelConstants.getMinecraft().gui.setScreen(new GuiColorEditScreen(host.get(), Component.translatable(titleKey), getter, setter)),
                enabled, reason);
    }

    private static SettingsGroup portalMarkers(MapSettingsManager map) {
        return group("options.voxelmap.group.portalMarkers",
                toggle("markers.netherPortals", "options.voxelmap.markers.netherPortals", map,
                        () -> map.showNetherPortalMarkers, value -> map.showNetherPortalMarkers = value,
                        () -> map.minimapAllowed, serverDisabled(), 0),
                toggle("markers.endPortals", "options.voxelmap.markers.endPortals", map,
                        () -> map.showEndPortalMarkers, value -> map.showEndPortalMarkers = value,
                        () -> map.minimapAllowed, serverDisabled(), 0),
                toggle("markers.endGateways", "options.voxelmap.markers.endGateways", map,
                        () -> map.showEndGatewayMarkers, value -> map.showEndGatewayMarkers = value,
                        () -> map.minimapAllowed, serverDisabled(), 0));
    }

    private static SettingsGroup detections(MapSettingsManager map, SeedMapperSettingsManager seed, Supplier<Screen> host) {
        return group("options.voxelmap.group.detections",
                seedToggle("detect.elytra", "options.voxelmap.detect.elytra", map, () -> seed.elytraDetection, value -> seed.elytraDetection = value),
                seedToggle("detect.containers", "options.voxelmap.detect.containers", map, () -> seed.containerDetection, value -> seed.containerDetection = value),
                markerOptionsAction("detect.containersOptions", host, SeedMapperMarkerOption.Category.CONTAINERS),
                seedToggle("detect.workstations", "options.voxelmap.detect.workstations", map, () -> seed.workstationDetection, value -> seed.workstationDetection = value),
                markerOptionsAction("detect.workstationsOptions", host, SeedMapperMarkerOption.Category.WORKSTATIONS),
                seedToggle("detect.redstone", "options.voxelmap.detect.redstone", map, () -> seed.redstoneDetection, value -> seed.redstoneDetection = value),
                markerOptionsAction("detect.redstoneOptions", host, SeedMapperMarkerOption.Category.REDSTONE),
                seedToggle("detect.spawners", "options.voxelmap.detect.spawners", map, () -> seed.spawnerDetection, value -> seed.spawnerDetection = value),
                markerOptionsAction("detect.spawnersOptions", host, SeedMapperMarkerOption.Category.SPAWNERS));
    }

    private static SettingsGroup markerDisplay(MapSettingsManager map, SeedMapperSettingsManager seed) {
        return group("options.voxelmap.group.markerDisplay",
                seedToggle("markers.clustering", "options.voxelmap.markers.clustering", map, () -> seed.markerClustering, value -> seed.markerClustering = value),
                seedToggle("markers.persistence", "options.voxelmap.markers.persistence", map, () -> seed.markerPersistence, value -> seed.markerPersistence = value),
                SettingsOption.slider("entities.mapScale", "options.voxelmap.entities.mapScale", null,
                        () -> Math.min(seed.mapEntityScale, 2.0D), value -> changed(map, ignored -> seed.mapEntityScale = value, value),
                        0.5, 2.0, 0.01, value -> Component.literal(String.format(Locale.ROOT, "%.2fx", value)),
                        () -> map.minimapAllowed, serverDisabled(), 0),
                SettingsOption.slider("entities.minimapScale", "options.voxelmap.entities.minimapScale", null,
                        () -> Math.min(seed.minimapEntityScale, 1.2D), value -> changed(map, ignored -> seed.minimapEntityScale = value, value),
                        0.5, 1.2, 0.01, value -> Component.literal(String.format(Locale.ROOT, "%.2fx", value)),
                        () -> map.minimapAllowed, serverDisabled(), 0),
                SettingsOption.slider("entities.worldMapLimit", "options.voxelmap.entities.worldMapLimit", null,
                        () -> seed.worldMapEntityLimit == 0 ? 10000.0D : (double) seed.worldMapEntityLimit,
                        value -> changed(map, ignored -> {
                            int limit = ((int) Math.round(value / 100.0D)) * 100;
                            seed.worldMapEntityLimit = limit >= 10000 ? 0 : Math.max(200, limit);
                        }, value),
                        200, 10000, 100,
                        value -> value >= 9950.0D ? Component.translatable("options.voxelmap.entities.worldMapLimit.none") : Component.literal(String.valueOf((int) Math.round(value / 100.0D) * 100)),
                        () -> map.minimapAllowed, serverDisabled(), 0));
    }

    private static SettingsOption<Boolean> seedToggle(String id, String key, MapSettingsManager map, java.util.function.Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return SettingsOption.toggle(id, key, null, getter, value -> changed(map, setter, value), () -> map.minimapAllowed, serverDisabled(), 0);
    }

    private static SettingsOption<Void> markerOptionsAction(String id, Supplier<Screen> host, SeedMapperMarkerOption.Category category) {
        return SettingsOption.action(id, "options.voxelmap." + id, null, Component.translatable("options.voxelmap.action.open"),
                () -> VoxelConstants.getMinecraft().gui.setScreen(new GuiSeedMapperMarkerOptions(host.get(), category)),
                () -> VoxelConstants.getVoxelMapInstance().getMapOptions().minimapAllowed, serverDisabled());
    }

    private static String formatMinimapZoomFactor(int internalZoom) {
        int clamped = Math.max(0, Math.min(4, internalZoom));
        double factor = Math.pow(2.0, 2.0 - clamped);
        if (factor >= 1.0) {
            return ((int) factor) + "x";
        }
        return String.format(Locale.ROOT, "%.2fx", factor);
    }

    private static SettingsCategory general(MapSettingsManager map) {
        return new SettingsCategory("general", "options.voxelmap.category.general", List.of(
                group("options.voxelmap.group.overlays",
                        toggle("minimap.chunkGrid", "options.minimap.chunkGrid", map, () -> map.chunkGrid, value -> map.chunkGrid = value),
                        toggle("minimap.slimeChunks", "options.minimap.slimeChunks", map, () -> map.slimeChunks, value -> map.slimeChunks = value,
                                () -> VoxelConstants.getMinecraft().hasSingleplayerServer() || !VoxelConstants.getVoxelMapInstance().getWorldSeed().isEmpty(),
                                requires("options.voxelmap.requires.seed"), 0),
                        toggle("minimap.worldBorder", "options.minimap.worldBorder", map, () -> map.worldBorder, value -> map.worldBorder = value),
                        choice("advanced.biomeOverlay", "options.voxelmap.advanced.biomeOverlay", map, () -> map.biomeOverlay, value -> map.biomeOverlay = value,
                                value(0, "options.off"), value(2, "options.minimap.biomeOverlay.transparent"), value(1, "options.voxelmap.biomeOverlay.solid"))),
                group("options.voxelmap.group.mapRendering",
                        toggle("advanced.lighting", "options.minimap.dynamicLighting", map, () -> map.dynamicLighting, value -> map.dynamicLighting = value),
                        choice("advanced.terrain", "options.minimap.terrainDepth", map,
                                () -> map.heightmap && map.slopemap ? 3 : map.heightmap ? 1 : map.slopemap ? 2 : 0,
                                value -> {
                                    map.heightmap = value == 1 || value == 3;
                                    map.slopemap = value == 2 || value == 3;
                                },
                                value(0, "options.off"), value(1, "options.minimap.terrain.height"), value(2, "options.minimap.terrain.slope"), value(3, "options.minimap.terrain.both")),
                        toggle("advanced.water", "options.minimap.waterTransparency", map, () -> map.waterTransparency, value -> map.waterTransparency = value),
                        toggle("advanced.blocks", "options.minimap.blockTransparency", map, () -> map.blockTransparency, value -> map.blockTransparency = value),
                        toggle("advanced.biomes", "options.minimap.biomes", map, () -> map.biomes, value -> map.biomes = value),
                        toggle("advanced.filtering", "options.voxelmap.advanced.textureFiltering", map, () -> map.filtering, value -> map.filtering = value))));
    }

    private static SettingsCategory minimap(VoxelMap voxelMap, MapSettingsManager map, SeedMapperSettingsManager seed, Supplier<Screen> host) {
        return new SettingsCategory("minimap", "options.voxelmap.category.minimap", List.of(
                group("options.voxelmap.group.visibilityPosition",
                        toggle("minimap.visible", "options.voxelmap.minimap.visible", map, () -> !map.hide, value -> map.hide = !value,
                                () -> map.minimapAllowed, serverDisabled(), 0),
                        choice("minimap.location", "options.minimap.location", map, () -> map.mapCorner, value -> map.mapCorner = value,
                                value(0, "options.minimap.location.topLeft"), value(1, "options.minimap.location.topRight"),
                                value(2, "options.minimap.location.bottomRight"), value(3, "options.minimap.location.bottomLeft")),
                        choice("minimap.size", "options.minimap.size", map, () -> map.sizeModifier, value -> map.sizeModifier = value,
                                value(-1, "options.minimap.size.small"), value(0, "options.minimap.size.medium"), value(1, "options.minimap.size.large"),
                                value(2, "options.minimap.size.xl"), value(3, "options.minimap.size.xxl"), value(4, "options.minimap.size.xxxl")),
                        choice("minimap.shape", "options.voxelmap.minimap.shape", map, () -> map.squareMap, value -> map.squareMap = value,
                                value(false, "options.voxelmap.minimap.shape.round"), value(true, "options.voxelmap.minimap.shape.square")),
                        choice("minimap.orientation", "options.voxelmap.minimap.orientation", map, () -> map.rotates, value -> map.rotates = value,
                                value(false, "options.voxelmap.minimap.orientation.north"), value(true, "options.voxelmap.minimap.orientation.rotate"))),
                group("options.voxelmap.group.information",
                        choice("minimap.coordinates", "options.voxelmap.minimap.coordinates", map, () -> map.coordsMode, value -> map.coordsMode = value,
                                value(0, "options.off"), value(2, "options.minimap.showCoordinates.horizontal"), value(1, "options.minimap.showCoordinates.classic")),
                        toggle("minimap.biome", "options.minimap.showBiome", map, () -> map.showBiome, value -> map.showBiome = value),
                        toggle("minimap.caves", "options.minimap.caveMode", map, () -> map.getBooleanValue(EnumOptionsMinimap.CAVE_MODE),
                                value -> {
                                    if (map.getBooleanValue(EnumOptionsMinimap.CAVE_MODE) != value) {
                                        MapSettingsManager.updateBooleanOrListValue(map, EnumOptionsMinimap.CAVE_MODE);
                                    }
                                },
                                () -> map.cavesAllowed, serverDisabled(), 0)),
                group("options.voxelmap.group.hudLayout",
                        toggle("minimap.effects", "options.minimap.moveMapBelowStatusEffectIcons", map, () -> map.moveMapBelowStatusEffectIcons, value -> map.moveMapBelowStatusEffectIcons = value),
                        toggle("minimap.scoreboard", "options.minimap.moveScoreboardBelowMap", map, () -> map.moveScoreboardBelowMap, value -> map.moveScoreboardBelowMap = value)),
                minimapExtras(voxelMap, map, seed),
                highlights(map, host)));
    }

    private static SettingsCategory radar(RadarSettingsManager radar, MapSettingsManager map, SeedMapperSettingsManager seed, Runnable openEntityTypeDialog, Supplier<Screen> host) {
        return new SettingsCategory("radar", "options.voxelmap.category.radar", List.of(
                group("options.voxelmap.group.radarGeneral",
                        toggle("radar.visible", "options.minimap.radar.showRadar", radar, () -> radar.showRadar, value -> radar.showRadar = value,
                                () -> radarAvailable(radar), serverDisabled(), 0),
                        choice("radar.mode", "options.minimap.radar.radarMode", radar, () -> radar.radarMode, value -> radar.radarMode = value,
                                value(1, "options.voxelmap.radar.mode.simple"), value(2, "options.voxelmap.radar.mode.full")),
                        dependentRadarToggle("radar.elevation", "options.minimap.radar.showEntityElevation", radar, () -> radar.showEntityElevation, value -> radar.showEntityElevation = value, () -> true),
                        dependentRadarToggle("radar.invisible", "options.minimap.radar.hideInvisibleEntities", radar, () -> radar.hideInvisibleEntities, value -> radar.hideInvisibleEntities = value, () -> true)),
                group("options.voxelmap.group.players",
                        toggle("radar.players", "options.minimap.radar.showPlayers", radar, () -> radar.showPlayers, value -> radar.showPlayers = value,
                                () -> radarEnabled(radar) && radar.radarPlayersAllowed, serverDisabled(), 0),
                        dependentRadarToggle("radar.playerNames", "options.minimap.radar.showPlayerNames", radar, () -> radar.showPlayerNames, value -> radar.showPlayerNames = value, () -> radar.showPlayers && radar.radarMode == 2),
                        dependentRadarToggle("radar.playerHelmets", "options.minimap.radar.showPlayerHelmets", radar, () -> radar.showPlayerHelmets, value -> radar.showPlayerHelmets = value, () -> radar.showPlayers && radar.radarMode == 2),
                        dependentRadarToggle("radar.sneaking", "options.minimap.radar.hideSneakingPlayers", radar, () -> radar.hideSneakingPlayers, value -> radar.hideSneakingPlayers = value, () -> radar.showPlayers),
                        dependentRadarToggle("radar.facing", "options.minimap.radar.showFacing", radar, () -> radar.showFacing, value -> radar.showFacing = value,
                                () -> radar.radarMode == 1 && (radar.showPlayers || radar.showHostiles || radar.showNeutrals))),
                group("options.voxelmap.group.creatures",
                        toggle("radar.hostiles", "options.voxelmap.radar.hostiles", radar, () -> radar.showHostiles, value -> radar.showHostiles = value,
                                () -> radarEnabled(radar) && radar.radarMobsAllowed, serverDisabled(), 0),
                        toggle("radar.neutrals", "options.voxelmap.radar.neutrals", radar, () -> radar.showNeutrals, value -> radar.showNeutrals = value,
                                () -> radarEnabled(radar) && radar.radarMobsAllowed, serverDisabled(), 0),
                        dependentRadarToggle("radar.mobNames", "options.minimap.radar.showMobNames", radar, () -> radar.showMobNames, value -> radar.showMobNames = value, () -> radar.radarMode == 2 && (radar.showHostiles || radar.showNeutrals)),
                        dependentRadarToggle("radar.mobHelmets", "options.minimap.radar.showMobHelmets", radar, () -> radar.showMobHelmets, value -> radar.showMobHelmets = value, () -> radar.radarMode == 2 && (radar.showHostiles || radar.showNeutrals)),
                        dependentRadarToggle("radar.fullNames", "options.minimap.radar.showFullEntityNames", radar, () -> radar.showFullEntityNames, value -> radar.showFullEntityNames = value, () -> radar.radarMode == 2 && (radar.showHostiles || radar.showNeutrals)),
                        SettingsOption.action("radar.individualEntities", "options.voxelmap.radar.individualEntities", tooltip("radar.individualEntities"),
                                Component.translatable("options.voxelmap.action.open"), openEntityTypeDialog,
                                () -> radarEnabled(radar) && radar.radarMobsAllowed && (radar.showHostiles || radar.showNeutrals),
                                () -> Component.translatable(!radar.radarAllowed || !radar.radarMobsAllowed
                                        ? "options.voxelmap.managed.serverDisabled"
                                        : "options.voxelmap.requires.entityCategory"))),
                group("options.voxelmap.group.iconDisplay",
                        dependentRadarToggle("radar.filtering", "options.minimap.radar.iconFiltering", radar, () -> radar.filtering, value -> radar.filtering = value, () -> radar.radarMode == 2),
                        dependentRadarToggle("radar.outlines", "options.minimap.radar.iconOutlines", radar, () -> radar.outlines, value -> radar.outlines = value, () -> radar.radarMode == 2)),
                portalMarkers(map),
                detections(map, seed, host),
                markerDisplay(map, seed)));
    }

    private static SettingsCategory waypoints(MapSettingsManager map) {
        return new SettingsCategory("waypoints", "options.voxelmap.category.waypoints", List.of(
                group("options.voxelmap.group.waypointDisplay",
                        toggle("waypoints.visible", "options.voxelmap.waypoints.visible", map,
                                () -> SettingsValueAdapters.waypointsVisible(map.showWaypointBeacons, map.showWaypointSigns),
                                value -> {
                                    SettingsValueAdapters.WaypointStyle style = SettingsValueAdapters.waypointVisibility(value);
                                    map.showWaypointBeacons = style.beacons();
                                    map.showWaypointSigns = style.signs();
                                },
                                () -> map.waypointsAllowed, serverDisabled(), 0),
                        SettingsOption.choice("waypoints.style", "options.voxelmap.waypoints.style", tooltip("waypoints.style"),
                                () -> SettingsValueAdapters.waypointStyle(map.showWaypointBeacons, map.showWaypointSigns),
                                value -> changed(map, ignored -> {
                                    SettingsValueAdapters.WaypointStyle style = SettingsValueAdapters.waypointStyle(value);
                                    map.showWaypointBeacons = style.beacons();
                                    map.showWaypointSigns = style.signs();
                                }, value),
                                List.of(value(1, "options.minimap.inGameWaypoints.beacons"), value(2, "options.minimap.inGameWaypoints.signs"), value(3, "options.minimap.inGameWaypoints.both")),
                                () -> map.waypointsAllowed && (map.showWaypointBeacons || map.showWaypointSigns), requires("options.voxelmap.requires.waypoints"), 1),
                        slider("waypoints.distance", "options.minimap.waypoints.distance", map,
                                () -> map.maxWaypointDisplayDistance < 0 ? 10050.0 : (double) map.maxWaypointDisplayDistance,
                                value -> map.maxWaypointDisplayDistance = value > 10000 ? -1 : value.intValue(), 50, 10050, 50,
                                value -> value > 10000 ? Component.translatable("options.minimap.waypoints.infinite") : Component.literal(value.intValue() + " m"),
                                () -> map.waypointsAllowed, requires("options.voxelmap.requires.waypoints"), 1),
                        slider("waypoints.scale", "options.minimap.waypoints.waypointSignScale", map, () -> (double) map.waypointSignScale,
                                value -> map.waypointSignScale = value.floatValue(), 0.5, 1.5, 0.01,
                                value -> Component.literal(String.format(Locale.ROOT, "%.2fx", value)),
                                () -> map.waypointsAllowed && map.showWaypointSigns, requires("options.voxelmap.requires.waypointSigns"), 1),
                        toggle("waypoints.names", "options.voxelmap.waypoints.names", map, () -> map.waypointNamesLocation != 0,
                                value -> map.waypointNamesLocation = value ? 2 : 0, () -> map.waypointsAllowed && map.showWaypointSigns, requires("options.voxelmap.requires.waypointSigns"), 1),
                        SettingsOption.choice("waypoints.namePosition", "options.voxelmap.waypoints.namePosition", tooltip("waypoints.namePosition"),
                                () -> map.waypointNamesLocation == 1 ? 1 : 2, value -> changed(map, ignored -> map.waypointNamesLocation = value, value),
                                List.of(value(1, "options.minimap.waypoints.showWaypointNames.aboveIcon"), value(2, "options.minimap.waypoints.showWaypointNames.belowIcon")),
                                () -> map.waypointsAllowed && map.showWaypointSigns && map.waypointNamesLocation != 0, requires("options.voxelmap.requires.waypointNames"), 2),
                        toggle("waypoints.distances", "options.voxelmap.waypoints.distances", map, () -> map.waypointDistancesLocation != 0,
                                value -> map.waypointDistancesLocation = value ? 2 : 0, () -> map.waypointsAllowed && map.showWaypointSigns, requires("options.voxelmap.requires.waypointSigns"), 1),
                        SettingsOption.choice("waypoints.distancePosition", "options.voxelmap.waypoints.distancePosition", tooltip("waypoints.distancePosition"),
                                () -> map.waypointDistancesLocation == 1 ? 1 : 2, value -> changed(map, ignored -> map.waypointDistancesLocation = value, value),
                                List.of(value(1, "options.voxelmap.waypoints.distancePosition.primary"), value(2, "options.voxelmap.waypoints.distancePosition.secondary")),
                                () -> map.waypointsAllowed && map.showWaypointSigns && map.waypointDistancesLocation != 0, requires("options.voxelmap.requires.waypointDistances"), 2),
                        choice("waypoints.units", "options.minimap.waypoints.distanceUnitConversion", map, () -> map.waypointDistanceConversion,
                                value -> map.waypointDistanceConversion = value, value(0, "options.off"),
                                value(1, "options.minimap.waypoints.distanceUnitConversion.from1000m"), value(2, "options.minimap.waypoints.distanceUnitConversion.from10000m"))),
                group("options.voxelmap.group.deathpoints",
                        toggle("waypoints.deathpoints", "options.voxelmap.waypoints.deathpoints.enabled", map, () -> map.deathpoints != 0,
                                value -> map.deathpoints = SettingsValueAdapters.optionalMode(value, map.deathpoints), () -> map.deathWaypointAllowed, serverDisabled(), 0),
                        SettingsOption.choice("waypoints.deathpointRetention", "options.voxelmap.waypoints.deathpoints.retention", tooltip("waypoints.deathpointRetention"),
                                () -> map.deathpoints == 2 ? 2 : 1, value -> changed(map, ignored -> map.deathpoints = value, value),
                                List.of(value(1, "options.minimap.waypoints.deathpoints.mostRecent"), value(2, "options.minimap.waypoints.deathpoints.all")),
                                () -> map.deathWaypointAllowed && map.deathpoints != 0, requires("options.voxelmap.requires.deathpoints"), 1)),
                group("options.voxelmap.group.waypointBehavior",
                        toggle("waypoints.autoPortal", "options.minimap.waypoints.autoPortalWaypoints", map,
                                () -> map.autoPortalWaypoints, value -> map.autoPortalWaypoints = value,
                                () -> map.waypointsAllowed, serverDisabled(), 0),
                        toggle("waypoints.confirmDelete", "options.minimap.waypoints.confirmDelete", map,
                                () -> map.confirmWaypointDelete, value -> map.confirmWaypointDelete = value)),
                group("options.voxelmap.group.waypointCompass",
                        toggle("compass.enabled", "options.minimap.waypoints.compass", map,
                                () -> map.waypointCompass, value -> map.waypointCompass = value,
                                () -> map.waypointsAllowed, serverDisabled(), 0),
                        compassToggle("compass.coords", "options.minimap.waypoints.compassShowCoords", map,
                                () -> map.waypointCompassShowCoords, value -> map.waypointCompassShowCoords = value),
                        compassToggle("compass.outline", "options.minimap.waypoints.compassTextOutline", map,
                                () -> map.waypointCompassTextOutline, value -> map.waypointCompassTextOutline = value),
                        compassSlider("compass.range", "options.minimap.waypoints.compassIconRange", map,
                                () -> map.waypointCompassIconRange < 0 ? 10050.0 : (double) map.waypointCompassIconRange,
                                value -> map.waypointCompassIconRange = value > 10000 ? -1 : value.intValue(), 50, 10050, 50,
                                value -> value > 10000 ? Component.translatable("options.minimap.waypoints.infinite") : Component.literal(value.intValue() + " m")),
                        compassSlider("compass.max", "options.minimap.waypoints.compassMaxWaypoints", map,
                                () -> (double) map.waypointCompassMaxWaypoints, value -> map.waypointCompassMaxWaypoints = value.intValue(), 1, 64, 1,
                                value -> Component.literal(String.valueOf(value.intValue()))),
                        compassSlider("compass.x", "options.minimap.waypoints.compassX", map,
                                () -> (double) map.waypointCompassX, value -> map.waypointCompassX = value.intValue(), 0, 100, 1,
                                value -> Component.literal(value.intValue() + "%")),
                        compassSlider("compass.y", "options.minimap.waypoints.compassY", map,
                                () -> (double) map.waypointCompassY, value -> map.waypointCompassY = value.intValue(), 0, 40, 1,
                                value -> Component.literal(String.valueOf(value.intValue()))),
                        compassSlider("compass.textOpacity", "options.minimap.waypoints.compassTextOpacity", map,
                                () -> (double) map.waypointCompassTextOpacity, value -> map.waypointCompassTextOpacity = value.intValue(), 0, 100, 1,
                                value -> Component.literal(value.intValue() + "%")),
                        compassSlider("compass.outlineOpacity", "options.minimap.waypoints.compassOutlineOpacity", map,
                                () -> (double) map.waypointCompassOutlineOpacity, value -> map.waypointCompassOutlineOpacity = value.intValue(), 0, 100, 1,
                                value -> Component.literal(value.intValue() + "%")),
                        compassSlider("compass.backgroundOpacity", "options.minimap.waypoints.compassBackgroundOpacity", map,
                                () -> (double) map.waypointCompassBackgroundOpacity, value -> map.waypointCompassBackgroundOpacity = value.intValue(), 0, 100, 1,
                                value -> Component.literal(value.intValue() + "%")))));
    }

    private static SettingsOption<Boolean> compassToggle(String id, String key, MapSettingsManager map, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return SettingsOption.toggle(id, key, null, getter, value -> changed(map, setter, value),
                () -> map.waypointsAllowed && map.waypointCompass, requires("options.voxelmap.requires.compass"), 1);
    }

    private static SettingsOption<Double> compassSlider(String id, String key, MapSettingsManager map, Supplier<Double> getter, Consumer<Double> setter,
            double min, double max, double step, java.util.function.Function<Double, Component> formatter) {
        return SettingsOption.slider(id, key, null, getter, value -> changed(map, setter, value), min, max, step, formatter,
                () -> map.waypointsAllowed && map.waypointCompass, requires("options.voxelmap.requires.compass"), 1);
    }

    private static SettingsCategory worldMap(MapSettingsManager map, PersistentMapSettingsManager world, Supplier<Screen> host) {
        return new SettingsCategory("worldmap", "options.voxelmap.category.worldmap", List.of(
                group("options.voxelmap.group.worldmapDisplay",
                        persistentToggle("worldmap.coordinates", "options.worldmap.showCoordinates", world, EnumOptionsMinimap.SHOW_WORLDMAP_COORDS, () -> true, Component::empty, 0),
                        persistentToggle("worldmap.arrow", "options.worldmap.showPlayerDirectionArrow", world, EnumOptionsMinimap.SHOW_WORLDMAP_PLAYER_DIRECTION_ARROW, () -> true, Component::empty, 0),
                        persistentToggle("worldmap.hideMultiworld", "options.worldmap.hideMultiworldButton", world, EnumOptionsMinimap.WORLDMAP_HIDE_MULTIWORLD_BUTTON, () -> true, Component::empty, 0),
                        persistentToggle("worldmap.literalLines", "options.worldmap.literalLineMode", world, EnumOptionsMinimap.WORLDMAP_LITERAL_LINE_MODE, () -> true, Component::empty, 0),
                        persistentToggle("worldmap.waypoints", "options.worldmap.showWaypoints", world, EnumOptionsMinimap.SHOW_WAYPOINTS, () -> map.waypointsAllowed, serverDisabled(), 0),
                        persistentToggle("worldmap.names", "options.worldmap.showWaypointNames", world, EnumOptionsMinimap.SHOW_WAYPOINT_NAMES, () -> map.waypointsAllowed && world.showWaypoints, requires("options.voxelmap.requires.worldmapWaypoints"), 1),
                        persistentToggle("worldmap.distant", "options.worldmap.showDistantWaypoints", world, EnumOptionsMinimap.SHOW_DISTANT_WAYPOINTS, () -> map.waypointsAllowed && world.showWaypoints, requires("options.voxelmap.requires.worldmapWaypoints"), 1),
                        persistentToggle("worldmap.cluster", "options.worldmap.clusterWaypoints", world, EnumOptionsMinimap.WORLDMAP_CLUSTER_WAYPOINTS, () -> map.waypointsAllowed && world.showWaypoints, requires("options.voxelmap.requires.worldmapWaypoints"), 1),
                        persistentToggle("worldmap.perfWaypoints", "options.worldmap.showWaypointsInPerformanceMode", world, EnumOptionsMinimap.WORLDMAP_SHOW_WAYPOINTS_IN_PERFORMANCE_MODE, () -> map.waypointsAllowed && world.showWaypoints, requires("options.voxelmap.requires.worldmapWaypoints"), 1)),
                group("options.voxelmap.group.zoomCache",
                        SettingsOption.slider("worldmap.farthestZoom", "options.voxelmap.worldmap.farthestZoom", tooltip("worldmap.farthestZoom"),
                                () -> (double) world.getFloatValue(EnumOptionsMinimap.MIN_ZOOM),
                                value -> world.setFloatValue(EnumOptionsMinimap.MIN_ZOOM, zoomPowerToNormalized(value)),
                                PersistentMapSettingsManager.MIN_WORLDMAP_ZOOM_POWER, PersistentMapSettingsManager.MAX_WORLDMAP_ZOOM_POWER, 1,
                                VoxelMapSettings::zoomLabel, () -> true, Component::empty, 0),
                        SettingsOption.slider("worldmap.nearestZoom", "options.voxelmap.worldmap.nearestZoom", tooltip("worldmap.nearestZoom"),
                                () -> (double) world.getFloatValue(EnumOptionsMinimap.MAX_ZOOM),
                                value -> world.setFloatValue(EnumOptionsMinimap.MAX_ZOOM, zoomPowerToNormalized(value)),
                                PersistentMapSettingsManager.MIN_WORLDMAP_ZOOM_POWER, PersistentMapSettingsManager.MAX_WORLDMAP_ZOOM_POWER, 1,
                                VoxelMapSettings::zoomLabel, () -> true, Component::empty, 0),
                        SettingsOption.slider("worldmap.cache", "options.worldmap.cacheSize", tooltip("worldmap.cache"),
                                () -> (double) world.getFloatValue(EnumOptionsMinimap.CACHE_SIZE), value -> world.setFloatValue(EnumOptionsMinimap.CACHE_SIZE, (float) (value / 5000.0)),
                                30, 5000, 10, value -> Component.translatable("options.voxelmap.value.regions", value.intValue()), () -> true, Component::empty, 0),
                        persistentSlider("worldmap.perfThreshold", "options.worldmap.performanceModeThreshold", world, EnumOptionsMinimap.WORLDMAP_PERFORMANCE_MODE_THRESHOLD,
                                PersistentMapSettingsManager.MIN_PERFORMANCE_MODE_THRESHOLD, PersistentMapSettingsManager.MAX_PERFORMANCE_MODE_THRESHOLD, 0.0025,
                                value -> Component.literal(String.format(Locale.ROOT, "%.4f", value))),
                        persistentSlider("worldmap.lineThickness", "options.worldmap.chunkLineThickness", world, EnumOptionsMinimap.WORLDMAP_CHUNK_LINE_THICKNESS,
                                PersistentMapSettingsManager.MIN_CHUNK_LINE_THICKNESS, PersistentMapSettingsManager.MAX_CHUNK_LINE_THICKNESS, 0.05,
                                value -> Component.literal(String.format(Locale.ROOT, "%.2f", value)))),
                group("options.voxelmap.group.seedMap",
                        SettingsOption.choice("worldmap.seedmapStyle", "options.worldmap.seedmapStyle", null,
                                () -> world.seedMapStyle,
                                value -> {
                                    world.seedMapStyle = value;
                                    world.saveAll();
                                },
                                seedMapStyleChoices()),
                        persistentToggle("worldmap.seedmapMoving", "options.worldmap.seedmapUpdateWhileMoving", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_UPDATE_WHILE_MOVING, () -> true, Component::empty, 0),
                        persistentToggle("worldmap.seedmapBiome", "options.worldmap.seedmapBiomeUnderCursor", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_BIOME_UNDER_CURSOR, () -> true, Component::empty, 0),
                        persistentSlider("worldmap.seedmapContour", "options.worldmap.seedmapContourStrength", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_CONTOUR_STRENGTH,
                                PersistentMapSettingsManager.MIN_SEEDMAP_CONTOUR_STRENGTH, PersistentMapSettingsManager.MAX_SEEDMAP_CONTOUR_STRENGTH, 0.01,
                                value -> Component.literal(String.format(Locale.ROOT, "%.2f", value))),
                        persistentSlider("worldmap.seedmapPadding", "options.worldmap.seedmapPreviewPadding", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_PREVIEW_PADDING,
                                PersistentMapSettingsManager.MIN_SEEDMAP_PREVIEW_PADDING, PersistentMapSettingsManager.MAX_SEEDMAP_PREVIEW_PADDING, 128,
                                value -> Component.literal(String.valueOf(value.intValue()))),
                        persistentSlider("worldmap.seedmapMinZoom", "options.worldmap.seedmapMinZoom", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_MIN_ZOOM,
                                PersistentMapSettingsManager.MIN_SEEDMAP_MIN_ZOOM, PersistentMapSettingsManager.MAX_SEEDMAP_MIN_ZOOM, 0.001953125,
                                value -> Component.literal(String.format(Locale.ROOT, "%.4f", value))),
                        persistentSlider("worldmap.seedmapTerrainZoom", "options.worldmap.seedmapTerrainMinZoom", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_TERRAIN_MIN_ZOOM,
                                PersistentMapSettingsManager.MIN_SEEDMAP_TERRAIN_MIN_ZOOM, PersistentMapSettingsManager.MAX_SEEDMAP_TERRAIN_MIN_ZOOM, 0.001953125,
                                value -> Component.literal(String.format(Locale.ROOT, "%.4f", value))),
                        persistentSlider("worldmap.seedmapResolution", "options.worldmap.seedmapPreviewResolution", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_PREVIEW_RESOLUTION,
                                PersistentMapSettingsManager.MIN_SEEDMAP_PREVIEW_RESOLUTION, PersistentMapSettingsManager.MAX_SEEDMAP_PREVIEW_RESOLUTION, 128,
                                value -> Component.literal(String.valueOf(value.intValue()))),
                        persistentSlider("worldmap.seedmapCache", "options.worldmap.seedmapPreviewCache", world, EnumOptionsMinimap.WORLDMAP_SEEDMAP_PREVIEW_CACHE,
                                PersistentMapSettingsManager.MIN_SEEDMAP_PREVIEW_CACHE, PersistentMapSettingsManager.MAX_SEEDMAP_PREVIEW_CACHE, 1,
                                value -> Component.literal(String.valueOf(value.intValue())))),
                group("options.voxelmap.group.transport",
                        SettingsOption.action("worldmap.transport", "options.voxelmap.worldmap.transportShortcuts", null,
                                Component.translatable("options.voxelmap.action.open"),
                                () -> VoxelConstants.getMinecraft().gui.setScreen(new GuiTransportShortcutsOptions(host.get()))))));
    }

    private static List<SettingsOption.Choice<PersistentMapSettingsManager.SeedMapStyle>> seedMapStyleChoices() {
        List<SettingsOption.Choice<PersistentMapSettingsManager.SeedMapStyle>> choices = new ArrayList<>();
        for (PersistentMapSettingsManager.SeedMapStyle style : PersistentMapSettingsManager.SeedMapStyle.values()) {
            choices.add(new SettingsOption.Choice<>(style, Component.literal(style.displayName())));
        }
        return choices;
    }

    private static SettingsOption<Double> persistentSlider(String id, String key, PersistentMapSettingsManager world, EnumOptionsMinimap option,
            double min, double max, double step, java.util.function.Function<Double, Component> formatter) {
        return SettingsOption.slider(id, key, null,
                () -> (double) world.getFloatValue(option),
                value -> world.setFloatValue(option, (float) ((value - min) / (max - min))),
                min, max, step, formatter, () -> true, Component::empty, 0);
    }

    private static SettingsCategory advanced(VoxelMap voxelMap, MapSettingsManager map, RadarSettingsManager radar) {
        return new SettingsCategory("advanced", "options.voxelmap.category.advanced", List.of(
                group("options.voxelmap.group.worldServer",
                        SettingsOption.text("advanced.seed", "options.minimap.worldSeed", tooltip("advanced.seed"), voxelMap::getWorldSeed,
                                value -> {
                                    voxelMap.setWorldSeed(value.trim());
                                    voxelMap.getMap().forceFullRender(true);
                                },
                                () -> !VoxelConstants.getMinecraft().hasSingleplayerServer(), requires("options.voxelmap.managed.singleplayerSeed")),
                        SettingsOption.text("advanced.teleport", "options.minimap.teleportCommand", tooltip("advanced.teleport"), () -> map.teleportCommand,
                                value -> {
                                    map.teleportCommand = value.isBlank() ? "tp %p %x %y %z" : value;
                                    map.markChanged();
                                },
                                () -> map.serverTeleportCommand == null, requires("options.voxelmap.managed.server"))),
                group("options.voxelmap.group.troubleshooting",
                        toggle("advanced.compatibilityRenderer", "options.minimap.radar.cpuRendering", radar,
                                () -> radar.cpuRendering || radar.forceCpuRendering, value -> radar.cpuRendering = value || radar.forceCpuRendering,
                                () -> !radar.forceCpuRendering, requires("options.voxelmap.managed.renderer"), 0)),
                group("options.voxelmap.group.interface",
                        choice("advanced.colorPicker", "options.minimap.colorPickerMode", map, () -> map.colorPickerMode, value -> map.colorPickerMode = value,
                                value(0, "options.minimap.colorPickerMode.simple"), value(1, "options.minimap.colorPickerMode.full")),
                        toggle("advanced.updates", "options.minimap.updateNotifier", map, () -> map.updateNotifier, value -> map.updateNotifier = value))));
    }

    private static SettingsCategory chunks(MapSettingsManager map, RadarSettingsManager radar, PersistentMapSettingsManager world, Supplier<Screen> host) {
        return new SettingsCategory("chunks", "options.voxelmap.category.chunks", List.of(
                group("options.voxelmap.group.chunkOverlays",
                        radarToggle("chunks.explored", "options.voxelmap.chunks.explored", radar, () -> radar.showExploredChunks, value -> radar.showExploredChunks = value),
                        radarToggle("chunks.newDetector", "options.voxelmap.chunks.newDetector", radar, () -> radar.showNewerNewChunks, value -> radar.showNewerNewChunks = value),
                        enumToggle("chunks.grid", map, EnumOptionsMinimap.CHUNK_GRID),
                        SettingsOption.toggle("chunks.slime", "options.minimap.slimeChunks", null,
                                () -> map.getBooleanValue(EnumOptionsMinimap.SLIME_CHUNKS),
                                value -> {
                                    if (map.getBooleanValue(EnumOptionsMinimap.SLIME_CHUNKS) != value) {
                                        MapSettingsManager.updateBooleanOrListValue(map, EnumOptionsMinimap.SLIME_CHUNKS);
                                    }
                                },
                                () -> VoxelConstants.getMinecraft().hasSingleplayerServer() || !VoxelConstants.getVoxelMapInstance().getWorldSeed().isEmpty(),
                                requires("options.voxelmap.requires.seed"), 0),
                        SettingsOption.toggle("chunks.worldmapExplored", "options.voxelmap.chunks.worldmapExplored", null,
                                () -> world.showExploredChunks,
                                value -> {
                                    world.showExploredChunks = value;
                                    world.saveAll();
                                }),
                        SettingsOption.toggle("chunks.worldmapNewOld", "options.worldmap.showNewOldChunks", null,
                                () -> world.getBooleanValue(EnumOptionsMinimap.WORLDMAP_SHOW_NEW_OLD_CHUNKS),
                                value -> {
                                    if (world.getBooleanValue(EnumOptionsMinimap.WORLDMAP_SHOW_NEW_OLD_CHUNKS) != value) {
                                        world.toggleBooleanValue(EnumOptionsMinimap.WORLDMAP_SHOW_NEW_OLD_CHUNKS);
                                        world.saveAll();
                                    }
                                })),
                group("options.voxelmap.group.chunkDetection",
                        SettingsOption.choice("chunks.detectMode", "options.voxelmap.chunks.detectMode", null,
                                () -> radar.newerNewChunksDetectMode,
                                value -> {
                                    radar.newerNewChunksDetectMode = value;
                                    radar.markChanged();
                                },
                                List.of(value(0, "options.voxelmap.chunks.detectMode.normal"),
                                        value(1, "options.voxelmap.chunks.detectMode.ignoreBlockExploit"),
                                        value(2, "options.voxelmap.chunks.detectMode.blockExploit"))),
                        radarToggle("chunks.liquidExploit", "options.voxelmap.chunks.liquidExploit", radar, () -> radar.newerNewChunksLiquidExploit, value -> radar.newerNewChunksLiquidExploit = value),
                        radarToggle("chunks.blockExploit", "options.voxelmap.chunks.blockExploit", radar, () -> radar.newerNewChunksBlockUpdateExploit, value -> radar.newerNewChunksBlockUpdateExploit = value)),
                group("options.voxelmap.group.chunkColors",
                        colorText("chunks.exploredColor", "options.voxelmap.chunks.exploredColor", map, () -> radar.exploredChunksColor, value -> radar.exploredChunksColor = value, () -> true, Component::empty),
                        colorPick("chunks.exploredColorPick", "options.voxelmap.chunks.exploredColorPick", host, "options.voxelmap.chunks.exploredColor",
                                () -> radar.exploredChunksColor, value -> radar.exploredChunksColor = value, () -> true, Component::empty),
                        colorText("chunks.newColor", "options.voxelmap.chunks.newColor", map, () -> radar.newerNewChunksNewColor, value -> radar.newerNewChunksNewColor = value, () -> true, Component::empty),
                        colorPick("chunks.newColorPick", "options.voxelmap.chunks.newColorPick", host, "options.voxelmap.chunks.newColor",
                                () -> radar.newerNewChunksNewColor, value -> radar.newerNewChunksNewColor = value, () -> true, Component::empty),
                        colorText("chunks.oldColor", "options.voxelmap.chunks.oldColor", map, () -> radar.newerNewChunksOldColor, value -> radar.newerNewChunksOldColor = value, () -> true, Component::empty),
                        colorPick("chunks.oldColorPick", "options.voxelmap.chunks.oldColorPick", host, "options.voxelmap.chunks.oldColor",
                                () -> radar.newerNewChunksOldColor, value -> radar.newerNewChunksOldColor = value, () -> true, Component::empty),
                        colorText("chunks.blockColor", "options.voxelmap.chunks.blockColor", map, () -> radar.newerNewChunksBlockColor, value -> radar.newerNewChunksBlockColor = value, () -> true, Component::empty),
                        colorPick("chunks.blockColorPick", "options.voxelmap.chunks.blockColorPick", host, "options.voxelmap.chunks.blockColor",
                                () -> radar.newerNewChunksBlockColor, value -> radar.newerNewChunksBlockColor = value, () -> true, Component::empty)),
                group("options.voxelmap.group.chunkOpacity",
                        radarPctSlider("chunks.exploredOpacity", "options.voxelmap.chunks.exploredOpacity", radar, () -> (double) radar.exploredChunksOpacity, value -> radar.exploredChunksOpacity = value.intValue()),
                        radarPctSlider("chunks.newOpacity", "options.voxelmap.chunks.newOpacity", radar, () -> (double) radar.newerNewChunksNewOpacity, value -> radar.newerNewChunksNewOpacity = value.intValue()),
                        radarPctSlider("chunks.oldOpacity", "options.voxelmap.chunks.oldOpacity", radar, () -> (double) radar.newerNewChunksOldOpacity, value -> radar.newerNewChunksOldOpacity = value.intValue()),
                        radarPctSlider("chunks.blockOpacity", "options.voxelmap.chunks.blockOpacity", radar, () -> (double) radar.newerNewChunksBlockOpacity, value -> radar.newerNewChunksBlockOpacity = value.intValue()),
                        SettingsOption.slider("chunks.windowRadius", "options.voxelmap.chunks.windowRadius", null,
                                () -> (double) radar.newerNewChunksWindowRadiusChunks,
                                value -> {
                                    radar.newerNewChunksWindowRadiusChunks = value.intValue();
                                    radar.markChanged();
                                },
                                16, 256, 1, value -> Component.literal(value.intValue() + " chunks"), () -> true, Component::empty, 0),
                        SettingsOption.slider("chunks.refreshDistance", "options.voxelmap.chunks.refreshDistance", null,
                                () -> (double) radar.newerNewChunksRefreshDistanceChunks,
                                value -> {
                                    radar.newerNewChunksRefreshDistanceChunks = value.intValue();
                                    radar.markChanged();
                                },
                                8, 128, 1, value -> Component.literal(value.intValue() + " chunks"), () -> true, Component::empty, 0)),
                group("options.voxelmap.group.chunkMaintenance",
                        SettingsOption.action("chunks.clearExplored", "options.voxelmap.chunks.clearExplored", null,
                                Component.translatable("options.voxelmap.action.clear"),
                                () -> confirmAction(host, "options.voxelmap.chunks.clearExplored.confirm",
                                        () -> VoxelConstants.getVoxelMapInstance().getExploredChunksManager().clearCurrentWorld())),
                        SettingsOption.action("chunks.clearNew", "options.voxelmap.chunks.clearNew", null,
                                Component.translatable("options.voxelmap.action.clear"),
                                () -> confirmAction(host, "options.voxelmap.chunks.clearNew.confirm",
                                        () -> VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().clearCurrentWorldData())))));
    }

    private static SettingsCategory sharing(Supplier<Screen> host) {
        String[] shareTo = {""};
        String[] shareCode = {""};
        String[] layerName = {""};
        String[] transferName = {""};
        String[] importAs = {""};

        return new SettingsCategory("sharing", "options.voxelmap.category.sharing", List.of(
                group("options.voxelmap.group.sharingSetup",
                        SettingsOption.text("sharing.passphrase", "options.voxelmap.sharing.passphrase", null,
                                () -> ChunkShareConfig.getPassphrase() == null ? "" : ChunkShareConfig.getPassphrase(),
                                value -> ChunkSyncCommandHandler.runFromGui("key " + value.trim()),
                                () -> true, Component::empty),
                        SettingsOption.choice("sharing.host", "options.voxelmap.sharing.host", null,
                                ChunkShareConfig::getHost,
                                value -> ChunkSyncCommandHandler.runFromGui("host " + value.id),
                                List.of(new SettingsOption.Choice<>(ChunkShareTransport.Host.LITTERBOX, Component.literal(ChunkShareTransport.Host.LITTERBOX.id)),
                                        new SettingsOption.Choice<>(ChunkShareTransport.Host.FILE_IO, Component.literal(ChunkShareTransport.Host.FILE_IO.id)))),
                        SettingsOption.toggle("sharing.hideWarning", "options.voxelmap.sharing.hideWarning", null,
                                ChunkShareConfig::isPublicShareWarningHidden, ChunkShareConfig::setPublicShareWarningHidden)),
                group("options.voxelmap.group.sharingShare",
                        SettingsOption.text("sharing.shareTo", "options.voxelmap.sharing.shareTo", null,
                                () -> shareTo[0], value -> shareTo[0] = value.trim(), () -> true, Component::empty),
                        espAction("sharing.shareToRun", "options.voxelmap.sharing.shareToRun", host,
                                () -> ChunkSyncCommandHandler.runFromGui("share to " + shareTo[0])),
                        SettingsOption.action("sharing.sharePublic", "options.voxelmap.sharing.sharePublic", null,
                                Component.translatable("options.voxelmap.action.run"),
                                () -> {
                                    commitText(host);
                                    if (ChunkShareConfig.isPublicShareWarningHidden()) {
                                        ChunkSyncCommandHandler.runFromGui("share");
                                    } else {
                                        Screen previous = host.get();
                                        VoxelConstants.getMinecraft().gui.setScreen(new ConfirmScreen(confirmed -> {
                                            if (confirmed) {
                                                ChunkSyncCommandHandler.runFromGui("share");
                                            }
                                            VoxelConstants.getMinecraft().gui.setScreen(previous);
                                        }, Component.translatable("chunksync.publicShareWarning.title"),
                                                Component.translatable("chunksync.publicShareWarning.line1")
                                                        .append("\n")
                                                        .append(Component.translatable("chunksync.publicShareWarning.line2"))));
                                    }
                                })),
                group("options.voxelmap.group.sharingGet",
                        SettingsOption.text("sharing.code", "options.voxelmap.sharing.code", null,
                                () -> shareCode[0], value -> shareCode[0] = value.trim(), () -> true, Component::empty),
                        espAction("sharing.getMerge", "options.voxelmap.sharing.getMerge", host,
                                () -> ChunkSyncCommandHandler.runFromGui("get " + shareCode[0])),
                        SettingsOption.text("sharing.layerName", "options.voxelmap.sharing.layerName", null,
                                () -> layerName[0], value -> layerName[0] = value.trim(), () -> true, Component::empty),
                        espAction("sharing.getAsLayer", "options.voxelmap.sharing.getAsLayer", host,
                                () -> ChunkSyncCommandHandler.runFromGui("get " + shareCode[0] + " as " + layerName[0])),
                        openScreen("sharing.layers", "options.voxelmap.sharing.layers", host, GuiChunkSyncLayers::new)),
                group("options.voxelmap.group.sharingTransfer",
                        SettingsOption.text("sharing.transferName", "options.voxelmap.sharing.transferName", null,
                                () -> transferName[0], value -> transferName[0] = value.trim(), () -> true, Component::empty),
                        SettingsOption.text("sharing.importAs", "options.voxelmap.sharing.importAs", null,
                                () -> importAs[0], value -> importAs[0] = value.trim(), () -> true, Component::empty),
                        espAction("sharing.export", "options.voxelmap.sharing.export", host,
                                () -> ChunkSyncCommandHandler.runFromGui("export" + optionalArg(transferName[0]))),
                        espAction("sharing.import", "options.voxelmap.sharing.import", host,
                                () -> {
                                    String suffix = optionalArg(transferName[0]);
                                    if (!importAs[0].isBlank()) {
                                        suffix += " as " + importAs[0];
                                    }
                                    ChunkSyncCommandHandler.runFromGui("import" + suffix);
                                }))));
    }

    private static String optionalArg(String argument) {
        return argument == null || argument.isBlank() ? "" : " " + argument.trim();
    }

    private static SettingsCategory seedmapper(SeedMapperSettingsManager seed, Supplier<Screen> host) {
        return new SettingsCategory("seedmapper", "options.voxelmap.category.seedmapper", List.of(
                group("options.voxelmap.group.seedmapperGeneral",
                        SettingsOption.text("seedmapper.seed", "options.voxelmap.seedmapper.seed", null,
                                () -> seed.manualSeed == null ? "" : seed.manualSeed,
                                value -> {
                                    seed.manualSeed = value.trim();
                                    seed.putSavedSeed(seed.getCurrentServerKey(), seed.manualSeed);
                                    MapSettingsManager.instance.saveAll();
                                },
                                () -> true, Component::empty),
                        SettingsOption.choice("seedmapper.version", "options.voxelmap.seedmapper.version", null,
                                () -> selectedVersionId(seed),
                                value -> {
                                    seed.minecraftVersion = value;
                                    MapSettingsManager.instance.saveAll();
                                },
                                versionChoices()),
                        seedToggleSimple("seedmapper.overlay", "options.voxelmap.seedmapper.overlay", () -> seed.enabled, value -> seed.enabled = value),
                        seedToggleSimple("seedmapper.lootOnly", "options.voxelmap.seedmapper.lootOnly", () -> seed.showLootableOnly, value -> seed.showLootableOnly = value),
                        SettingsOption.slider("seedmapper.markerLimit", "options.voxelmap.seedmapper.markerLimit", null,
                                () -> (double) seed.worldMapMarkerLimit,
                                value -> {
                                    seed.worldMapMarkerLimit = Math.max(200, ((int) Math.round(value / 100.0D)) * 100);
                                    MapSettingsManager.instance.saveAll();
                                },
                                200, 20000, 100, value -> Component.literal(String.valueOf(((int) Math.round(value / 100.0D)) * 100)), () -> true, Component::empty, 0),
                        SettingsOption.slider("seedmapper.minimapIconScale", "options.voxelmap.seedmapper.minimapIconScale", null,
                                () -> seed.seedMapperMinimapIconScale,
                                value -> {
                                    seed.seedMapperMinimapIconScale = value;
                                    MapSettingsManager.instance.saveAll();
                                },
                                0.5, 1.25, 0.01, value -> Component.literal(String.format(Locale.ROOT, "%.2fx", value)), () -> true, Component::empty, 0),
                        SettingsOption.slider("seedmapper.worldmapIconScale", "options.voxelmap.seedmapper.worldmapIconScale", null,
                                () -> seed.seedMapperWorldMapIconScale,
                                value -> {
                                    seed.seedMapperWorldMapIconScale = value;
                                    MapSettingsManager.instance.saveAll();
                                },
                                0.5, 1.25, 0.01, value -> Component.literal(String.format(Locale.ROOT, "%.2fx", value)), () -> true, Component::empty, 0)),
                group("options.voxelmap.group.seedmapperLocate",
                        openScreen("seedmapper.locateStructure", "options.voxelmap.seedmapper.locateStructure", host, parent -> new GuiSeedMapperLocator(parent, GuiSeedMapperLocator.Mode.STRUCTURE)),
                        openScreen("seedmapper.locateBiome", "options.voxelmap.seedmapper.locateBiome", host, parent -> new GuiSeedMapperLocator(parent, GuiSeedMapperLocator.Mode.BIOME)),
                        openScreen("seedmapper.locateLoot", "options.voxelmap.seedmapper.locateLoot", host, parent -> new GuiSeedMapperLocator(parent, GuiSeedMapperLocator.Mode.LOOT)),
                        openScreen("seedmapper.lootViewer", "options.voxelmap.seedmapper.lootViewer", host, GuiSeedMapperLootViewer::new),
                        openScreen("seedmapper.savedSeeds", "options.voxelmap.seedmapper.savedSeeds", host, GuiSeedMapperSavedSeeds::new),
                        openScreen("seedmapper.cracking", "options.voxelmap.seedmapper.cracking", host, GuiSeedMapperCrackingMods::new)),
                group("options.voxelmap.group.seedmapperEsp",
                        SettingsOption.text("seedmapper.espTarget", "options.voxelmap.seedmapper.espTarget", null,
                                () -> seed.espTarget == null ? "" : seed.espTarget,
                                value -> {
                                    seed.espTarget = value.trim();
                                    MapSettingsManager.instance.saveAll();
                                },
                                () -> true, Component::empty)
                                .withAutocomplete(SeedMapperCommandTree.getCommonOreBlocks()),
                        SettingsOption.slider("seedmapper.espChunks", "options.voxelmap.seedmapper.espChunks", null,
                                () -> (double) seed.espDefaultChunks,
                                value -> {
                                    seed.espDefaultChunks = (int) Math.round(value);
                                    MapSettingsManager.instance.saveAll();
                                },
                                0, 8, 1, value -> Component.literal(String.valueOf((int) Math.round(value))), () -> true, Component::empty, 0),
                        seedToggleSimple("seedmapper.espFill", "options.voxelmap.seedmapper.espFill",
                                () -> seed.getEspStyle(SeedMapperEspTarget.BLOCK_HIGHLIGHT).fillEnabled,
                                value -> seed.getEspStyle(SeedMapperEspTarget.BLOCK_HIGHLIGHT).fillEnabled = value),
                        espAction("seedmapper.runEsp", "options.voxelmap.seedmapper.runEsp", host, () -> {
                            String target = seed.espTarget == null ? "" : seed.espTarget.trim();
                            if (target.isBlank()) {
                                SeedMapperCommandHandler.handleChatCommand("seedmap highlight clear");
                            } else {
                                SeedMapperCommandHandler.handleChatCommand("seedmap highlight ore " + target + " " + seed.espDefaultChunks);
                            }
                        }),
                        espAction("seedmapper.runOreVein", "options.voxelmap.seedmapper.runOreVein", host,
                                () -> SeedMapperCommandHandler.handleChatCommand("seedmap highlight orevein " + seed.espDefaultChunks)),
                        espAction("seedmapper.runCanyon", "options.voxelmap.seedmapper.runCanyon", host,
                                () -> SeedMapperCommandHandler.handleChatCommand("seedmap highlight canyon " + seed.espDefaultChunks)),
                        espAction("seedmapper.runCave", "options.voxelmap.seedmapper.runCave", host,
                                () -> SeedMapperCommandHandler.handleChatCommand("seedmap highlight cave " + seed.espDefaultChunks)),
                        espAction("seedmapper.runTerrain", "options.voxelmap.seedmapper.runTerrain", host,
                                () -> SeedMapperCommandHandler.handleChatCommand("seedmap highlight terrain " + seed.espDefaultChunks)),
                        espAction("seedmapper.clearEsp", "options.voxelmap.seedmapper.clearEsp", host,
                                () -> SeedMapperCommandHandler.handleChatCommand("seedmap highlight clear")),
                        openScreen("seedmapper.espSettings", "options.voxelmap.seedmapper.espSettings", host, GuiSeedMapperEspProfiles::new)),
                group("options.voxelmap.group.seedmapperDatapack",
                        SettingsOption.text("seedmapper.datapackUrl", "options.voxelmap.seedmapper.datapackUrl", null,
                                () -> seed.datapackUrl == null ? "" : seed.datapackUrl,
                                value -> {
                                    seed.datapackUrl = value.trim();
                                    MapSettingsManager.instance.saveAll();
                                },
                                () -> true, Component::empty),
                        seedToggleSimple("seedmapper.datapackEnabled", "options.voxelmap.seedmapper.datapackEnabled",
                                () -> seed.datapackEnabled,
                                value -> {
                                    seed.datapackEnabled = value;
                                    seed.setFeatureEnabled(SeedMapperFeature.DATAPACK_STRUCTURE, value);
                                }),
                        openScreen("seedmapper.datapackSettings", "options.voxelmap.seedmapper.datapackSettings", host, GuiSeedMapperDatapackOptions::new),
                        SettingsOption.action("seedmapper.datapackImport", "options.voxelmap.seedmapper.datapackImport", null,
                                Component.translatable("options.voxelmap.action.run"),
                                () -> {
                                    commitText(host);
                                    try {
                                        SeedMapperDatapackManager.ImportResult result = SeedMapperDatapackManager.importFromUrl(seed.datapackUrl);
                                        seed.datapackEnabled = true;
                                        seed.setFeatureEnabled(SeedMapperFeature.DATAPACK_STRUCTURE, true);
                                        seed.datapackCachePath = result.datapackRoot().toAbsolutePath().toString();
                                        seed.clearDisabledDatapackStructures(currentWorldKey());
                                        seed.putDatapackSavedUrl(seed.getCurrentServerKey(), seed.datapackUrl);
                                        seed.putDatapackSavedCachePath(seed.getCurrentServerKey(), seed.datapackCachePath);
                                        MessageUtils.chatInfo("Imported " + result.structureIds().size() + " datapack structures.");
                                    } catch (Exception e) {
                                        MessageUtils.chatInfo("Datapack import failed: " + e.getMessage());
                                    }
                                    MapSettingsManager.instance.saveAll();
                                }),
                        SettingsOption.action("seedmapper.exportJson", "options.voxelmap.seedmapper.exportJson", null,
                                Component.translatable("options.voxelmap.action.run"),
                                () -> {
                                    commitText(host);
                                    GuiPersistentMap persistentMap = findPersistentMapScreen(host.get());
                                    if (persistentMap != null) {
                                        persistentMap.exportVisibleSeedMap();
                                    }
                                }))));
    }

    private static SettingsOption<Boolean> persistentToggle(String id, String key, PersistentMapSettingsManager manager, EnumOptionsMinimap option,
            java.util.function.BooleanSupplier enabled, Supplier<Component> reason, int indentation) {
        return SettingsOption.toggle(id, key, tooltip(id), () -> manager.getBooleanValue(option), value -> {
            if (manager.getBooleanValue(option) != value)
                manager.toggleBooleanValue(option);
        }, enabled, reason, indentation);
    }

    private static Component zoomLabel(Double power) {
        return Component.literal(String.format(Locale.ROOT, "%.3gx", Math.pow(2.0, power)));
    }

    private static float zoomPowerToNormalized(double power) {
        int range = PersistentMapSettingsManager.MAX_WORLDMAP_ZOOM_POWER - PersistentMapSettingsManager.MIN_WORLDMAP_ZOOM_POWER;
        return (float) ((power - PersistentMapSettingsManager.MIN_WORLDMAP_ZOOM_POWER + 0.5) / range);
    }

    private static SettingsOption<Boolean> radarToggle(String id, String key, RadarSettingsManager radar, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return SettingsOption.toggle(id, key, null, getter, value -> {
            setter.accept(value);
            radar.markChanged();
        });
    }

    private static SettingsOption<Double> radarPctSlider(String id, String key, RadarSettingsManager radar, Supplier<Double> getter, Consumer<Double> setter) {
        return SettingsOption.slider(id, key, null, getter, value -> {
            setter.accept(value);
            radar.markChanged();
        }, 0, 100, 1, value -> Component.literal(value.intValue() + "%"), () -> true, Component::empty, 0);
    }

    private static SettingsOption<Boolean> enumToggle(String id, ISettingsManager manager, EnumOptionsMinimap option) {
        return SettingsOption.toggle(id, option.getName(), null,
                () -> manager.getBooleanValue(option),
                value -> {
                    if (manager.getBooleanValue(option) != value) {
                        MapSettingsManager.updateBooleanOrListValue(manager, option);
                    }
                });
    }

    private static SettingsOption<Boolean> seedToggleSimple(String id, String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return SettingsOption.toggle(id, key, null, getter, value -> {
            setter.accept(value);
            MapSettingsManager.instance.saveAll();
        });
    }

    private static SettingsOption<Void> openScreen(String id, String key, Supplier<Screen> host, java.util.function.Function<Screen, Screen> factory) {
        return SettingsOption.action(id, key, null, Component.translatable("options.voxelmap.action.open"), () -> {
            commitText(host);
            VoxelConstants.getMinecraft().gui.setScreen(factory.apply(host.get()));
        });
    }

    private static SettingsOption<Void> espAction(String id, String key, Supplier<Screen> host, Runnable action) {
        return SettingsOption.action(id, key, null, Component.translatable("options.voxelmap.action.run"), () -> {
            commitText(host);
            action.run();
        });
    }

    private static void confirmAction(Supplier<Screen> host, String messageKey, Runnable action) {
        Screen previous = host.get();
        VoxelConstants.getMinecraft().gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                action.run();
            }
            VoxelConstants.getMinecraft().gui.setScreen(previous);
        }, Component.translatable("options.voxelmap.confirm.title"), Component.translatable(messageKey)));
    }

    private static void commitText(Supplier<Screen> host) {
        if (host.get() instanceof com.mamiyaotaru.voxelmap.gui.GuiMinimapOptions options) {
            options.commitPendingText();
        }
    }

    private static GuiPersistentMap findPersistentMapScreen(Screen start) {
        Screen current = start;
        while (current != null) {
            if (current instanceof GuiPersistentMap persistentMap) {
                return persistentMap;
            }
            current = current instanceof GuiScreenMinimap minimapScreen ? minimapScreen.getLastScreen() : null;
        }
        return null;
    }

    private static String selectedVersionId(SeedMapperSettingsManager seed) {
        for (SeedMapperCompat.MinecraftVersion version : SeedMapperCompat.getSupportedVersions()) {
            if (version.id().equalsIgnoreCase(seed.minecraftVersion)) {
                return version.id();
            }
        }
        return SeedMapperCompat.AUTO_VERSION;
    }

    private static List<SettingsOption.Choice<String>> versionChoices() {
        List<SettingsOption.Choice<String>> choices = new ArrayList<>();
        choices.add(new SettingsOption.Choice<>(SeedMapperCompat.AUTO_VERSION, Component.literal("Auto")));
        for (SeedMapperCompat.MinecraftVersion version : SeedMapperCompat.getSupportedVersions()) {
            choices.add(new SettingsOption.Choice<>(version.id(), Component.literal(version.label())));
        }
        return choices;
    }

    private static String currentWorldKey() {
        String world = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentWorldName();
        String sub = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false);
        var level = com.mamiyaotaru.voxelmap.util.GameVariableAccessShim.getWorld();
        String dim = level == null ? "unknown" : level.dimension().identifier().toString();
        return (world == null ? "unknown" : world) + "|" + (sub == null ? "" : sub) + "|" + dim;
    }

    private static boolean radarAvailable(RadarSettingsManager radar) {
        return radar.radarAllowed && (radar.radarPlayersAllowed || radar.radarMobsAllowed);
    }

    private static boolean radarEnabled(RadarSettingsManager radar) {
        return radarAvailable(radar) && radar.showRadar;
    }

    private static SettingsOption<Boolean> dependentRadarToggle(String id, String key, RadarSettingsManager radar, java.util.function.Supplier<Boolean> getter,
            Consumer<Boolean> setter, java.util.function.BooleanSupplier extra) {
        return toggle(id, key, radar, getter, setter, () -> radarEnabled(radar) && extra.getAsBoolean(), requires("options.voxelmap.requires.radar"), 1);
    }

    private static SettingsOption<Boolean> toggle(String id, String key, MapSettingsManager manager, java.util.function.Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return toggle(id, key, manager, getter, setter, () -> true, Component::empty, 0);
    }

    private static SettingsOption<Boolean> toggle(String id, String key, MapSettingsManager manager, java.util.function.Supplier<Boolean> getter,
            Consumer<Boolean> setter, java.util.function.BooleanSupplier enabled, java.util.function.Supplier<Component> reason, int indentation) {
        return SettingsOption.toggle(id, key, tooltip(id), getter, value -> changed(manager, setter, value), enabled, reason, indentation);
    }

    private static SettingsOption<Boolean> toggle(String id, String key, RadarSettingsManager manager, java.util.function.Supplier<Boolean> getter,
            Consumer<Boolean> setter, java.util.function.BooleanSupplier enabled, java.util.function.Supplier<Component> reason, int indentation) {
        return SettingsOption.toggle(id, key, tooltip(id), getter, value -> {
            setter.accept(value);
            manager.markChanged();
        }, enabled, reason, indentation);
    }

    private static SettingsOption<Boolean> toggle(String id, String key, RadarSettingsManager manager, java.util.function.Supplier<Boolean> getter,
            Consumer<Boolean> setter) {
        return toggle(id, key, manager, getter, setter, () -> true, Component::empty, 0);
    }

    @SafeVarargs
    private static <T> SettingsOption<T> choice(String id, String key, MapSettingsManager manager, java.util.function.Supplier<T> getter,
            Consumer<T> setter, SettingsOption.Choice<T>... values) {
        return SettingsOption.choice(id, key, tooltip(id), getter, value -> changed(manager, setter, value), List.of(values));
    }

    @SafeVarargs
    private static <T> SettingsOption<T> choice(String id, String key, RadarSettingsManager manager, java.util.function.Supplier<T> getter,
            Consumer<T> setter, SettingsOption.Choice<T>... values) {
        return SettingsOption.choice(id, key, tooltip(id), getter, value -> {
            setter.accept(value);
            manager.markChanged();
        }, List.of(values),
                () -> radarEnabled(manager), requires("options.voxelmap.requires.radar"), 1);
    }

    private static SettingsOption<Double> slider(String id, String key, MapSettingsManager manager, java.util.function.Supplier<Double> getter,
            Consumer<Double> setter, double min, double max, double step, java.util.function.Function<Double, Component> formatter,
            java.util.function.BooleanSupplier enabled, java.util.function.Supplier<Component> reason, int indentation) {
        return SettingsOption.slider(id, key, tooltip(id), getter, value -> changed(manager, setter, value), min, max, step, formatter, enabled, reason, indentation);
    }

    private static SettingsGroup group(String titleKey, SettingsOption<?>... options) {
        return new SettingsGroup(titleKey, List.of(options));
    }

    private static <T> SettingsOption.Choice<T> value(T value, String key) {
        return new SettingsOption.Choice<>(value, Component.translatable(key));
    }

    private static String tooltip(String id) {
        return "options.voxelmap." + id + ".tooltip";
    }

    private static java.util.function.Supplier<Component> requires(String key) {
        return () -> Component.translatable(key);
    }

    private static java.util.function.Supplier<Component> serverDisabled() {
        return requires("options.voxelmap.managed.serverDisabled");
    }

    private static <T> void changed(MapSettingsManager manager, Consumer<T> setter, T value) {
        setter.accept(value);
        manager.markChanged();
    }
}
