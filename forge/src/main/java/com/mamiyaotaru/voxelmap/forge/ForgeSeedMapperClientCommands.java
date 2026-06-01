package com.mamiyaotaru.voxelmap.forge;

import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommandHandler;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommands;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandTree;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

public final class ForgeSeedMapperClientCommands {
    private ForgeSeedMapperClientCommands() {
    }

    public static void register(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("seedmap", ForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("sm", ForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("voxelmap", ForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("vmap", ForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildSourceRoot(ForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(ChunkSyncCommands.buildRoot("chunksync", ForgeSeedMapperClientCommands::runChunkSync));
    }

    private static int run(String command) {
        return SeedMapperCommandHandler.handleChatCommand(command) ? 1 : 0;
    }

    private static int runChunkSync(String command) {
        return ChunkSyncCommandHandler.handleChatCommand(command) ? 1 : 0;
    }
}
