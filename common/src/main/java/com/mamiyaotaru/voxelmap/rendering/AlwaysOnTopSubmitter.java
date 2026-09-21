package com.mamiyaotaru.voxelmap.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;

/**
 * Submits geometry and text at a fixed order so overlays drawn after the world
 * still end up on top of everything submitted before them.
 */
public final class AlwaysOnTopSubmitter {
    private final OrderedSubmitNodeCollector collector;

    private AlwaysOnTopSubmitter(OrderedSubmitNodeCollector collector) {
        this.collector = collector;
    }

    public static AlwaysOnTopSubmitter order(SubmitNodeCollector submitNodeCollector, int order) {
        return new AlwaysOnTopSubmitter(submitNodeCollector.order(order));
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        this.collector.submitCustomGeometry(poseStack, renderType, renderer);
    }

    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow, Font.DisplayMode displayMode,
                           int lightCoords, int color, int backgroundColor, int outlineColor) {
        this.collector.submitText(poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
    }
}
