package com.mamiyaotaru.voxelmap.mixins;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinWorldRenderer {
    @Unique
    private final PoseStack voxelmap$overlayPose = new PoseStack();

    /**
     * Draws the VoxelMap world overlays after the level frame graph has executed.
     *
     * <p>This has to happen here rather than through the level's own submit collector:
     * 26.3 composites blended custom geometry against the opaque depth buffer, which hid
     * the ESP whenever the player was not underneath it. See
     * {@link VoxelConstants#onRenderWaypoints}.</p>
     *
     * <p>The pose carries the camera view rotation because the overlay code emits
     * camera-relative coordinates.</p>
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void voxelmap$renderWorldOverlays(GraphicsResourceAllocator resourceAllocator, boolean renderOutline,
                                              CameraRenderState cameraRenderState, GpuBufferSlice terrainFog,
                                              Vector4f fogColor, boolean shouldRenderSky,
                                              boolean consistentDepthRequired, CallbackInfo ci) {
        PoseStack pose = voxelmap$overlayPose;
        pose.pushPose();
        pose.last().pose().set(cameraRenderState.viewRotationMatrix);
        VoxelConstants.onRenderWaypoints(
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false),
                pose,
                Minecraft.getInstance().gameRenderer.mainCamera());
        pose.popPose();
    }
}
