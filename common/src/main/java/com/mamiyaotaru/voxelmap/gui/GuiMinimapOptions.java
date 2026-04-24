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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Locale;
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
    private int pageNavY = 0;
    private Button nextPageButton;
    private Button prevPageButton;
    private GuiScreenMinimap embeddedTabScreen;
    private int embeddedTabIndex = -1;
    private final ArrayList<OptionSection> optionSections = new ArrayList<>();

    private static final EnumOptionsMinimap[] GENERAL_OPTIONS = { EnumOptionsMinimap.HIDE_MINIMAP, EnumOptionsMinimap.UPDATE_NOTIFIER, EnumOptionsMinimap.SHOW_BIOME, EnumOptionsMinimap.SHOW_COORDS, EnumOptionsMinimap.SHOW_FACING_DEGREES, EnumOptionsMinimap.LOCATION, EnumOptionsMinimap.SIZE, EnumOptionsMinimap.SQUARE_MAP, EnumOptionsMinimap.ROTATES, EnumOptionsMinimap.IN_GAME_WAYPOINTS, EnumOptionsMinimap.CAVE_MODE, EnumOptionsMinimap.MOVE_MAP_BELOW_STATUS_EFFECT_ICONS, EnumOptionsMinimap.MOVE_SCOREBOARD_BELOW_MAP};
    private static final EnumOptionsMinimap[] PERFORMANCE_OPTIONS = { EnumOptionsMinimap.DYNAMIC_LIGHTING, EnumOptionsMinimap.TERRAIN_DEPTH, EnumOptionsMinimap.WATER_TRANSPARENCY, EnumOptionsMinimap.BLOCK_TRANSPARENCY, EnumOptionsMinimap.BIOMES, EnumOptionsMinimap.BIOME_OVERLAY, EnumOptionsMinimap.CHUNK_GRID, EnumOptionsMinimap.SLIME_CHUNKS, EnumOptionsMinimap.WORLD_BORDER,  EnumOptionsMinimap.FILTERING, EnumOptionsMinimap.TELEPORT_COMMAND };
    private static final EnumOptionsMinimap[] RADAR_FULL_OPTIONS = { EnumOptionsMinimap.SHOW_RADAR, EnumOptionsMinimap.RADAR_MODE, EnumOptionsMinimap.SHOW_MOBS, EnumOptionsMinimap.SHOW_PLAYERS, EnumOptionsMinimap.SHOW_MOB_NAMES, EnumOptionsMinimap.SHOW_PLAYER_NAMES, EnumOptionsMinimap.SHOW_MOB_HELMETS, EnumOptionsMinimap.SHOW_PLAYER_HELMETS, EnumOptionsMinimap.RADAR_FILTERING, EnumOptionsMinimap.RADAR_OUTLINES, EnumOptionsMinimap.RADAR_CPU_RENDERING, EnumOptionsMinimap.SHOW_FULL_ENTITY_NAMES, EnumOptionsMinimap.SHOW_ENTITY_ELEVATION, EnumOptionsMinimap.HIDE_SNEAKING_PLAYERS, EnumOptionsMinimap.HIDE_INVISIBLE_ENTITIES };
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
    private Button chunkOverlayButton;
    private Button highlightTracerGeneralButton;
    private Button autoHideHighlightsWhenNearButton;
    private Button tracerColorPickerButton;
    private GuiValueSliderMinimap tracerThicknessSlider;
    private GuiValueSliderMinimap autoHideHighlightsDistanceSlider;
    private GuiValueSliderMinimap minimapZoomSlider;
    private GuiButtonText tracerColorInput;
    private GuiColorPickerContainer tracerColorPicker;
    private Button tracerColorPickerModeButton;
    private Button tracerColorPickerApplyButton;
    private Button tracerColorPickerCancelButton;
    private boolean tracerColorPickerOpen;
    private boolean swallowTracerColorMouseRelease;
    private GuiValueSliderMinimap radarTextScaleSlider;
    private static final int OPTION_BUTTON_WIDTH = 190;
    private static final int OPTION_COLUMN_GAP = 10;
    private static final int FULL_ROW_WIDTH = OPTION_BUTTON_WIDTH * 2 + OPTION_COLUMN_GAP;

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
                new OptionsTab(Component.literal("Minimap"), 0),
                new OptionsTab(Component.literal("Visuals"), 1),
                new OptionsTab(Component.literal("Entities"), 2),
                new OptionsTab(Component.translatable("controls.title"), 3),
                new OptionsTab(Component.literal("World Map"), 4),
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
        chunkOverlayButton = null;
        highlightTracerGeneralButton = null;
        autoHideHighlightsWhenNearButton = null;
        tracerColorPickerButton = null;
        tracerThicknessSlider = null;
        autoHideHighlightsDistanceSlider = null;
        minimapZoomSlider = null;
        radarTextScaleSlider = null;
        tracerColorInput = null;
        tracerColorPickerOpen = false;
        for (GuiEventListener widget : optionButtons) {
            removeWidget(widget);
        }
        optionButtons.clear();
        optionSections.clear();

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
        // Unified one-page layouts for General / Performance / Radar
        pageInfo = "";
        nextPageButton.active = false;
        prevPageButton.active = false;
        nextPageButton.visible = false;
        prevPageButton.visible = false;

        if (relevantOptions == GENERAL_OPTIONS) {
            addSection("Essentials", 0, 1);
            addMappedOption(EnumOptionsMinimap.HIDE_MINIMAP, 0, 0);
            addMappedOption(EnumOptionsMinimap.UPDATE_NOTIFIER, 0, 1);
            addMappedOption(EnumOptionsMinimap.SHOW_BIOME, 1, 0);
            addMappedOption(EnumOptionsMinimap.SHOW_COORDS, 1, 1);

            addSection("Position & Shape", 3, 5);
            addMappedOption(EnumOptionsMinimap.SHOW_FACING_DEGREES, 3, 0);
            addMappedOption(EnumOptionsMinimap.LOCATION, 3, 1);
            addMappedOption(EnumOptionsMinimap.SIZE, 4, 0);
            addMappedOption(EnumOptionsMinimap.SQUARE_MAP, 4, 1);
            addMappedOption(EnumOptionsMinimap.ROTATES, 5, 0);
            addMappedOption(EnumOptionsMinimap.CAVE_MODE, 5, 1);

            int sliderY = fromSlot(6, 0)[1];
            minimapZoomSlider = new GuiValueSliderMinimap(width / 2 - FULL_ROW_WIDTH / 2, sliderY, FULL_ROW_WIDTH, 20, mapOptions.zoom, 0.0D, 4.0D, value -> {
                voxelMap.getMap().setZoomLevel((int) Math.round(value));
                updateMinimapZoomSlider();
            }, value -> "Minimap Zoom: " + formatMinimapZoomFactor((int) Math.round(value)));
            addOptionButton(minimapZoomSlider);

            addSection("Map Tools", 8, 8);
            int left = fromSlot(0, 0)[0];
            int right = fromSlot(0, 1)[0];
            chunkOverlayButton = createModernButton(width / 2 - FULL_ROW_WIDTH / 2, fromSlot(8, 0)[1], FULL_ROW_WIDTH, Component.literal("Chunk Overlay Options"), x -> minecraft.setScreen(new GuiRadarChunkOverlays(this)));
            addOptionButton(chunkOverlayButton);

            addSection("Waypoints & Highlights", 10, 12);
            autoHideHighlightsWhenNearButton = createModernButton(left, fromSlot(10, 0)[1], OPTION_BUTTON_WIDTH, Component.empty(), button -> {
                mapOptions.autoHideHighlightsWhenNear = !mapOptions.autoHideHighlightsWhenNear;
                updateTracerWidgets();
                MapSettingsManager.instance.saveAll();
            });
            addOptionButton(autoHideHighlightsWhenNearButton);
            addMappedOption(EnumOptionsMinimap.IN_GAME_WAYPOINTS, 10, 1);

            autoHideHighlightsDistanceSlider = new GuiValueSliderMinimap(right, fromSlot(11, 1)[1], OPTION_BUTTON_WIDTH, 20, mapOptions.autoHideHighlightsNearDistance, 1.0D, 64.0D, value -> {
                mapOptions.autoHideHighlightsNearDistance = (float) value;
                updateTracerWidgets();
                MapSettingsManager.instance.saveAll();
            }, value -> "Highlight Remove Radius: " + (int) Math.round(value) + " blocks");
            addOptionButton(autoHideHighlightsDistanceSlider);

            highlightTracerGeneralButton = createModernButton(left, fromSlot(11, 0)[1], OPTION_BUTTON_WIDTH, Component.empty(), button -> {
                mapOptions.highlightTracerEnabled = !mapOptions.highlightTracerEnabled;
                updateTracerWidgets();
                MapSettingsManager.instance.saveAll();
            });
            addOptionButton(highlightTracerGeneralButton);

            tracerThicknessSlider = new GuiValueSliderMinimap(left, fromSlot(12, 0)[1], OPTION_BUTTON_WIDTH, 20, mapOptions.highlightTracerThickness, 1.0D, 6.0D, value -> {
                mapOptions.highlightTracerThickness = (float) value;
                updateTracerWidgets();
                MapSettingsManager.instance.saveAll();
            }, value -> "Tracer Thickness: " + String.format("%.1f", value));
            addOptionButton(tracerThicknessSlider);
            int colorInputWidth = OPTION_BUTTON_WIDTH - 32;
            tracerColorInput = new GuiButtonText(getFont(), right, fromSlot(12, 1)[1], colorInputWidth, 20, Component.literal("Tracer Color"), button -> {});
            tracerColorInput.active = false;
            tracerColorInput.setText(mapOptions.highlightTracerColor);
            addOptionButton(tracerColorInput);
            tracerColorPickerButton = createModernButton(right + colorInputWidth + 4, fromSlot(12, 1)[1], 28, Component.literal("..."), button -> openTracerColorPicker());
            addOptionButton(tracerColorPickerButton);

            addSection("HUD Layout", 14, 14);
            addMappedOption(EnumOptionsMinimap.MOVE_MAP_BELOW_STATUS_EFFECT_ICONS, 14, 0);
            addMappedOption(EnumOptionsMinimap.MOVE_SCOREBOARD_BELOW_MAP, 14, 1);
            updateTracerWidgets();
        } else if (relevantOptions == PERFORMANCE_OPTIONS) {
            addSection("Lighting & Terrain", 0, 2);
            addMappedOption(EnumOptionsMinimap.DYNAMIC_LIGHTING, 0, 0);
            addMappedOption(EnumOptionsMinimap.TERRAIN_DEPTH, 0, 1);
            addMappedOption(EnumOptionsMinimap.WATER_TRANSPARENCY, 1, 0);
            addMappedOption(EnumOptionsMinimap.BLOCK_TRANSPARENCY, 1, 1);
            addMappedOption(EnumOptionsMinimap.BIOMES, 2, 0);
            addMappedOption(EnumOptionsMinimap.BIOME_OVERLAY, 2, 1);

            addSection("World Overlays", 4, 5);
            addMappedOption(EnumOptionsMinimap.CHUNK_GRID, 4, 0);
            addMappedOption(EnumOptionsMinimap.SLIME_CHUNKS, 4, 1);
            addMappedOption(EnumOptionsMinimap.WORLD_BORDER, 5, 0);
            addMappedOption(EnumOptionsMinimap.FILTERING, 5, 1);

            addSection("Utility", 7, 7);
            addMappedOption(EnumOptionsMinimap.TELEPORT_COMMAND, 7, -1);

            addSection("HUD Scale", 9, 9);
            radarTextScaleSlider = new GuiValueSliderMinimap(width / 2 - FULL_ROW_WIDTH / 2, fromSlot(9, 0)[1], FULL_ROW_WIDTH, 20, mapOptions.radarTextScale, 0.5D, 2.0D, value -> {
                mapOptions.radarTextScale = (float) value;
                MapSettingsManager.instance.saveAll();
            }, value -> "Radar Text Scale: " + String.format(Locale.ROOT, "%.2fx", value));
            addOptionButton(radarTextScaleSlider);
        } else if (relevantOptions == RADAR_FULL_OPTIONS || relevantOptions == RADAR_SIMPLE_OPTIONS) {
            addSection("Radar Core", 0, 1);
            addMappedOption(EnumOptionsMinimap.SHOW_RADAR, 0, 0);
            addMappedOption(EnumOptionsMinimap.RADAR_MODE, 0, 1);
            addMappedOption(EnumOptionsMinimap.SHOW_MOBS, 1, 0);
            addMappedOption(EnumOptionsMinimap.SHOW_PLAYERS, 1, 1);

            addSection("Entity Labels", 3, relevantOptions == RADAR_FULL_OPTIONS ? 8 : 5);
            int row = 3;
            if (relevantOptions == RADAR_FULL_OPTIONS) {
                addMappedOption(EnumOptionsMinimap.SHOW_MOB_NAMES, row, 0);
                addMappedOption(EnumOptionsMinimap.SHOW_PLAYER_NAMES, row, 1);
                row++;
                addMappedOption(EnumOptionsMinimap.SHOW_MOB_HELMETS, row, 0);
                addMappedOption(EnumOptionsMinimap.SHOW_PLAYER_HELMETS, row, 1);
                row++;
                addMappedOption(EnumOptionsMinimap.RADAR_FILTERING, row, 0);
                addMappedOption(EnumOptionsMinimap.RADAR_OUTLINES, row, 1);
                row++;
                addMappedOption(EnumOptionsMinimap.RADAR_CPU_RENDERING, row, 0);
                addMappedOption(EnumOptionsMinimap.SHOW_FULL_ENTITY_NAMES, row, 1);
                row++;
                addMappedOption(EnumOptionsMinimap.SHOW_ENTITY_ELEVATION, row, 0);
                addMappedOption(EnumOptionsMinimap.HIDE_SNEAKING_PLAYERS, row, 1);
                row++;
                addMappedOption(EnumOptionsMinimap.HIDE_INVISIBLE_ENTITIES, row, -1);
                row++;
            } else {
                addMappedOption(EnumOptionsMinimap.SHOW_FACING, row, 0);
                addMappedOption(EnumOptionsMinimap.SHOW_ENTITY_ELEVATION, row, 1);
                row++;
                addMappedOption(EnumOptionsMinimap.HIDE_SNEAKING_PLAYERS, row, 0);
                addMappedOption(EnumOptionsMinimap.HIDE_INVISIBLE_ENTITIES, row, 1);
                row++;
            }

            addSection("Entity Display & Markers", row + 1, row + 4);
            row++;
            int actionY = fromSlot(row, 0)[1];
            if (relevantOptions == RADAR_FULL_OPTIONS) {
                mobListButton = createModernButton(fromSlot(row, 0)[0], actionY, OPTION_BUTTON_WIDTH, Component.translatable("options.minimap.radar.selectMobs"), x -> minecraft.setScreen(new GuiMobs(this, radarOptions)));
                addOptionButton(mobListButton);
            }

            int left = fromSlot(0, 0)[0];
            int right = fromSlot(0, 1)[0];
            int netherX = relevantOptions == RADAR_FULL_OPTIONS ? right : left;
            netherPortalGeneralButton = createModernButton(netherX, actionY, OPTION_BUTTON_WIDTH, Component.empty(), button -> {
                mapOptions.showNetherPortalMarkers = !mapOptions.showNetherPortalMarkers;
                button.setMessage(Component.literal("Nether Portals: " + (mapOptions.showNetherPortalMarkers ? "ON" : "OFF")));
                MapSettingsManager.instance.saveAll();
            });
            netherPortalGeneralButton.setMessage(Component.literal("Nether Portals: " + (mapOptions.showNetherPortalMarkers ? "ON" : "OFF")));
            addOptionButton(netherPortalGeneralButton);
            actionY += relevantOptions == RADAR_FULL_OPTIONS ? 22 : 0;
            endPortalGeneralButton = createModernButton(relevantOptions == RADAR_FULL_OPTIONS ? left : right, actionY, OPTION_BUTTON_WIDTH, Component.empty(), button -> {
                mapOptions.showEndPortalMarkers = !mapOptions.showEndPortalMarkers;
                button.setMessage(Component.literal("End Portals: " + (mapOptions.showEndPortalMarkers ? "ON" : "OFF")));
                MapSettingsManager.instance.saveAll();
            });
            endPortalGeneralButton.setMessage(Component.literal("End Portals: " + (mapOptions.showEndPortalMarkers ? "ON" : "OFF")));
            addOptionButton(endPortalGeneralButton);
            endGatewayGeneralButton = createModernButton(relevantOptions == RADAR_FULL_OPTIONS ? right : left, actionY, OPTION_BUTTON_WIDTH, Component.empty(), button -> {
                mapOptions.showEndGatewayMarkers = !mapOptions.showEndGatewayMarkers;
                button.setMessage(Component.literal("End Gateways: " + (mapOptions.showEndGatewayMarkers ? "ON" : "OFF")));
                MapSettingsManager.instance.saveAll();
            });
            endGatewayGeneralButton.setMessage(Component.literal("End Gateways: " + (mapOptions.showEndGatewayMarkers ? "ON" : "OFF")));
            addOptionButton(endGatewayGeneralButton);
        }

        layoutPageNavigation(0);

        setButtonsActive();
        updateMinimapZoomSlider();

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

    private Button createModernButton(int x, int y, int width, Component message, Button.OnPress onPress) {
        return new GuiOptionButtonMinimap(x, y, width, null, message, onPress);
    }

    private void addSection(String title, int firstRow, int lastRow) {
        int panelX = getWidth() / 2 - FULL_ROW_WIDTH / 2 - 12;
        int panelY = fromSlot(firstRow, 0)[1] - 15;
        int panelWidth = FULL_ROW_WIDTH + 24;
        int panelHeight = (lastRow - firstRow + 1) * 22 + 16;
        optionSections.add(new OptionSection(title, panelX, panelY, panelWidth, panelHeight));
    }

    private void addMappedOption(EnumOptionsMinimap option, int row, int col) {
        ISettingsManager settingsManager = getSettingsManager(option);
        if (settingsManager == null) {
            return;
        }

        int x;
        int y = fromSlot(row, 0)[1];
        int width = OPTION_BUTTON_WIDTH;
        if (col < 0) {
            x = getWidth() / 2 - FULL_ROW_WIDTH / 2;
            width = FULL_ROW_WIDTH;
        } else {
            x = fromSlot(row, col)[0];
        }

        if (option.getType() == EnumOptionsMinimap.Type.BOOLEAN || option.getType() == EnumOptionsMinimap.Type.LIST) {
            StringBuilder text = new StringBuilder().append(settingsManager.getKeyText(option));
            if ((option == EnumOptionsMinimap.WATER_TRANSPARENCY || option == EnumOptionsMinimap.BLOCK_TRANSPARENCY || option == EnumOptionsMinimap.BIOMES)
                    && !mapOptions.multicore && settingsManager.getBooleanValue(option)) {
                text.insert(0, "§c");
            }
            GuiOptionButtonMinimap optionButton = new GuiOptionButtonMinimap(x, y, width, option, Component.literal(text.toString()), this::optionClicked);
            optionButton.setTooltip(createButtonTooltip(option));
            addOptionButton(optionButton);
            if (option == EnumOptionsMinimap.SLIME_CHUNKS) {
                slimeChunksButton = optionButton;
            }
        } else if (option == EnumOptionsMinimap.TELEPORT_COMMAND) {
            String buttonTeleportText = I18n.get("options.minimap.teleportCommand") + ": " + mapOptions.teleportCommand;
            teleportCommandButton = new GuiButtonText(getFont(), x, y, width, 20, Component.literal(buttonTeleportText), button -> teleportCommandButton.setEditing(true));
            teleportCommandButton.setText(mapOptions.teleportCommand);
            teleportCommandButton.active = mapOptions.serverTeleportCommand == null;
            addOptionButton(teleportCommandButton);
        }
    }

    private int[] fromSlot(int row, int col) {
        int x = getWidth() / 2 - FULL_ROW_WIDTH / 2 + col * (OPTION_BUTTON_WIDTH + OPTION_COLUMN_GAP);
        int y = getHeight() / 6 + 22 * row;
        return new int[] { x, y };
    }

    private void layoutPageNavigation(int pageCount) {
        if (pageCount <= 0) {
            pageNavY = height / 6 + 120;
            nextPageButton.setPosition(width / 2 + 140, pageNavY);
            prevPageButton.setPosition(width / 2 - 180, pageNavY);
            return;
        }

        int minNavY = height / 6 + 120;
        int maxBottom = 0;
        for (AbstractWidget widget : optionButtons) {
            if (widget.visible) {
                maxBottom = Math.max(maxBottom, widget.getY() + widget.getHeight());
            }
        }
        pageNavY = Math.max(minNavY, maxBottom + 4);
        nextPageButton.setPosition(width / 2 + 140, pageNavY);
        prevPageButton.setPosition(width / 2 - 180, pageNavY);
    }

    private void optionClicked(Button button) {
        if (!(button instanceof GuiOptionButtonMinimap button2)) {
            return;
        }
        EnumOptionsMinimap option = button2.returnEnumOptions();

        ISettingsManager settingsManager = getSettingsManager(option);
        if (settingsManager == null) return;

        if (option == EnumOptionsMinimap.SHOW_FACING_DEGREES && settingsManager == mapOptions) {
            mapOptions.cycleFacingDisplayMode();
        } else {
            MapSettingsManager.updateBooleanOrListValue(settingsManager, option);
        }

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
        MapSettingsManager.instance.saveAll();
        setButtonsActive();
    }

    private void setButtonsActive() {
        for (GuiEventListener button : children()) {
            if (!(button instanceof GuiOptionButtonMinimap button2)){
                continue;
            }
            EnumOptionsMinimap option = button2.returnEnumOptions();
            if (option == null) {
                continue;
            }

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
                case RADAR_CPU_RENDERING -> button2.active = button2.active && !radarOptions.forceCpuRendering;
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
        if (chunkOverlayButton != null) {
            chunkOverlayButton.active = mapOptions.minimapAllowed;
        }
        if (highlightTracerGeneralButton != null) {
            highlightTracerGeneralButton.active = mapOptions.minimapAllowed;
        }
        if (autoHideHighlightsWhenNearButton != null) {
            autoHideHighlightsWhenNearButton.active = mapOptions.minimapAllowed;
        }
        if (minimapZoomSlider != null) {
            minimapZoomSlider.active = mapOptions.minimapAllowed;
        }
        if (radarTextScaleSlider != null) {
            radarTextScaleSlider.active = mapOptions.minimapAllowed;
        }
        if (autoHideHighlightsDistanceSlider != null) {
            autoHideHighlightsDistanceSlider.active = mapOptions.minimapAllowed && mapOptions.autoHideHighlightsWhenNear;
        }
    }

    private Tooltip createButtonTooltip(EnumOptionsMinimap option) {
        MutableComponent tooltip = Component.empty();

        if (option == EnumOptionsMinimap.RADAR_CPU_RENDERING) {
            if (VoxelConstants.hasVulkanMod()) {
                tooltip.append(Component.translatable("options.minimap.radar.cpuRendering.tooltipVk").withStyle(ChatFormatting.RED));
                tooltip.append("\n");
            }
            tooltip.append(Component.translatable("options.minimap.radar.cpuRendering.tooltip"));
        } else {
            return null;
        }

        return Tooltip.create(tooltip);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (embeddedTabScreen != null) {
            embeddedTabScreen.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, height - layout.getFooterHeight() - 2, 0.0F, 0.0F, width, 2, 32, 2);
        if (!pageInfo.isEmpty()) {
            int barTop = pageNavY + 2;
            int barBottom = pageNavY + 18;

            graphics.fill(width / 2 - 40, barTop, width / 2 + 40, barBottom, 0x88000000);
            graphics.centeredText(font, pageInfo, width / 2, pageNavY + 6, 0xFFFFFFFF);
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
        if (autoHideHighlightsWhenNearButton != null) {
            autoHideHighlightsWhenNearButton.setMessage(Component.literal("Auto Remove Highlights: " + (mapOptions.autoHideHighlightsWhenNear ? "ON" : "OFF")));
        }
        if (autoHideHighlightsDistanceSlider != null) {
            autoHideHighlightsDistanceSlider.setActualValue(mapOptions.autoHideHighlightsNearDistance);
            autoHideHighlightsDistanceSlider.active = mapOptions.minimapAllowed && mapOptions.autoHideHighlightsWhenNear;
        }
    }

    private void updateMinimapZoomSlider() {
        if (minimapZoomSlider != null) {
            minimapZoomSlider.setActualValue(mapOptions.zoom);
        }
    }

    private static String formatMinimapZoomFactor(int internalZoom) {
        int clamped = Math.max(0, Math.min(4, internalZoom));
        double factor = Math.pow(2.0, 2.0 - clamped);
        if (factor >= 1.0) {
            return ((int) factor) + "x";
        }
        return String.format(Locale.ROOT, "%.2fx", factor);
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
        if (embeddedTabScreen == null) {
            renderOptionSections(graphics);
        }
    }

    private void renderOptionSections(GuiGraphicsExtractor graphics) {
        for (OptionSection section : optionSections) {
            int x = section.x();
            int y = section.y();
            int right = x + section.width();
            int bottom = y + section.height();
            graphics.fill(x + 12, y + 9, x + 30, y + 10, 0xFFA9B4C3);
            graphics.text(this.font, section.title(), x + 36, y + 5, 0xFFE6EAF0, false);
        }
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
            if (embeddedTabScreen instanceof GuiMinimapControls controls
                    && controls.unbindEditingKey()) {
                return true;
            }
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

    private record OptionSection(String title, int x, int y, int width, int height) {
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
