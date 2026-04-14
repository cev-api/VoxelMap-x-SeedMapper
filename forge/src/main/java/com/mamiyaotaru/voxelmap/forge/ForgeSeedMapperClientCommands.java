package com.mamiyaotaru.voxelmap.forge;

import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandHandler;
import com.mamiyaotaru.voxelmap.seedmapper.SeedMapperCommandTree;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

public final class ForgeSeedMapperClientCommands {
    private ForgeSeedMapperClientCommands() {
    }

    public static void register(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("seedmap", ForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildRoot("sm", ForgeSeedMapperClientCommands::run));
        event.getDispatcher().register(SeedMapperCommandTree.buildSourceRoot(ForgeSeedMapperClientCommands::run));
    }

    private static int run(String command) {
        return SeedMapperCommandHandler.handleChatCommand(command) ? 1 : 0;
    }
}
