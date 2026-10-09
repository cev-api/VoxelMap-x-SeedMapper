package com.mamiyaotaru.voxelmap.rendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

public record WorldMapQuadRenderState(RenderPipeline pipeline, TextureSetup textureSetup,
        Matrix3x2f pose, float[] vertices, int firstQuad, int count, int color,
        ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
    public WorldMapQuadRenderState {
        if (firstQuad < 0 || count < 0 || count > WorldMapGeometryBatch.MAX_QUADS
                || (long) firstQuad + count > vertices.length / 8)
            throw new IllegalArgumentException("Invalid world-map geometry batch");
    }
    @Override public void buildVertices(VertexConsumer consumer) {
        int end = (firstQuad + count) * 8;
        for (int i = firstQuad * 8; i < end; i += 2) consumer.addVertexWith2DPose(pose, vertices[i], vertices[i + 1]).setColor(color);
    }
}
