package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.CanyonCarverConfig;
import com.github.cubiomes.CaveCarverConfig;
import com.github.cubiomes.Generator;
import com.github.cubiomes.OreConfig;
import com.github.cubiomes.OreVeinParameters;
import com.github.cubiomes.Pos3;
import com.github.cubiomes.Pos3List;
import com.github.cubiomes.SurfaceNoise;
import com.github.cubiomes.TerrainNoise;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntBiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SeedMapperCommandHandler {
    public record LocateResult(String query, int x, int z, int lootCount, String lootItem, String lootStructure) {
        public LocateResult(String query, int x, int z) {
            this(query, x, z, 0, "", "");
        }

        public boolean hasLootSummary() {
            return lootCount > 0
                    && lootItem != null && !lootItem.isBlank()
                    && lootStructure != null && !lootStructure.isBlank();
        }
    }

    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern LOCATE_COORDS = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\]");
    private static String pendingDatapackLocateId;
    private static Consumer<String> statusSink;
    private static final ThreadLocal<SourceOverrides> SOURCE_OVERRIDES = new ThreadLocal<>();
    private static final int MAX_HIGHLIGHT_CHUNK_RANGE = 8;
    private static final int[] ORE_TYPES = new int[] {
            Cubiomes.AndesiteOre(), Cubiomes.BlackstoneOre(), Cubiomes.BuriedDiamondOre(), Cubiomes.BuriedLapisOre(),
            Cubiomes.ClayOre(), Cubiomes.CoalOre(), Cubiomes.CopperOre(), Cubiomes.DeepslateOre(), Cubiomes.DeltasGoldOre(),
            Cubiomes.DeltasQuartzOre(), Cubiomes.DiamondOre(), Cubiomes.DioriteOre(), Cubiomes.DirtOre(), Cubiomes.EmeraldOre(),
            Cubiomes.ExtraGoldOre(), Cubiomes.GoldOre(), Cubiomes.GraniteOre(), Cubiomes.GravelOre(), Cubiomes.IronOre(),
            Cubiomes.LapisOre(), Cubiomes.LargeCopperOre(), Cubiomes.LargeDebrisOre(), Cubiomes.LargeDiamondOre(),
            Cubiomes.LowerAndesiteOre(), Cubiomes.LowerCoalOre(), Cubiomes.LowerDioriteOre(), Cubiomes.LowerGoldOre(),
            Cubiomes.LowerGraniteOre(), Cubiomes.LowerRedstoneOre(), Cubiomes.MagmaOre(), Cubiomes.MediumDiamondOre(),
            Cubiomes.MiddleIronOre(), Cubiomes.NetherGoldOre(), Cubiomes.NetherGravelOre(), Cubiomes.NetherQuartzOre(),
            Cubiomes.RedstoneOre(), Cubiomes.SmallDebrisOre(), Cubiomes.SmallIronOre(), Cubiomes.SoulSandOre(),
            Cubiomes.TuffOre(), Cubiomes.UpperAndesiteOre(), Cubiomes.UpperCoalOre(), Cubiomes.UpperDioriteOre(),
            Cubiomes.UpperGraniteOre(), Cubiomes.UpperIronOre()
    };

    private SeedMapperCommandHandler() {
    }

    public static boolean handleChatCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) return false;

        String command = rawCommand.trim();
        if (command.startsWith("/")) command = command.substring(1);
        String lower = command.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("seedmap") && !lower.startsWith("sm")) return false;
        if (lower.equals("seedmap source") || lower.startsWith("seedmap source ")
                || lower.equals("sm source") || lower.startsWith("sm source ")
                || lower.equals("sm:source") || lower.startsWith("sm:source ")) {
            handleSourceRaw(command);
            return true;
        }

        String[] args = command.split("\\s+");
        if (args.length < 2) {
            sendHelp();
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "help" -> { sendHelp(); yield true; }
            case "seed" -> {
                if (args.length < 3) send("Usage: /seedmap seed <seed>");
                else applySeed(args[2]);
                yield true;
            }
            case "locate" -> { handleLocate(args); yield true; }
            case "highlight", "esp" -> { handleHighlight(args); yield true; }
            case "export" -> { handleExport(args); yield true; }
            case "source" -> { handleSource(args); yield true; }
            default -> { send("Unknown subcommand. Use /seedmap help"); yield true; }
        };
    }

    public static void handlePotentialLocateResult(String chatMessage) {
        if (pendingDatapackLocateId == null || chatMessage == null || chatMessage.isBlank()) return;
        Matcher matcher = LOCATE_COORDS.matcher(chatMessage);
        if (!matcher.find()) return;
        try {
            int x = Integer.parseInt(matcher.group(1));
            int z = Integer.parseInt(matcher.group(3));
            int dimension = getCurrentCubiomesDimension();
            if (dimension == Integer.MIN_VALUE) return;
            SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
            settings.addDatapackLocatedMarker(pendingDatapackLocateId, dimension, x, z);
            highlightLocation("datapack:" + pendingDatapackLocateId, x, z);
            send("Stored datapack structure '" + pendingDatapackLocateId + "' at X=" + x + " Z=" + z);
        } catch (NumberFormatException ignored) {
        } finally {
            pendingDatapackLocateId = null;
        }
    }

    private static void handleLocate(String[] args) {
        if (args.length < 3) {
            send("Usage: /seedmap locate <structure|biome|orevein|slime|loot> ...");
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "structure", "feature" -> locateStructure(args);
            case "biome" -> locateBiome(args);
            case "orevein", "ore_vein" -> locateOreVein(args);
            case "slime", "slimechunk", "slime_chunk" -> locateSlimeChunk();
            case "loot" -> locateLoot(args);
            default -> send("Unknown locate type. Use structure, biome, orevein, slime, or loot.");
        }
    }

    private static void handleHighlight(String[] args) {
        if (args.length < 3) {
            send("Usage: /seedmap highlight <ore|orevein|terrain|canyon|cave|clear> ...");
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "clear", "off" -> {
                SeedMapperEspManager.clear();
                send("Cleared ESP highlights.");
            }
            case "ore", "block" -> highlightOre(args);
            case "orevein", "ore_vein" -> highlightOreVeinEsp(args);
            case "terrain" -> highlightTerrainEsp(args);
            case "canyon", "ravine" -> highlightCanyonEsp(args);
            case "cave", "caves" -> highlightCaveEsp(args);
            default -> send("Unknown highlight type. Use ore, orevein, terrain, canyon, cave, or clear.");
        }
    }

    private static void handleExport(String[] args) {
        if (args.length == 2) {
            exportSeedMap();
            return;
        }

        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "visible" -> exportSeedMap();
            case "radius" -> {
                if (args.length < 4) {
                    send("Usage: /seedmap export radius <blocks>");
                    return;
                }
                try {
                    int radius = Math.max(0, Integer.parseInt(args[3]));
                    exportArea(GameVariableAccessShim.xCoord(), GameVariableAccessShim.zCoord(), radius, "player_radius");
                } catch (NumberFormatException e) {
                    send("Invalid radius: " + args[3]);
                }
            }
            case "area" -> {
                if (args.length < 6) {
                    send("Usage: /seedmap export area <x> <z> <radius>");
                    return;
                }
                try {
                    int x = Integer.parseInt(args[3]);
                    int z = Integer.parseInt(args[4]);
                    int radius = Math.max(0, Integer.parseInt(args[5]));
                    exportArea(x, z, radius, "manual_area");
                } catch (NumberFormatException e) {
                    send("Usage: /seedmap export area <x> <z> <radius>");
                }
            }
            default -> send("Usage: /seedmap export [visible|radius <blocks>|area <x> <z> <radius>]");
        }
    }

    private static void handleSource(String[] args) {
        if (args.length < 4) {
            send("Usage: /seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...");
            return;
        }

        int commandStart = parseSourceCommandStart(args);
        if (commandStart < 0) return;

        String forwarded = String.join(" ", Arrays.copyOfRange(args, commandStart, args.length)).trim();
        if (forwarded.isEmpty()) {
            send("Usage: /seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...");
            return;
        }
        forwarded = normalizeForwardedSourceCommand(forwarded);
        final String forwardedCommand = forwarded;
        SourceOverrides overrides = parseSourceOverrides(args, commandStart);
        boolean handled = runWithSourceOverrides(overrides, () -> handleChatCommand(forwardedCommand));
        if (!handled) {
            send("Unknown source command: " + forwarded);
        }
    }

    private static void handleSourceRaw(String command) {
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int sourceIndex = lower.indexOf("source");
        if (sourceIndex < 0) {
            send("Usage: /seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...");
            return;
        }
        String tail = trimmed.substring(sourceIndex + "source".length()).trim();
        if (tail.isBlank()) {
            send("Usage: /seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...");
            return;
        }
        String[] args = ("seedmap source " + tail).split("\\s+");
        handleSource(args);
    }

    private static int parseSourceCommandStart(String[] args) {
        int index = 2;
        while (index < args.length) {
            String token = args[index].toLowerCase(Locale.ROOT);
            switch (token) {
                case "run" -> {
                    int start = index + 1;
                    if (start >= args.length) {
                        send("Usage: /seedmap source run <command>");
                        return -1;
                    }
                    return start;
                }
                case "seeded", "in", "versioned", "flagged", "as" -> {
                    if (index + 1 >= args.length) {
                        send("Usage: /seedmap source " + token + " <value> <command>");
                        return -1;
                    }
                    index += 2;
                }
                case "positioned" -> {
                    if (index + 3 >= args.length) {
                        send("Usage: /seedmap source positioned <x> <y> <z> <command>");
                        return -1;
                    }
                    index += 4;
                }
                case "rotated" -> {
                    if (index + 2 >= args.length) {
                        send("Usage: /seedmap source rotated <yaw> <pitch> <command>");
                        return -1;
                    }
                    index += 3;
                }
                default -> {
                    return index;
                }
            }
        }
        send("Usage: /seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...");
        return -1;
    }

    private static String normalizeForwardedSourceCommand(String forwarded) {
        String normalized = forwarded.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("seedmap ")) return normalized;
        if (lower.startsWith("sm ")) return normalized;
        if (lower.startsWith("sm:")) {
            return "seedmap " + normalized.substring(3).trim();
        }
        if (lower.startsWith("seedmap:")) {
            return "seedmap " + normalized.substring(8).trim();
        }
        return "seedmap " + normalized;
    }

    private static SourceOverrides parseSourceOverrides(String[] args, int commandStart) {
        SourceOverridesBuilder builder = new SourceOverridesBuilder();
        int index = 2;
        while (index < commandStart && index < args.length) {
            String token = args[index].toLowerCase(Locale.ROOT);
            switch (token) {
                case "seeded" -> {
                    if (index + 1 < commandStart) {
                        try {
                            builder.seed = Long.parseLong(args[index + 1]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    index += 2;
                }
                case "positioned" -> {
                    if (index + 3 < commandStart) {
                        builder.x = parseIntOrNull(args[index + 1]);
                        builder.y = parseIntOrNull(args[index + 2]);
                        builder.z = parseIntOrNull(args[index + 3]);
                    }
                    index += 4;
                }
                case "in" -> {
                    if (index + 1 < commandStart) {
                        builder.dimension = parseDimensionOrNull(args[index + 1]);
                    }
                    index += 2;
                }
                case "versioned", "flagged", "as" -> index += 2;
                case "rotated" -> index += 3;
                case "run" -> index += 1;
                default -> index += 1;
            }
        }
        return builder.build();
    }

    private static boolean runWithSourceOverrides(SourceOverrides overrides, java.util.function.Supplier<Boolean> action) {
        SourceOverrides previous = SOURCE_OVERRIDES.get();
        try {
            if (overrides != null && !overrides.isEmpty()) {
                SOURCE_OVERRIDES.set(overrides);
            } else {
                SOURCE_OVERRIDES.remove();
            }
            return action.get();
        } finally {
            if (previous != null) {
                SOURCE_OVERRIDES.set(previous);
            } else {
                SOURCE_OVERRIDES.remove();
            }
        }
    }

    private static <T> T runWithSourceOverridesValue(SourceOverrides overrides, java.util.function.Supplier<T> action) {
        SourceOverrides previous = SOURCE_OVERRIDES.get();
        try {
            if (overrides != null && !overrides.isEmpty()) {
                SOURCE_OVERRIDES.set(overrides);
            } else {
                SOURCE_OVERRIDES.remove();
            }
            return action.get();
        } finally {
            if (previous != null) {
                SOURCE_OVERRIDES.set(previous);
            } else {
                SOURCE_OVERRIDES.remove();
            }
        }
    }

    private static Integer parseIntOrNull(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseDimensionOrNull(String token) {
        if (token == null) return null;
        String normalized = token.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        return switch (normalized) {
            case "overworld", "0" -> Cubiomes.DIM_OVERWORLD();
            case "the_nether", "nether", "-1" -> Cubiomes.DIM_NETHER();
            case "the_end", "end", "1" -> Cubiomes.DIM_END();
            default -> null;
        };
    }

    private static void locateStructure(String[] args) {
        if (args.length < 4) {
            send("Usage: /seedmap locate structure <feature_id>");
            return;
        }
        String query = args[3].toLowerCase(Locale.ROOT);
        SeedMapperFeature feature = resolveFeature(query);
        if (feature == null) {
            if (tryDatapackLocate(query)) return;
            send("Unknown structure feature: " + query);
            return;
        }

        SeedMapperMarker marker = findNearestMarker(m -> m.feature() == feature, 32768);
        if (marker == null) {
            send("No " + feature.id() + " found within 32768 blocks.");
            return;
        }
        highlightLocation(feature.id(), marker.blockX(), marker.blockZ());
        send(formatFoundCoords(marker.blockX(), marker.blockZ(), commandX(), commandZ(), true));
    }

    public static List<String> getStructureQueries() {
        int dimension = getCurrentCubiomesDimension();
        return java.util.Arrays.stream(SeedMapperFeature.values())
                .filter(feature -> feature.dimension() != Integer.MIN_VALUE)
                .filter(feature -> feature.availableInDimension(dimension))
                .map(SeedMapperFeature::id)
                .sorted()
                .toList();
    }

    public static List<String> getBiomeQueries() {
        int mc = SeedMapperCompat.getMcVersion();
        int dimension = getCurrentCubiomesDimension();
        ArrayList<String> names = new ArrayList<>();
        for (int id = 0; id < 256; id++) {
            try {
                MemorySegment biomeName = Cubiomes.biome2str(mc, id);
                if (biomeName == null || biomeName.address() == 0) continue;
                String name = biomeName.getString(0);
                if (name == null || name.isBlank()) continue;
                String normalized = name.toLowerCase(Locale.ROOT);
                if (isBiomeAvailableInDimension(normalized, dimension)) {
                    names.add(normalized);
                }
            } catch (Throwable ignored) {
            }
        }
        return names.stream().distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private static boolean isBiomeAvailableInDimension(String biomeName, int dimension) {
        if (biomeName == null || biomeName.isBlank() || dimension == Integer.MIN_VALUE) {
            return true;
        }

        boolean isEnd = biomeName.equals("the_end")
                || biomeName.equals("small_end_islands")
                || biomeName.startsWith("end_");
        boolean isNether = biomeName.equals("nether_wastes")
                || biomeName.equals("crimson_forest")
                || biomeName.equals("warped_forest")
                || biomeName.equals("basalt_deltas")
                || biomeName.equals("soul_sand_valley")
                || biomeName.equals("hell");

        if (dimension == Cubiomes.DIM_END()) {
            return isEnd;
        }
        if (dimension == Cubiomes.DIM_NETHER()) {
            return isNether;
        }
        return !isEnd && !isNether;
    }

    public static LocateResult locateStructureByQuery(String query, int maxRadius) {
        if (query == null || query.isBlank()) return null;
        SeedMapperFeature feature = resolveFeature(query.toLowerCase(Locale.ROOT));
        if (feature == null) return null;
        SeedMapperMarker marker = findNearestMarker(m -> m.feature() == feature, maxRadius);
        if (marker == null) return null;
        return new LocateResult(feature.id(), marker.blockX(), marker.blockZ());
    }

    public static LocateResult locateStructureByQuery(String query, int maxRadius, Integer fromX, Integer fromZ) {
        SourceOverrides overrides = new SourceOverridesBuilder().withPosition(fromX, fromZ).build();
        return runWithSourceOverridesValue(overrides, () -> locateStructureByQuery(query, maxRadius));
    }

    public static LocateResult locateBiomeByQuery(String query, int maxRadius) {
        if (query == null || query.isBlank()) return null;
        String biomeQuery = query.toLowerCase(Locale.ROOT);
        int wantedBiomeId = resolveBiomeId(biomeQuery);
        if (wantedBiomeId < 0) return null;

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return null;

        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            return null;
        }

        int px = GameVariableAccessShim.xCoord();
        int pz = GameVariableAccessShim.zCoord();
        int flags = 0;
        try (Arena arena = Arena.ofConfined()) {
            SeedMapperNative.ensureLoaded();
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, SeedMapperCompat.getMcVersion(), flags);
            Cubiomes.applySeed(generator, dimension, seed);

            int step = 64;
            for (int radius = 0; radius <= maxRadius; radius += step) {
                for (int dx = -radius; dx <= radius; dx += step) {
                    int dz = radius - Math.abs(dx);
                    int[] candidates = dz == 0 ? new int[]{0} : new int[]{dz, -dz};
                    for (int zOff : candidates) {
                        int x = px + dx;
                        int z = pz + zOff;
                        int biome = Cubiomes.getBiomeAt(generator, 4, x >> 2, 80, z >> 2);
                        if (biome == wantedBiomeId) {
                            return new LocateResult(biomeQuery, x, z);
                        }
                    }
                }
            }
        }
        return null;
    }

    public static LocateResult locateBiomeByQuery(String query, int maxRadius, Integer fromX, Integer fromZ) {
        SourceOverrides overrides = new SourceOverridesBuilder().withPosition(fromX, fromZ).build();
        return runWithSourceOverridesValue(overrides, () -> locateBiomeByQuery(query, maxRadius));
    }

    public static LocateResult locateLootByQuery(String query, int amount, int maxRadius, Integer fromX, Integer fromZ) {
        if (query == null || query.isBlank()) return null;
        SourceOverrides overrides = new SourceOverridesBuilder().withPosition(fromX, fromZ).build();
        return runWithSourceOverridesValue(overrides, () -> {
            LootMatch match = findNearestLootMarker(query.toLowerCase(Locale.ROOT), Math.max(1, amount), maxRadius);
            if (match == null) {
                return null;
            }
            return new LocateResult(
                    query.toLowerCase(Locale.ROOT),
                    match.marker.blockX(),
                    match.marker.blockZ(),
                    match.totalCount,
                    match.itemLabel,
                    match.marker.feature().id()
            );
        });
    }

    private static void locateLoot(String[] args) {
        if (args.length < 4) {
            send("Usage: /seedmap locate loot <text>");
            return;
        }
        String query;
        int requiredCount = 1;
        if (args.length >= 5 && isIntegerToken(args[3])) {
            // SeedMapper-compatible form: locate loot <amount> <predicate>
            requiredCount = Math.max(1, Integer.parseInt(args[3]));
            query = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).toLowerCase(Locale.ROOT);
        } else {
            query = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).toLowerCase(Locale.ROOT);
        }
        if (query.isBlank()) {
            send("Usage: /seedmap locate loot <text>");
            return;
        }
        LootMatch match = findNearestLootMarker(query, requiredCount, 32768);
        if (match == null) {
            send("No lootable structure matching '" + query + "' found within 32768 blocks.");
            return;
        }
        highlightLocation("loot:" + match.marker.feature().id(), match.marker.blockX(), match.marker.blockZ());
        send("Found " + match.totalCount + " of " + match.itemLabel + " in " + match.marker.feature().id());
        send(formatFoundCoords(match.marker.blockX(), match.marker.blockZ(), commandX(), commandZ(), false));
    }

    private static boolean isIntegerToken(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
    private static void locateSlimeChunk() {
        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return;

        int px = GameVariableAccessShim.xCoord();
        int pz = GameVariableAccessShim.zCoord();
        int centerChunkX = floorDiv(px, 16);
        int centerChunkZ = floorDiv(pz, 16);

        for (int radius = 0; radius <= 256; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int dz = radius - Math.abs(dx);
                int[] candidates = dz == 0 ? new int[]{0} : new int[]{dz, -dz};
                for (int zOff : candidates) {
                    int cx = centerChunkX + dx;
                    int cz = centerChunkZ + zOff;
                    if (isSlimeChunk(seed, cx, cz)) {
                        int x = cx * 16 + 8;
                        int z = cz * 16 + 8;
                        highlightLocation("slime_chunk", x, z);
                        send("Nearest slime chunk at chunk " + cx + "," + cz + " (X=" + x + " Z=" + z + ")");
                        return;
                    }
                }
            }
        }
        send("No slime chunk found within 256 chunk radius.");
    }

    private static void locateOreVein(String[] args) {
        if (args.length < 4) {
            send("Usage: /seedmap locate orevein <iron|copper>");
            return;
        }
        String type = args[3].toLowerCase(Locale.ROOT);
        boolean copper = type.contains("copper");
        boolean iron = type.contains("iron");
        if (!copper && !iron) {
            send("Unknown ore vein type: " + type + " (use iron or copper)");
            return;
        }

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return;

        int px = GameVariableAccessShim.xCoord();
        int pz = GameVariableAccessShim.zCoord();
        int sampleY = copper ? 48 : -8;
        try (Arena arena = Arena.ofConfined()) {
            SeedMapperNative.ensureLoaded();
            MemorySegment params = OreVeinParameters.allocate(arena);
            if (Cubiomes.initOreVeinNoise(params, seed, SeedMapperCompat.getMcVersion()) == 0) {
                send("Ore vein noise is unavailable for this MC version.");
                return;
            }

            for (int radius = 0; radius <= 4096; radius += 16) {
                for (int dx = -radius; dx <= radius; dx += 16) {
                    int dz = radius - Math.abs(dx);
                    int[] candidates = dz == 0 ? new int[]{0} : new int[]{dz, -dz};
                    for (int zOff : candidates) {
                        int x = px + dx;
                        int z = pz + zOff;
                        int block = Cubiomes.getOreVeinBlockAt(x, sampleY, z, params);
                        if (matchesOreVeinType(block, copper, iron)) {
                            highlightLocation((copper ? "copper" : "iron") + "_ore_vein", x, z);
                            send("Nearest " + (copper ? "copper" : "iron") + " ore vein sample at X=" + x + " Z=" + z);
                            return;
                        }
                    }
                }
            }
        }

        send("No matching ore vein sample found within 4096 blocks.");
    }

    private static void locateBiome(String[] args) {
        if (args.length < 4) {
            send("Usage: /seedmap locate biome <biome_name>");
            return;
        }
        String biomeQuery = args[3].toLowerCase(Locale.ROOT);
        int wantedBiomeId = resolveBiomeId(biomeQuery);
        if (wantedBiomeId < 0) {
            send("Unknown biome: " + biomeQuery);
            return;
        }

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return;

        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            send("No world loaded.");
            return;
        }

        int px = GameVariableAccessShim.xCoord();
        int pz = GameVariableAccessShim.zCoord();
        int flags = 0;
        try (Arena arena = Arena.ofConfined()) {
            SeedMapperNative.ensureLoaded();
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, SeedMapperCompat.getMcVersion(), flags);
            Cubiomes.applySeed(generator, dimension, seed);

            int step = 64;
            for (int radius = 0; radius <= 8192; radius += step) {
                for (int dx = -radius; dx <= radius; dx += step) {
                    int dz = radius - Math.abs(dx);
                    int[] candidates = dz == 0 ? new int[]{0} : new int[]{dz, -dz};
                    for (int zOff : candidates) {
                        int x = px + dx;
                        int z = pz + zOff;
                        int biome = Cubiomes.getBiomeAt(generator, 4, x >> 2, 80, z >> 2);
                        if (biome == wantedBiomeId) {
                            highlightLocation("biome:" + biomeQuery, x, z);
                            send("Nearest biome match at X=" + x + " Z=" + z);
                            return;
                        }
                    }
                }
            }
        }

        send("No biome match found within 8192 blocks.");
    }

    private static void highlightOre(String[] args) {
        if (args.length < 4) {
            send("Usage: /seedmap highlight ore <block> [chunks]");
            return;
        }
        int targetBlock = resolveBlockId(args[3]);
        if (targetBlock == -1) {
            send("Unknown ore block '" + args[3] + "'. Example: diamond_ore, iron_ore, nether_quartz_ore");
            return;
        }
        int chunkRange = 0;
        if (args.length >= 5) {
            try {
                chunkRange = Integer.parseInt(args[4]);
            } catch (NumberFormatException ignored) {
                send("Invalid chunks value: " + args[4]);
                return;
            }
        }
        if (chunkRange < 0 || chunkRange > MAX_HIGHLIGHT_CHUNK_RANGE) {
            send("Chunk range must be 0-" + MAX_HIGHLIGHT_CHUNK_RANGE + " for stability.");
            return;
        }

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return;
        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            send("No world loaded.");
            return;
        }

        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            send("No world loaded.");
            return;
        }

        int playerChunkX = floorDiv(GameVariableAccessShim.xCoord(), 16);
        int playerChunkZ = floorDiv(GameVariableAccessShim.zCoord(), 16);
        int mcVersion = SeedMapperCompat.getMcVersion();
        int flags = 0;

        List<BlockPos> matches = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            SeedMapperNative.ensureLoaded();
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, mcVersion, flags);
            Cubiomes.applySeed(generator, dimension, seed);
            MemorySegment surfaceNoise = SurfaceNoise.allocate(arena);
            Cubiomes.initSurfaceNoise(surfaceNoise, dimension, seed);

            forEachChunkInSpiral(playerChunkX, playerChunkZ, chunkRange, (chunkX, chunkZ) -> {
                var chunkAccess = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                LevelChunk chunk = chunkAccess instanceof LevelChunk lc ? lc : null;
                boolean doAirCheck = chunk != null;
                Map<BlockPos, Integer> generatedOres = new HashMap<>();
                List<Integer> biomes = mcVersion <= Cubiomes.MC_1_17()
                        ? List.of(Cubiomes.getBiomeForOreGen(generator, chunkX, chunkZ, 0))
                        : List.of(Cubiomes.getBiomeForOreGen(generator, chunkX, chunkZ, -30), Cubiomes.getBiomeForOreGen(generator, chunkX, chunkZ, 64), Cubiomes.getBiomeForOreGen(generator, chunkX, chunkZ, 120));

                for (int oreType : ORE_TYPES) {
                    boolean viable = false;
                    for (int biome : biomes) {
                        if (Cubiomes.isViableOreBiome(mcVersion, oreType, biome) != 0) {
                            viable = true;
                            break;
                        }
                    }
                    if (!viable) continue;

                    MemorySegment oreConfig = OreConfig.allocate(arena);
                    if (Cubiomes.getOreConfig(oreType, mcVersion, biomes.getFirst(), oreConfig) == 0) continue;

                    int oreBlock = OreConfig.oreBlock(oreConfig);
                    int numReplaceBlocks = OreConfig.numReplaceBlocks(oreConfig) & 0xFF;
                    MemorySegment replaceBlocks = OreConfig.replaceBlocks(oreConfig);
                    MemorySegment pos3List = Cubiomes.generateOres(arena, generator, surfaceNoise, oreConfig, chunkX, chunkZ);
                    try {
                        int size = Pos3List.size(pos3List);
                        MemorySegment pos3s = Pos3List.pos3s(pos3List);
                        for (int i = 0; i < size; i++) {
                            MemorySegment pos3 = Pos3.asSlice(pos3s, i);
                            BlockPos pos = new BlockPos(Pos3.x(pos3), Pos3.y(pos3), Pos3.z(pos3));
                            if (doAirCheck && isAirOrLava(chunk, pos)) continue;

                            Integer previous = generatedOres.get(pos);
                            if (previous != null) {
                                boolean canReplace = false;
                                for (int j = 0; j < numReplaceBlocks; j++) {
                                    int replaceBlock = replaceBlocks.getAtIndex(Cubiomes.C_INT, j);
                                    if (replaceBlock == previous) {
                                        canReplace = true;
                                        break;
                                    }
                                }
                                if (!canReplace) continue;
                            }
                            generatedOres.put(pos, oreBlock);
                        }
                    } finally {
                        Cubiomes.freePos3List(pos3List);
                    }
                }

                for (Map.Entry<BlockPos, Integer> entry : generatedOres.entrySet()) {
                    if (entry.getValue() == targetBlock) matches.add(entry.getKey());
                }
            });
        }

        if (matches.isEmpty()) {
            send("No matching ore blocks found in " + (chunkRange * 2 + 1) + "x" + (chunkRange * 2 + 1) + " chunk area.");
            return;
        }

        SeedMapperEspManager.drawBoxes(SeedMapperEspTarget.BLOCK_HIGHLIGHT, matches, colorForBlock(targetBlock));
        send("Highlighted " + matches.size() + " ore blocks.");
    }
    private static void highlightOreVeinEsp(String[] args) {
        int chunkRange = 0;
        if (args.length >= 4) {
            try {
                chunkRange = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                send("Invalid chunks value: " + args[3]);
                return;
            }
        }
        if (chunkRange < 0 || chunkRange > MAX_HIGHLIGHT_CHUNK_RANGE) {
            send("Chunk range must be 0-" + MAX_HIGHLIGHT_CHUNK_RANGE + " for stability.");
            return;
        }

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return;

        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            send("No world loaded.");
            return;
        }

        int playerChunkX = floorDiv(GameVariableAccessShim.xCoord(), 16);
        int playerChunkZ = floorDiv(GameVariableAccessShim.zCoord(), 16);

        Map<BlockPos, Integer> blocks = new HashMap<>();
        try (Arena arena = Arena.ofConfined()) {
            SeedMapperNative.ensureLoaded();
            MemorySegment params = OreVeinParameters.allocate(arena);
            if (Cubiomes.initOreVeinNoise(params, seed, SeedMapperCompat.getMcVersion()) == 0) {
                send("Ore vein ESP is unavailable for this MC version.");
                return;
            }

            forEachChunkInSpiral(playerChunkX, playerChunkZ, chunkRange, (chunkX, chunkZ) -> {
                var chunkAccess = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                LevelChunk chunk = chunkAccess instanceof LevelChunk lc ? lc : null;
                boolean doAirCheck = chunk != null;
                int minX = chunkX << 4;
                int minZ = chunkZ << 4;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = -60; y <= 50; y++) {
                            int block = Cubiomes.getOreVeinBlockAt(minX + x, y, minZ + z, params);
                            if (block == -1 || block == Cubiomes.GRANITE() || block == Cubiomes.TUFF()) continue;
                            BlockPos pos = new BlockPos(minX + x, y, minZ + z);
                            if (doAirCheck && isAirOrLava(chunk, pos)) continue;
                            blocks.put(pos, block);
                        }
                    }
                }
            });
        }

        if (blocks.isEmpty()) {
            send("No ore vein blocks found in " + (chunkRange * 2 + 1) + "x" + (chunkRange * 2 + 1) + " chunk area.");
            return;
        }

        blocks.entrySet().stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toList())))
                .forEach((block, positions) -> SeedMapperEspManager.drawBoxes(SeedMapperEspTarget.ORE_VEIN, positions, colorForBlock(block)));
        send("Highlighted " + blocks.size() + " ore vein blocks.");
    }

    private static void highlightTerrainEsp(String[] args) {
        int chunkRange = parseHighlightChunkRange(args, 3);
        if (chunkRange < 0) {
            return;
        }
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            send("No world loaded.");
            return;
        }

        int dimension = getCurrentCubiomesDimension();
        if (dimension != Cubiomes.DIM_OVERWORLD()) {
            send("Terrain ESP only works in the overworld.");
            return;
        }

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) {
            return;
        }

        int version = SeedMapperCompat.getMcVersion();
        int generatorFlags = 0;
        int playerChunkX = floorDiv(GameVariableAccessShim.xCoord(), 16);
        int playerChunkZ = floorDiv(GameVariableAccessShim.zCoord(), 16);
        int minChunkX = playerChunkX - chunkRange;
        int minChunkZ = playerChunkZ - chunkRange;
        int chunkW = chunkRange * 2 + 1;
        int chunkH = chunkRange * 2 + 1;
        int blockW = chunkW << 4;
        int blockH = chunkH << 4;
        int minX = minChunkX << 4;
        int minZ = minChunkZ << 4;

        Set<BlockPos> blocks = new HashSet<>();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment params = TerrainNoise.allocate(arena);
            if (Cubiomes.setupTerrainNoise(params, version, generatorFlags) == 0) {
                send("Terrain ESP is unavailable for this MC version.");
                return;
            }
            if (Cubiomes.initTerrainNoise(params, seed, dimension) == 0) {
                send("Terrain ESP could not initialize for this seed.");
                return;
            }

            SequenceLayout columnLayout = MemoryLayout.sequenceLayout(384, Cubiomes.C_INT);
            MemorySegment blockStates = arena.allocate(columnLayout, (long) blockW * blockH);
            MemorySegment heights = arena.allocate(Cubiomes.C_INT, (long) blockW * blockH);
            Cubiomes.generateRegion(params, minChunkX, minChunkZ, chunkW, chunkH, blockStates, heights, 1);

            for (int relX = 0; relX < blockW; relX++) {
                int x = minX + relX;
                for (int relZ = 0; relZ < blockH; relZ++) {
                    int z = minZ + relZ;
                    int columnIndex = relX * blockH + relZ;
                    int stored = heights.getAtIndex(Cubiomes.C_INT, columnIndex);
                    if (stored <= -64) {
                        continue;
                    }
                    int surfaceY = stored - 1 - 64;
                    blocks.add(new BlockPos(x, surfaceY, z));
                }
            }
        }

        if (blocks.isEmpty()) {
            send("No terrain samples found in loaded chunks.");
            return;
        }

        SeedMapperEspManager.clear();
        SeedMapperEspManager.drawBoxes(SeedMapperEspTarget.TERRAIN, blocks, 0xFF0000);
        send("Highlighted " + blocks.size() + " terrain samples.");
    }

    private static void highlightCanyonEsp(String[] args) {
        int chunkRange = parseHighlightChunkRange(args, 3);
        if (chunkRange < 0) {
            return;
        }
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            send("No world loaded.");
            return;
        }

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) {
            return;
        }

        int dimension = getCurrentCubiomesDimension();
        int version = SeedMapperCompat.getMcVersion();
        int playerChunkX = floorDiv(GameVariableAccessShim.xCoord(), 16);
        int playerChunkZ = floorDiv(GameVariableAccessShim.zCoord(), 16);
        Set<BlockPos> blocks = new HashSet<>();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ccc = CanyonCarverConfig.allocate(arena);
            if (Cubiomes.getCanyonCarverConfig(0, version, ccc) == 0) {
                send("Canyon ESP is unavailable for this MC version.");
                return;
            }
            if (CanyonCarverConfig.dim(ccc) != dimension) {
                send("Canyon ESP is unavailable in this dimension.");
                return;
            }

            ToIntBiFunction<Integer, Integer> biomeFunction = getCarverBiomeFunction(arena, seed, dimension, version, 0);
            forEachChunkInSpiral(playerChunkX, playerChunkZ, chunkRange, (chunkX, chunkZ) -> {
                int biome = biomeFunction.applyAsInt(chunkX, chunkZ);
                if (Cubiomes.isViableCanyonBiome(0, biome) == 0) {
                    return;
                }
                MemorySegment pos3List = Cubiomes.carveCanyon(arena, seed, chunkX, chunkZ, ccc);
                addPos3List(blocks, pos3List);
            });
        }

        if (blocks.isEmpty()) {
            send("No canyon/ravine samples found in loaded chunks.");
            return;
        }

        SeedMapperEspManager.clear();
        SeedMapperEspManager.drawBoxes(SeedMapperEspTarget.CANYON, blocks, 0xE38B34);
        send("Highlighted " + blocks.size() + " canyon samples.");
    }

    private static void highlightCaveEsp(String[] args) {
        int chunkRange = parseHighlightChunkRange(args, 3);
        if (chunkRange < 0) {
            return;
        }
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) {
            send("No world loaded.");
            return;
        }

        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) {
            return;
        }

        int dimension = getCurrentCubiomesDimension();
        int version = SeedMapperCompat.getMcVersion();
        int playerChunkX = floorDiv(GameVariableAccessShim.xCoord(), 16);
        int playerChunkZ = floorDiv(GameVariableAccessShim.zCoord(), 16);
        Set<BlockPos> blocks = new HashSet<>();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ccc = CaveCarverConfig.allocate(arena);
            if (Cubiomes.getCaveCarverConfig(0, version, -1, ccc) == 0) {
                send("Cave ESP is unavailable for this MC version.");
                return;
            }
            if (CaveCarverConfig.dim(ccc) != dimension) {
                send("Cave ESP is unavailable in this dimension.");
                return;
            }

            ToIntBiFunction<Integer, Integer> biomeFunction = getCarverBiomeFunction(arena, seed, dimension, version, 0);
            forEachChunkInSpiral(playerChunkX, playerChunkZ, chunkRange, (chunkX, chunkZ) -> {
                int biome = biomeFunction.applyAsInt(chunkX, chunkZ);
                if (Cubiomes.isViableCaveBiome(0, biome) == 0) {
                    return;
                }
                MemorySegment pos3List = Cubiomes.carveCave(arena, seed, chunkX, chunkZ, ccc);
                addPos3List(blocks, pos3List);
            });
        }

        if (blocks.isEmpty()) {
            send("No cave samples found in loaded chunks.");
            return;
        }

        SeedMapperEspManager.clear();
        SeedMapperEspManager.drawBoxes(SeedMapperEspTarget.CAVE, blocks, 0x8D68FF);
        send("Highlighted " + blocks.size() + " cave samples.");
    }

    private static void applySeed(String seedText) {
        VoxelConstants.getVoxelMapInstance().setWorldSeed(seedText);
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        settings.manualSeed = seedText;
        settings.putSavedSeed(settings.getCurrentServerKey(), seedText);
        send("Seed set to " + seedText + " for this server/world.");
    }

    private static void exportSeedMap() {
        exportArea(GameVariableAccessShim.xCoord(), GameVariableAccessShim.zCoord(), 8192, "visible");
    }

    public static void exportArea(int centerX, int centerZ, int radius, String sourceLabel) {
        exportBounds(centerX - radius, centerX + radius, centerZ - radius, centerZ + radius, sourceLabel, centerX, centerZ, radius);
    }

    public static void exportBounds(int minX, int maxX, int minZ, int maxZ, String sourceLabel) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int radius = Math.max(Math.abs(maxX - centerX), Math.abs(maxZ - centerZ));
        exportBounds(minX, maxX, minZ, maxZ, sourceLabel, centerX, centerZ, radius);
    }

    private static void exportBounds(int minX, int maxX, int minZ, int maxZ, String sourceLabel, int centerX, int centerZ, int radius) {
        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return;
        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            send("No world loaded.");
            return;
        }

        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        int flags = 0;
        List<SeedMapperMarker> markers = queryMarkers(seed, dimension, flags, minX, maxX, minZ, maxZ, settings);
        writeExport(seed, dimension, centerX, centerZ, radius, minX, maxX, minZ, maxZ, sourceLabel, markers, settings);
    }

    private static List<SeedMapperMarker> queryMarkers(long seed, int dimension, int flags, int minX, int maxX, int minZ, int maxZ, SeedMapperSettingsManager settings) {
        return SeedMapperLocatorService.get().queryBlocking(seed, dimension, SeedMapperCompat.getMcVersion(), flags, minX, maxX, minZ, maxZ, settings);
    }

    private static void writeExport(long seed, int dimension, int centerX, int centerZ, int radius, int minX, int maxX, int minZ, int maxZ,
                                    String sourceLabel, List<SeedMapperMarker> markers, SeedMapperSettingsManager settings) {
        JsonObject root = new JsonObject();
        root.addProperty("seed", seed);
        root.addProperty("dimension", dimension);
        root.addProperty("playerX", GameVariableAccessShim.xCoord());
        root.addProperty("playerZ", GameVariableAccessShim.zCoord());
        root.addProperty("centerX", centerX);
        root.addProperty("centerZ", centerZ);
        root.addProperty("radius", radius);
        root.addProperty("minX", minX);
        root.addProperty("maxX", maxX);
        root.addProperty("minZ", minZ);
        root.addProperty("maxZ", maxZ);
        root.addProperty("source", sourceLabel == null ? "export" : sourceLabel);
        root.addProperty("datapackEnabled", settings.datapackEnabled);
        root.addProperty("datapackCachePath", settings.datapackCachePath);

        String worldKey = currentWorldKey();
        JsonArray arr = new JsonArray();
        for (SeedMapperMarker marker : markers) {
            JsonObject entry = new JsonObject();
            entry.addProperty("feature", marker.feature().id());
            entry.addProperty("label", marker.label());
            entry.addProperty("x", marker.blockX());
            entry.addProperty("z", marker.blockZ());
            entry.addProperty("completed", settings.isCompleted(worldKey, marker.feature(), marker.blockX(), marker.blockZ()));
            arr.add(entry);
        }
        root.add("markers", arr);

        JsonArray lootTables = new JsonArray();
        for (SeedMapperLootService.LootTablePreview preview : SeedMapperLootService.buildLootTablePreviews(seed, "", SeedMapperCompat.getMcVersion())) {
            JsonObject entry = new JsonObject();
            entry.addProperty("feature", preview.feature().id());
            entry.addProperty("table", preview.tableName());
            entry.addProperty("error", preview.error());
            JsonArray items = new JsonArray();
            for (String item : preview.items()) items.add(item);
            entry.add("items", items);
            lootTables.add(entry);
        }
        root.add("lootTables", lootTables);

        JsonArray lootEntries = new JsonArray();
        for (SeedMapperLootService.LootEntry lootEntry : collectLootEntriesForMarkers(seed, dimension, markers)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", lootEntry.id());
            entry.addProperty("type", lootEntry.type());
            entry.addProperty("piece", lootEntry.pieceName());
            entry.addProperty("x", lootEntry.pos().getX());
            entry.addProperty("z", lootEntry.pos().getZ());
            JsonArray items = new JsonArray();
            for (SeedMapperLootService.LootItem item : lootEntry.items()) {
                JsonObject itemJson = new JsonObject();
                itemJson.addProperty("slot", item.slot());
                itemJson.addProperty("count", item.count());
                itemJson.addProperty("itemId", item.itemId());
                itemJson.addProperty("displayName", item.displayName());
                itemJson.addProperty("nbt", item.nbt());
                JsonArray enchants = new JsonArray();
                for (int i = 0; i < item.enchantments().size(); i++) {
                    JsonObject enchant = new JsonObject();
                    enchant.addProperty("id", item.enchantments().get(i));
                    if (i < item.enchantmentLevels().size()) {
                        enchant.addProperty("level", item.enchantmentLevels().get(i));
                    }
                    enchants.add(enchant);
                }
                itemJson.add("enchantments", enchants);
                items.add(itemJson);
            }
            entry.add("items", items);
            lootEntries.add(entry);
        }
        root.add("lootEntries", lootEntries);

        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("voxelmap").resolve("seedmapper").resolve("exports");
        try {
            Files.createDirectories(dir);
            Path out = dir.resolve("seedmap_" + seed + "_" + EXPORT_TIMESTAMP.format(LocalDateTime.now()) + ".json");
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            send("Exported " + markers.size() + " markers and " + lootEntries.size() + " loot entries to " + out.toAbsolutePath());
        } catch (IOException e) {
            send("Failed to export seedmap: " + e.getMessage());
        }
    }

    private static List<SeedMapperLootService.LootEntry> collectLootEntriesForMarkers(long seed, int dimension, List<SeedMapperMarker> markers) {
        List<SeedMapperLootService.LootTarget> targets = new ArrayList<>();
        for (SeedMapperMarker marker : markers) {
            if (marker == null || marker.feature() == null || !marker.feature().lootable() || marker.feature().structureId() < 0) {
                continue;
            }
            targets.add(new SeedMapperLootService.LootTarget(marker.feature().structureId(), new BlockPos(marker.blockX(), 0, marker.blockZ())));
        }
        if (targets.isEmpty()) {
            return List.of();
        }
        return SeedMapperLootService.collectLootEntries(seed, SeedMapperCompat.getMcVersion(), dimension, 0, targets);
    }

    private static String currentWorldKey() {
        String worldName = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentWorldName();
        String subworld = VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false);
        String dimId = GameVariableAccessShim.getWorld() == null ? "unknown" : GameVariableAccessShim.getWorld().dimension().identifier().toString();
        return (worldName == null ? "unknown" : worldName) + "|" + (subworld == null ? "" : subworld) + "|" + dimId;
    }

    private static void forEachChunkInSpiral(int centerChunkX, int centerChunkZ, int range, ChunkConsumer consumer) {
        consumer.accept(centerChunkX, centerChunkZ);
        for (int radius = 1; radius <= range; radius++) {
            int minX = centerChunkX - radius;
            int maxX = centerChunkX + radius;
            int minZ = centerChunkZ - radius;
            int maxZ = centerChunkZ + radius;
            for (int x = minX; x <= maxX; x++) {
                consumer.accept(x, minZ);
                consumer.accept(x, maxZ);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                consumer.accept(minX, z);
                consumer.accept(maxX, z);
            }
        }
    }

    private static int resolveBlockId(String input) {
        String q = input.toLowerCase(Locale.ROOT);
        return switch (q) {
            case "ancient_debris" -> Cubiomes.ANCIENT_DEBRIS();
            case "andesite" -> Cubiomes.ANDESITE();
            case "blackstone" -> Cubiomes.BLACKSTONE();
            case "clay" -> Cubiomes.CLAY();
            case "coal_ore" -> Cubiomes.COAL_ORE();
            case "copper_ore" -> Cubiomes.COPPER_ORE();
            case "deepslate" -> Cubiomes.DEEPSLATE();
            case "diamond_ore" -> Cubiomes.DIAMOND_ORE();
            case "diorite" -> Cubiomes.DIORITE();
            case "dirt" -> Cubiomes.DIRT();
            case "emerald_ore" -> Cubiomes.EMERALD_ORE();
            case "gold_ore" -> Cubiomes.GOLD_ORE();
            case "granite" -> Cubiomes.GRANITE();
            case "gravel" -> Cubiomes.GRAVEL();
            case "iron_ore" -> Cubiomes.IRON_ORE();
            case "lapis_ore" -> Cubiomes.LAPIS_ORE();
            case "magma_block" -> Cubiomes.MAGMA_BLOCK();
            case "nether_gold_ore" -> Cubiomes.NETHER_GOLD_ORE();
            case "nether_quartz_ore" -> Cubiomes.NETHER_QUARTZ_ORE();
            case "raw_copper_block" -> Cubiomes.RAW_COPPER_BLOCK();
            case "raw_iron_block" -> Cubiomes.RAW_IRON_BLOCK();
            case "redstone_ore" -> Cubiomes.REDSTONE_ORE();
            case "soul_sand" -> Cubiomes.SOUL_SAND();
            case "stone" -> Cubiomes.STONE();
            case "tuff" -> Cubiomes.TUFF();
            default -> -1;
        };
    }

    private static int parseHighlightChunkRange(String[] args, int index) {
        int chunkRange = 0;
        if (args.length >= index + 1) {
            try {
                chunkRange = Integer.parseInt(args[index]);
            } catch (NumberFormatException ignored) {
                send("Invalid chunks value: " + args[index]);
                return -1;
            }
        }
        if (chunkRange < 0 || chunkRange > MAX_HIGHLIGHT_CHUNK_RANGE) {
            send("Chunk range must be 0-" + MAX_HIGHLIGHT_CHUNK_RANGE + " for stability.");
            return -1;
        }
        return chunkRange;
    }

    private static LevelChunk getLoadedChunk(Level level, int chunkX, int chunkZ) {
        var chunkAccess = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        return chunkAccess instanceof LevelChunk lc ? lc : null;
    }

    private static void addPos3List(Set<BlockPos> blocks, MemorySegment pos3List) {
        if (pos3List == null) {
            return;
        }
        int size = Pos3List.size(pos3List);
        MemorySegment pos3s = Pos3List.pos3s(pos3List);
        for (int i = 0; i < size; i++) {
            MemorySegment pos3 = Pos3.asSlice(pos3s, i);
            blocks.add(new BlockPos(Pos3.x(pos3), Pos3.y(pos3), Pos3.z(pos3)));
        }
    }

    private static int colorForBlock(int block) {
        if (block == Cubiomes.DIAMOND_ORE()) return 0x3AD9D4;
        if (block == Cubiomes.EMERALD_ORE()) return 0x36CB62;
        if (block == Cubiomes.GOLD_ORE() || block == Cubiomes.NETHER_GOLD_ORE()) return 0xF0B800;
        if (block == Cubiomes.IRON_ORE() || block == Cubiomes.RAW_IRON_BLOCK()) return 0xD8AF93;
        if (block == Cubiomes.COPPER_ORE() || block == Cubiomes.RAW_COPPER_BLOCK()) return 0xC7744A;
        if (block == Cubiomes.COAL_ORE()) return 0x303030;
        if (block == Cubiomes.LAPIS_ORE()) return 0x3158D9;
        if (block == Cubiomes.REDSTONE_ORE()) return 0xC42020;
        if (block == Cubiomes.ANCIENT_DEBRIS()) return 0x5B3E2B;
        if (block == Cubiomes.NETHER_QUARTZ_ORE()) return 0xD8D3C8;
        return 0x00CFFF;
    }

    private static boolean isAirOrLava(LevelChunk chunk, BlockPos pos) {
        if (chunk == null) return false;
        var state = chunk.getBlockState(pos);
        return state.isAir() || state.is(Blocks.LAVA) || state.getFluidState().is(Fluids.LAVA);
    }

    private static SeedMapperMarker findNearestMarker(java.util.function.Predicate<SeedMapperMarker> predicate, int maxRadius) {
        List<SeedMapperMarker> markers = queryMarkers(maxRadius);
        if (markers.isEmpty()) {
            return null;
        }
        int px = commandX();
        int pz = commandZ();
        SeedMapperMarker best = null;
        long bestDist = Long.MAX_VALUE;
        for (SeedMapperMarker marker : markers) {
            if (!predicate.test(marker)) continue;
            long dx = marker.blockX() - (long) px;
            long dz = marker.blockZ() - (long) pz;
            long dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = marker;
            }
        }
        return best;
    }

    private static LootMatch findNearestLootMarker(String rawQuery, int requiredCount, int maxRadius) {
        List<SeedMapperMarker> markers = queryMarkers(maxRadius).stream()
                .filter(marker -> marker.feature().lootable())
                .sorted(Comparator.comparingLong(marker -> {
                    long dx = marker.blockX() - (long) commandX();
                    long dz = marker.blockZ() - (long) commandZ();
                    return dx * dx + dz * dz;
                }))
                .limit(96)
                .toList();
        if (markers.isEmpty()) {
            return null;
        }

        long seed = resolveSeed();
        int dimension = getCurrentCubiomesDimension();
        if (seed == Long.MIN_VALUE || dimension == Integer.MIN_VALUE) {
            return null;
        }
        int mcVersion = SeedMapperCompat.getMcVersion();
        LootQuery query = parseLootQuery(rawQuery);

        for (SeedMapperMarker marker : markers) {
            List<SeedMapperLootService.LootEntry> lootEntries = SeedMapperLootService.collectLootEntries(
                    seed,
                    mcVersion,
                    dimension,
                    0,
                    List.of(new SeedMapperLootService.LootTarget(marker.feature().structureId(), new BlockPos(marker.blockX(), 0, marker.blockZ()))));
            LootMatchSummary summary = summarizeLootQuery(lootEntries, query);
            if (summary != null && summary.totalCount() >= requiredCount) {
                return new LootMatch(marker, summary.itemLabel(), summary.totalCount());
            }
        }
        return null;
    }

    private static List<SeedMapperMarker> queryMarkers(int maxRadius) {
        long seed = resolveSeed();
        if (seed == Long.MIN_VALUE) return List.of();
        int dimension = getCurrentCubiomesDimension();
        if (dimension == Integer.MIN_VALUE) {
            send("No world loaded.");
            return List.of();
        }
        int px = commandX();
        int pz = commandZ();
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        return SeedMapperLocatorService.get().queryBlocking(
                seed, dimension, SeedMapperCompat.getMcVersion(), 0,
                px - maxRadius, px + maxRadius, pz - maxRadius, pz + maxRadius, settings);
    }

    private static String normalizeLootQueryToken(String query) {
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized.replace(' ', '_');
    }

    private static String normalizeLootItemId(String itemId) {
        if (itemId == null) {
            return "";
        }
        return itemId.startsWith("minecraft:") ? itemId.substring("minecraft:".length()) : itemId;
    }

    private static LootQuery parseLootQuery(String rawQuery) {
        String normalized = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT).trim();
        String[] parts = normalized.split("\\s+with\\s+", 2);
        String itemToken = normalizeLootQueryToken(parts[0]);
        String enchantToken = "";
        int minEnchantLevel = 0;
        if (parts.length > 1) {
            String rawEnchantPart = parts[1].trim();
            String compact = normalizeLootQueryToken(rawEnchantPart);
            String[] compactParts = compact.split("_");
            if (compactParts.length >= 2) {
                String last = compactParts[compactParts.length - 1];
                Integer parsed = parseEnchantLevelToken(last);
                if (parsed != null) {
                    minEnchantLevel = parsed;
                    enchantToken = compact.substring(0, compact.lastIndexOf('_'));
                } else {
                    enchantToken = compact;
                }
            } else {
                String[] spaced = rawEnchantPart.split("\\s+");
                if (spaced.length >= 2) {
                    Integer parsed = parseEnchantLevelToken(spaced[spaced.length - 1]);
                    if (parsed != null) {
                        minEnchantLevel = parsed;
                        String enchantName = String.join(" ", Arrays.copyOf(spaced, spaced.length - 1));
                        enchantToken = normalizeLootQueryToken(enchantName);
                    } else {
                        enchantToken = compact;
                    }
                } else {
                    enchantToken = compact;
                }
            }
        }
        return new LootQuery(itemToken, enchantToken, minEnchantLevel);
    }

    private static Integer parseEnchantLevelToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            int numeric = Integer.parseInt(token);
            return numeric > 0 ? numeric : null;
        } catch (NumberFormatException ignored) {
        }
        return romanToInt(token);
    }

    private static Integer romanToInt(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        int value = switch (s) {
            case "i" -> 1;
            case "ii" -> 2;
            case "iii" -> 3;
            case "iv" -> 4;
            case "v" -> 5;
            case "vi" -> 6;
            case "vii" -> 7;
            case "viii" -> 8;
            case "ix" -> 9;
            case "x" -> 10;
            default -> -1;
        };
        return value > 0 ? value : null;
    }

    private static LootMatchSummary summarizeLootQuery(List<SeedMapperLootService.LootEntry> lootEntries, LootQuery query) {
        int totalCount = 0;
        String itemLabel = "";
        for (SeedMapperLootService.LootEntry entry : lootEntries) {
            for (SeedMapperLootService.LootItem item : entry.items()) {
                if (!matchesLootItem(item, query)) {
                    continue;
                }
                totalCount += Math.max(1, item.count());
                if (itemLabel.isBlank()) {
                    itemLabel = !query.item().isBlank() ? query.item() : normalizeLootItemId(item.itemId());
                    if (itemLabel.isBlank()) {
                        itemLabel = normalizeLootItemId(item.displayName());
                    }
                }
            }
        }
        return totalCount > 0 ? new LootMatchSummary(totalCount, itemLabel.isBlank() ? query.item() : itemLabel) : null;
    }

    private static boolean matchesLootItem(SeedMapperLootService.LootItem item, LootQuery query) {
        String itemPath = normalizeLootQueryToken(item.itemId());
        String display = normalizeLootQueryToken(item.displayName());
        boolean itemMatches = query.item().isBlank() || itemPath.contains(query.item()) || display.contains(query.item());
        if (!itemMatches) {
            return false;
        }
        if (query.enchant().isBlank()) {
            return true;
        }

        for (int i = 0; i < item.enchantments().size(); i++) {
            String enchantment = item.enchantments().get(i);
            String enchantPath = normalizeLootQueryToken(enchantment);
            if (!enchantPath.contains(query.enchant())) {
                continue;
            }
            if (query.minEnchantLevel() <= 0) {
                return true;
            }
            int level = i < item.enchantmentLevels().size() ? item.enchantmentLevels().get(i) : 0;
            if (level >= query.minEnchantLevel()) {
                return true;
            }
        }
        return false;
    }

    private record LootQuery(String item, String enchant, int minEnchantLevel) {
    }

    private record LootMatch(SeedMapperMarker marker, String itemLabel, int totalCount) {
    }

    private record LootMatchSummary(int totalCount, String itemLabel) {
    }

    private static String formatFoundCoords(int x, int z, int originX, int originZ, boolean foundPrefix) {
        int dx = x - originX;
        int dz = z - originZ;
        int distance = (int)Math.round(Math.hypot(dx, dz));
        return (foundPrefix ? "Found Coords: " : "Coords: ") + x + ", 0, " + z + " | Distance: " + distance + " blocks " + describeDirection(dx, dz);
    }

    private static String describeDirection(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "HERE";
        }
        String northSouth = dz < 0 ? "NORTH" : dz > 0 ? "SOUTH" : "";
        String eastWest = dx > 0 ? "EAST" : dx < 0 ? "WEST" : "";
        if (!northSouth.isEmpty() && !eastWest.isEmpty()) {
            return northSouth + "-" + eastWest;
        }
        return !northSouth.isEmpty() ? northSouth : eastWest;
    }

    private static void highlightLocation(String name, int x, int z) {
        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        Level level = VoxelConstants.getPlayer().level();
        dimensions.add(VoxelConstants.getVoxelMapInstance().getDimensionManager().getDimensionContainerByWorld(level));
        Waypoint point = new Waypoint(name, x, z, Math.max(GameVariableAccessShim.yCoord(), 64), true, 0.1F, 0.85F, 1.0F, "target",
                VoxelConstants.getVoxelMapInstance().getWaypointManager().getCurrentSubworldDescriptor(false), dimensions);
        VoxelConstants.getVoxelMapInstance().getWaypointManager().setHighlightedWaypoint(point, false);
    }

    private static long resolveSeed() {
        SourceOverrides overrides = SOURCE_OVERRIDES.get();
        if (overrides != null && overrides.seed() != null) {
            return overrides.seed();
        }
        try {
            return VoxelConstants.getVoxelMapInstance().getSeedMapperOptions().resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException e) {
            send("No seed is available. Set one with /seedmap seed <seed>.");
            return Long.MIN_VALUE;
        }
    }

    private static int getCurrentCubiomesDimension() {
        SourceOverrides overrides = SOURCE_OVERRIDES.get();
        if (overrides != null && overrides.dimension() != null) {
            return overrides.dimension();
        }
        Level level = GameVariableAccessShim.getWorld();
        if (level == null) return Integer.MIN_VALUE;
        if (level.dimension() == Level.NETHER) return Cubiomes.DIM_NETHER();
        if (level.dimension() == Level.END) return Cubiomes.DIM_END();
        return Cubiomes.DIM_OVERWORLD();
    }

    private static int commandX() {
        SourceOverrides overrides = SOURCE_OVERRIDES.get();
        return overrides != null && overrides.x() != null ? overrides.x() : GameVariableAccessShim.xCoord();
    }

    private static int commandZ() {
        SourceOverrides overrides = SOURCE_OVERRIDES.get();
        return overrides != null && overrides.z() != null ? overrides.z() : GameVariableAccessShim.zCoord();
    }

    private static SeedMapperFeature resolveFeature(String query) {
        for (SeedMapperFeature feature : SeedMapperFeature.values()) {
            String translated = Component.translatable(feature.translationKey()).getString().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (feature.id().equalsIgnoreCase(query) || translated.equalsIgnoreCase(query) || feature.name().equalsIgnoreCase(query)) return feature;
        }
        return null;
    }

    private static int resolveBiomeId(String query) {
        int mc = SeedMapperCompat.getMcVersion();
        for (int id = 0; id < 256; id++) {
            try {
                MemorySegment biomeName = Cubiomes.biome2str(mc, id);
                if (biomeName == null || biomeName.address() == 0) continue;
                String name = biomeName.getString(0);
                if (name == null || name.isBlank()) continue;
                String normalized = name.toLowerCase(Locale.ROOT);
                if (normalized.equals(query) || normalized.endsWith("/" + query) || normalized.endsWith("_" + query) || normalized.contains(query)) return id;
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    private static boolean matchesOreVeinType(int blockId, boolean copper, boolean iron) {
        if (copper) return blockId == Cubiomes.COPPER_ORE() || blockId == Cubiomes.RAW_COPPER_BLOCK();
        if (iron) return blockId == Cubiomes.IRON_ORE() || blockId == Cubiomes.RAW_IRON_BLOCK();
        return false;
    }

    private static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        long mixed = seed + (long) (chunkX * chunkX * 4987142) + (long) (chunkX * 5947611) + (long) (chunkZ * chunkZ) * 4392871L + (long) (chunkZ * 389711) ^ 987234911L;
        return new Random(mixed).nextInt(10) == 0;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    private static ToIntBiFunction<Integer, Integer> getCarverBiomeFunction(Arena arena, long seed, int dimension, int version, int generatorFlags) {
        if (version > Cubiomes.MC_1_17_1()) {
            return (_, _) -> -1;
        }
        MemorySegment generator = Generator.allocate(arena);
        Cubiomes.setupGenerator(generator, version, generatorFlags);
        Cubiomes.applySeed(generator, dimension, seed);
        return (chunkX, chunkZ) -> Cubiomes.getBiomeAt(generator, 4, chunkX << 2, 0, chunkZ << 2);
    }

    private static void sendHelp() {
        List<String> lines = new ArrayList<>();
        lines.add("/seedmap seed <seed>");
        lines.add("/seedmap locate structure <feature_id>");
        lines.add("/seedmap locate biome <biome_name>");
        lines.add("/seedmap locate orevein <iron|copper>");
        lines.add("/seedmap locate slime");
        lines.add("/seedmap locate loot <text>");
        lines.add("/seedmap highlight ore <block> [chunks]");
        lines.add("/seedmap highlight orevein [chunks]");
        lines.add("/seedmap highlight terrain [chunks]");
        lines.add("/seedmap highlight canyon [chunks]");
        lines.add("/seedmap highlight cave [chunks]");
        lines.add("/seedmap highlight clear");
        lines.add("/seedmap export [visible|radius <blocks>|area <x> <z> <radius>]");
        lines.add("/seedmap source <run|seeded|positioned|in|versioned|flagged|as|rotated> ...");
        for (String line : lines) send(line);
    }

    private record SourceOverrides(Long seed, Integer x, Integer y, Integer z, Integer dimension) {
        private boolean isEmpty() {
            return seed == null && x == null && y == null && z == null && dimension == null;
        }
    }

    private static final class SourceOverridesBuilder {
        private Long seed;
        private Integer x;
        private Integer y;
        private Integer z;
        private Integer dimension;

        private SourceOverridesBuilder withPosition(Integer x, Integer z) {
            this.x = x;
            this.z = z;
            return this;
        }

        private SourceOverrides build() {
            return new SourceOverrides(seed, x, y, z, dimension);
        }
    }

    private static boolean tryDatapackLocate(String query) {
        SeedMapperSettingsManager settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        if (!settings.datapackEnabled) return false;
        List<String> imported = SeedMapperDatapackManager.readImportedStructureIds(settings.datapackCachePath);
        if (imported.isEmpty()) return false;
        String normalized = query.contains(":") ? query : "minecraft:" + query;
        String resolved = null;
        for (String id : imported) {
            if (id.equalsIgnoreCase(query) || id.equalsIgnoreCase(normalized) || id.endsWith(":" + query)) {
                resolved = id;
                break;
            }
        }
        if (resolved == null) return false;
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand("locate structure " + resolved);
            pendingDatapackLocateId = resolved;
            send("Requested server locate for datapack structure '" + resolved + "'.");
            return true;
        }
        return false;
    }

    private static void send(String text) {
        if (statusSink != null) {
            statusSink.accept(text);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) minecraft.gui.getChat().addClientSystemMessage(Component.literal("[SeedMapper] " + text));
    }

    public static void setStatusSink(Consumer<String> sink) {
        statusSink = sink;
    }

    private interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }
}
