package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.chunkanalysis.ChunkAnalysisSettingsManager;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.ARGB;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Compact submenu for all Chunk Analysis overlay colors. */
public final class GuiChunkAnalysisColors extends GuiScreenMinimap {
    private static final int COLOR_BUTTON_WIDTH = 100;
    private static final int COLOR_LABEL_COLUMN_WIDTH = 170;
    private static final int COLOR_ROW_GAP = 20;
    private static final int COLOR_GROUP_WIDTH = COLOR_LABEL_COLUMN_WIDTH + COLOR_ROW_GAP + COLOR_BUTTON_WIDTH;
    private static final int COLOR_ROW_TOP_OFFSET = 24;
    private final ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
    private final ColorEntry[] colorEntries = new ColorEntry[7];
    private GuiColorPickerContainer colorPicker;
    private Button colorPickerModeButton;
    private Button colorPickerApplyButton;
    private Button colorPickerCancelButton;
    private Consumer<String> activeSetter;
    private Component activeTitle;
    private boolean swallowNextMouseRelease;

    public GuiChunkAnalysisColors(Screen parent) {
        this.lastScreen = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int y = height / 6 + COLOR_ROW_TOP_OFFSET;
        addColorButton(0, y, "options.voxelmap.chunkanalysis.missingExpectedColor", () -> settings.missingExpectedColor,
                value -> settings.missingExpectedColor = value);
        y += 28;
        addColorButton(1, y, "options.voxelmap.chunkanalysis.excavationColor", () -> settings.excavationColor,
                value -> settings.excavationColor = value);
        y += 28;
        addColorButton(2, y, "options.voxelmap.chunkanalysis.unexpectedColor", () -> settings.unexpectedColor,
                value -> settings.unexpectedColor = value);
        y += 28;
        addColorButton(3, y, "options.voxelmap.chunkanalysis.changedColor", () -> settings.changedColor,
                value -> settings.changedColor = value);
        y += 28;
        addColorButton(4, y, "options.voxelmap.chunkanalysis.inventoriesColor", () -> settings.inventoriesColor,
                value -> settings.inventoriesColor = value);
        y += 28;
        addColorButton(5, y, "options.voxelmap.chunkanalysis.redstoneColor", () -> settings.redstoneColor,
                value -> settings.redstoneColor = value);
        y += 28;
        addColorButton(6, y, "options.voxelmap.chunkanalysis.workstationsColor", () -> settings.workstationsColor,
                value -> settings.workstationsColor = value);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 75, y + 32, 150, 20).build());

        boolean simpleMode = VoxelConstants.getVoxelMapInstance().getMapOptions().colorPickerMode == 0;
        colorPicker = new GuiColorPickerContainer(width / 2, height / 2, 200, 140, simpleMode, picker -> {});
        colorPickerModeButton = Button.builder(Component.literal(VoxelConstants.getVoxelMapInstance().getMapOptions().getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleColorPickerMode)
                .bounds(0, 0, 66, 16).build();
        colorPickerApplyButton = Button.builder(Component.translatable("gui.done"), button -> applyColorPicker())
                .bounds(0, 0, 66, 18).build();
        colorPickerCancelButton = Button.builder(Component.translatable("gui.cancel"), button -> cancelColorPicker())
                .bounds(0, 0, 66, 18).build();
    }

    private void addColorButton(int index, int y, String labelKey, Supplier<String> getter, Consumer<String> setter) {
        int groupLeft = width / 2 - COLOR_GROUP_WIDTH / 2;
        Button button = addRenderableWidget(Button.builder(Component.literal("Color :"), ignored -> openColorPicker(labelKey, getter, setter))
                .bounds(groupLeft + COLOR_LABEL_COLUMN_WIDTH + COLOR_ROW_GAP, y, COLOR_BUTTON_WIDTH, 20).build());
        colorEntries[index] = new ColorEntry(button, Component.translatable(labelKey), getter);
    }

    private void openColorPicker(String titleKey, Supplier<String> getter, Consumer<String> setter) {
        activeTitle = Component.translatable(titleKey);
        activeSetter = setter;
        colorPicker.setColor(parseColor(getter.get()));
        swallowNextMouseRelease = true;
    }

    private boolean colorPickerOpen() {
        return activeSetter != null;
    }

    private void cycleColorPickerMode(Button ignored) {
        MapSettingsManager mapSettings = VoxelConstants.getVoxelMapInstance().getMapOptions();
        mapSettings.colorPickerMode = mapSettings.colorPickerMode == 0 ? 1 : 0;
        colorPicker.updateMode(mapSettings.colorPickerMode == 0);
        colorPickerModeButton.setMessage(Component.literal(mapSettings.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)));
        MapSettingsManager.instance.saveAll();
    }

    private void applyColorPicker() {
        if (activeSetter == null) {
            return;
        }
        activeSetter.accept("#" + String.format("%06X", colorPicker.getColor() & 0x00FFFFFF));
        activeSetter = null;
        activeTitle = null;
        MapSettingsManager.instance.saveAll();
    }

    private void cancelColorPicker() {
        activeSetter = null;
        activeTitle = null;
    }

    private static int parseColor(String color) {
        try {
            return Integer.parseInt(color.replace("#", ""), 16) & 0x00FFFFFF;
        } catch (RuntimeException ignored) {
            return 0xFFFFFF;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean pickerOpen = colorPickerOpen();
        int panelLeft = width / 2 - 270;
        int panelTop = height / 6 + 4;
        int rowTop = height / 6 + COLOR_ROW_TOP_OFFSET;
        int panelBottom = rowTop + 7 * 28 + 36;
        graphics.fill(panelLeft, panelTop, width / 2 + 270, panelBottom, 0x70000000);
        super.extractRenderState(graphics, pickerOpen ? 0 : mouseX, pickerOpen ? 0 : mouseY, delta);
        graphics.centeredText(getFont(), pickerOpen ? activeTitle : Component.translatable("options.voxelmap.chunkanalysis.colors"), width / 2, 16, 0xFFFFFFFF);
        if (!pickerOpen) {
            for (int i = 0; i < colorEntries.length; i++) {
                ColorEntry entry = colorEntries[i];
                drawLabel(graphics, i, entry.button, entry.getter.get());
            }
        }

        if (pickerOpen) {
            graphics.nextStratum();
            extractTransparentBackground(graphics);
            int popupX = colorPicker.getX() - colorPicker.getWidth() / 2 - 30;
            int popupY = colorPicker.getY() - colorPicker.getHeight() / 2 - 10;
            TooltipRenderUtil.extractTooltipBackground(graphics, popupX, popupY, colorPicker.getWidth() + 60, colorPicker.getHeight() + 56, null);
            colorPicker.extractRenderState(graphics, mouseX, mouseY, delta);

            String colorText = "#" + String.format("%06X", colorPicker.getColor() & 0x00FFFFFF);
            int textX = (width - colorPicker.getWidth()) / 2;
            int textY = (height + colorPicker.getHeight()) / 2 + 8;
            int textWidth = getFont().width(colorText);
            graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, ARGB.opaque(colorPicker.getColor() & 0x00FFFFFF));
            graphics.fill(textX - 1, textY, textX + textWidth + 1, textY + 8, ARGB.black(0.2F));
            graphics.text(getFont(), colorText, textX, textY, 0xFFFFFFFF, false);

            int buttonsY = textY + 16;
            int centerX = width / 2;
            colorPickerModeButton.setPosition(centerX - 106, buttonsY);
            colorPickerApplyButton.setPosition(centerX - 32, buttonsY - 1);
            colorPickerCancelButton.setPosition(centerX + 42, buttonsY - 1);
            colorPickerModeButton.extractRenderState(graphics, mouseX, mouseY, delta);
            colorPickerApplyButton.extractRenderState(graphics, mouseX, mouseY, delta);
            colorPickerCancelButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    private void drawLabel(GuiGraphicsExtractor graphics, int index, Button button, String color) {
        int x = width / 2 - COLOR_GROUP_WIDTH / 2;
        int y = height / 6 + COLOR_ROW_TOP_OFFSET + index * 28;
        graphics.text(getFont(), colorEntries[index].label(), x, y, 0xFFE0E0E0, false);
        int rgb = parseColor(color);
        int swatchRight = button.getX() + button.getWidth() - 8;
        int swatchLeft = swatchRight - 16;
        graphics.fill(swatchLeft - 1, button.getY() + 4, swatchRight + 1, button.getY() + 16, 0xFF000000);
        graphics.fill(swatchLeft, button.getY() + 5, swatchRight, button.getY() + 15, 0xFF000000 | rgb);
    }

    private record ColorEntry(Button button, Component label, Supplier<String> getter) {
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!colorPickerOpen()) {
            return super.keyPressed(keyEvent);
        }
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            cancelColorPicker();
            return true;
        }
        if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
            applyColorPicker();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!colorPickerOpen()) {
            return super.mouseClicked(event, doubleClick);
        }
        boolean handled = colorPicker.mouseClicked(event, doubleClick)
                || colorPickerModeButton.mouseClicked(event, doubleClick)
                || colorPickerApplyButton.mouseClicked(event, doubleClick)
                || colorPickerCancelButton.mouseClicked(event, doubleClick);
        if (handled) {
            swallowNextMouseRelease = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (swallowNextMouseRelease) {
            swallowNextMouseRelease = false;
            return true;
        }
        if (!colorPickerOpen()) {
            return super.mouseReleased(event);
        }
        colorPicker.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!colorPickerOpen()) {
            return super.mouseDragged(event, deltaX, deltaY);
        }
        colorPicker.mouseDragged(event, deltaX, deltaY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        return colorPickerOpen() || super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
    }
}
