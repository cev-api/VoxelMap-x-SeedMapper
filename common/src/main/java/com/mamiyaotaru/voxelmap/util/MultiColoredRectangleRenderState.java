package com.mamiyaotaru.voxelmap.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * A single {@link GuiElementRenderState} that draws many solid rectangles batched.
 */
public final class MultiColoredRectangleRenderState implements GuiElementRenderState {
    private final RenderPipeline pipeline;
    private final TextureSetup textureSetup;
    private final Matrix3x2f pose;
    private final float[] coords;
    private final int[] colors;
    private final int count;
    @Nullable private final ScreenRectangle scissorArea;
    @Nullable private final ScreenRectangle bounds;

    public MultiColoredRectangleRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            float[] coords,
            int[] colors,
            int count,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) {
        this.pipeline = pipeline;
        this.textureSetup = textureSetup;
        this.pose = pose;
        this.coords = coords;
        this.colors = colors;
        this.count = count;
        this.scissorArea = scissorArea;
        this.bounds = bounds;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        for (int i = 0; i < this.count; i++) {
            int offset = i << 2;
            float x0 = this.coords[offset];
            float y0 = this.coords[offset + 1];
            float x1 = this.coords[offset + 2];
            float y1 = this.coords[offset + 3];
            int color = this.colors[i];
            vertexConsumer.addVertexWith2DPose(this.pose, x0, y0).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose, x0, y1).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose, x1, y1).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose, x1, y0).setColor(color);
        }
    }

    public RenderPipeline pipeline() {
        return this.pipeline;
    }

    public TextureSetup textureSetup() {
        return this.textureSetup;
    }

    public Matrix3x2f pose() {
        return this.pose;
    }

    @Nullable
    public ScreenRectangle scissorArea() {
        return this.scissorArea;
    }

    @Nullable
    public ScreenRectangle bounds() {
        return this.bounds;
    }
}
