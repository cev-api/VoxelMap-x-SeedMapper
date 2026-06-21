package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class SeedMapperEspRenderer {
    private SeedMapperEspRenderer() {
    }

    public static void render(float partialTick, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Camera camera) {
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        if (!settings.espEnabled) {
            return;
        }
        SeedMapperEspManager.render(poseStack, submitNodeCollector, camera, settings);
    }
}
