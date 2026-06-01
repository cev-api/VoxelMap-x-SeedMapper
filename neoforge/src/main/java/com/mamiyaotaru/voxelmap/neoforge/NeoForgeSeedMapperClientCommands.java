package com.mamiyaotaru.voxelmap.neoforge;

import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommandHandler;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommands;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandTree;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class NeoForgeSeedMapperClientCommands {
    private NeoForgeSeedMapperClientCommands() {
    }

    public static void register(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("seedmap", NeoForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("sm", NeoForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("voxelmap", NeoForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("vmap", NeoForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildSourceRoot(NeoForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(ChunkSyncCommands.buildRoot("chunksync", NeoForgeSeedMapperClientCommands::runChunkSync));
    }

    private static int run(String command) {
        return SeedMapperCommandHandler.handleChatCommand(command) ? 1 : 0;
    }

    private static int runChunkSync(String command) {
        return ChunkSyncCommandHandler.handleChatCommand(command) ? 1 : 0;
    }
}
