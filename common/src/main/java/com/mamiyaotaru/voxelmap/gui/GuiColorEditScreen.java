package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiColorPickerContainer;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class GuiColorEditScreen extends GuiScreenMinimap {
    private final MapSettingsManager mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
    private final Component title;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private GuiColorPickerContainer colorPicker;
    private Button modeButton;

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

        colorPicker = new GuiColorPickerContainer(width / 2, height / 2 - 10, 200, 140, mapOptions.colorPickerMode == 0, picker -> {});
        colorPicker.setColor(previousColor);
        addRenderableWidget(colorPicker);

        int buttonsY = height / 2 + 76;
        modeButton = Button.builder(Component.literal(mapOptions.getListValue(EnumOptionsMinimap.COLOR_PICKER_MODE)), this::cycleMode)
                .bounds(width / 2 - 106, buttonsY, 66, 20).build();
        addRenderableWidget(modeButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> apply())
                .bounds(width / 2 - 32, buttonsY, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 + 42, buttonsY, 66, 20).build());
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
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(getFont(), title, width / 2, 16, 0xFFFFFFFF);

        int pickerColor = colorPicker.getColor() & 0x00FFFFFF;
        String colorText = "#" + String.format("%06X", pickerColor);
        int textX = (width - getFont().width(colorText)) / 2;
        int textY = height / 2 + 62;
        int textWidth = getFont().width(colorText);
        graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, ARGB.opaque(pickerColor));
        graphics.fill(textX - 1, textY, textX + textWidth + 1, textY + 8, ARGB.black(0.2F));
        graphics.text(getFont(), colorText, textX, textY, 0xFFFFFFFF, false);
    }
}
