package com.mamiyaotaru.voxelmap.gui.overridden;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.Function;

public class GuiValueSliderMinimap extends AbstractSliderButton {
    private final double minValue;
    private final double maxValue;
    private final DoubleConsumer onApply;
    private final Function<Double, String> labelFactory;

    public GuiValueSliderMinimap(int x, int y, int width, int height, double value, double minValue, double maxValue, DoubleConsumer onApply, Function<Double, String> labelFactory) {
        super(x, y, width, height, Component.literal(""), normalize(value, minValue, maxValue));
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.onApply = onApply;
        this.labelFactory = labelFactory;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(this.labelFactory.apply(getActualValue())));
    }

    @Override
    protected void applyValue() {
        this.onApply.accept(getActualValue());
    }

    public double getActualValue() {
        return this.minValue + (this.maxValue - this.minValue) * this.value;
    }

    public void setActualValue(double value) {
        if (isHovered()) {
            return;
        }
        this.value = normalize(value, this.minValue, this.maxValue);
        updateMessage();
    }

    private static double normalize(double value, double minValue, double maxValue) {
        if (maxValue <= minValue) {
            return 0.0D;
        }
        double clamped = Math.max(minValue, Math.min(maxValue, value));
        return (clamped - minValue) / (maxValue - minValue);
    }
}
