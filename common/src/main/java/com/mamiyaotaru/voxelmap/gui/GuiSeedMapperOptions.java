package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiButtonText;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiValueSliderMinimap;
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperDatapackManager;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperEspStyle;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperEspTarget;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperSettingsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GuiSeedMapperOptions extends GuiScreenMinimap {
    private final SeedMapperSettingsManager settings;
    private final MapSettingsManager mapSettings;
    private final Component screenTitle = Component.translatable("options.seedmapper.title");
    private final List<Component> statusMessages = new ArrayList<>();

    private GuiButtonText seedInput;
    private GuiButtonText espTargetInput;
    private GuiValueSliderMinimap worldMapMarkerLimitSlider;
    private GuiValueSliderMinimap espChunksSlider;
    private GuiButtonText datapackUrlInput;

    private Button structureOverlayButton;
    private Button lootOnlyButton;
    private Button locateStructureButton;
    private Button locateBiomeButton;
    private Button locateLootButton;
    private Button lootViewerButton;
    private Button runEspButton;
    private Button runOreVeinButton;
    private Button runCanyonButton;
    private Button runCaveButton;
    private Button runTerrainButton;
    private Button clearEspButton;
    private Button espFillButton;
    private Button espSettingsButton;
    private Button datapackEnabledButton;
    private Button datapackImportButton;
    private Button datapackSettingsButton;
    private Button savedSeedsButton;
    private Button exportVisibleButton;

    public GuiSeedMapperOptions(Screen parent) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        this.mapSettings = VoxelConstants.getVoxelMapInstance().getMapOptions();
    }

    @Override
    public void init() {
        SeedMapperCommandHandler.setStatusSink(this::showStatusMessage);

        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = this.height / 6 - 10;
        int fullWidth = 310;
        int rowGap = 24;
        int sectionGap = 32;

        seedInput = new GuiButtonText(font, left, y, fullWidth, 20,
                Component.literal("Seed Input: " + settings.manualSeed),
                button -> setActiveEditor(seedInput));
        seedInput.setMaxLength(256);
        seedInput.setText(settings.manualSeed);
        addRenderableWidget(seedInput);

        y += rowGap;

        structureOverlayButton = addRenderableWidget(new Button.Builder(toggleLabel("Structure Overlay", settings.enabled), button -> {
            settings.enabled = !settings.enabled;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(left, y, 150, 20).build());

        lootOnlyButton = addRenderableWidget(new Button.Builder(toggleLabel("Lootable Structures Only", settings.showLootableOnly), button -> {
            settings.showLootableOnly = !settings.showLootableOnly;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y += rowGap;

        worldMapMarkerLimitSlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, fullWidth, 20, settings.worldMapMarkerLimit, 200.0D, 20000.0D, value -> {
            settings.worldMapMarkerLimit = Math.max(200, ((int) Math.round(value / 100.0D)) * 100);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "WorldMap Marker Limit: " + (((int) Math.round(value / 100.0D)) * 100)));

        y += rowGap;

        locateStructureButton = addRenderableWidget(new Button.Builder(Component.literal("Locate Structure"), button -> {
            applyTextValues();
            minecraft.setScreen(new GuiSeedMapperLocator(this, GuiSeedMapperLocator.Mode.STRUCTURE));
        }).bounds(left, y, 150, 20).build());

        locateBiomeButton = addRenderableWidget(new Button.Builder(Component.literal("Locate Biome"), button -> {
            applyTextValues();
            minecraft.setScreen(new GuiSeedMapperLocator(this, GuiSeedMapperLocator.Mode.BIOME));
        }).bounds(right, y, 150, 20).build());

        y += rowGap;

        locateLootButton = addRenderableWidget(new Button.Builder(Component.literal("Locate Loot"), button -> {
            applyTextValues();
            minecraft.setScreen(new GuiSeedMapperLocator(this, GuiSeedMapperLocator.Mode.LOOT));
        }).bounds(left, y, 150, 20).build());

        lootViewerButton = addRenderableWidget(new Button.Builder(Component.literal("Loot Viewer"), button -> {
            applyTextValues();
            minecraft.setScreen(new GuiSeedMapperLootViewer(this));
        }).bounds(right, y, 150, 20).build());

        y += sectionGap;

        espTargetInput = new GuiButtonText(font, left, y, fullWidth, 20,
                Component.literal("ESP Target: " + settings.espTarget),
                button -> setActiveEditor(espTargetInput));
        espTargetInput.setMaxLength(128);
        espTargetInput.setText(settings.espTarget);
        addRenderableWidget(espTargetInput);

        y += rowGap;

        runEspButton = addRenderableWidget(new Button.Builder(Component.literal("Run ESP Highlight"), button -> {
            applyTextValues();
            runBlockEspHighlight();
        }).bounds(left, y, 150, 20).build());

        runOreVeinButton = addRenderableWidget(new Button.Builder(Component.literal("Run Ore Vein ESP"), button -> {
            applyTextValues();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight orevein " + settings.espDefaultChunks);
        }).bounds(right, y, 150, 20).build());

        y += rowGap;

        runCanyonButton = addRenderableWidget(new Button.Builder(Component.literal("Run Canyon ESP"), button -> {
            applyTextValues();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight canyon " + settings.espDefaultChunks);
        }).bounds(left, y, 150, 20).build());

        runCaveButton = addRenderableWidget(new Button.Builder(Component.literal("Run Cave ESP"), button -> {
            applyTextValues();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight cave " + settings.espDefaultChunks);
        }).bounds(right, y, 150, 20).build());

        y += rowGap;

        runTerrainButton = addRenderableWidget(new Button.Builder(Component.literal("Run Terrain ESP"), button -> {
            applyTextValues();
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight terrain " + settings.espDefaultChunks);
        }).bounds(left, y, 150, 20).build());

        clearEspButton = addRenderableWidget(new Button.Builder(Component.literal("Clear ESP"), button ->
                SeedMapperCommandHandler.handleChatCommand("seedmap highlight clear"))
                .bounds(right, y, 150, 20).build());

        y += rowGap;

        espChunksSlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 150, 20, settings.espDefaultChunks, 0.0D, 8.0D, value -> {
            settings.espDefaultChunks = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "ESP Chunks: " + (int) Math.round(value)));

        espFillButton = addRenderableWidget(new Button.Builder(Component.literal("ESP Fill: " + toggleText(activeEspStyle().fillEnabled)), button -> {
            activeEspStyle().fillEnabled = !activeEspStyle().fillEnabled;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y += rowGap;

        espSettingsButton = addRenderableWidget(new Button.Builder(Component.literal("ESP Settings"), button ->
                minecraft.setScreen(new GuiSeedMapperEspProfiles(this)))
                .bounds(left, y, fullWidth, 20).build());

        y += sectionGap;

        datapackUrlInput = new GuiButtonText(font, left, y, fullWidth, 20,
                Component.literal("Datapack URL: " + settings.datapackUrl),
                button -> setActiveEditor(datapackUrlInput));
        datapackUrlInput.setMaxLength(4096);
        datapackUrlInput.setText(settings.datapackUrl);
        addRenderableWidget(datapackUrlInput);

        y += rowGap;

        datapackEnabledButton = addRenderableWidget(new Button.Builder(toggleLabel("Datapack Structures", settings.datapackEnabled), button -> {
            settings.datapackEnabled = !settings.datapackEnabled;
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(left, y, 150, 20).build());

        datapackImportButton = addRenderableWidget(new Button.Builder(Component.literal("Import Datapack"), button -> {
            applyTextValues();
            try {
                SeedMapperDatapackManager.ImportResult result = SeedMapperDatapackManager.importFromUrl(settings.datapackUrl);
                settings.datapackEnabled = true;
                settings.datapackCachePath = result.datapackRoot().toAbsolutePath().toString();
                settings.putDatapackSavedUrl(settings.getCurrentServerKey(), settings.datapackUrl);
                settings.putDatapackSavedCachePath(settings.getCurrentServerKey(), settings.datapackCachePath);
                showStatusMessage("Imported " + result.structureIds().size() + " datapack structures.");
            } catch (Exception e) {
                showStatusMessage("Datapack import failed: " + e.getMessage());
            }
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y += rowGap;

        datapackSettingsButton = addRenderableWidget(new Button.Builder(Component.literal("Datapack Settings"), button ->
                minecraft.setScreen(new GuiSeedMapperDatapackOptions(this)))
                .bounds(left, y, fullWidth, 20).build());

        y += sectionGap;

        exportVisibleButton = addRenderableWidget(new Button.Builder(Component.literal("Export JSON"), button -> {
            applyTextValues();
            GuiPersistentMap persistentMap = findPersistentMapScreen();
            if (persistentMap != null) {
                persistentMap.exportVisibleSeedMap();
            }
        }).bounds(left, y, 150, 20).build());
        exportVisibleButton.active = findPersistentMapScreen() != null;

        savedSeedsButton = addRenderableWidget(new Button.Builder(Component.literal("Saved Seeds"), button ->
                minecraft.setScreen(new GuiSeedMapperSavedSeeds(this)))
                .bounds(right, y, 150, 20).build());

        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());

        refreshLabels();
    }

    @Override
    public void onClose() {
        applyTextValues();
        MapSettingsManager.instance.saveAll();
        super.onClose();
    }

    @Override
    public void removed() {
        SeedMapperCommandHandler.setStatusSink(null);
        super.removed();
    }

    private void refreshLabels() {
        structureOverlayButton.setMessage(toggleLabel("Structure Overlay", settings.enabled));
        lootOnlyButton.setMessage(toggleLabel("Lootable Structures Only", settings.showLootableOnly));
        if (seedInput != null) {
            seedInput.setMessage(Component.literal("Seed Input: " + seedInput.getText()));
        }
        if (espTargetInput != null) {
            espTargetInput.setMessage(Component.literal("ESP Target: " + espTargetInput.getText()));
        }
        if (worldMapMarkerLimitSlider != null) {
            worldMapMarkerLimitSlider.setActualValue(settings.worldMapMarkerLimit);
        }
        if (espChunksSlider != null) {
            espChunksSlider.setActualValue(settings.espDefaultChunks);
        }
        if (espFillButton != null) {
            espFillButton.setMessage(Component.literal("ESP Fill: " + toggleText(activeEspStyle().fillEnabled)));
        }
        if (datapackEnabledButton != null) {
            datapackEnabledButton.setMessage(toggleLabel("Datapack Structures", settings.datapackEnabled));
        }
        if (datapackUrlInput != null) {
            datapackUrlInput.setMessage(Component.literal("Datapack URL: " + datapackUrlInput.getText()));
        }
    }

    private Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + toggleText(enabled));
    }

    private String toggleText(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private void applyTextValues() {
        if (seedInput != null) {
            settings.manualSeed = seedInput.getText().trim();
        }
        if (espTargetInput != null) {
            settings.espTarget = espTargetInput.getText().trim();
        }
        if (espChunksSlider != null) {
            settings.espDefaultChunks = (int) Math.round(espChunksSlider.getActualValue());
        }
        if (worldMapMarkerLimitSlider != null) {
            settings.worldMapMarkerLimit = Math.max(200, ((int) Math.round(worldMapMarkerLimitSlider.getActualValue() / 100.0D)) * 100);
        }
        if (datapackUrlInput != null) {
            settings.datapackUrl = datapackUrlInput.getText().trim();
        }
    }

    private void runBlockEspHighlight() {
        String target = settings.espTarget == null ? "" : settings.espTarget.trim();
        if (target.isBlank()) {
            SeedMapperCommandHandler.handleChatCommand("seedmap highlight clear");
            return;
        }
        SeedMapperCommandHandler.handleChatCommand("seedmap highlight ore " + target + " " + settings.espDefaultChunks);
    }

    private SeedMapperEspStyle activeEspStyle() {
        return settings.getEspStyle(SeedMapperEspTarget.BLOCK_HIGHLIGHT);
    }

    private void setActiveEditor(GuiButtonText target) {
        if (seedInput != null) {
            seedInput.setEditing(seedInput == target);
        }
        if (espTargetInput != null) {
            espTargetInput.setEditing(espTargetInput == target);
        }
        if (datapackUrlInput != null) {
            datapackUrlInput.setEditing(datapackUrlInput == target);
        }
    }

    private GuiButtonText getActiveEditor() {
        if (seedInput != null && seedInput.isEditing()) return seedInput;
        if (espTargetInput != null && espTargetInput.isEditing()) return espTargetInput;
        if (datapackUrlInput != null && datapackUrlInput.isEditing()) return datapackUrlInput;
        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            applyTextValues();
            setActiveEditor(null);
            refreshLabels();
            MapSettingsManager.instance.saveAll();
            return true;
        }

        GuiButtonText activeEditor = getActiveEditor();
        if (activeEditor != null) {
            activeEditor.keyPressed(keyEvent);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        GuiButtonText activeEditor = getActiveEditor();
        if (activeEditor != null) {
            return activeEditor.charTyped(characterEvent);
        }
        return super.charTyped(characterEvent);
    }

    private void showStatusMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        statusMessages.add(Component.literal("[SeedMapper] " + text));
        while (statusMessages.size() > 3) {
            statusMessages.remove(0);
        }
    }

    private GuiPersistentMap findPersistentMapScreen() {
        Screen screen = this.lastScreen;
        while (screen != null) {
            if (screen instanceof GuiPersistentMap persistentMap) {
                return persistentMap;
            }
            if (screen instanceof GuiScreenMinimap minimapScreen) {
                screen = minimapScreen.getLastScreen();
            } else {
                break;
            }
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!isEmbeddedInParent()) {
            graphics.centeredText(this.getFont(), this.screenTitle, this.width / 2, 20, 0xFFFFFFFF);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int statusY = this.height - 56;
        for (int i = 0; i < statusMessages.size(); i++) {
            graphics.centeredText(this.getFont(), statusMessages.get(i), this.width / 2, statusY + i * 10, 0xFFE0E0E0);
        }
    }
}
