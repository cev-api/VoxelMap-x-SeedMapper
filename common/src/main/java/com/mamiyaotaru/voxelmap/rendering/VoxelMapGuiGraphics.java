package com.mamiyaotaru.voxelmap.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;

public class VoxelMapGuiGraphics {
    private static final GpuSampler DEFAULT_SAMPLER = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.LINEAR, false);

    public static void blitFloatGradient(GuiGraphicsExtractor graphics, RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color, int color2) {
        graphics.guiRenderState.addGuiElement(new FloatBlitRenderState(pipeline, TextureSetup.singleTexture(texture, sampler),
                new Matrix3x2f(graphics.pose()), x, y, x + w, y + h,
                minu, maxu, minv, maxv, color, color2, graphics.scissorStack.peek()));
    }

    public static void blitFloatGradient(GuiGraphicsExtractor graphics, RenderPipeline pipeline, AbstractTexture texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color, int color2) {
        blitFloatGradient(graphics, pipeline, texture.getTextureView(), texture.getSampler(), x, y, w, h, minu, maxu, minv, maxv, color, color2);
    }

    public static void blitFloat(GuiGraphicsExtractor graphics, RenderPipeline pipeline, AbstractTexture texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        blitFloatGradient(graphics, pipeline, texture, x, y, w, h, minu, maxu, minv, maxv, color, color);
    }

    public static void blitFloat(GuiGraphicsExtractor graphics, RenderPipeline pipeline, GpuTextureView texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        blitFloatGradient(graphics, pipeline, texture, DEFAULT_SAMPLER, x, y, w, h, minu, maxu, minv, maxv, color, color);
    }

    public static void blitFloatGradient(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color, int color2) {
        blitFloatGradient(graphics, pipeline, Minecraft.getInstance().getTextureManager().getTexture(texture), x, y, w, h, minu, maxu, minv, maxv, color, color2);
    }

    public static void blitFloat(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        blitFloatGradient(graphics, pipeline, Minecraft.getInstance().getTextureManager().getTexture(texture), x, y, w, h, minu, maxu, minv, maxv, color, color);
    }

    public static void fillGradient(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color00, int color10, int color01, int color11) {
        graphics.guiRenderState.addGuiElement(new FourColoredRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, color00, color10, color01, color11, graphics.scissorStack.peek()));
    }

    /** Terrain has an explicit stratum; tiles must not run the GUI's pairwise overlap search. */
    public static void blitMapTile(GuiGraphicsExtractor graphics, Identifier location,
            float x, float z, float width) {
        blitMapRectangle(graphics, location, x, z, width, width);
    }

    public static void blitMapRectangle(GuiGraphicsExtractor graphics, Identifier location,
            float x, float z, float width, float height) {
        blitMapRectangle(graphics, location, x, z, width, height, true);
    }

    public static void blitMapOverlay(GuiGraphicsExtractor graphics, Identifier location,
            float x, float z, float width, float height) {
        blitMapRectangle(graphics, location, x, z, width, height, false);
    }

    private static void blitMapRectangle(GuiGraphicsExtractor graphics, Identifier location,
            float x, float z, float width, float height, boolean terrain) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        FloatBlitRenderState state = new FloatBlitRenderState(RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()), new Matrix3x2f(graphics.pose()),
                x, z, x + width, z + height, 0, 1, 0, 1, -1, -1, graphics.scissorStack.peek());
        if (state.bounds() != null) {
            graphics.guiRenderState.current.addGuiElement(state);
            if (terrain) com.mamiyaotaru.voxelmap.persistent.WorldMapProfiler.tile();
            else com.mamiyaotaru.voxelmap.persistent.WorldMapProfiler.quads(1);
        }
    }

    public static void fillMapQuads(GuiGraphicsExtractor graphics, float[] vertices, int count, int color) {
        if (count == 0 || (color >>> 24) == 0) return;
        if (count < 0 || count > vertices.length / 8) throw new IllegalArgumentException("Invalid world-map quad count");
        com.mamiyaotaru.voxelmap.persistent.WorldMapProfiler.quads(count);
        // Retain the immutable snapshot; ranges avoid copying large cached arrays.
        // MixinGuiRendererWorldMap isolates each range into a separate native draw.
        ScreenRectangle scissor = graphics.scissorStack.peek();
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        WorldMapGeometryBatch.forEach(count, (first, length) -> graphics.guiRenderState.current.addGuiElement(new WorldMapQuadRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), pose,
                vertices, first, length, color, scissor, scissor)));
    }

    public static void fillRectsBatched(GuiGraphicsExtractor graphics, float[] coords, int[] colors, int count) {
        if (count <= 0) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissorArea = graphics.scissorStack.peek();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int offset = i << 2;
            minX = Math.min(minX, coords[offset]);
            minY = Math.min(minY, coords[offset + 1]);
            maxX = Math.max(maxX, coords[offset + 2]);
            maxY = Math.max(maxY, coords[offset + 3]);
        }
        ScreenRectangle bounds = new ScreenRectangle(Mth.floor(minX), Mth.floor(minY),
                Mth.ceil(maxX - minX), Mth.ceil(maxY - minY)).transformMaxBounds(pose);
        if (scissorArea != null) {
            bounds = scissorArea.intersection(bounds);
        }
        graphics.guiRenderState.addGuiElement(new MultiColoredRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), pose, coords, colors, count, scissorArea, bounds));
    }
}
