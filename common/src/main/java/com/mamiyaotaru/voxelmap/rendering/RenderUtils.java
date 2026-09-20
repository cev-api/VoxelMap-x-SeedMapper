package com.mamiyaotaru.voxelmap.rendering;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fStack;
import org.joml.Vector4fc;

public class RenderUtils {
    public static void submitTexturedModalRect(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, RenderType renderType, float x, float y, float z, float width, float height, int color) {
        submitTexturedModalRect(submitNodeCollector, matrixStack, renderType, x, y, z, width, height, 0.0F, 1.0F, 0.0F, 1.0F, color);
    }

    public static void submitTexturedModalRect(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, RenderType renderType, Sprite sprite, float x, float y, float z, float width, float height, int color) {
        submitTexturedModalRect(submitNodeCollector, matrixStack, renderType, x, y, z, width, height, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), color);
    }

    public static void submitTexturedModalRect(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, RenderType renderType, float x, float y, float z, float width, float height, float u0, float u1, float v0, float v1, int color) {
        PoseStack poseStack = poseStackFor(matrixStack);
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
            vertexConsumer.addVertex(pose, x + 0.0F, y + 0.0F, z).setUv(u0, v0).setColor(color);
            vertexConsumer.addVertex(pose, x + 0.0F, y + height, z).setUv(u0, v1).setColor(color);
            vertexConsumer.addVertex(pose, x + width, y + height, z).setUv(u1, v1).setColor(color);
            vertexConsumer.addVertex(pose, x + width, y + 0.0F, z).setUv(u1, v0).setColor(color);
        });
    }

    public static void submitColoredQuad(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, RenderType renderType,
                                         float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float z, int color) {
        PoseStack poseStack = poseStackFor(matrixStack);
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
            vertexConsumer.addVertex(pose, x0, y0, z).setColor(color);
            vertexConsumer.addVertex(pose, x1, y1, z).setColor(color);
            vertexConsumer.addVertex(pose, x2, y2, z).setColor(color);
            vertexConsumer.addVertex(pose, x3, y3, z).setColor(color);
        });
    }

    public static void submitString(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, String text, float x, float y, float z, int color, boolean shadow) {
        submitString(submitNodeCollector, matrixStack, Component.nullToEmpty(text), x, y, z, color, shadow);
    }

    public static void submitString(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, Component text, float x, float y, float z, int color, boolean shadow) {
        matrixStack.pushMatrix();
        matrixStack.translate(x, y, z);
        submitPreparedText(submitNodeCollector, matrixStack, text.getVisualOrderText(), 0.0F, 0.0F, color, shadow, Font.DisplayMode.SEE_THROUGH, 0, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);

        matrixStack.popMatrix();
    }

    public static void submitCenteredString(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, String text, float x, float y, float z, int color, boolean shadow) {
        submitCenteredString(submitNodeCollector, matrixStack, Component.nullToEmpty(text), x, y, z, color, shadow);
    }

    public static void submitCenteredString(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, Component text, float x, float y, float z, int color, boolean shadow) {
        submitString(submitNodeCollector, matrixStack, text, x - (Minecraft.getInstance().font.width(text) / 2.0F), y, z, color, shadow);
    }

    public static void submitPreparedText(OrderedSubmitNodeCollector submitNodeCollector, Matrix4fStack matrixStack, FormattedCharSequence text, float x, float y, int color, boolean shadow, Font.DisplayMode displayMode, int backgroundColor, int light) {
        submitNodeCollector.submitText(poseStackFor(matrixStack), x, y, text, shadow, displayMode, light, color, backgroundColor, 0);
    }

    private static PoseStack poseStackFor(Matrix4fStack matrixStack) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(matrixStack);
        return poseStack;
    }
    private static final Matrix4fStack MATRIX_STACK = new Matrix4fStack(16);
    private static final SubmitNodeStorage SUBMIT_NODE_STORAGE = new SubmitNodeStorage();
    private static final VoxelMapRenderTarget FULLSCREEN_TARGET = new VoxelMapRenderTarget("VoxelMap Fullscreen Target", GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
    private static final ArrayDeque<ProjectionEntry> PROJECTION_STACK = new ArrayDeque<>();

    public static void init() {
        FULLSCREEN_TARGET.createBuffers(getSafeScreenWidth(), getSafeScreenHeight());
    }

    private static int getSafeScreenWidth() {
        return Math.max(1, Minecraft.getInstance().getWindow().getScreenWidth());
    }

    private static int getSafeScreenHeight() {
        return Math.max(1, Minecraft.getInstance().getWindow().getScreenHeight());
    }

    public static float getGuiWidth() {
        return (float) getSafeScreenWidth() / Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static float getGuiHeight() {
        return (float) getSafeScreenHeight() / Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static Matrix4fStack getMatrixStack() {
        return MATRIX_STACK;
    }

    public static SubmitNodeStorage getSubmitNodeStorage() {
        return SUBMIT_NODE_STORAGE;
    }

    public static boolean hasFlippedV() {
        return !VoxelConstants.hasVulkanMod(); // Returns true if the renderer uses flipped textures
    }

    public static void blitToScreen(GuiGraphicsExtractor graphics, GpuTextureView texture, float x, float y, float width, float height, int color) {
        float v0 = RenderUtils.hasFlippedV() ? 1.0F : 0.0F;
        float v1 = RenderUtils.hasFlippedV() ? 0.0F : 1.0F;
        VoxelMapGuiGraphics.blitFloat(graphics, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, texture, x, y, width, height, 0.0F, 1.0F, v0, v1, color);
    }

    public static GpuSampler getSampler(boolean linear, boolean repeat) {
        return getSampler(linear, repeat, false);
    }

    public static GpuSampler getSampler(boolean linear, boolean repeat, boolean mipmaps) {
        AddressMode addressMode = repeat ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
        FilterMode filterMode = linear ? FilterMode.LINEAR : FilterMode.NEAREST;

        return RenderSystem.getSamplerCache().getSampler(addressMode, addressMode, filterMode, filterMode, mipmaps);
    }


    public static SubmitPass createSubmitPass(String name, RenderTarget target, Vector4fc colorClear, double depthClear) {
        return new SubmitPass(name, target.getColorTextureView(), Optional.of(colorClear), target.getDepthTextureView(), OptionalDouble.of(depthClear));
    }

    public static RenderPass createRenderPass(String name, RenderTarget target, Vector4fc colorClear, double depthClear) {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> name, target.getColorTextureView(), Optional.of(colorClear), target.getDepthTextureView(), OptionalDouble.of(depthClear));
    }

    public static void flushCmds() {
        RenderSystem.assertOnRenderThread();
        if (!VoxelConstants.hasVulkanMod()) {
            RenderSystem.getDevice().createCommandEncoder().submit();
        } else {
            try {
                Class<?> vkRendererClass = Class.forName("net.vulkanmod.vulkan.Renderer");
                vkRendererClass.getMethod("flushCmds").invoke(vkRendererClass.getMethod("getInstance").invoke(null));
            } catch (Exception ignored) {
            }
        }
    }

    public static VoxelMapRenderTarget getFullscreenTarget() {
        int width = getSafeScreenWidth();
        int height = getSafeScreenHeight();
        if (FULLSCREEN_TARGET.width != width || FULLSCREEN_TARGET.height != height) {
            FULLSCREEN_TARGET.resize(width, height);
        }
        return FULLSCREEN_TARGET;
    }

    public static void setupProjectionMatrix(GpuBufferSlice matrix, ProjectionType type) {
        setupProjectionMatrix(matrix, type, 0.0F);
    }

    public static void setupProjectionMatrix(GpuBufferSlice matrix, ProjectionType type, float initialDepth) {
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.getModelViewStack().translate(0.0f, 0.0F, initialDepth);
        PROJECTION_STACK.push(new ProjectionEntry(RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getProjectionType()));
        RenderSystem.setProjectionMatrix(matrix, type);
    }

    public static void restoreProjectionMatrix() {
        ProjectionEntry projection = PROJECTION_STACK.pop();
        RenderSystem.setProjectionMatrix(projection.matrix(), projection.type());
        RenderSystem.getModelViewStack().popMatrix();
    }

    public static void readTextureContentsToBufferedImage(GpuTexture gpuTexture, Consumer<BufferedImage> resultConsumer) {
        RenderSystem.assertOnRenderThread();
        int bytePerPixel = gpuTexture.getFormat().blockSize();
        int width = gpuTexture.getWidth(0);
        int height = gpuTexture.getHeight(0);
        int bufferSize = bytePerPixel * width * height;
        GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(() -> "Texture read buffer", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bufferSize);
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        commandEncoder.copyTextureToBuffer(gpuTexture, gpuBuffer, 0, () -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            try (GpuBufferSlice.MappedView readView = gpuBuffer.map(true, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = readView.data().getInt((x + y * width) * bytePerPixel);
                        image.setRGB(x, y, ARGB.fromABGR(pixel));
                    }
                }
            }
            gpuBuffer.close();
            resultConsumer.accept(image);
        }, 0);
    }

    public static record ProjectionEntry(GpuBufferSlice matrix, ProjectionType type) {
    }
}
