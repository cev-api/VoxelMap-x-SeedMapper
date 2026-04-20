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

public class GuiPersistentMapOptions extends GuiScreenMinimap {
    private static final float WORLDMAP_ZOOM_POWER_MIN = -4.0F;
    private static final float WORLDMAP_ZOOM_POWER_MAX = 5.0F;
    private static final float WORLDMAP_CACHE_MAX = 20000.0F;
    private final PersistentMapSettingsManager options;
    private final MapSettingsManager mapOptions;
    private final RadarSettingsManager radarOptions;
    private final Component screenTitle = Component.translatable("options.worldmap.title");
    private final Component cacheSettings = Component.translatable("options.worldmap.cacheSettings");
    private final Component warning = Component.translatable("options.worldmap.warning").withStyle(ChatFormatting.RED);
    private Button exploredChunkLinesButton;
    private GuiButtonText exploredChunkLineColorInput;
    private Button exploredChunkLineColorPickerButton;
    private GuiColorPickerContainer exploredChunkLineColorPicker;
    private Button exploredChunkLineColorPickerModeButton;
    private Button exploredChunkLineColorPickerApplyButton;
    private Button exploredChunkLineColorPickerCancelButton;
    private boolean exploredChunkLineColorPickerOpen;
    private boolean swallowExploredChunkLineColorMouseRelease;

    public GuiPersistentMapOptions(Screen parent) {
        this.lastScreen = parent;

        this.options = VoxelConstants.getVoxelMapInstance().getPersistentMapOptions();
        this.mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
        this.radarOptions = VoxelConstants.getVoxelMapInstance().getRadarOptions();
    }

    @Override
    public void init() {
        EnumOptionsMinimap[] relevantOptions = {
                EnumOptionsMinimap.SHOW_WORLDMAP_COORDS,
                EnumOptionsMinimap.SHOW_WORLDMAP_PLAYER_DIRECTION_ARROW,
                EnumOptionsMinimap.SHOW_WAYPOINTS,
                EnumOptionsMinimap.SHOW_WAYPOINT_NAMES,
                EnumOptionsMinimap.SHOW_DISTANT_WAYPOINTS,
                EnumOptionsMinimap.CONFIRM_WAYPOINT_DELETE
        };

        int counter = 0;

        for (EnumOptionsMinimap option : relevantOptions) {
            GuiOptionButtonMinimap optionButton = new GuiOptionButtonMinimap(this.getWidth() / 2 - 155 + counter % 2 * 160, this.getHeight() / 6 + 24 * (counter >> 1), option, Component.literal(this.getKeyText(option)), this::optionClicked);
            this.addRenderableWidget(optionButton);

            if (option == EnumOptionsMinimap.SHOW_WAYPOINTS) {
                optionButton.active = mapOptions.waypointsAllowed;
            }
            if (option == EnumOptionsMinimap.SHOW_WAYPOINT_NAMES) {
                optionButton.active = mapOptions.waypointsAllowed;
            }
            counter++;
        }

        int exploredButtonY = this.getHeight() / 6 + 24 * (counter >> 1);
        this.exploredChunkLinesButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> {
            radarOptions.showExploredChunks = !radarOptions.showExploredChunks;
            mapOptions.saveAll();
            refreshExploredChunkButtons();
        }).bounds(this.getWidth() / 2 - 155, exploredButtonY, 150, 20).build());
        this.exploredChunkLineColorInput = new GuiButtonText(this.getFont(), this.getWidth() / 2 + 5, exploredButtonY, 118, 20, Component.literal("Line Color"), button -> {});
        this.exploredChunkLineColorInput.active = false;
        this.exploredChunkLineColorInput.setText(normalizeHexColor(radarOptions.exploredChunksColor));
        this.addRenderableWidget(this.exploredChunkLineColorInput);
        this.exploredChunkLineColorPickerButton = this.addRenderableWidget(new Button.Builder(Component.literal("..."), button -> openExploredChunkLineColorPicker())
                .bounds(this.getWidth() / 2 + 127, exploredButtonY, 28, 20).build());
        refreshExploredChunkButtons();
        counter += 2;

        EnumOptionsMinimap[] relevantOptions2 = { EnumOptionsMinimap.MIN_ZOOM, EnumOptionsMinimap.MAX_ZOOM, EnumOptionsMinimap.CACHE_SIZE };
        counter += (counter % 2 == 0 ? 2 : 3);

        for (EnumOptionsMinimap option : relevantOptions2) {
            if (option.getType() == EnumOptionsMinimap.Type.FLOAT) {
                float sValue = this.options.getFloatValue(option);

                this.addRenderableWidget(new GuiOptionSliderMinimap(this.getWidth() / 2 - 155 + counter % 2 * 160, this.getHeight() / 6 + 24 * (counter >> 1), option, switch (option) {
                    case MIN_ZOOM, MAX_ZOOM -> Mth.clamp((sValue - WORLDMAP_ZOOM_POWER_MIN) / (WORLDMAP_ZOOM_POWER_MAX - WORLDMAP_ZOOM_POWER_MIN), 0.0F, 1.0F);
                    case CACHE_SIZE -> Mth.clamp(sValue / WORLDMAP_CACHE_MAX, 0.0F, 1.0F);
                    default ->
                            throw new IllegalArgumentException("Add code to handle EnumOptionMinimap: " + option.getName() + ". (possibly not a float value applicable to persistent map)");
                }, this.options));
            } else {
                this.addRenderableWidget(new GuiOptionButtonMinimap(this.getWidth() / 2 - 155 + counter % 2 * 160, this.getHeight() / 6 + 24 * (counter >> 1), option, Component.literal(this.options.getKeyText(option)), this::optionClicked));
            }

            counter++;
        }

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.done"), buttonx -> this.onClose()).bounds(this.getWidth() / 2 - 100, this.getHeight() - 26, 200, 20).build());

        setButtonsActive();
    }

    protected void optionClicked(Button par1GuiButton) {
        EnumOptionsMinimap option = ((GuiOptionButtonMinimap) par1GuiButton).returnEnumOptions();
        MapSettingsManager.updateBooleanOrListValue(this.getSettingsManager(option), option);
        par1GuiButton.setMessage(Component.literal(this.getKeyText(option)));

        setButtonsActive();
    }

    private void refreshExploredChunkButtons() {
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

            switch (button.returnEnumOptions()) {
                case SHOW_WAYPOINT_NAMES, SHOW_DISTANT_WAYPOINTS -> button.active = options.showWaypoints && mapOptions.waypointsAllowed;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        for (Object buttonObj : this.children()) {
            if (buttonObj instanceof GuiOptionSliderMinimap slider) {
                EnumOptionsMinimap option = slider.returnEnumOptions();
                float sValue = this.options.getFloatValue(option);
                float fValue;

                fValue = switch (option) {
                    case MIN_ZOOM, MAX_ZOOM -> Mth.clamp((sValue - WORLDMAP_ZOOM_POWER_MIN) / (WORLDMAP_ZOOM_POWER_MAX - WORLDMAP_ZOOM_POWER_MIN), 0.0F, 1.0F);
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
            graphics.centeredText(this.getFont(), this.cacheSettings, this.getWidth() / 2, this.getHeight() / 6 + 49, 0xFFFFFFFF);
            graphics.centeredText(this.getFont(), this.warning, this.getWidth() / 2, this.getHeight() / 6 + 59, 0xFFFFFFFF);
        }

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
