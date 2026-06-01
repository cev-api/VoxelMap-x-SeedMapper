package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.chunksync.ChunkShareConfig;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSharePlayerSettings;
import com.mamiyaotaru.voxelmap.chunksync.ChunkShareTransport;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommandHandler;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiButtonText;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiValueSliderMinimap;
import com.mamiyaotaru.voxelmap.persistent.PersistentMapSettingsManager;
import com.mamiyaotaru.voxelmap.util.AppChatMessages;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class GuiRadarChunkOverlays extends GuiScreenMinimap {
    private enum ColorTarget {
        EXPLORED,
        NEW,
        OLD,
        BLOCK
    }

    private final RadarSettingsManager settings;
    private final MapSettingsManager mapSettings;
    private final PersistentMapSettingsManager worldMapSettings;
    private GuiButtonText exploredColorInput;
    private GuiButtonText newColorInput;
    private GuiButtonText oldColorInput;
    private GuiButtonText blockColorInput;
    private Button exploredColorPickerButton;
    private Button newColorPickerButton;
    private Button oldColorPickerButton;
    private Button blockColorPickerButton;
    private Button exploredToggle;
    private Button newerToggle;
    private Button chunkGridToggle;
    private Button slimeChunksToggle;
    private Button worldMapNewOldToggle;
    private Button liquidToggle;
    private Button blockToggle;
    private Button detectModeButton;
    private GuiValueSliderMinimap exploredOpacitySlider;
    private GuiValueSliderMinimap newOpacitySlider;
    private GuiValueSliderMinimap oldOpacitySlider;
    private GuiValueSliderMinimap blockOpacitySlider;
    private GuiValueSliderMinimap windowRadiusSlider;
    private GuiValueSliderMinimap windowRefreshDistanceSlider;
    private GuiColorPickerContainer colorPicker;
    private Button colorPickerModeButton;
    private Button colorPickerApplyButton;
    private Button colorPickerCancelButton;
    private Button clearExploredButton;
    private Button clearNewerNewChunksButton;
    private long clearExploredConfirmUntilMs;
    private long clearNewChunksConfirmUntilMs;
    private ColorTarget activeColorTarget;
    private boolean swallowNextMouseRelease;
    private boolean syncPage;
    private Button overlayPageButton;
    private Button syncPageButton;
    private Button hostButton;
    private EditBox keyInput;
    private EditBox shareToInput;
    private EditBox getCodeInput;
    private EditBox getAsInput;
    private EditBox transferNameInput;
    private EditBox importAsInput;
    private GuiChunkSyncPlayerList playerLayerList;
    private int syncPlayerListHeight;
    private int syncPlayerListLeft;
    private int syncPlayerListWidth;
    private boolean publicShareWarningOpen;
    private Button publicShareWarningToggle;
    private Button publicShareWarningContinue;
    private Button publicShareWarningCancel;
    private final List<Component> syncStatusMessages = new java.util.ArrayList<>();

    public GuiRadarChunkOverlays(Screen parent) {
        this.lastScreen = parent;
        this.settings = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        this.mapSettings = VoxelConstants.getVoxelMapInstance().getMapOptions();
        this.worldMapSettings = VoxelConstants.getVoxelMapInstance().getPersistentMapOptions();
    }

    @Override
    public void init() {
        overlayPageButton = addRenderableWidget(new Button.Builder(Component.literal("Overlay Options"), button -> switchPage(false))
                .bounds(this.width / 2 - 155, 58, 150, 20).build());
        syncPageButton = addRenderableWidget(new Button.Builder(Component.literal("Chunk Sync"), button -> switchPage(true))
                .bounds(this.width / 2 + 5, 58, 150, 20).build());
        overlayPageButton.active = syncPage;
        syncPageButton.active = !syncPage;
        if (syncPage) {
            initSyncPage();
            return;
        }
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = 106;

        exploredToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.showExploredChunks = !settings.showExploredChunks;
            refreshLabels();
        }).bounds(left, y, 150, 20).build());
        newerToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.showNewerNewChunks = !settings.showNewerNewChunks;
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y = 130;
        chunkGridToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            mapSettings.toggleBooleanValue(EnumOptionsMinimap.CHUNK_GRID);
            refreshLabels();
        }).bounds(left, y, 150, 20).build());
        slimeChunksToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            mapSettings.toggleBooleanValue(EnumOptionsMinimap.SLIME_CHUNKS);
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y = 154;
        worldMapNewOldToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            worldMapSettings.toggleBooleanValue(EnumOptionsMinimap.WORLDMAP_SHOW_NEW_OLD_CHUNKS);
            worldMapSettings.saveAll();
            refreshLabels();
        }).bounds(left, y, 310, 20).build());

        y = 196;
        liquidToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.newerNewChunksLiquidExploit = !settings.newerNewChunksLiquidExploit;
            refreshLabels();
        }).bounds(left, y, 150, 20).build());
        blockToggle = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.newerNewChunksBlockUpdateExploit = !settings.newerNewChunksBlockUpdateExploit;
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y = 220;
        clearExploredButton = addRenderableWidget(new Button.Builder(Component.literal("Clear Explored Chunks"), button -> {
            long now = System.currentTimeMillis();
            if (now > clearExploredConfirmUntilMs) {
                clearExploredConfirmUntilMs = now + 4000L;
                refreshLabels();
                return;
            }
            clearExploredConfirmUntilMs = 0L;
            VoxelConstants.getVoxelMapInstance().getExploredChunksManager().clearCurrentWorld();
            refreshLabels();
        }).bounds(left, y, 150, 20).build());
        clearNewerNewChunksButton = addRenderableWidget(new Button.Builder(Component.literal("Clear New Chunks"), button -> {
            long now = System.currentTimeMillis();
            if (now > clearNewChunksConfirmUntilMs) {
                clearNewChunksConfirmUntilMs = now + 4000L;
                refreshLabels();
                return;
            }
            clearNewChunksConfirmUntilMs = 0L;
            VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().clearCurrentWorldData();
            refreshLabels();
        }).bounds(right, y, 150, 20).build());

        y = 244;
        detectModeButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            settings.newerNewChunksDetectMode = settings.newerNewChunksDetectMode >= 2 ? 0 : settings.newerNewChunksDetectMode + 1;
            refreshLabels();
        }).bounds(left, y, 310, 20).build());

        y = 286;
        exploredColorInput = new GuiButtonText(font, left, y, 118, 20, Component.literal("Explored Color"), button -> setActiveEditor(exploredColorInput));
        exploredColorInput.setText(settings.exploredChunksColor);
        addRenderableWidget(exploredColorInput);
        exploredColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.EXPLORED))
                .bounds(left + 122, y, 28, 20).build());
        newColorInput = new GuiButtonText(font, right, y, 118, 20, Component.literal("New Chunk Color"), button -> setActiveEditor(newColorInput));
        newColorInput.setText(settings.newerNewChunksNewColor);
        addRenderableWidget(newColorInput);
        newColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.NEW))
                .bounds(right + 122, y, 28, 20).build());

        y = 310;
        oldColorInput = new GuiButtonText(font, left, y, 118, 20, Component.literal("Old Chunk Color"), button -> setActiveEditor(oldColorInput));
        oldColorInput.setText(settings.newerNewChunksOldColor);
        addRenderableWidget(oldColorInput);
        oldColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.OLD))
                .bounds(left + 122, y, 28, 20).build());
        blockColorInput = new GuiButtonText(font, right, y, 118, 20, Component.literal("Block Update Color"), button -> setActiveEditor(blockColorInput));
        blockColorInput.setText(settings.newerNewChunksBlockColor);
        addRenderableWidget(blockColorInput);
        blockColorPickerButton = addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openColorPicker(ColorTarget.BLOCK))
                .bounds(right + 122, y, 28, 20).build());

        y = 334;
        exploredOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 150, 20, settings.exploredChunksOpacity, 0.0D, 100.0D, value -> {
            settings.exploredChunksOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Explored Opacity: " + (int) Math.round(value) + "%"));
        newOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(right, y, 150, 20, settings.newerNewChunksNewOpacity, 0.0D, 100.0D, value -> {
            settings.newerNewChunksNewOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "New Opacity: " + (int) Math.round(value) + "%"));

        y = 358;
        oldOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 150, 20, settings.newerNewChunksOldOpacity, 0.0D, 100.0D, value -> {
            settings.newerNewChunksOldOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Old Opacity: " + (int) Math.round(value) + "%"));
        blockOpacitySlider = addRenderableWidget(new GuiValueSliderMinimap(right, y, 150, 20, settings.newerNewChunksBlockOpacity, 0.0D, 100.0D, value -> {
            settings.newerNewChunksBlockOpacity = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Block Opacity: " + (int) Math.round(value) + "%"));

        y = 400;
        windowRadiusSlider = addRenderableWidget(new GuiValueSliderMinimap(left, y, 150, 20, settings.newerNewChunksWindowRadiusChunks, 16.0D, 256.0D, value -> {
            settings.newerNewChunksWindowRadiusChunks = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().reloadWindowNow();
            refreshLabels();
        }, value -> "Window Radius: " + (int) Math.round(value) + " chunks"));
        windowRefreshDistanceSlider = addRenderableWidget(new GuiValueSliderMinimap(right, y, 150, 20, settings.newerNewChunksRefreshDistanceChunks, 8.0D, 128.0D, value -> {
            settings.newerNewChunksRefreshDistanceChunks = (int) Math.round(value);
            MapSettingsManager.instance.saveAll();
            refreshLabels();
        }, value -> "Refresh Distance: " + (int) Math.round(value) + " chunks"));

        y = 430;
        addRenderableWidget(new Button.Builder(Component.literal("Save"), button -> saveValues()).bounds(this.width / 2 - 75, y, 150, 20).build());
        y += 24;
        addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), button -> onClose()).bounds(this.width / 2 - 75, y, 150, 20).build());

        boolean simpleMode = mapSettings.colorPickerMode == 0;
        colorPicker = new GuiColorPickerContainer(this.width / 2, this.height / 2, 200, 140, simpleMode, picker -> {});
        colorPickerModeButton = new Button.Builder(Component.literal(mapSettings.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleColorPickerMode)
                .bounds(0, 0, 66, 16)
                .build();
        colorPickerApplyButton = new Button.Builder(Component.translatable("gui.done"), button -> applyColorPickerSelection())
                .bounds(0, 0, 66, 18)
                .build();
        colorPickerCancelButton = new Button.Builder(Component.translatable("gui.cancel"), button -> cancelColorPickerSelection())
                .bounds(0, 0, 66, 18)
                .build();
        refreshLabels();
    }

    private void switchPage(boolean syncPage) {
        if (this.syncPage == syncPage) {
            return;
        }
        ChunkSyncCommandHandler.setStatusSink(null);
        this.syncPage = syncPage;
        this.clearWidgets();
        this.init();
    }

    private void initSyncPage() {
        ChunkSyncCommandHandler.setStatusSink(this::showSyncStatusMessage);
        int left = this.width / 2 - 260;
        int fullWidth = 520;
        int fieldWidth = 330;
        int actionX = left + 340;
        int actionWidth = 180;

        keyInput = addSyncInput(left, 118, fieldWidth, "Passphrase",
                ChunkShareConfig.getPassphrase() == null ? "" : ChunkShareConfig.getPassphrase());
        addRenderableWidget(new Button.Builder(Component.literal("Set Passphrase"), button -> run("key " + keyInput.getValue()))
                .bounds(actionX, 118, actionWidth, 20).build());
        hostButton = addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            ChunkShareTransport.Host next = ChunkShareConfig.getHost() == ChunkShareTransport.Host.LITTERBOX
                    ? ChunkShareTransport.Host.FILE_IO : ChunkShareTransport.Host.LITTERBOX;
            run("host " + next.id);
            refreshSyncLabels();
        }).bounds(left, 142, fullWidth, 20).build());

        shareToInput = addSyncInput(left, 190, 220, "Player name", "");
        addRenderableWidget(new Button.Builder(Component.literal("Share To Player"), button -> run("share to " + shareToInput.getValue()))
                .bounds(left + 230, 190, 140, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Share Publicly"), button -> sharePublicly())
                .bounds(left + 380, 190, 140, 20).build());

        getCodeInput = addSyncInput(left, 248, fieldWidth, "Share code or URL", "");
        addRenderableWidget(new Button.Builder(Component.literal("Get + Merge"), button -> run("get " + getCodeInput.getValue()))
                .bounds(actionX, 248, actionWidth, 20).build());
        getAsInput = addSyncInput(left, 284, fieldWidth, "Layer name", "");
        addRenderableWidget(new Button.Builder(Component.literal("Get As Player Layer"), button ->
                run("get " + getCodeInput.getValue() + " as " + getAsInput.getValue()))
                .bounds(actionX, 284, actionWidth, 20).build());

        transferNameInput = addSyncInput(left, 342, 230, "Folder or .zip name", "");
        importAsInput = addSyncInput(left + 240, 342, 150, "Layer name (optional)", "");
        addRenderableWidget(new Button.Builder(Component.literal("Export"), button -> runOptional("export", transferNameInput.getValue()))
                .bounds(left + 400, 342, 56, 20).build());
        addRenderableWidget(new Button.Builder(Component.literal("Import"), button -> {
            String suffix = optional(transferNameInput.getValue());
            if (!importAsInput.getValue().isBlank()) suffix += " as " + importAsInput.getValue().trim();
            run("import" + suffix);
        }).bounds(left + 464, 342, 56, 20).build());

        syncPlayerListLeft = left;
        syncPlayerListWidth = fullWidth;
        syncPlayerListHeight = Math.max(44, Math.min(88, this.height - 480));
        playerLayerList = addRenderableWidget(new GuiChunkSyncPlayerList(this, syncPlayerListLeft, 412, syncPlayerListWidth, syncPlayerListHeight));
        initColorPicker();
        initPublicShareWarning();
        refreshSyncLabels();
    }

    private EditBox addSyncInput(int x, int y, int width, String label, String value) {
        EditBox input = new EditBox(font, x, y, width, 20, Component.literal(label));
        input.setMaxLength(1024);
        input.setValue(value);
        addRenderableWidget(input);
        return input;
    }

    private void initColorPicker() {
        boolean simpleMode = mapSettings.colorPickerMode == 0;
        colorPicker = new GuiColorPickerContainer(this.width / 2, this.height / 2, 200, 140, simpleMode, picker -> {});
        colorPickerModeButton = new Button.Builder(Component.literal(mapSettings.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleColorPickerMode)
                .bounds(0, 0, 66, 16)
                .build();
        colorPickerApplyButton = new Button.Builder(Component.translatable("gui.done"), button -> applyColorPickerSelection())
                .bounds(0, 0, 66, 18)
                .build();
        colorPickerCancelButton = new Button.Builder(Component.translatable("gui.cancel"), button -> cancelColorPickerSelection())
                .bounds(0, 0, 66, 18)
                .build();
    }

    private void initPublicShareWarning() {
        publicShareWarningToggle = new Button.Builder(Component.empty(), button -> {
            ChunkShareConfig.setPublicShareWarningHidden(!ChunkShareConfig.isPublicShareWarningHidden());
            refreshPublicShareWarningLabel();
        }).bounds(0, 0, 220, 20).build();
        publicShareWarningContinue = new Button.Builder(Component.translatable("chunksync.publicShareWarning.continue"), button -> {
            publicShareWarningOpen = false;
            run("share");
        }).bounds(0, 0, 100, 20).build();
        publicShareWarningCancel = new Button.Builder(Component.translatable("gui.cancel"), button -> publicShareWarningOpen = false)
                .bounds(0, 0, 100, 20).build();
        refreshPublicShareWarningLabel();
    }

    private void sharePublicly() {
        if (ChunkShareConfig.isPublicShareWarningHidden()) {
            run("share");
            return;
        }
        publicShareWarningOpen = true;
    }

    private void refreshPublicShareWarningLabel() {
        if (publicShareWarningToggle != null) {
            publicShareWarningToggle.setMessage(Component.translatable("chunksync.publicShareWarning.hide",
                    ChunkShareConfig.isPublicShareWarningHidden() ? "ON" : "OFF"));
        }
    }

    private void runOptional(String command, String argument) {
        run(command + optional(argument));
    }

    private String optional(String argument) {
        return argument == null || argument.isBlank() ? "" : " " + argument.trim();
    }

    private void run(String arguments) {
        ChunkSyncCommandHandler.runFromGui(arguments);
    }

    void refreshPlayerLayerList() {
        if (playerLayerList != null) {
            playerLayerList.rebuildEntries();
        }
    }

    void removePlayerLayer(String slug) {
        run("remove " + slug);
        refreshPlayerLayerList();
    }

    void setPlayerLayerColor(String slug, String colorText) {
        ChunkSharePlayerSettings.setColor(slug, parseColor(colorText, ChunkSharePlayerSettings.get(slug).rgb()));
        refreshPlayerLayerList();
    }

    void openPlayerLayerColorPicker(String slug) {
        playerLayerList.setActiveColorSlug(slug);
        colorPicker.setColor(ChunkSharePlayerSettings.get(slug).rgb());
        swallowNextMouseRelease = true;
    }

    private void refreshSyncLabels() {
        if (hostButton != null) {
            hostButton.setMessage(Component.literal("Upload Host: " + ChunkShareConfig.getHost().id));
        }
    }

    private void showSyncStatusMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        syncStatusMessages.add(AppChatMessages.prefixed("ChunkSync", text));
        while (syncStatusMessages.size() > 3) {
            syncStatusMessages.remove(0);
        }
        refreshPlayerLayerList();
    }

    @Override
    public void removed() {
        ChunkSyncCommandHandler.setStatusSink(null);
        super.removed();
    }

    private void refreshLabels() {
        long now = System.currentTimeMillis();
        if (clearExploredConfirmUntilMs > 0L && now > clearExploredConfirmUntilMs) {
            clearExploredConfirmUntilMs = 0L;
        }
        if (clearNewChunksConfirmUntilMs > 0L && now > clearNewChunksConfirmUntilMs) {
            clearNewChunksConfirmUntilMs = 0L;
        }
        exploredToggle.setMessage(Component.literal("Explored Chunks: " + (settings.showExploredChunks ? "ON" : "OFF")));
        newerToggle.setMessage(Component.literal("New Chunk Detector: " + (settings.showNewerNewChunks ? "ON" : "OFF")));
        chunkGridToggle.setMessage(Component.literal("Chunk Grid: " + (mapSettings.chunkGrid ? "ON" : "OFF")));
        slimeChunksToggle.setMessage(Component.literal("Slime Chunks: " + (mapSettings.slimeChunks ? "ON" : "OFF")));
        slimeChunksToggle.active = minecraft.hasSingleplayerServer() || !VoxelConstants.getVoxelMapInstance().getWorldSeed().isEmpty();
        worldMapNewOldToggle.setMessage(Component.literal("World Map New/Old Chunk Overlay: "
                + (worldMapSettings.getBooleanValue(EnumOptionsMinimap.WORLDMAP_SHOW_NEW_OLD_CHUNKS) ? "ON" : "OFF")));
        liquidToggle.setMessage(Component.literal("Liquid Exploit: " + (settings.newerNewChunksLiquidExploit ? "ON" : "OFF")));
        blockToggle.setMessage(Component.literal("Block Update Exploit: " + (settings.newerNewChunksBlockUpdateExploit ? "ON" : "OFF")));
        detectModeButton.setMessage(Component.literal("Detect Mode: " + switch (settings.newerNewChunksDetectMode) {
            case 1 -> "Ignore Block Exploit";
            case 2 -> "Block Exploit Mode";
            default -> "Normal";
        }));
        exploredOpacitySlider.setActualValue(settings.exploredChunksOpacity);
        newOpacitySlider.setActualValue(settings.newerNewChunksNewOpacity);
        oldOpacitySlider.setActualValue(settings.newerNewChunksOldOpacity);
        blockOpacitySlider.setActualValue(settings.newerNewChunksBlockOpacity);
        if (windowRadiusSlider != null) windowRadiusSlider.setActualValue(settings.newerNewChunksWindowRadiusChunks);
        if (windowRefreshDistanceSlider != null) windowRefreshDistanceSlider.setActualValue(settings.newerNewChunksRefreshDistanceChunks);
        if (clearExploredButton != null) {
            clearExploredButton.setMessage(Component.literal(clearExploredConfirmUntilMs > 0L
                    ? "Confirm Clear Explored"
                    : "Clear Explored Chunks"));
            clearExploredButton.active = settings.showExploredChunks;
        }
        if (clearNewerNewChunksButton != null) {
            clearNewerNewChunksButton.setMessage(Component.literal(clearNewChunksConfirmUntilMs > 0L
                    ? "Confirm Clear New"
                    : "Clear New Chunks"));
            clearNewerNewChunksButton.active = settings.showNewerNewChunks;
        }
    }

    private void saveValues() {
        settings.exploredChunksColor = sanitize(exploredColorInput.getText(), settings.exploredChunksColor);
        settings.newerNewChunksNewColor = sanitize(newColorInput.getText(), settings.newerNewChunksNewColor);
        settings.newerNewChunksOldColor = sanitize(oldColorInput.getText(), settings.newerNewChunksOldColor);
        settings.newerNewChunksBlockColor = sanitize(blockColorInput.getText(), settings.newerNewChunksBlockColor);
        MapSettingsManager.instance.saveAll();
        refreshLabels();
    }

    private String sanitize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        return normalized.matches("#[0-9a-fA-F]{6}") ? normalized.toUpperCase() : fallback;
    }

    private void setActiveEditor(GuiButtonText target) {
        if (exploredColorInput != null) exploredColorInput.setEditing(exploredColorInput == target);
        if (newColorInput != null) newColorInput.setEditing(newColorInput == target);
        if (oldColorInput != null) oldColorInput.setEditing(oldColorInput == target);
        if (blockColorInput != null) blockColorInput.setEditing(blockColorInput == target);
    }

    private GuiButtonText getActiveEditor() {
        if (exploredColorInput != null && exploredColorInput.isEditing()) return exploredColorInput;
        if (newColorInput != null && newColorInput.isEditing()) return newColorInput;
        if (oldColorInput != null && oldColorInput.isEditing()) return oldColorInput;
        if (blockColorInput != null && blockColorInput.isEditing()) return blockColorInput;
        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (publicShareWarningOpen) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                publicShareWarningOpen = false;
            } else if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
                publicShareWarningOpen = false;
                run("share");
            }
            return true;
        }
        if (syncPage && !isColorPickerOpen()) {
            return super.keyPressed(keyEvent);
        }
        if (isColorPickerOpen()) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelColorPickerSelection();
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applyColorPickerSelection();
                return true;
            }
            return true;
        }
        GuiButtonText active = getActiveEditor();
        if (active != null) {
            active.keyPressed(keyEvent);
            return true;
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            saveValues();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (publicShareWarningOpen) {
            return true;
        }
        if (isColorPickerOpen()) {
            return true;
        }
        GuiButtonText active = getActiveEditor();
        if (active != null) {
            return active.charTyped(characterEvent);
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (publicShareWarningOpen) {
            publicShareWarningToggle.mouseClicked(mouseButtonEvent, doubleClick);
            publicShareWarningContinue.mouseClicked(mouseButtonEvent, doubleClick);
            publicShareWarningCancel.mouseClicked(mouseButtonEvent, doubleClick);
            return true;
        }
        if (!isColorPickerOpen()) {
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        colorPicker.mouseClicked(mouseButtonEvent, doubleClick);
        colorPickerModeButton.mouseClicked(mouseButtonEvent, doubleClick);
        colorPickerApplyButton.mouseClicked(mouseButtonEvent, doubleClick);
        colorPickerCancelButton.mouseClicked(mouseButtonEvent, doubleClick);
        swallowNextMouseRelease = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        if (publicShareWarningOpen) {
            publicShareWarningToggle.mouseReleased(mouseButtonEvent);
            publicShareWarningContinue.mouseReleased(mouseButtonEvent);
            publicShareWarningCancel.mouseReleased(mouseButtonEvent);
            return true;
        }
        if (!isColorPickerOpen()) {
            return super.mouseReleased(mouseButtonEvent);
        }
        boolean swallowed = swallowNextMouseRelease;
        swallowNextMouseRelease = false;
        colorPicker.mouseReleased(mouseButtonEvent);
        colorPickerModeButton.mouseReleased(mouseButtonEvent);
        colorPickerApplyButton.mouseReleased(mouseButtonEvent);
        colorPickerCancelButton.mouseReleased(mouseButtonEvent);
        return swallowed || true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        if (publicShareWarningOpen) {
            return true;
        }
        if (!isColorPickerOpen()) {
            return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
        }
        colorPicker.mouseDragged(mouseButtonEvent, deltaX, deltaY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        if (publicShareWarningOpen) {
            return true;
        }
        if (!isColorPickerOpen()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
        }
        colorPicker.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (syncPage) {
            int left = this.width / 2 - 260;
            drawSyncSection(graphics, "One Time Setup", left, 94);
            graphics.text(this.getFont(), "Passphrase", left, 107, 0xFFFFFFFF, false);
            drawSyncSection(graphics, "Share", left, 166);
            graphics.text(this.getFont(), "Player name", left, 179, 0xFFFFFFFF, false);
            drawSyncSection(graphics, "Receive", left, 224);
            graphics.text(this.getFont(), "Share code or URL", left, 237, 0xFFFFFFFF, false);
            graphics.text(this.getFont(), "Separate player layer name", left, 273, 0xFFFFFFFF, false);
            drawSyncSection(graphics, "Manual File Transfer", left, 318);
            graphics.text(this.getFont(), "Folder or .zip name", left, 331, 0xFFFFFFFF, false);
            graphics.text(this.getFont(), "Import as layer", left + 240, 331, 0xFFFFFFFF, false);
            drawSyncSection(graphics, "Imported Player Layers", left, 376);
            graphics.text(this.getFont(), "Player", syncPlayerListLeft + 6, 398, 0xFFFFFFFF, false);
            graphics.text(this.getFont(), "Visible", syncPlayerListLeft + syncPlayerListWidth - 276, 398, 0xFFFFFFFF, false);
            graphics.text(this.getFont(), "Color / HSV", syncPlayerListLeft + syncPlayerListWidth - 216, 398, 0xFFFFFFFF, false);
            graphics.fill(syncPlayerListLeft, 412, syncPlayerListLeft + syncPlayerListWidth, 412 + syncPlayerListHeight, 0x66000000);
            super.extractRenderState(graphics, isModalOpen() ? 0 : mouseX, isModalOpen() ? 0 : mouseY, delta);

            int statusY = 412 + syncPlayerListHeight + 16;
            drawSyncSection(graphics, "Status", left, statusY - 12);
            for (int i = 0; i < syncStatusMessages.size(); i++) {
                graphics.centeredText(this.getFont(), syncStatusMessages.get(i), this.width / 2, statusY + i * 10, 0xFFE0E0E0);
            }
            renderColorPickerPopup(graphics, mouseX, mouseY, delta);
            renderPublicShareWarning(graphics, mouseX, mouseY, delta);
            return;
        }
        refreshLabels();
        int left = this.width / 2 - 155;
        drawSyncSection(graphics, "Visibility", left, 92);
        drawSyncSection(graphics, "Detection", left, 182);
        drawSyncSection(graphics, "Overlay Styling", left, 272);
        drawSyncSection(graphics, "Scan Range", left, 386);
        super.extractRenderState(graphics, isColorPickerOpen() ? 0 : mouseX, isColorPickerOpen() ? 0 : mouseY, delta);
        renderColorPickerPopup(graphics, mouseX, mouseY, delta);
    }

    private void drawSyncSection(GuiGraphicsExtractor graphics, String title, int x, int y) {
        graphics.fill(x, y + 5, x + 24, y + 6, 0xFFA9B4C3);
        graphics.text(this.getFont(), title, x + 30, y, 0xFFE6EAF0, false);
    }

    private void renderColorPickerPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!isColorPickerOpen()) {
            return;
        }
        graphics.nextStratum();
        extractTransparentBackground(graphics);

        int popupX0 = colorPicker.getX() - (colorPicker.getWidth() / 2) - 30;
        int popupY0 = colorPicker.getY() - (colorPicker.getHeight() / 2) - 10;
        int popupW = colorPicker.getWidth() + 60;
        int popupH = colorPicker.getHeight() + 56;
        TooltipRenderUtil.extractTooltipBackground(graphics, popupX0, popupY0, popupW, popupH, null);

        colorPicker.extractRenderState(graphics, mouseX, mouseY, delta);
        int pickerColor = colorPicker.getColor() & 0x00FFFFFF;
        String colorText = "#" + String.format("%06X", pickerColor);
        int textX = (this.width - colorPicker.getWidth()) / 2;
        int textY = (this.height + colorPicker.getHeight()) / 2 + 8;
        int textWidth = this.getFont().width(colorText);
        graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, ARGB.opaque(pickerColor));
        graphics.fill(textX - 1, textY, textX + textWidth + 1, textY + 8, ARGB.black(0.2F));
        graphics.text(this.getFont(), colorText, textX, textY, 0xFFFFFFFF, false);

        int buttonsY = textY + 16;
        int centerX = this.width / 2;
        colorPickerModeButton.setPosition(centerX - 106, buttonsY);
        colorPickerApplyButton.setPosition(centerX - 32, buttonsY - 1);
        colorPickerCancelButton.setPosition(centerX + 42, buttonsY - 1);
        colorPickerModeButton.extractRenderState(graphics, mouseX, mouseY, delta);
        colorPickerApplyButton.extractRenderState(graphics, mouseX, mouseY, delta);
        colorPickerCancelButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderPublicShareWarning(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!publicShareWarningOpen) {
            return;
        }
        graphics.nextStratum();
        extractTransparentBackground(graphics);

        int popupWidth = 420;
        int popupHeight = 132;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;
        TooltipRenderUtil.extractTooltipBackground(graphics, popupX, popupY, popupWidth, popupHeight, null);
        graphics.centeredText(this.getFont(), Component.translatable("chunksync.publicShareWarning.title"),
                this.width / 2, popupY + 14, 0xFFFFFFFF);
        graphics.centeredText(this.getFont(), Component.translatable("chunksync.publicShareWarning.line1"),
                this.width / 2, popupY + 36, 0xFFE0E0E0);
        graphics.centeredText(this.getFont(), Component.translatable("chunksync.publicShareWarning.line2"),
                this.width / 2, popupY + 48, 0xFFE0E0E0);

        publicShareWarningToggle.setPosition(this.width / 2 - 110, popupY + 66);
        publicShareWarningContinue.setPosition(this.width / 2 - 105, popupY + 96);
        publicShareWarningCancel.setPosition(this.width / 2 + 5, popupY + 96);
        publicShareWarningToggle.extractRenderState(graphics, mouseX, mouseY, delta);
        publicShareWarningContinue.extractRenderState(graphics, mouseX, mouseY, delta);
        publicShareWarningCancel.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void openColorPicker(ColorTarget target) {
        activeColorTarget = target;
        String current = switch (target) {
            case EXPLORED -> settings.exploredChunksColor;
            case NEW -> settings.newerNewChunksNewColor;
            case OLD -> settings.newerNewChunksOldColor;
            case BLOCK -> settings.newerNewChunksBlockColor;
        };
        colorPicker.setColor(parseColor(current, 0x00CFFF));
        swallowNextMouseRelease = true;
    }

    private boolean isColorPickerOpen() {
        return activeColorTarget != null
                || (syncPage && playerLayerList != null && playerLayerList.getActiveColorSlug() != null);
    }

    private boolean isModalOpen() {
        return isColorPickerOpen() || publicShareWarningOpen;
    }

    private void cycleColorPickerMode(Button button) {
        mapSettings.colorPickerMode = mapSettings.colorPickerMode == 0 ? 1 : 0;
        colorPicker.updateMode(mapSettings.colorPickerMode == 0);
        colorPickerModeButton.setMessage(Component.literal(mapSettings.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)));
        MapSettingsManager.instance.saveAll();
    }

    private void applyColorPickerSelection() {
        String hex = "#" + String.format("%06X", colorPicker.getColor() & 0x00FFFFFF);
        if (syncPage && playerLayerList != null && playerLayerList.getActiveColorSlug() != null) {
            setPlayerLayerColor(playerLayerList.getActiveColorSlug(), hex);
            playerLayerList.setActiveColorSlug(null);
            return;
        }
        switch (activeColorTarget) {
            case EXPLORED -> exploredColorInput.setText(hex);
            case NEW -> newColorInput.setText(hex);
            case OLD -> oldColorInput.setText(hex);
            case BLOCK -> blockColorInput.setText(hex);
        }
        activeColorTarget = null;
        saveValues();
    }

    private void cancelColorPickerSelection() {
        if (syncPage && playerLayerList != null) {
            playerLayerList.setActiveColorSlug(null);
        }
        activeColorTarget = null;
    }

    private int parseColor(String colorText, int fallbackRgb) {
        String normalized = colorText == null ? "" : colorText.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (!normalized.matches("#[0-9a-fA-F]{6}")) {
            return fallbackRgb & 0x00FFFFFF;
        }
        try {
            return Integer.parseInt(normalized.substring(1), 16) & 0x00FFFFFF;
        } catch (NumberFormatException ignored) {
            return fallbackRgb & 0x00FFFFFF;
        }
    }
}
