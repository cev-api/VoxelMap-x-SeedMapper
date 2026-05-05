package com.mamiyaotaru.voxelmap.persistent;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiButtonText;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiOptionButtonMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiOptionSliderMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

public class GuiPersistentMapOptions extends GuiScreenMinimap {
    private static final float WORLDMAP_ZOOM_POWER_MIN = -9.0F;
    private static final float WORLDMAP_ZOOM_POWER_MAX = 8.0F;
    private static final float WORLDMAP_CACHE_MAX = 20000.0F;
    private final PersistentMapSettingsManager options;
    private final MapSettingsManager mapOptions;
    private final RadarSettingsManager radarOptions;
    private final Component screenTitle = Component.translatable("options.worldmap.title");
    private final Component cacheSettings = Component.translatable("options.worldmap.cacheSettings");
    private final Component warning = Component.translatable("options.worldmap.warning").withStyle(ChatFormatting.RED);
    private Button exploredChunkLinesButton;
    private Button clearExploredChunksButton;
    private long clearExploredConfirmUntilMs;
    private GuiButtonText exploredChunkLineColorInput;
    private Button exploredChunkLineColorPickerButton;
    private GuiColorPickerContainer exploredChunkLineColorPicker;
    private Button exploredChunkLineColorPickerModeButton;
    private Button exploredChunkLineColorPickerApplyButton;
    private Button exploredChunkLineColorPickerCancelButton;
    private boolean exploredChunkLineColorPickerOpen;
    private boolean swallowExploredChunkLineColorMouseRelease;
    private final ArrayList<OptionSection> optionSections = new ArrayList<>();
    private static final int OPTION_BUTTON_WIDTH = 190;
    private static final int OPTION_COLUMN_GAP = 10;
    private static final int FULL_ROW_WIDTH = OPTION_BUTTON_WIDTH * 2 + OPTION_COLUMN_GAP;

    public GuiPersistentMapOptions(Screen parent) {
        this.lastScreen = parent;

        this.options = VoxelConstants.getVoxelMapInstance().getPersistentMapOptions();
        this.mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
        this.radarOptions = VoxelConstants.getVoxelMapInstance().getRadarOptions();
    }

    @Override
    public void init() {
        optionSections.clear();

        addSection("Map Display", 0, 2);
        addMappedOption(EnumOptionsMinimap.SHOW_WORLDMAP_COORDS, 0, 0);
        addMappedOption(EnumOptionsMinimap.SHOW_WORLDMAP_PLAYER_DIRECTION_ARROW, 0, 1);
        addMappedOption(EnumOptionsMinimap.WORLDMAP_LITERAL_LINE_MODE, 1, 0);
        addMappedOption(EnumOptionsMinimap.CONFIRM_WAYPOINT_DELETE, 1, 1);
        addMappedOption(EnumOptionsMinimap.WORLDMAP_SHOW_NEW_OLD_CHUNKS, 2, -1);

        addSection("Waypoints", 3, 4);
        addMappedOption(EnumOptionsMinimap.SHOW_WAYPOINTS, 3, 0);
        addMappedOption(EnumOptionsMinimap.SHOW_WAYPOINT_NAMES, 3, 1);
        addMappedOption(EnumOptionsMinimap.SHOW_DISTANT_WAYPOINTS, 4, 0);
        addMappedOption(EnumOptionsMinimap.WORLDMAP_SHOW_WAYPOINTS_IN_PERFORMANCE_MODE, 4, 1);

        addSection("Explored Chunks", 6, 7);
        int exploredButtonY = fromSlot(6, 0)[1];
        this.exploredChunkLinesButton = this.addRenderableWidget(new GuiOptionButtonMinimap(fromSlot(6, 0)[0], exploredButtonY, OPTION_BUTTON_WIDTH, null, Component.empty(), button -> {
            radarOptions.showExploredChunks = !radarOptions.showExploredChunks;
            mapOptions.saveAll();
            refreshExploredChunkButtons();
        }));
        int colorInputWidth = OPTION_BUTTON_WIDTH - 32;
        this.exploredChunkLineColorInput = new GuiButtonText(this.getFont(), fromSlot(6, 1)[0], exploredButtonY, colorInputWidth, 20, Component.literal("Line Color"), button -> {});
        this.exploredChunkLineColorInput.active = false;
        this.exploredChunkLineColorInput.setText(normalizeHexColor(radarOptions.exploredChunksColor));
        this.addRenderableWidget(this.exploredChunkLineColorInput);
        this.exploredChunkLineColorPickerButton = this.addRenderableWidget(new GuiOptionButtonMinimap(fromSlot(6, 1)[0] + colorInputWidth + 4, exploredButtonY, 28, null, Component.literal("..."), button -> openExploredChunkLineColorPicker()));
        this.clearExploredChunksButton = this.addRenderableWidget(new GuiOptionButtonMinimap(this.getWidth() / 2 - FULL_ROW_WIDTH / 2, fromSlot(7, 0)[1], FULL_ROW_WIDTH, null, Component.literal("Clear Explored Chunks"), button -> {
            long now = System.currentTimeMillis();
            if (now > clearExploredConfirmUntilMs) {
                clearExploredConfirmUntilMs = now + 4000L;
                refreshExploredChunkButtons();
                return;
            }
            clearExploredConfirmUntilMs = 0L;
            VoxelConstants.getVoxelMapInstance().getExploredChunksManager().clearCurrentWorld();
            refreshExploredChunkButtons();
        }));
        refreshExploredChunkButtons();

        addSection("Zoom & Performance", 9, 12);
        addMappedOption(EnumOptionsMinimap.MIN_ZOOM, 9, 0);
        addMappedOption(EnumOptionsMinimap.MAX_ZOOM, 9, 1);
        addMappedOption(EnumOptionsMinimap.WORLDMAP_PERFORMANCE_MODE_THRESHOLD, 10, 0);
        addMappedOption(EnumOptionsMinimap.WORLDMAP_CHUNK_LINE_THICKNESS, 10, 1);
        addMappedOption(EnumOptionsMinimap.CACHE_SIZE, 12, -1);

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), buttonx -> this.onClose()).bounds(this.getWidth() / 2 - 100, this.getHeight() - 26, 200, 20).build());

        setButtonsActive();
    }

    private void addMappedOption(EnumOptionsMinimap option, int row, int col) {
        int x;
        int y = fromSlot(row, 0)[1];
        int width = OPTION_BUTTON_WIDTH;
        if (col < 0) {
            x = this.getWidth() / 2 - FULL_ROW_WIDTH / 2;
            width = FULL_ROW_WIDTH;
        } else {
            x = fromSlot(row, col)[0];
        }

        if (option.getType() == EnumOptionsMinimap.Type.FLOAT) {
            float sValue = this.options.getFloatValue(option);
            float sliderValue = switch (option) {
                case MIN_ZOOM, MAX_ZOOM -> Mth.clamp((sValue - WORLDMAP_ZOOM_POWER_MIN) / (WORLDMAP_ZOOM_POWER_MAX - WORLDMAP_ZOOM_POWER_MIN), 0.0F, 1.0F);
                case WORLDMAP_PERFORMANCE_MODE_THRESHOLD -> Mth.clamp(
                        (sValue - PersistentMapSettingsManager.MIN_PERFORMANCE_MODE_THRESHOLD)
                                / (PersistentMapSettingsManager.MAX_PERFORMANCE_MODE_THRESHOLD - PersistentMapSettingsManager.MIN_PERFORMANCE_MODE_THRESHOLD),
                        0.0F,
                        1.0F);
                case WORLDMAP_CHUNK_LINE_THICKNESS -> Mth.clamp(
                        (sValue - PersistentMapSettingsManager.MIN_CHUNK_LINE_THICKNESS)
                                / (PersistentMapSettingsManager.MAX_CHUNK_LINE_THICKNESS - PersistentMapSettingsManager.MIN_CHUNK_LINE_THICKNESS),
                        0.0F,
                        1.0F);
                case CACHE_SIZE -> Mth.clamp(sValue / WORLDMAP_CACHE_MAX, 0.0F, 1.0F);
                default -> throw new IllegalArgumentException("Add code to handle EnumOptionMinimap: " + option.getName() + ". (possibly not a float value applicable to persistent map)");
            };
            this.addRenderableWidget(new GuiOptionSliderMinimap(x, y, width, option, sliderValue, this.options));
        } else {
            GuiOptionButtonMinimap optionButton = new GuiOptionButtonMinimap(x, y, width, option, Component.literal(this.getKeyText(option)), this::optionClicked);
            this.addRenderableWidget(optionButton);
        }
    }

    private int[] fromSlot(int row, int col) {
        int x = this.getWidth() / 2 - FULL_ROW_WIDTH / 2 + col * (OPTION_BUTTON_WIDTH + OPTION_COLUMN_GAP);
        int y = this.getHeight() / 6 + 22 * row;
        return new int[] { x, y };
    }

    private void addSection(String title, int firstRow, int lastRow) {
        int panelX = this.getWidth() / 2 - FULL_ROW_WIDTH / 2 - 12;
        int panelY = fromSlot(firstRow, 0)[1] - 15;
        int panelWidth = FULL_ROW_WIDTH + 24;
        int panelHeight = (lastRow - firstRow + 1) * 22 + 16;
        optionSections.add(new OptionSection(title, panelX, panelY, panelWidth, panelHeight));
    }

    protected void optionClicked(Button par1GuiButton) {
        EnumOptionsMinimap option = ((GuiOptionButtonMinimap) par1GuiButton).returnEnumOptions();
        MapSettingsManager.updateBooleanOrListValue(this.getSettingsManager(option), option);
        par1GuiButton.setMessage(Component.literal(this.getKeyText(option)));

        setButtonsActive();
    }

    private void refreshExploredChunkButtons() {
        long now = System.currentTimeMillis();
        if (clearExploredConfirmUntilMs > 0L && now > clearExploredConfirmUntilMs) {
            clearExploredConfirmUntilMs = 0L;
        }
        if (this.exploredChunkLinesButton != null) {
            this.exploredChunkLinesButton.setMessage(Component.literal("Explored Chunk Lines: " + (radarOptions.showExploredChunks ? "ON" : "OFF")));
        }
        if (this.exploredChunkLineColorInput != null) {
            String color = normalizeHexColor(radarOptions.exploredChunksColor);
            this.exploredChunkLineColorInput.setText(color);
            this.exploredChunkLineColorInput.setMessage(Component.literal("Line Color: " + color));
            this.exploredChunkLineColorInput.active = radarOptions.showExploredChunks;
        }
        if (this.exploredChunkLineColorPickerButton != null) {
            this.exploredChunkLineColorPickerButton.active = radarOptions.showExploredChunks;
        }
        if (this.clearExploredChunksButton != null) {
            this.clearExploredChunksButton.setMessage(Component.literal(clearExploredConfirmUntilMs > 0L
                    ? "Confirm Clear Explored Chunks"
                    : "Clear Explored Chunks"));
            this.clearExploredChunksButton.active = radarOptions.showExploredChunks;
        }
    }

    private static String normalizeHexColor(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        return normalized.matches("#[0-9A-F]{6}") ? normalized : "#22A8FF";
    }

    private String getKeyText(EnumOptionsMinimap option) {
        return this.getSettingsManager(option).getKeyText(option);
    }

    private com.mamiyaotaru.voxelmap.interfaces.ISettingsManager getSettingsManager(EnumOptionsMinimap option) {
        return option == EnumOptionsMinimap.CONFIRM_WAYPOINT_DELETE ? this.mapOptions : this.options;
    }

    private void setButtonsActive() {
        for (GuiEventListener renderable : this.children()) {
            if (!(renderable instanceof GuiOptionButtonMinimap button)) continue;
            if (button.returnEnumOptions() == null) continue;

            switch (button.returnEnumOptions()) {
                case SHOW_WAYPOINT_NAMES, SHOW_DISTANT_WAYPOINTS -> button.active = options.showWaypoints && mapOptions.waypointsAllowed;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        refreshExploredChunkButtons();
        for (Object buttonObj : this.children()) {
            if (buttonObj instanceof GuiOptionSliderMinimap slider) {
                EnumOptionsMinimap option = slider.returnEnumOptions();
                float sValue = this.options.getFloatValue(option);
                float fValue;

                fValue = switch (option) {
                    case MIN_ZOOM, MAX_ZOOM -> Mth.clamp((sValue - WORLDMAP_ZOOM_POWER_MIN) / (WORLDMAP_ZOOM_POWER_MAX - WORLDMAP_ZOOM_POWER_MIN), 0.0F, 1.0F);
                    case WORLDMAP_PERFORMANCE_MODE_THRESHOLD -> Mth.clamp(
                            (sValue - PersistentMapSettingsManager.MIN_PERFORMANCE_MODE_THRESHOLD)
                                    / (PersistentMapSettingsManager.MAX_PERFORMANCE_MODE_THRESHOLD - PersistentMapSettingsManager.MIN_PERFORMANCE_MODE_THRESHOLD),
                            0.0F,
                            1.0F);
                    case WORLDMAP_CHUNK_LINE_THICKNESS -> Mth.clamp(
                            (sValue - PersistentMapSettingsManager.MIN_CHUNK_LINE_THICKNESS)
                                    / (PersistentMapSettingsManager.MAX_CHUNK_LINE_THICKNESS - PersistentMapSettingsManager.MIN_CHUNK_LINE_THICKNESS),
                            0.0F,
                            1.0F);
                    case CACHE_SIZE -> Mth.clamp(sValue / WORLDMAP_CACHE_MAX, 0.0F, 1.0F);
                    default -> throw new IllegalArgumentException("Add code to handle EnumOptionMinimap: " + option.getName() + ". (possibly not a float value applicable to persistent map)");
                };
                if (this.getFocused() != slider) {
                    slider.setValue(fValue);
                }
            }
        }

        if (!isEmbeddedInParent()) {
            graphics.centeredText(this.getFont(), this.screenTitle, this.getWidth() / 2, 20, 0xFFFFFFFF);
        }

        renderOptionSections(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (exploredChunkLineColorPickerOpen) {
            graphics.nextStratum();
            extractTransparentBackground(graphics);

            int popupX0 = exploredChunkLineColorPicker.getX() - (exploredChunkLineColorPicker.getWidth() / 2) - 30;
            int popupY0 = exploredChunkLineColorPicker.getY() - (exploredChunkLineColorPicker.getHeight() / 2) - 10;
            int popupW = exploredChunkLineColorPicker.getWidth() + 60;
            int popupH = exploredChunkLineColorPicker.getHeight() + 56;
            TooltipRenderUtil.extractTooltipBackground(graphics, popupX0, popupY0, popupW, popupH, null);

            exploredChunkLineColorPicker.extractRenderState(graphics, mouseX, mouseY, delta);
            int pickerColor = exploredChunkLineColorPicker.getColor() & 0x00FFFFFF;
            String colorText = "#" + String.format("%06X", pickerColor);
            int textX = (this.getWidth() - exploredChunkLineColorPicker.getWidth()) / 2;
            int textY = (this.getHeight() + exploredChunkLineColorPicker.getHeight()) / 2 + 8;
            int textWidth = this.getFont().width(colorText);
            graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, ARGB.opaque(pickerColor));
            graphics.fill(textX - 1, textY, textX + textWidth + 1, textY + 8, ARGB.black(0.2F));
            graphics.text(this.getFont(), colorText, textX, textY, 0xFFFFFFFF, false);

            int buttonsY = textY + 16;
            int centerX = this.getWidth() / 2;
            exploredChunkLineColorPickerModeButton.setPosition(centerX - 106, buttonsY);
            exploredChunkLineColorPickerApplyButton.setPosition(centerX - 32, buttonsY - 1);
            exploredChunkLineColorPickerCancelButton.setPosition(centerX + 42, buttonsY - 1);
            exploredChunkLineColorPickerModeButton.extractRenderState(graphics, mouseX, mouseY, delta);
            exploredChunkLineColorPickerApplyButton.extractRenderState(graphics, mouseX, mouseY, delta);
            exploredChunkLineColorPickerCancelButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    private void renderOptionSections(GuiGraphicsExtractor graphics) {
        for (OptionSection section : optionSections) {
            int x = section.x();
            int y = section.y();
            int right = x + section.width();
            int bottom = y + section.height();
            graphics.fill(x + 12, y + 9, x + 30, y + 10, 0xFFA9B4C3);
            graphics.text(this.getFont(), section.title(), x + 36, y + 5, 0xFFE6EAF0, false);
        }
    }

    private record OptionSection(String title, int x, int y, int width, int height) {
    }

    private void openExploredChunkLineColorPicker() {
        boolean simpleMode = mapOptions.colorPickerMode == 0;
        exploredChunkLineColorPicker = new GuiColorPickerContainer(this.getWidth() / 2, this.getHeight() / 2, 200, 140, simpleMode, picker -> {});
        exploredChunkLineColorPicker.setColor(parseColor(radarOptions.exploredChunksColor, 0x22A8FF));
        exploredChunkLineColorPickerModeButton = new Button.Builder(Component.literal(mapOptions.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleExploredChunkLineColorPickerMode)
                .bounds(0, 0, 66, 16)
                .build();
        exploredChunkLineColorPickerApplyButton = new Button.Builder(Component.translatable("gui.done"), button -> applyExploredChunkLineColorPicker())
                .bounds(0, 0, 66, 18)
                .build();
        exploredChunkLineColorPickerCancelButton = new Button.Builder(Component.translatable("gui.cancel"), button -> closeExploredChunkLineColorPicker())
                .bounds(0, 0, 66, 18)
                .build();
        exploredChunkLineColorPickerOpen = true;
        swallowExploredChunkLineColorMouseRelease = true;
    }

    private void closeExploredChunkLineColorPicker() {
        exploredChunkLineColorPickerOpen = false;
    }

    private void cycleExploredChunkLineColorPickerMode(Button button) {
        mapOptions.colorPickerMode = mapOptions.colorPickerMode == 0 ? 1 : 0;
        exploredChunkLineColorPicker.updateMode(mapOptions.colorPickerMode == 0);
        exploredChunkLineColorPickerModeButton.setMessage(Component.literal(mapOptions.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)));
        mapOptions.saveAll();
    }

    private void applyExploredChunkLineColorPicker() {
        radarOptions.exploredChunksColor = "#" + String.format("%06X", exploredChunkLineColorPicker.getColor() & 0x00FFFFFF);
        closeExploredChunkLineColorPicker();
        refreshExploredChunkButtons();
        mapOptions.saveAll();
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (exploredChunkLineColorPickerOpen) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeExploredChunkLineColorPicker();
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applyExploredChunkLineColorPicker();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (exploredChunkLineColorPickerOpen) {
            exploredChunkLineColorPicker.mouseClicked(mouseButtonEvent, doubleClick);
            exploredChunkLineColorPickerModeButton.mouseClicked(mouseButtonEvent, doubleClick);
            exploredChunkLineColorPickerApplyButton.mouseClicked(mouseButtonEvent, doubleClick);
            exploredChunkLineColorPickerCancelButton.mouseClicked(mouseButtonEvent, doubleClick);
            swallowExploredChunkLineColorMouseRelease = true;
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent) {
        if (exploredChunkLineColorPickerOpen) {
            boolean swallowed = swallowExploredChunkLineColorMouseRelease;
            swallowExploredChunkLineColorMouseRelease = false;
            exploredChunkLineColorPicker.mouseReleased(mouseButtonEvent);
            exploredChunkLineColorPickerModeButton.mouseReleased(mouseButtonEvent);
            exploredChunkLineColorPickerApplyButton.mouseReleased(mouseButtonEvent);
            exploredChunkLineColorPickerCancelButton.mouseReleased(mouseButtonEvent);
            return swallowed || true;
        }
        return super.mouseReleased(mouseButtonEvent);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        if (exploredChunkLineColorPickerOpen) {
            exploredChunkLineColorPicker.mouseDragged(mouseButtonEvent, deltaX, deltaY);
            return true;
        }
        return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        if (exploredChunkLineColorPickerOpen) {
            exploredChunkLineColorPicker.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
    }
}
