package com.mamiyaotaru.voxelmap.mixins;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {
    @Inject(method = "sendBlockUpdated", at = @At("RETURN"))
    private void voxelmap$onSendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (pos == null || VoxelConstants.getVoxelMapInstance() == null) {
            return;
        }

        var mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();

        boolean usePortalMarkers = mapOptions.showNetherPortalMarkers || mapOptions.showEndPortalMarkers || mapOptions.showEndGatewayMarkers;
        if (!usePortalMarkers) {
            return;
        }

        if (usePortalMarkers) {
            VoxelConstants.getVoxelMapInstance().getPortalMarkersManager().onBlockUpdated(pos);
        }
    }
}
