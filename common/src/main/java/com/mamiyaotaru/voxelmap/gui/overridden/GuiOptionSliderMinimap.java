package com.mamiyaotaru.voxelmap.gui.overridden;

import com.mamiyaotaru.voxelmap.interfaces.ISettingsManager;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class GuiOptionSliderMinimap extends AbstractSliderButton {
    private final ISettingsManager options;
    private final EnumOptionsMinimap option;

    public GuiOptionSliderMinimap(int x, int y, EnumOptionsMinimap optionIn, float value, ISettingsManager options) {
        this(x, y, 150, optionIn, value, options);
    }

    public GuiOptionSliderMinimap(int x, int y, int width, EnumOptionsMinimap optionIn, float value, ISettingsManager options) {
        super (x, y, width, 20, Component.literal(options.getKeyText(optionIn)), Mth.clamp(value, 0.0F, 1.0F));
        this.options = options;
        this.option = optionIn;
    }

    @Override
    protected void updateMessage() { setMessage(Component.literal(this.options.getKeyText(this.option))); }

    @Override
    protected void applyValue() { this.options.setFloatValue(option, (float) this.value); }

    public EnumOptionsMinimap returnEnumOptions() { return option; }

    public void setValue(float value) {
        if (isHovered()) {
            return;
        }

        this.value = Mth.clamp(value, 0.0F, 1.0F);
        this.updateMessage();
    }
}
