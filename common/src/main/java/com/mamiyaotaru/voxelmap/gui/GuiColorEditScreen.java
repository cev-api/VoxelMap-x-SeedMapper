package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class GuiColorEditScreen extends GuiScreenMinimap {
    private final MapSettingsManager mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
    private final Component title;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private GuiColorPickerContainer colorPicker;
    private Button modeButton;
    private Button applyButton;
    private Button cancelButton;

    public GuiColorEditScreen(Screen parent, Component title, Supplier<String> getter, Consumer<String> setter) {
        super();
        this.lastScreen = parent;
        this.title = title;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void init() {
        int previousColor = colorPicker != null ? colorPicker.getColor() : parseColor(getter.get(), 0xFF0000);
        clearWidgets();

        colorPicker = new GuiColorPickerContainer(width / 2, height / 2, 200, 140, mapOptions.colorPickerMode == 0, picker -> {});
        colorPicker.setColor(previousColor);
        modeButton = Button.builder(Component.literal(mapOptions.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleMode)
                .bounds(0, 0, 66, 16).build();
        applyButton = Button.builder(Component.translatable("gui.done"), button -> apply())
                .bounds(0, 0, 66, 18).build();
        cancelButton = Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(0, 0, 66, 18).build();
    }

    private void cycleMode(Button button) {
        mapOptions.colorPickerMode = mapOptions.colorPickerMode == 0 ? 1 : 0;
        colorPicker.updateMode(mapOptions.colorPickerMode == 0);
        modeButton.setMessage(Component.literal(mapOptions.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)));
        MapSettingsManager.instance.saveAll();
    }

    private void apply() {
        setter.accept("#" + String.format("%06X", colorPicker.getColor() & 0x00FFFFFF));
        MapSettingsManager.instance.saveAll();
        onClose();
    }

    private static int parseColor(String colorText, int fallbackRgb) {
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, 0, 0, delta);
        graphics.centeredText(getFont(), title, width / 2, 16, 0xFFFFFFFF);

        graphics.nextStratum();
        extractTransparentBackground(graphics);

        int popupX = colorPicker.getX() - colorPicker.getWidth() / 2 - 30;
        int popupY = colorPicker.getY() - colorPicker.getHeight() / 2 - 10;
        TooltipRenderUtil.extractTooltipBackground(graphics, popupX, popupY, colorPicker.getWidth() + 60, colorPicker.getHeight() + 56, null);
        colorPicker.extractRenderState(graphics, mouseX, mouseY, delta);

        int pickerColor = colorPicker.getColor() & 0x00FFFFFF;
        String colorText = "#" + String.format("%06X", pickerColor);
        int textX = (width - colorPicker.getWidth()) / 2;
        int textY = (height + colorPicker.getHeight()) / 2 + 8;
        int textWidth = getFont().width(colorText);
        graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, ARGB.opaque(pickerColor));
        graphics.fill(textX - 1, textY, textX + textWidth + 1, textY + 8, ARGB.black(0.2F));
        graphics.text(getFont(), colorText, textX, textY, 0xFFFFFFFF, false);

        int buttonsY = textY + 16;
        int centerX = width / 2;
        modeButton.setPosition(centerX - 106, buttonsY);
        applyButton.setPosition(centerX - 32, buttonsY - 1);
        cancelButton.setPosition(centerX + 42, buttonsY - 1);
        modeButton.extractRenderState(graphics, mouseX, mouseY, delta);
        applyButton.extractRenderState(graphics, mouseX, mouseY, delta);
        cancelButton.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
            apply();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        colorPicker.mouseClicked(event, doubleClick);
        modeButton.mouseClicked(event, doubleClick);
        applyButton.mouseClicked(event, doubleClick);
        cancelButton.mouseClicked(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        colorPicker.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        colorPicker.mouseDragged(event, deltaX, deltaY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        return true;
    }
}
