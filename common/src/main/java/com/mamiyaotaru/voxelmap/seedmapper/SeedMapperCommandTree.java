package com.mamiyaotaru.voxelmap.seedmapper;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class SeedMapperCommandTree {
    private static final List<String> ORE_VEIN_TYPES = List.of("iron", "copper");
    private static final List<String> ESP_TYPES = List.of("terrain", "canyon", "cave");
    private static final List<String> SOURCE_WRAPPERS = List.of("run", "seeded", "positioned", "in", "versioned", "flagged", "as", "rotated");
    private static final List<String> ROOT_COMMANDS = List.of("help", "seed", "locate", "highlight", "export");
    private static final List<String> LOCATE_TYPES = List.of("structure", "feature", "biome", "orevein", "slime", "slimechunk", "slime_chunk", "loot");
    private static final List<String> LOOT_QUERY_TERMS = List.of(
            "sentry_armor_trim_smithing_template", "vex_armor_trim_smithing_template",
            "wild_armor_trim_smithing_template", "ward_armor_trim_smithing_template",
            "wayfinder_armor_trim_smithing_template", "raiser_armor_trim_smithing_template",
            "shaper_armor_trim_smithing_template", "host_armor_trim_smithing_template",
            "silence_armor_trim_smithing_template", "snout_armor_trim_smithing_template",
            "coast_armor_trim_smithing_template", "eye_armor_trim_smithing_template",
            "rib_armor_trim_smithing_template", "spire_armor_trim_smithing_template",
            "netherite_upgrade_smithing_template", "enchanted_golden_apple",
            "elytra", "diamond", "netherite_ingot"
    );
    private static final List<String> COMMON_ORE_BLOCKS = List.of(
            "diamond_ore", "deepslate_diamond_ore", "iron_ore", "deepslate_iron_ore",
            "gold_ore", "deepslate_gold_ore", "emerald_ore", "deepslate_emerald_ore",
            "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
            "coal_ore", "deepslate_coal_ore", "copper_ore", "deepslate_copper_ore",
            "nether_quartz_ore", "nether_gold_ore", "ancient_debris"
    );
    private static final List<String> COMMON_BIOMES = List.of(
            "plains", "forest", "desert", "savanna", "taiga", "jungle", "badlands",
            "swamp", "mangrove_swamp", "ocean", "deep_ocean", "river", "beach",
            "meadow", "stony_peaks", "frozen_peaks", "snowy_plains", "dripstone_caves",
            "lush_caves", "deep_dark", "nether_wastes", "crimson_forest",
            "warped_forest", "basalt_deltas", "soul_sand_valley", "the_end",
            "end_highlands", "end_midlands", "small_end_islands"
    );

    private SeedMapperCommandTree() {
    }

    public static <S> LiteralArgumentBuilder<S> buildRoot(String name, Function<String, Integer> runner) {
        return LiteralArgumentBuilder.<S>literal(name)
                .executes(context -> runRaw(context, runner, "help"))
                .then(LiteralArgumentBuilder.<S>literal("help").executes(context -> runRaw(context, runner, "help")))
                .then(LiteralArgumentBuilder.<S>literal("seed")
                        .then(RequiredArgumentBuilder.<S, String>argument("seed", StringArgumentType.greedyString())
                                .executes(context -> run(context, runner, "seed " + StringArgumentType.getString(context, "seed")))))
                .then(LiteralArgumentBuilder.<S>literal("locate")
                        .then(LiteralArgumentBuilder.<S>literal("structure")
                                .then(RequiredArgumentBuilder.<S, String>argument("feature_id", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getStructureSuggestions(), builder))
                                        .executes(context -> run(context, runner, "locate structure " + StringArgumentType.getString(context, "feature_id")))))
                        .then(LiteralArgumentBuilder.<S>literal("biome")
                                .then(RequiredArgumentBuilder.<S, String>argument("biome_name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(COMMON_BIOMES, builder))
                                        .executes(context -> run(context, runner, "locate biome " + StringArgumentType.getString(context, "biome_name")))))
                        .then(LiteralArgumentBuilder.<S>literal("orevein")
                                .then(RequiredArgumentBuilder.<S, String>argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ORE_VEIN_TYPES, builder))
                                        .executes(context -> run(context, runner, "locate orevein " + StringArgumentType.getString(context, "type")))))
                        .then(LiteralArgumentBuilder.<S>literal("slime")
                                .executes(context -> runRaw(context, runner, "locate slime")))
                        .then(LiteralArgumentBuilder.<S>literal("loot")
                                .then(RequiredArgumentBuilder.<S, String>argument("text", StringArgumentType.greedyString())
                                        .executes(context -> run(context, runner, "locate loot " + StringArgumentType.getString(context, "text"))))))
                .then(LiteralArgumentBuilder.<S>literal("highlight")
                        .then(LiteralArgumentBuilder.<S>literal("clear").executes(context -> runRaw(context, runner, "highlight clear")))
                        .then(LiteralArgumentBuilder.<S>literal("ore")
                                .then(RequiredArgumentBuilder.<S, String>argument("block", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(COMMON_ORE_BLOCKS, builder))
                                        .executes(context -> run(context, runner, "highlight ore " + StringArgumentType.getString(context, "block")))
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("chunks", IntegerArgumentType.integer(0, 8))
                                                .executes(context -> run(context, runner,
                                                        "highlight ore " + StringArgumentType.getString(context, "block") + " " + IntegerArgumentType.getInteger(context, "chunks"))))))
                        .then(LiteralArgumentBuilder.<S>literal("orevein")
                                .executes(context -> runRaw(context, runner, "highlight orevein"))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("chunks", IntegerArgumentType.integer(0, 8))
                                        .executes(context -> run(context, runner, "highlight orevein " + IntegerArgumentType.getInteger(context, "chunks")))))
                        .then(RequiredArgumentBuilder.<S, String>argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ESP_TYPES, builder))
                                .executes(context -> run(context, runner, "highlight " + StringArgumentType.getString(context, "mode")))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("chunks", IntegerArgumentType.integer(0, 8))
                                        .executes(context -> run(context, runner,
                                                "highlight " + StringArgumentType.getString(context, "mode") + " " + IntegerArgumentType.getInteger(context, "chunks"))))))
                .then(LiteralArgumentBuilder.<S>literal("export").executes(context -> runRaw(context, runner, "export")))
                .then(buildSourceSubtree("source", "seedmap source ", runner));
    }

    public static <S> LiteralArgumentBuilder<S> buildSourceRoot(Function<String, Integer> runner) {
        return buildSourceSubtree("sm:source", "", runner);
    }

    private static <S> LiteralArgumentBuilder<S> buildSourceSubtree(String literal, String prefix, Function<String, Integer> runner) {
        return LiteralArgumentBuilder.<S>literal(literal)
                .then(RequiredArgumentBuilder.<S, String>argument("command", StringArgumentType.greedyString())
                        .suggests(SeedMapperCommandTree::suggestSourceCommand)
                        .executes(context -> runner.apply(prefix + StringArgumentType.getString(context, "command"))));
    }

    private static List<String> getStructureSuggestions() {
        return Arrays.stream(SeedMapperFeature.values())
                .map(SeedMapperFeature::id)
                .map(id -> id.toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    private static <S> CompletableFuture<Suggestions> suggestSourceCommand(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String trimmed = remaining.trim();
        boolean trailingSpace = remaining.endsWith(" ");
        ArrayList<String> tokens = new ArrayList<>();
        if (!trimmed.isEmpty()) {
            tokens.addAll(List.of(trimmed.toLowerCase(Locale.ROOT).split("\\s+")));
        }

        String current = "";
        if (!trailingSpace && !tokens.isEmpty()) {
            current = tokens.remove(tokens.size() - 1);
        }

        ParseState state = parseSourcePrefix(tokens);
        if (state.awaitingWrapperValue) {
            return suggestToken(builder, current, List.of());
        }

        if (!state.inCommand) {
            ArrayList<String> candidates = new ArrayList<>(SOURCE_WRAPPERS);
            candidates.addAll(ROOT_COMMANDS);
            return suggestToken(builder, current, candidates);
        }

        List<String> command = state.commandTokens;
        if (command.isEmpty()) {
            return suggestToken(builder, current, ROOT_COMMANDS);
        }

        String first = command.get(0);
        if ("locate".equals(first)) {
            return suggestLocate(builder, current, command);
        }

        if ("highlight".equals(first) && command.size() <= 2) {
            ArrayList<String> highlight = new ArrayList<>(List.of("clear", "ore", "orevein"));
            highlight.addAll(ESP_TYPES);
            return suggestToken(builder, current, highlight);
        }

        if ("seed".equals(first) || "help".equals(first) || "export".equals(first)) {
            return suggestToken(builder, current, List.of());
        }

        return suggestToken(builder, current, ROOT_COMMANDS);
    }

    private static CompletableFuture<Suggestions> suggestLocate(SuggestionsBuilder builder, String current, List<String> command) {
        if (command.size() == 1) {
            return suggestToken(builder, current, LOCATE_TYPES);
        }

        String type = command.get(1);
        switch (type) {
            case "structure", "feature":
                return suggestToken(builder, current, getStructureSuggestions());
            case "biome":
                return suggestToken(builder, current, COMMON_BIOMES);
            case "orevein":
                return suggestToken(builder, current, ORE_VEIN_TYPES);
            case "loot":
                if (command.size() == 2) {
                    ArrayList<String> candidates = new ArrayList<>(LOOT_QUERY_TERMS);
                    candidates.add("1");
                    return suggestToken(builder, current, candidates);
                }
                if (command.size() == 3 && isNumeric(command.get(2))) {
                    return suggestToken(builder, current, LOOT_QUERY_TERMS);
                }
                return suggestToken(builder, current, LOOT_QUERY_TERMS);
            default:
                return suggestToken(builder, current, LOCATE_TYPES);
        }
    }

    private static ParseState parseSourcePrefix(List<String> tokens) {
        int index = 0;
        int remainingValues = 0;
        boolean inCommand = false;
        ArrayList<String> commandTokens = new ArrayList<>();

        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (!inCommand && remainingValues > 0) {
                remainingValues--;
                index++;
                continue;
            }

            if (!inCommand) {
                switch (token) {
                    case "seeded", "in", "versioned", "flagged", "as" -> {
                        remainingValues = 1;
                        index++;
                        continue;
                    }
                    case "positioned" -> {
                        remainingValues = 3;
                        index++;
                        continue;
                    }
                    case "rotated" -> {
                        remainingValues = 2;
                        index++;
                        continue;
                    }
                    case "run" -> {
                        inCommand = true;
                        index++;
                        continue;
                    }
                    default -> inCommand = true;
                }
            }

            commandTokens.add(token);
            index++;
        }

        return new ParseState(inCommand, remainingValues > 0, commandTokens);
    }

    private static CompletableFuture<Suggestions> suggestToken(SuggestionsBuilder builder, String current, List<String> candidates) {
        String normalizedCurrent = current == null ? "" : current.toLowerCase(Locale.ROOT);
        int tokenStart = builder.getInput().length() - normalizedCurrent.length();
        SuggestionsBuilder tokenBuilder = builder.createOffset(tokenStart);
        return SharedSuggestionProvider.suggest(
                candidates.stream()
                        .map(candidate -> candidate.toLowerCase(Locale.ROOT))
                        .filter(candidate -> normalizedCurrent.isBlank() || candidate.startsWith(normalizedCurrent))
                        .distinct(),
                tokenBuilder
        );
    }

    private static boolean isNumeric(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    private record ParseState(boolean inCommand, boolean awaitingWrapperValue, List<String> commandTokens) {}

    private static <S> int runRaw(CommandContext<S> context, Function<String, Integer> runner, String subcommand) {
        return run(context, runner, subcommand);
    }

    private static <S> int run(CommandContext<S> context, Function<String, Integer> runner, String subcommand) {
        return runner.apply("seedmap " + subcommand);
    }
}
