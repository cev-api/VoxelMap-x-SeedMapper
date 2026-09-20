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
                .onChunkDataPacket(packet.x(), packet.z());
        com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerDetection.onChunkLoaded(packet.x(), packet.z());
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
        boolean usePortalTracking = mapOptions.autoPortalWaypoints
                || mapOptions.showNetherPortalMarkers
                || mapOptions.showEndPortalMarkers
                || mapOptions.showEndGatewayMarkers;
        var seedMapper = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        boolean useMarkerTracking = seedMapper != null && (seedMapper.containerDetection
                || seedMapper.spawnerDetection || seedMapper.workstationDetection || seedMapper.redstoneDetection);
        if (!useChunkExploits && !usePortalTracking && !useMarkerTracking) {
            return;
        }

        BlockPos pos = packet.getPos();
        com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerDetection.onBlockUpdated(pos);
        if (useChunkExploits) {
            VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().onBlockUpdated(
                    pos,
                    radarOptions.newerNewChunksBlockUpdateExploit,
                    radarOptions.newerNewChunksLiquidExploit
            );
        }
        if (usePortalTracking) {
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
        boolean usePortalTracking = mapOptions.autoPortalWaypoints
                || mapOptions.showNetherPortalMarkers
                || mapOptions.showEndPortalMarkers
                || mapOptions.showEndGatewayMarkers;
        var seedMapper = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        boolean useMarkerTracking = seedMapper != null && (seedMapper.containerDetection
                || seedMapper.spawnerDetection || seedMapper.workstationDetection || seedMapper.redstoneDetection);
        if (!useChunkExploits && !usePortalTracking && !useMarkerTracking) {
            return;
        }

        packet.runUpdates((BlockPos pos, BlockState state) -> {
            com.mamiyaotaru.voxelmap.seedmapper.SeedMapperContainerDetection.onBlockUpdated(pos);
            if (useChunkExploits) {
                VoxelConstants.getVoxelMapInstance().getNewerNewChunksManager().onChunkDeltaUpdated(
                        pos,
                        state,
                        radarOptions.newerNewChunksLiquidExploit
                );
            }
            if (usePortalTracking) {
                VoxelConstants.getVoxelMapInstance().getPortalMarkersManager().onBlockUpdated(pos);
            }
        });
    }
}
