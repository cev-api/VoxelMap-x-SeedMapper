package com.mamiyaotaru.voxelmap.gui.overridden;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class GuiOptionButtonMinimap extends Button.Plain {
    private final EnumOptionsMinimap enumOptions;

    public GuiOptionButtonMinimap(int x, int y, EnumOptionsMinimap par4EnumOptions, Component message, OnPress onPress) {
        super (x, y, 150, 20, message, onPress, DEFAULT_NARRATION);
        this.enumOptions = par4EnumOptions;
    }

    public GuiOptionButtonMinimap(int x, int y, int width, EnumOptionsMinimap par4EnumOptions, Component message, OnPress onPress) {
        super (x, y, width, 20, message, onPress, DEFAULT_NARRATION);
        this.enumOptions = par4EnumOptions;
    }

    public EnumOptionsMinimap returnEnumOptions() { return this.enumOptions; }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = this.active && this.isHovered();
        int x = this.getX();
        int y = this.getY();
        int right = x + this.getWidth();
        int bottom = y + this.getHeight();
        int topColor = this.active ? (hovered ? 0xFF536071 : 0xFF3D4652) : 0xFF252A31;
        int bottomColor = this.active ? (hovered ? 0xFF404B5B : 0xFF303945) : 0xFF20242A;
        int borderColor = hovered ? 0xFF8FA7C8 : 0xFF1A2028;
        int accentColor = hovered ? 0xFF7DB7FF : 0xFF5A8FD8;

        graphics.fill(x, y, right, bottom, 0xAA05070A);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, borderColor);
        graphics.fillGradient(x + 2, y + 2, right - 2, bottom - 2, topColor, bottomColor);
        graphics.fill(x + 3, y + 3, right - 3, y + 4, 0x55FFFFFF);
        graphics.fill(x + 2, bottom - 3, right - 2, bottom - 2, 0x66000000);
        if (hovered) {
            graphics.fill(x + 2, y + 2, x + 5, bottom - 2, accentColor);
        }

        super.extractContents(graphics, mouseX, mouseY, delta);
    }
}
