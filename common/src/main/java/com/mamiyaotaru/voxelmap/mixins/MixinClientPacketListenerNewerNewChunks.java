package com.mamiyaotaru.voxelmap.mixins;

import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListenerNewerNewChunks {
    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void voxelmap$afterHandleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (packet == null || VoxelConstants.getVoxelMapInstance() == null) {
            return;
        }

        VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager()
                .onChunkDataPacket(packet.getX(), packet.getZ());
    }

    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void voxelmap$afterHandleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        if (packet == null || VoxelConstants.getVoxelMapInstance() == null) {
            return;
        }

        RadarSettingsManager radarOptions = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        if (radarOptions == null) {
            return;
        }
        var mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();

        boolean useChunkExploits = radarOptions.newerNewChunksBlockUpdateExploit || radarOptions.newerNewChunksLiquidExploit;
        boolean usePortalMarkers = mapOptions.showNetherPortalMarkers || mapOptions.showEndPortalMarkers || mapOptions.showEndGatewayMarkers;
        if (!useChunkExploits && !usePortalMarkers) {
            return;
        }

        BlockPos pos = packet.getPos();
        if (useChunkExploits) {
            VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().onBlockUpdated(
                    pos,
                    radarOptions.newerNewChunksBlockUpdateExploit,
                    radarOptions.newerNewChunksLiquidExploit
            );
        }
        if (usePortalMarkers) {
            VoxelConstants.getVoxelMapInstance().getPortalMarkersManager().onBlockUpdated(pos);
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void voxelmap$afterHandleChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        if (packet == null || VoxelConstants.getVoxelMapInstance() == null) {
            return;
        }

        RadarSettingsManager radarOptions = VoxelConstants.getVoxelMapInstance().getRadarOptions();
        if (radarOptions == null) {
            return;
        }
        var mapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();

        boolean useChunkExploits = radarOptions.newerNewChunksLiquidExploit;
        boolean usePortalMarkers = mapOptions.showNetherPortalMarkers || mapOptions.showEndPortalMarkers || mapOptions.showEndGatewayMarkers;
        if (!useChunkExploits && !usePortalMarkers) {
            return;
        }

        packet.runUpdates((BlockPos pos, BlockState state) -> {
            if (useChunkExploits) {
                VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().onChunkDeltaUpdated(
                        pos,
                        state,
                        radarOptions.newerNewChunksLiquidExploit
                );
            }
            if (usePortalMarkers) {
                VoxelConstants.getVoxelMapInstance().getPortalMarkersManager().onBlockUpdated(pos);
            }
        });
    }
}
