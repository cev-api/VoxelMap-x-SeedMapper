package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiButtonText;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiOptionButtonMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiValueSliderMinimap;
import com.mamiyaotaru.voxelmap.interfaces.ISettingsManager;
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMapOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.function.Consumer;

public class GuiMinimapOptions extends GuiScreenMinimap {
    protected String screenTitle = "Minimap Options";
    private final VoxelMap voxelMap = VoxelConstants.getVoxelMapInstance();
    private final MapSettingsManager mapOptions;
    private final RadarSettingsManager radarOptions;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private TabNavigationBar tabNavigationBar;

    private final ArrayList<AbstractWidget> optionButtons = new ArrayList<>();
    private int pageIndex = 0;
    private String pageInfo = "";
    private int tabIndex = 0;
    private int lastTabIndex = 0;
    private Button nextPageButton;
    private Button prevPageButton;
    private GuiScreenMinimap embeddedTabScreen;
    private int embeddedTabIndex = -1;

    private static final EnumOptionsMinimap[] GENERAL_OPTIONS = { EnumOptionsMinimap.HIDE_MINIMAP, EnumOptionsMinimap.UPDATE_NOTIFIER, EnumOptionsMinimap.SHOW_BIOME, EnumOptionsMinimap.SHOW_COORDS, EnumOptionsMinimap.LOCATION, EnumOptionsMinimap.SIZE, EnumOptionsMinimap.SQUARE_MAP, EnumOptionsMinimap.ROTATES, EnumOptionsMinimap.IN_GAME_WAYPOINTS, EnumOptionsMinimap.CAVE_MODE, EnumOptionsMinimap.MOVE_MAP_BELOW_STATUS_EFFECT_ICONS, EnumOptionsMinimap.MOVE_SCOREBOARD_BELOW_MAP};
    private static final EnumOptionsMinimap[] PERFORMANCE_OPTIONS = { EnumOptionsMinimap.DYNAMIC_LIGHTING, EnumOptionsMinimap.TERRAIN_DEPTH, EnumOptionsMinimap.WATER_TRANSPARENCY, EnumOptionsMinimap.BLOCK_TRANSPARENCY, EnumOptionsMinimap.BIOMES, EnumOptionsMinimap.BIOME_OVERLAY, EnumOptionsMinimap.CHUNK_GRID, EnumOptionsMinimap.SLIME_CHUNKS, EnumOptionsMinimap.WORLD_BORDER,  EnumOptionsMinimap.FILTERING, EnumOptionsMinimap.TELEPORT_COMMAND };
    private static final EnumOptionsMinimap[] RADAR_FULL_OPTIONS = { EnumOptionsMinimap.SHOW_RADAR, EnumOptionsMinimap.RADAR_MODE, EnumOptionsMinimap.SHOW_MOBS, EnumOptionsMinimap.SHOW_PLAYERS, EnumOptionsMinimap.SHOW_MOB_NAMES, EnumOptionsMinimap.SHOW_PLAYER_NAMES, EnumOptionsMinimap.SHOW_MOB_HELMETS, EnumOptionsMinimap.SHOW_PLAYER_HELMETS, EnumOptionsMinimap.RADAR_FILTERING, EnumOptionsMinimap.RADAR_OUTLINES, EnumOptionsMinimap.SHOW_FULL_ENTITY_NAMES, EnumOptionsMinimap.SHOW_ENTITY_ELEVATION, EnumOptionsMinimap.HIDE_SNEAKING_PLAYERS, EnumOptionsMinimap.HIDE_INVISIBLE_ENTITIES };
    private static final EnumOptionsMinimap[] RADAR_SIMPLE_OPTIONS = { EnumOptionsMinimap.SHOW_RADAR, EnumOptionsMinimap.RADAR_MODE, EnumOptionsMinimap.SHOW_MOBS, EnumOptionsMinimap.SHOW_PLAYERS, EnumOptionsMinimap.SHOW_FACING, EnumOptionsMinimap.SHOW_ENTITY_ELEVATION, EnumOptionsMinimap.HIDE_SNEAKING_PLAYERS, EnumOptionsMinimap.HIDE_INVISIBLE_ENTITIES };

    // Performance Tab
    private GuiButtonText worldSeedButton;
    private GuiButtonText teleportCommandButton;
    private GuiOptionButtonMinimap slimeChunksButton;

    // Radar Tab
    private Button mobListButton;
    private Button netherPortalGeneralButton;
    private Button endPortalGeneralButton;
    private Button endGatewayGeneralButton;
    private Button highlightTracerGeneralButton;
    private Button tracerColorPickerButton;
    private GuiValueSliderMinimap tracerThicknessSlider;
    private GuiButtonText tracerColorInput;
    private GuiColorPickerContainer tracerColorPicker;
    private Button tracerColorPickerModeButton;
    private Button tracerColorPickerApplyButton;
    private Button tracerColorPickerCancelButton;
    private boolean tracerColorPickerOpen;
    private boolean swallowTracerColorMouseRelease;

    public GuiMinimapOptions(Screen parent) {
        lastScreen = parent;
        mapOptions = voxelMap.getMapOptions();
        radarOptions = voxelMap.getRadarOptions();
    }

    public GuiMinimapOptions(Screen parent, int initialTab) {
        this(parent);
        this.tabIndex = initialTab;
    }

    @Override
    public void init() {
        screenTitle = I18n.get("options.minimap.title");

        tabNavigationBar = TabNavigationBar.builder(tabManager, width).addTabs(
                new OptionsTab(Component.translatable("stat.generalButton"), 0),
                new OptionsTab(Component.translatable("options.minimap.tab.detailsPerformance"), 1),
                new OptionsTab(Component.translatable("options.minimap.tab.radar"), 2),
                new OptionsTab(Component.translatable("controls.title"), 3),
                new OptionsTab(Component.translatable("options.minimap.tab.worldmap"), 4),
                new OptionsTab(Component.translatable("options.seedmapper.tab"), 5)).build();

        tabNavigationBar.selectTab(tabIndex, false);
        tabNavigationBar.arrangeElements();
        setFocused(tabNavigationBar);
        addRenderableWidget(tabNavigationBar);

        int tabBottom = tabNavigationBar.getRectangle().bottom();
        ScreenRectangle screenRect = new ScreenRectangle(0, tabBottom, width, height - layout.getFooterHeight() - tabBottom);
        tabManager.setTabArea(screenRect);
        layout.setHeaderHeight(tabBottom);
        layout.addToFooter(new Button.Builder(Component.translatable("gui.done"), button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();

        nextPageButton = new Button.Builder(Component.literal(">"), button -> {
            pageIndex++;
            replaceButtons();
        }).bounds(width / 2 + 140, height / 6 + 120, 40, 20).build();
        addRenderableWidget(nextPageButton);

        prevPageButton = new Button.Builder(Component.literal("<"), button -> {
            pageIndex--;
            replaceButtons();
        }).bounds(width / 2 - 180, height / 6 + 120, 40, 20).build();
        addRenderableWidget(prevPageButton);

        replaceButtons();
    }

    private void handleTabChange() {
        if (tabManager.getCurrentTab() instanceof OptionsTab tab) {
            if (tab.index() != tabIndex) {
                tabIndex = tab.index();
                replaceButtons();
            }
        }
    }

    public void replaceButtons() {
        embeddedTabScreen = null;
        embeddedTabIndex = -1;
        netherPortalGeneralButton = null;
        endPortalGeneralButton = null;
        endGatewayGeneralButton = null;
        highlightTracerGeneralButton = null;
        tracerColorPickerButton = null;
        tracerThicknessSlider = null;
        tracerColorInput = null;
        tracerColorPickerOpen = false;
        for (GuiEventListener widget : optionButtons) {
            removeWidget(widget);
        }
        optionButtons.clear();

        nextPageButton.visible = true;
        prevPageButton.visible = true;

        EnumOptionsMinimap[] relevantOptions = null;
        switch (tabIndex) {
            case 0 -> relevantOptions = GENERAL_OPTIONS;
            case 1 -> relevantOptions = PERFORMANCE_OPTIONS;
            case 2 -> {
                if (radarOptions.radarMode == 2) {
                    relevantOptions = RADAR_FULL_OPTIONS;
                } else {
                    relevantOptions = RADAR_SIMPLE_OPTIONS;
                }
            }
            case 3, 4, 5 -> {
                pageInfo = "";
                nextPageButton.active = false;
                prevPageButton.active = false;
                nextPageButton.visible = false;
                prevPageButton.visible = false;
                prepareEmbeddedTab(tabIndex);
                lastTabIndex = tabIndex;
                return;
            }
        }

        if (relevantOptions == null) {
            tabIndex = lastTabIndex;
            return;
        }
        if (tabIndex != lastTabIndex) {
            pageIndex = 0;
        }
        lastTabIndex = tabIndex;

        int itemCount = 10;
        int pageCount = (relevantOptions.length - 1) / itemCount;
        if (pageIndex > pageCount) {
            pageIndex = 0;
        }
        if (pageIndex < 0) {
            pageIndex = pageCount;
        }
        pageInfo = "[ " + (pageIndex + 1) + " / " + (pageCount + 1) + " ]";
        int pageStart = itemCount * pageIndex;
        int pageEnd = Math.min(itemCount * (pageIndex + 1), relevantOptions.length);

        nextPageButton.active = pageCount > 0;
        prevPageButton.active = pageCount > 0;

        // Menu Buttons
        for (int i = pageStart; i < pageEnd; i++) {
            EnumOptionsMinimap option = relevantOptions[i];

            ISettingsManager settingsManager = getSettingsManager(option);
            if (settingsManager == null) continue;

            int buttonX = getWidth() / 2 - 155 + (i - pageStart) % 2 * 160;
            int buttonY = getHeight() / 6 + 24 * ((i - pageStart) >> 1);

            // List / Toggle
            if (option.getType() == EnumOptionsMinimap.Type.BOOLEAN || option.getType() == EnumOptionsMinimap.Type.LIST) {
                StringBuilder text = new StringBuilder().append(settingsManager.getKeyText(option));
                if ((option == EnumOptionsMinimap.WATER_TRANSPARENCY || option == EnumOptionsMinimap.BLOCK_TRANSPARENCY || option == EnumOptionsMinimap.BIOMES) && !mapOptions.multicore && settingsManager.getBooleanValue(option)) {
                    text.append("§c").append(text);
                }

                GuiOptionButtonMinimap optionButton = new GuiOptionButtonMinimap(buttonX, buttonY, option, Component.literal(text.toString()), this::optionClicked);
                addOptionButton(optionButton);

                if (option == EnumOptionsMinimap.SLIME_CHUNKS) {
                    slimeChunksButton = optionButton;
                }
            }

            // Text Field
            if (option == EnumOptionsMinimap.TELEPORT_COMMAND) {
                String buttonTeleportText = I18n.get("options.minimap.teleportCommand") + ": " + mapOptions.teleportCommand;
                teleportCommandButton = new GuiButtonText(getFont(), buttonX, buttonY, 150, 20, Component.literal(buttonTeleportText), button -> teleportCommandButton.setEditing(true));
                teleportCommandButton.setText(mapOptions.teleportCommand);
                teleportCommandButton.active = mapOptions.serverTeleportCommand == null;
                addOptionButton(teleportCommandButton);
            }
        }

        int additionalButtonX = width / 2 - 75;
        int additionalButtonY = height / 6 + 144;

        // Additional Buttons

        if (relevantOptions == RADAR_FULL_OPTIONS) {
            mobListButton = new Button.Builder(Component.translatable("options.minimap.radar.selectMobs"), x -> minecraft.setScreen(new GuiMobs(this, radarOptions))).bounds(additionalButtonX, additionalButtonY, 150, 20).build();
            addOptionButton(mobListButton);
        }
        if (relevantOptions == GENERAL_OPTIONS && pageIndex == 1) {
            int left = this.width / 2 - 155;
            int right = this.width / 2 + 5;
            netherPortalGeneralButton = new Button.Builder(Component.empty(), button -> {
                mapOptions.showNetherPortalMarkers = !mapOptions.showNetherPortalMarkers;
                button.setMessage(Component.literal("Nether Portal Markers: " + (mapOptions.showNetherPortalMarkers ? "ON" : "OFF")));
                MapSettingsManager.instance.saveAll();
            }).bounds(left, getGridButtonY(2), 150, 20).build();
            netherPortalGeneralButton.setMessage(Component.literal("Nether Portal Markers: " + (mapOptions.showNetherPortalMarkers ? "ON" : "OFF")));
            addOptionButton(netherPortalGeneralButton);

            endPortalGeneralButton = new Button.Builder(Component.empty(), button -> {
                mapOptions.showEndPortalMarkers = !mapOptions.showEndPortalMarkers;
                button.setMessage(Component.literal("End Portal Markers: " + (mapOptions.showEndPortalMarkers ? "ON" : "OFF")));
                MapSettingsManager.instance.saveAll();
            }).bounds(right, getGridButtonY(2), 150, 20).build();
            endPortalGeneralButton.setMessage(Component.literal("End Portal Markers: " + (mapOptions.showEndPortalMarkers ? "ON" : "OFF")));
            addOptionButton(endPortalGeneralButton);

            endGatewayGeneralButton = new Button.Builder(Component.empty(), button -> {
                mapOptions.showEndGatewayMarkers = !mapOptions.showEndGatewayMarkers;
                button.setMessage(Component.literal("End Gateway Markers: " + (mapOptions.showEndGatewayMarkers ? "ON" : "OFF")));
                MapSettingsManager.instance.saveAll();
            }).bounds(left, getGridButtonY(4), 150, 20).build();
            endGatewayGeneralButton.setMessage(Component.literal("End Gateway Markers: " + (mapOptions.showEndGatewayMarkers ? "ON" : "OFF")));
            addOptionButton(endGatewayGeneralButton);

            // ### MODIFIED ###
            // Move the highlight tracer controls up so they sit beside End Gateway
            // and directly above the tracer thickness / color row.
            int tracerY = getGridButtonY(4);
            highlightTracerGeneralButton = new Button.Builder(Component.empty(), button -> {
                mapOptions.highlightTracerEnabled = !mapOptions.highlightTracerEnabled;
                updateTracerWidgets();
                MapSettingsManager.instance.saveAll();
            }).bounds(right, tracerY, 150, 20).build();
            addOptionButton(highlightTracerGeneralButton);

            tracerThicknessSlider = new GuiValueSliderMinimap(left, tracerY + 24, 150, 20, mapOptions.highlightTracerThickness, 1.0D, 6.0D, value -> {
                mapOptions.highlightTracerThickness = (float) value;
                updateTracerWidgets();
                MapSettingsManager.instance.saveAll();
            }, value -> "Tracer Thickness: " + String.format("%.1f", value));
            addOptionButton(tracerThicknessSlider);

            tracerColorInput = new GuiButtonText(getFont(), right, tracerY + 24, 118, 20, Component.literal("Tracer Color"), button -> {});
            tracerColorInput.active = false;
            tracerColorInput.setText(mapOptions.highlightTracerColor);
            addOptionButton(tracerColorInput);
            tracerColorPickerButton = new Button.Builder(Component.literal("..."), button -> openTracerColorPicker())
                    .bounds(right + 122, tracerY + 24, 28, 20).build();
            addOptionButton(tracerColorPickerButton);
            updateTracerWidgets();
        }
        if (relevantOptions == RADAR_FULL_OPTIONS || relevantOptions == RADAR_SIMPLE_OPTIONS) {
            int chunkOverlayY = additionalButtonY + (relevantOptions == RADAR_FULL_OPTIONS ? 24 : 0);
            Button chunkOverlayButton = new Button.Builder(Component.literal("Chunk Overlay Options"), x -> minecraft.setScreen(new GuiRadarChunkOverlays(this))).bounds(additionalButtonX, chunkOverlayY, 150, 20).build();
            addOptionButton(chunkOverlayButton);
        }

        setButtonsActive();

    }

    private void prepareEmbeddedTab(int index) {
        if (embeddedTabScreen != null && embeddedTabIndex == index) {
            return;
        }

        embeddedTabScreen = switch (index) {
            case 3 -> new GuiMinimapControls(this);
            case 4 -> new GuiPersistentMapOptions(this);
            case 5 -> new GuiSeedMapperOptions(this);
            default -> null;
        };
        embeddedTabIndex = index;

        if (embeddedTabScreen == null) {
            return;
        }

        embeddedTabScreen.setEmbeddedInParent(true);
        embeddedTabScreen.init(this.width, this.height);
        String done = Component.translatable("gui.done").getString();
        for (GuiEventListener child : embeddedTabScreen.children()) {
            if (child instanceof Button button && done.equals(button.getMessage().getString())) {
                button.visible = false;
                button.active = false;
            }
        }
    }

    private void addOptionButton(AbstractWidget widget) {
        optionButtons.add(widget);
        addRenderableWidget(widget);
    }

    private int getGridButtonY(int slot) {
        return getHeight() / 6 + 24 * (slot >> 1);
    }

    private void optionClicked(Button button) {
        if (!(button instanceof GuiOptionButtonMinimap button2)) {
            return;
        }
        EnumOptionsMinimap option = button2.returnEnumOptions();

        ISettingsManager settingsManager = getSettingsManager(option);
        if (settingsManager == null) return;

        MapSettingsManager.updateBooleanOrListValue(settingsManager, option);

        String prefix = "";
        switch (option) {
            case OLD_NORTH -> voxelMap.getWaypointManager().setOldNorth(mapOptions.oldNorth);
            case WATER_TRANSPARENCY, BLOCK_TRANSPARENCY, BIOMES -> {
                if (!mapOptions.multicore && option.getType() == EnumOptionsMinimap.Type.BOOLEAN && settingsManager.getBooleanValue(option)) {
                    prefix = "§c";
                }
            }
            case RADAR_MODE -> replaceButtons();
        }

        button2.setMessage(Component.literal(prefix + settingsManager.getKeyText(option)));
        setButtonsActive();
    }

    private void setButtonsActive() {
        for (GuiEventListener button : children()) {
            if (!(button instanceof GuiOptionButtonMinimap button2)){
                continue;
            }
            EnumOptionsMinimap option = button2.returnEnumOptions();

            boolean radarBlocked = !radarOptions.radarAllowed && !radarOptions.radarPlayersAllowed && !radarOptions.radarMobsAllowed;

            if (containsOption(option, RADAR_FULL_OPTIONS) || containsOption(option, RADAR_SIMPLE_OPTIONS)) {
                button2.active = radarOptions.showRadar && !radarBlocked;
                if (mobListButton != null) {
                    mobListButton.active = radarOptions.showRadar && !radarBlocked;
                }
            }

            switch (option) {
                case HIDE_MINIMAP -> button2.active = mapOptions.minimapAllowed;
                case IN_GAME_WAYPOINTS -> button2.active = mapOptions.waypointsAllowed;
                case CAVE_MODE -> button2.active = mapOptions.cavesAllowed;
                case SLIME_CHUNKS -> button2.active = minecraft.hasSingleplayerServer() || !voxelMap.getWorldSeed().isEmpty();
                case SHOW_RADAR -> button2.active = !radarBlocked;
                case SHOW_PLAYERS -> button2.active = button2.active && radarOptions.radarPlayersAllowed;
                case SHOW_MOBS -> button2.active = button2.active && radarOptions.radarMobsAllowed;
                case SHOW_PLAYER_HELMETS, SHOW_PLAYER_NAMES -> button2.active = button2.active && radarOptions.showPlayers && radarOptions.radarPlayersAllowed;
                case SHOW_MOB_HELMETS, SHOW_MOB_NAMES -> button2.active = button2.active && (radarOptions.showNeutrals || radarOptions.showHostiles) && radarOptions.radarMobsAllowed;
            }
        }
        if (netherPortalGeneralButton != null) {
            netherPortalGeneralButton.active = mapOptions.minimapAllowed;
        }
        if (endPortalGeneralButton != null) {
            endPortalGeneralButton.active = mapOptions.minimapAllowed;
        }
        if (endGatewayGeneralButton != null) {
            endGatewayGeneralButton.active = mapOptions.minimapAllowed;
        }
        if (highlightTracerGeneralButton != null) {
            highlightTracerGeneralButton.active = mapOptions.minimapAllowed;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (embeddedTabScreen != null) {
            embeddedTabScreen.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, height - layout.getFooterHeight() - 2, 0.0F, 0.0F, width, 2, 32, 2);
        if (!pageInfo.isEmpty()) {
            int navY = height / 6 + 120;
            int barTop = navY + 2;
            int barBottom = navY + 18;

            if (prevPageButton.visible && nextPageButton.visible) {
                int leftBarStart = prevPageButton.getX() + prevPageButton.getWidth() + 8;
                int leftBarEnd = width / 2 - 48;
                int rightBarStart = width / 2 + 48;
                int rightBarEnd = nextPageButton.getX() - 8;

                if (leftBarEnd > leftBarStart) {
                    graphics.fill(leftBarStart, barTop, leftBarEnd, barBottom, 0x66000000);
                }
                if (rightBarEnd > rightBarStart) {
                    graphics.fill(rightBarStart, barTop, rightBarEnd, barBottom, 0x66000000);
                }
            }

            graphics.fill(width / 2 - 40, barTop, width / 2 + 40, barBottom, 0x88000000);
            graphics.centeredText(font, pageInfo, width / 2, navY + 6, 0xFFFFFFFF);
        }
        if (tracerColorPickerOpen) {
            graphics.nextStratum();
            extractTransparentBackground(graphics);

            int popupX0 = tracerColorPicker.getX() - (tracerColorPicker.getWidth() / 2) - 30;
            int popupY0 = tracerColorPicker.getY() - (tracerColorPicker.getHeight() / 2) - 10;
            int popupW = tracerColorPicker.getWidth() + 60;
            int popupH = tracerColorPicker.getHeight() + 56;
            TooltipRenderUtil.extractTooltipBackground(graphics, popupX0, popupY0, popupW, popupH, null);

            tracerColorPicker.extractRenderState(graphics, mouseX, mouseY, delta);
            int pickerColor = tracerColorPicker.getColor() & 0x00FFFFFF;
            String colorText = "#" + String.format("%06X", pickerColor);
            int textX = (this.width - tracerColorPicker.getWidth()) / 2;
            int textY = (this.height + tracerColorPicker.getHeight()) / 2 + 8;
            int textWidth = this.getFont().width(colorText);
            graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, ARGB.opaque(pickerColor));
            graphics.fill(textX - 1, textY, textX + textWidth + 1, textY + 8, ARGB.black(0.2F));
            graphics.text(this.getFont(), colorText, textX, textY, 0xFFFFFFFF, false);

            int buttonsY = textY + 16;
            int centerX = this.width / 2;
            tracerColorPickerModeButton.setPosition(centerX - 106, buttonsY);
            tracerColorPickerApplyButton.setPosition(centerX - 32, buttonsY - 1);
            tracerColorPickerCancelButton.setPosition(centerX + 42, buttonsY - 1);
            tracerColorPickerModeButton.extractRenderState(graphics, mouseX, mouseY, delta);
            tracerColorPickerApplyButton.extractRenderState(graphics, mouseX, mouseY, delta);
            tracerColorPickerCancelButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        handleTabChange();
    }

    private void updateTracerWidgets() {
        if (highlightTracerGeneralButton != null) {
            highlightTracerGeneralButton.setMessage(Component.literal("Highlight Tracer: " + (mapOptions.highlightTracerEnabled ? "ON" : "OFF")));
        }
        if (tracerThicknessSlider != null) {
            tracerThicknessSlider.setActualValue(mapOptions.highlightTracerThickness);
            tracerThicknessSlider.active = mapOptions.highlightTracerEnabled;
        }
        if (tracerColorInput != null) {
            tracerColorInput.setText(mapOptions.highlightTracerColor);
            tracerColorInput.setMessage(Component.literal("Tracer Color: " + mapOptions.highlightTracerColor));
            tracerColorInput.active = mapOptions.highlightTracerEnabled;
        }
        if (tracerColorPickerButton != null) {
            tracerColorPickerButton.active = mapOptions.highlightTracerEnabled;
        }
    }

    private void openTracerColorPicker() {
        boolean simpleMode = mapOptions.colorPickerMode == 0;
        tracerColorPicker = new GuiColorPickerContainer(this.width / 2, this.height / 2, 200, 140, simpleMode, picker -> {});
        tracerColorPicker.setColor(parseColor(mapOptions.highlightTracerColor, 0xFF0000));
        tracerColorPickerModeButton = new Button.Builder(Component.literal(mapOptions.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleTracerColorPickerMode)
                .bounds(0, 0, 66, 16)
                .build();
        tracerColorPickerApplyButton = new Button.Builder(Component.translatable("gui.done"), button -> applyTracerColorPicker())
                .bounds(0, 0, 66, 18)
                .build();
        tracerColorPickerCancelButton = new Button.Builder(Component.translatable("gui.cancel"), button -> closeTracerColorPicker())
                .bounds(0, 0, 66, 18)
                .build();
        tracerColorPickerOpen = true;
        swallowTracerColorMouseRelease = true;
    }

    private void closeTracerColorPicker() {
        tracerColorPickerOpen = false;
    }

    private void cycleTracerColorPickerMode(Button button) {
        mapOptions.colorPickerMode = mapOptions.colorPickerMode == 0 ? 1 : 0;
        tracerColorPicker.updateMode(mapOptions.colorPickerMode == 0);
        tracerColorPickerModeButton.setMessage(Component.literal(mapOptions.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)));
        MapSettingsManager.instance.saveAll();
    }

    private void applyTracerColorPicker() {
        mapOptions.highlightTracerColor = "#" + String.format("%06X", tracerColorPicker.getColor() & 0x00FFFFFF);
        closeTracerColorPicker();
        updateTracerWidgets();
        MapSettingsManager.instance.saveAll();
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

    @Override
    public void extractMenuBackground(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CreateWorldScreen.TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, width, layout.getHeaderHeight(), 16, 16);
        extractMenuBackground(graphics, 0, layout.getHeaderHeight(), width, height);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (tracerColorPickerOpen) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeTracerColorPicker();
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applyTracerColorPicker();
                return true;
            }
            return true;
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (embeddedTabScreen != null && embeddedTabScreen.keyPressed(keyEvent)) {
            return true;
        }
        int keyCode = keyEvent.key();
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (worldSeedButton != null) {
                worldSeedButton.keyPressed(keyEvent);
            }
            if (teleportCommandButton != null) {
                teleportCommandButton.keyPressed(keyEvent);
            }
        }

        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (worldSeedButton != null && worldSeedButton.isEditing()) {
                newSeed();
            } else if (teleportCommandButton != null && teleportCommandButton.isEditing()) {
                newTeleportCommand();
            }

        }

        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (embeddedTabScreen != null && embeddedTabScreen.charTyped(characterEvent)) {
            return true;
        }
        boolean OK = super.charTyped(characterEvent);
        if (characterEvent.codepoint() == '\r') {
            if (worldSeedButton != null && worldSeedButton.isEditing()) {
                newSeed();
            } else if (teleportCommandButton != null && teleportCommandButton.isEditing()) {
                newTeleportCommand();
            }

        }

        return OK;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (tracerColorPickerOpen) {
            tracerColorPicker.mouseClicked(mouseButtonEvent, doubleClick);
            tracerColorPickerModeButton.mouseClicked(mouseButtonEvent, doubleClick);
            tracerColorPickerApplyButton.mouseClicked(mouseButtonEvent, doubleClick);
            tracerColorPickerCancelButton.mouseClicked(mouseButtonEvent, doubleClick);
            swallowTracerColorMouseRelease = true;
            return true;
        }
        if (super.mouseClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        return embeddedTabScreen != null && embeddedTabScreen.mouseClicked(mouseButtonEvent, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent) {
        if (tracerColorPickerOpen) {
            boolean swallowed = swallowTracerColorMouseRelease;
            swallowTracerColorMouseRelease = false;
            tracerColorPicker.mouseReleased(mouseButtonEvent);
            tracerColorPickerModeButton.mouseReleased(mouseButtonEvent);
            tracerColorPickerApplyButton.mouseReleased(mouseButtonEvent);
            tracerColorPickerCancelButton.mouseReleased(mouseButtonEvent);
            return swallowed || true;
        }
        if (super.mouseReleased(mouseButtonEvent)) {
            return true;
        }
        return embeddedTabScreen != null && embeddedTabScreen.mouseReleased(mouseButtonEvent);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        if (tracerColorPickerOpen) {
            tracerColorPicker.mouseDragged(mouseButtonEvent, deltaX, deltaY);
            return true;
        }
        if (super.mouseDragged(mouseButtonEvent, deltaX, deltaY)) {
            return true;
        }
        return embeddedTabScreen != null && embeddedTabScreen.mouseDragged(mouseButtonEvent, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        if (tracerColorPickerOpen) {
            tracerColorPicker.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount)) {
            return true;
        }
        return embeddedTabScreen != null && embeddedTabScreen.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
    }

    private record OptionsTab(Component title, int index) implements Tab {
        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public Component getTabExtraNarration() {
            return Component.empty();
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
        }

        @Override
        public void doLayout(ScreenRectangle screenRectangle) {
        }

    }

    private void newSeed() {
        if (worldSeedButton != null) {
            String newSeed = worldSeedButton.getText();
            voxelMap.setWorldSeed(newSeed);
            String worldSeedDisplay = voxelMap.getWorldSeed();

            String buttonText = I18n.get("options.minimap.worldSeed") + ": " + worldSeedDisplay;
            worldSeedButton.setMessage(Component.literal(buttonText));
            worldSeedButton.setText(voxelMap.getWorldSeed());
            voxelMap.getMap().forceFullRender(true);
        }
        if (slimeChunksButton != null) {
            slimeChunksButton.active = minecraft.hasSingleplayerServer() || !voxelMap.getWorldSeed().isEmpty();
        }

    }

    private void newTeleportCommand() {
        if (teleportCommandButton != null) {
            String newTeleportCommand = teleportCommandButton.getText().isEmpty() ? "tp %p %x %y %z" : teleportCommandButton.getText();
            mapOptions.teleportCommand = newTeleportCommand;

            String buttonText = I18n.get("options.minimap.teleportCommand") + ": " + newTeleportCommand;
            teleportCommandButton.setMessage(Component.literal(buttonText));
            teleportCommandButton.setText(mapOptions.teleportCommand);
        }

    }

    private ISettingsManager getSettingsManager(EnumOptionsMinimap option) {
        if (containsOption(option, GENERAL_OPTIONS) || containsOption(option, PERFORMANCE_OPTIONS)) {
            return mapOptions;
        }
        if (containsOption(option, RADAR_SIMPLE_OPTIONS) || containsOption(option, RADAR_FULL_OPTIONS)) {
            return radarOptions;
        }

        return null;
    }

    private boolean containsOption(EnumOptionsMinimap option, EnumOptionsMinimap[] optionArray) {
        for (EnumOptionsMinimap x : optionArray) {
            if (x == option) return true;
        }
        return false;
    }
}