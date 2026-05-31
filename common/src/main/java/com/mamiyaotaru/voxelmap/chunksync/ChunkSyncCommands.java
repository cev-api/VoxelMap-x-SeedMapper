package com.mamiyaotaru.voxelmap.chunksync;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.function.Function;

public final class ChunkSyncCommands {
    private ChunkSyncCommands() {
    }

    public static <S> LiteralArgumentBuilder<S> buildRoot(String name, Function<String, Integer> runner) {
        return LiteralArgumentBuilder.<S>literal(name)
                .executes(context -> runner.apply("chunksync help"))
                .then(LiteralArgumentBuilder.<S>literal("export")
                        .executes(context -> runner.apply("chunksync export"))
                        .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.greedyString())
                                .executes(context -> runner.apply("chunksync export " + StringArgumentType.getString(context, "name")))))
                .then(LiteralArgumentBuilder.<S>literal("import")
                        .executes(context -> runner.apply("chunksync import"))
                        .then(RequiredArgumentBuilder.<S, String>argument("args", StringArgumentType.greedyString())
                                .executes(context -> runner.apply("chunksync import " + StringArgumentType.getString(context, "args")))))
                .then(LiteralArgumentBuilder.<S>literal("players")
                        .executes(context -> runner.apply("chunksync players")))
                .then(LiteralArgumentBuilder.<S>literal("remove")
                        .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.greedyString())
                                .executes(context -> runner.apply("chunksync remove " + StringArgumentType.getString(context, "player")))))
                .then(LiteralArgumentBuilder.<S>literal("share")
                        .executes(context -> runner.apply("chunksync share"))
                        .then(LiteralArgumentBuilder.<S>literal("to")
                                .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                                        .executes(context -> runner.apply("chunksync share to " + StringArgumentType.getString(context, "player"))))))
                .then(LiteralArgumentBuilder.<S>literal("get")
                        .then(RequiredArgumentBuilder.<S, String>argument("args", StringArgumentType.greedyString())
                                .executes(context -> runner.apply("chunksync get " + StringArgumentType.getString(context, "args")))))
                .then(LiteralArgumentBuilder.<S>literal("key")
                        .then(RequiredArgumentBuilder.<S, String>argument("passphrase", StringArgumentType.greedyString())
                                .executes(context -> runner.apply("chunksync key " + StringArgumentType.getString(context, "passphrase")))))
                .then(LiteralArgumentBuilder.<S>literal("host")
                        .executes(context -> runner.apply("chunksync host"))
                        .then(RequiredArgumentBuilder.<S, String>argument("host", StringArgumentType.word())
                                .executes(context -> runner.apply("chunksync host " + StringArgumentType.getString(context, "host")))));
    }
}
