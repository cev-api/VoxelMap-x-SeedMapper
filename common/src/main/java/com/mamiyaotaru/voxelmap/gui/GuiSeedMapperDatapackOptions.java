package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperDatapackManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Random;

public class GuiSeedMapperDatapackOptions extends GuiScreenMinimap {
    private static final List<String> COLOR_SCHEMES = List.of("Palette Random", "Highly Saturated", "Vibrant");
    private static final List<String> ICON_STYLES = List.of("Default", "Large", "Potion");
    private static final int[] COLOR_COUNTS = {32, 64, 128, 256};

    private final SeedMapperSettingsManager settings;

    private Button autoloadButton;
    private Button colorSchemeButton;
    private Button iconStyleButton;
    private Button randomColorsButton;
    private Button regenerateColorsButton;
    private Button savedUrlsButton;
    private Button savedCachePathsButton;
    private Button structureDisabledButton;

    public GuiSeedMapperDatapackOptions(Screen parent) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
    }

    @Override
    public void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = this.height / 6 + 8;

        autoloadButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.datapackAutoload = !settings.datapackAutoload;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(left, y, 310, 20).build());

        y += 24;
        colorSchemeButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.datapackColorScheme = settings.datapackColorScheme >= COLOR_SCHEMES.size() ? 1 : settings.datapackColorScheme + 1;
            refreshLabels();
            MapSettingsManager.instance.saveAll();
        }).bounds(left, y, 150, 20).build());

        iconStyleButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.datapackIconStyle = settings.datapackIconStyle >= ICON_STYLES.size() ? 1 : settings.datapackIconStyle + 1;
            refreshLabels();
            MapSettingsManager.instance.saveAll();
        }).bounds(right, y, 150, 20).build());

        y += 24;
        randomColorsButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            cycleRandomColorCount();
            settings.datapackRandomColorCycle = 0;
            settings.datapackColorScheme = 1;
            refreshLabels();
            MapSettingsManager.instance.saveAll();
        }).bounds(left, y, 150, 20).build());

        regenerateColorsButton = addRenderableWidget(new Button.Builder(Component.literal("Regenerate Colors"), button -> {
            if (settings.getDatapackRandomColors().isEmpty()) {
                regenerateColors(COLOR_COUNTS[0]);
                settings.datapackRandomColorCycle = 0;
            } else {
                settings.cycleDatapackRandomColors();
            }
            settings.datapackColorScheme = 1;
            refreshLabels();
            MapSettingsManager.instance.saveAll();
        }).bounds(right, y, 150, 20).build());

        y += 24;
        savedUrlsButton = addRenderableWidget(new Button.Builder(Component.literal("Saved URLs"), button ->
                minecraft.setScreen(new GuiSeedMapperSavedStringMap(this, GuiSeedMapperSavedStringMap.Mode.URLS)))
                .bounds(left, y, 150, 20).build());

        savedCachePathsButton = addRenderableWidget(new Button.Builder(Component.literal("Saved Cache Paths"), button ->
                minecraft.setScreen(new GuiSeedMapperSavedStringMap(this, GuiSeedMapperSavedStringMap.Mode.CACHE_PATHS)))
                .bounds(right, y, 150, 20).build());

        y += 24;
        structureDisabledButton = addRenderableWidget(new Button.Builder(Component.empty(), button ->
                minecraft.setScreen(new GuiSeedMapperDatapackStructures(this)))
                .bounds(left, y, 310, 20).build());

        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());

        refreshLabels();
    }

    private void refreshLabels() {
        autoloadButton.setMessage(Component.literal("Autoload: " + toggleText(settings.datapackAutoload)));
        colorSchemeButton.setMessage(Component.literal("Color Scheme: " + COLOR_SCHEMES.get(Math.max(0, Math.min(COLOR_SCHEMES.size() - 1, settings.datapackColorScheme - 1)))));
        iconStyleButton.setMessage(Component.literal("Icon Style: " + ICON_STYLES.get(Math.max(0, Math.min(ICON_STYLES.size() - 1, settings.datapackIconStyle - 1)))));
        randomColorsButton.setMessage(Component.literal("Random Colors: " + settings.getDatapackRandomColors().size()));
        structureDisabledButton.setMessage(Component.literal("Datapack Structures: " + countEnabledStructures() + " shown"));
        savedUrlsButton.setMessage(Component.literal("Saved URLs"));
        savedCachePathsButton.setMessage(Component.literal("Saved Cache Paths"));
    }

    private int countEnabledStructures() {
        String worldKey = currentWorldKey();
        long seed = Long.MIN_VALUE;
        try {
            seed = settings.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException ignored) {
        }
        int total = SeedMapperDatapackManager.readImportedStructureIds(settings.datapackCachePath, seed).size();
        return Math.max(0, total - settings.getDisabledDatapackStructures(worldKey).size());
    }

    private void cycleRandomColorCount() {
        int current = settings.getDatapackRandomColors().size();
        int index = 0;
        for (int i = 0; i < COLOR_COUNTS.length; i++) {
            if (COLOR_COUNTS[i] == current) {
                index = i;
                break;
            }
        }
        regenerateColors(COLOR_COUNTS[(index + 1) % COLOR_COUNTS.length]);
    }

    private void regenerateColors(int count) {
        settings.getDatapackRandomColors().clear();
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            int rgb = 0xFF000000 | random.nextInt(0x00FFFFFF);
            settings.getDatapackRandomColors().add(rgb);
        }
    }

    private String toggleText(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private String currentWorldKey() {
        String world = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentWorldName();
        String sub = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false);
        var level = com.mamiyaotaru.voxelmap.util.GameVariableAccessShim.getWorld();
        String dim = level == null ? "unknown" : level.dimension().identifier().toString();
        return (world == null ? "unknown" : world) + "|" + (sub == null ? "" : sub) + "|" + dim;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.getFont(), Component.literal("Datapack Settings"), this.width / 2, 20, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
