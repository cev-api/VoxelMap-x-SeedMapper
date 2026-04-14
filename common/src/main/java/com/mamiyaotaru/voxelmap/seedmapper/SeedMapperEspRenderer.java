package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;

public final class SeedMapperEspRenderer {
    private SeedMapperEspRenderer() {
    }

    public static void render(float partialTick, PoseStack poseStack, BufferSource bufferSource, Camera camera) {
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        if (!settings.espEnabled) {
            return;
        }
        SeedMapperEspManager.render(poseStack, bufferSource, camera, settings);
    }
}
