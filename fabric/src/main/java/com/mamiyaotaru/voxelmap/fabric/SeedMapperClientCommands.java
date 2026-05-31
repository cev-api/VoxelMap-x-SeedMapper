package com.mamiyaotaru.voxelmap.fabric;

import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommandHandler;
import com.mamiyaotaru.voxelmap.chunksync.ChunkSyncCommands;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandTree;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class SeedMapperClientCommands {
    private SeedMapperClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(SeedMapperCommandTree.buildRoot("seedmap", SeedMapperClientCommands::run));
            dispatcher.register(SeedMapperCommandTree.buildRoot("sm", SeedMapperClientCommands::run));
            dispatcher.register(SeedMapperCommandTree.buildSourceRoot(SeedMapperClientCommands::run));
            dispatcher.register(ChunkSyncCommands.buildRoot("chunksync", SeedMapperClientCommands::runChunkSync));
        });
    }

    private static int run(String command) {
        return SeedMapperCommandHandler.handleChatCommand(command) ? 1 : 0;
    }

    private static int runChunkSync(String command) {
        return ChunkSyncCommandHandler.handleChatCommand(command) ? 1 : 0;
    }
}
