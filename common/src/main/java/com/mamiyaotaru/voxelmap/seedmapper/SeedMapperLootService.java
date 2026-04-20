package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.EnchantInstance;
import com.github.cubiomes.Generator;
import com.github.cubiomes.ItemStack;
import com.github.cubiomes.LootTableContext;
import com.github.cubiomes.Piece;
import com.github.cubiomes.Pos;
import com.github.cubiomes.StructureSaltConfig;
import com.github.cubiomes.StructureVariant;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class SeedMapperLootService {
    public static final Set<Integer> LOOT_SUPPORTED_STRUCTURES = Set.of(
            Cubiomes.Treasure(),
            Cubiomes.Desert_Pyramid(),
            Cubiomes.End_City(),
            Cubiomes.Igloo(),
            Cubiomes.Jungle_Pyramid(),
            Cubiomes.Ruined_Portal(),
            Cubiomes.Ruined_Portal_N(),
            Cubiomes.Fortress(),
            Cubiomes.Bastion(),
            Cubiomes.Outpost(),
            Cubiomes.Shipwreck()
    );
    private static final Map<SeedMapperFeature, List<String>> FEATURE_TABLES = new LinkedHashMap<>();
    private static final List<String> COMMON_ENCHANT_SUGGESTIONS = List.of(
            "mending", "unbreaking", "efficiency", "fortune", "silk_touch", "sharpness", "smite", "bane_of_arthropods",
            "sweeping_edge", "knockback", "fire_aspect", "looting", "power", "punch", "flame", "infinity",
            "multishot", "piercing", "quick_charge", "protection", "fire_protection", "blast_protection",
            "projectile_protection", "thorns", "feather_falling", "depth_strider", "frost_walker", "soul_speed",
            "swift_sneak", "respiration", "aqua_affinity", "impaling", "loyalty", "channeling", "riptide",
            "luck_of_the_sea", "lure", "wind_burst", "density", "breach", "binding_curse", "vanishing_curse"
    );

    static {
        FEATURE_TABLES.put(SeedMapperFeature.DESERT_PYRAMID, List.of("minecraft:chests/desert_pyramid"));
        FEATURE_TABLES.put(SeedMapperFeature.JUNGLE_PYRAMID, List.of("minecraft:chests/jungle_temple"));
        FEATURE_TABLES.put(SeedMapperFeature.IGLOO, List.of("minecraft:chests/igloo_chest"));
        FEATURE_TABLES.put(SeedMapperFeature.SHIPWRECK, List.of("minecraft:chests/shipwreck_supply", "minecraft:chests/shipwreck_map", "minecraft:chests/shipwreck_treasure"));
        FEATURE_TABLES.put(SeedMapperFeature.TREASURE, List.of("minecraft:chests/buried_treasure"));
        FEATURE_TABLES.put(SeedMapperFeature.OUTPOST, List.of("minecraft:chests/pillager_outpost"));
        FEATURE_TABLES.put(SeedMapperFeature.RUINED_PORTAL, List.of("minecraft:chests/ruined_portal"));
        FEATURE_TABLES.put(SeedMapperFeature.FORTRESS, List.of("minecraft:chests/nether_bridge"));
        FEATURE_TABLES.put(SeedMapperFeature.BASTION, List.of("minecraft:chests/bastion_treasure", "minecraft:chests/bastion_bridge", "minecraft:chests/bastion_hoglin_stable", "minecraft:chests/bastion_other"));
        FEATURE_TABLES.put(SeedMapperFeature.END_CITY, List.of("minecraft:chests/end_city_treasure"));
        FEATURE_TABLES.put(SeedMapperFeature.ELYTRA, List.of("minecraft:chests/end_city_treasure"));
        FEATURE_TABLES.put(SeedMapperFeature.ANCIENT_CITY, List.of("minecraft:chests/ancient_city"));
        FEATURE_TABLES.put(SeedMapperFeature.TRIAL_CHAMBERS, List.of("minecraft:chests/trial_chambers/reward", "minecraft:chests/trial_chambers/corridor"));
        FEATURE_TABLES.put(SeedMapperFeature.TRAIL_RUINS, List.of("minecraft:archaeology/trail_ruins_rare", "minecraft:archaeology/trail_ruins_common"));
    }

    private SeedMapperLootService() {
    }

    public static List<LootTablePreview> buildLootTablePreviews(long seed, String search, int mcVersion) {
        SeedMapperNative.ensureLoaded();
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        ArrayList<LootTablePreview> out = new ArrayList<>();

        for (Map.Entry<SeedMapperFeature, List<String>> entry : FEATURE_TABLES.entrySet()) {
            for (String tableName : entry.getValue()) {
                if (!isTableSupported(mcVersion, tableName)) {
                    continue;
                }
                if (!needle.isBlank()
                        && !tableName.toLowerCase(Locale.ROOT).contains(needle)
                        && !entry.getKey().id().contains(needle)) {
                    continue;
                }
                // Keep the viewer stable: avoid bulk native loot generation on screen open.
                out.add(new LootTablePreview(entry.getKey(), tableName, List.of(), ""));
            }
        }

        return out;
    }

    public static List<LootEntry> collectLootEntries(long seed, int mcVersion, int dimension, int generatorFlags, List<LootTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        SeedMapperNative.ensureLoaded();
        ArrayList<LootEntry> entries = new ArrayList<>();
        synchronized (SeedMapperNative.cubiomesLock()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment generator = Generator.allocate(arena);
                Cubiomes.setupGenerator(generator, mcVersion, generatorFlags);
                Cubiomes.applySeed(generator, dimension, seed);

                for (LootTarget target : targets) {
                    if (target == null || target.structureId() < 0 || target.pos() == null) {
                        continue;
                    }

                    int structure = target.structureId();
                    BlockPos pos = target.pos();
                    int biome = Cubiomes.getBiomeAt(generator, 4, QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(320), QuartPos.fromBlock(pos.getZ()));
                    MemorySegment structureVariant = StructureVariant.allocate(arena);
                    Cubiomes.getVariant(structureVariant, structure, mcVersion, seed, pos.getX(), pos.getZ(), biome);
                    biome = StructureVariant.biome(structureVariant) != -1 ? StructureVariant.biome(structureVariant) : biome;

                    MemorySegment structureSaltConfig = StructureSaltConfig.allocate(arena);
                    if (Cubiomes.getStructureSaltConfig(structure, mcVersion, biome, structureSaltConfig) == 0) {
                        continue;
                    }

                    MemorySegment pieces = Piece.allocateArray(400, arena);
                    int numPieces = Cubiomes.getStructurePieces(pieces, 400, structure, structureSaltConfig, structureVariant, mcVersion, seed, pos.getX(), pos.getZ());
                    if (numPieces <= 0) {
                        continue;
                    }

                    MemorySegment contextPtrPtr = arena.allocate(Cubiomes.C_POINTER);
                    for (int pieceIdx = 0; pieceIdx < numPieces; pieceIdx++) {
                        MemorySegment piece = Piece.asSlice(pieces, pieceIdx);
                        int chestCount = Piece.chestCount(piece);
                        if (chestCount <= 0) {
                            continue;
                        }

                        String pieceName = Piece.name(piece) == null || Piece.name(piece).address() == 0L ? "piece" : Piece.name(piece).getString(0);
                        MemorySegment chestPoses = Piece.chestPoses(piece);
                        MemorySegment lootTables = Piece.lootTables(piece);
                        MemorySegment lootSeeds = Piece.lootSeeds(piece);
                        for (int chestIdx = 0; chestIdx < chestCount && chestIdx < 4; chestIdx++) {
                            MemorySegment lootTable = lootTables.getAtIndex(Cubiomes.C_POINTER, chestIdx).reinterpret(Long.MAX_VALUE);
                            if (lootTable == null || lootTable.address() == 0L) {
                                continue;
                            }
                            String lootTableString = lootTable.getString(0);
                            if (!isTableSupported(mcVersion, lootTableString)) {
                                continue;
                            }
                            MemorySegment lootTableContext = initLootTableContext(contextPtrPtr, lootTable, arena, lootTableString, mcVersion);
                            if (lootTableContext == null || lootTableContext.address() == 0L) {
                                continue;
                            }

                            long lootSeed = lootSeeds.getAtIndex(Cubiomes.C_LONG_LONG, chestIdx);
                            Cubiomes.set_loot_seed(lootTableContext, lootSeed);
                            Cubiomes.generate_loot(lootTableContext);

                            int lootCount = LootTableContext.generated_item_count(lootTableContext);
                            List<LootItem> items = new ArrayList<>(lootCount);
                            for (int lootIdx = 0; lootIdx < lootCount; lootIdx++) {
                                MemorySegment itemStack = ItemStack.asSlice(LootTableContext.generated_items(lootTableContext), lootIdx);
                                int localItemId = ItemStack.item(itemStack);
                                int globalItemId = Cubiomes.get_global_item_id(lootTableContext, localItemId);
                                MemorySegment itemNamePtr = Cubiomes.global_id2item_name(globalItemId, mcVersion);
                                String itemName = itemNamePtr == null || itemNamePtr.address() == 0L ? "" : itemNamePtr.getString(0);
                                int count = Math.max(1, ItemStack.count(itemStack));

                                String displayName = itemName;
                                String nbt = itemName;
                                Item mcItem = resolveItem(itemName);
                                if (mcItem != Items.BARRIER) {
                                    net.minecraft.world.item.ItemStack mcStack = new net.minecraft.world.item.ItemStack(mcItem, count);
                                    if (mcItem == Items.SUSPICIOUS_STEW) {
                                        MutableComponent lore = Component.translatable("seedMap.chestLoot.stewEffect", Component.literal("Unknown"), "?");
                                        mcStack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
                                    }
                                    displayName = mcStack.getHoverName().getString();
                                    nbt = mcStack.toString();
                                }

                                List<String> enchantments = new ArrayList<>();
                                List<Integer> enchantmentLevels = new ArrayList<>();
                                MemorySegment enchantmentsInternal = ItemStack.enchantments(itemStack);
                                int enchantmentCount = ItemStack.enchantment_count(itemStack);
                                for (int enchantmentIdx = 0; enchantmentIdx < enchantmentCount; enchantmentIdx++) {
                                    MemorySegment enchantInstance = EnchantInstance.asSlice(enchantmentsInternal, enchantmentIdx);
                                    int itemEnchantment = EnchantInstance.enchantment(enchantInstance);
                                    MemorySegment enchantNamePtr = Cubiomes.get_enchantment_name(itemEnchantment);
                                    String enchantmentName = enchantNamePtr == null || enchantNamePtr.address() == 0L ? "unknown:" + itemEnchantment : enchantNamePtr.getString(0);
                                    if (!enchantmentName.contains(":") && !enchantmentName.startsWith("unknown:")) {
                                        enchantmentName = "minecraft:" + enchantmentName;
                                    }
                                    enchantments.add(enchantmentName);
                                    enchantmentLevels.add(EnchantInstance.level(enchantInstance));
                                }

                                items.add(new LootItem(lootIdx, count, itemName, displayName, nbt, enchantments, enchantmentLevels));
                            }

                            MemorySegment chestPos = Pos.asSlice(chestPoses, chestIdx);
                            MemorySegment structureNamePtr = Cubiomes.struct2str(structure);
                            String structureName = structureNamePtr == null || structureNamePtr.address() == 0L ? "structure" : structureNamePtr.getString(0);
                            BlockPos entryPos = new BlockPos(Pos.x(chestPos), 0, Pos.z(chestPos));
                            entries.add(new LootEntry(structureName + "-" + pieceName + "-" + chestIdx, structureName, pieceName, entryPos, items));
                        }
                    }
                }
            } catch (Throwable ignored) {
                return List.of();
            }
        }

        // Preserve order but drop accidental duplicates.
        LinkedHashSet<LootEntry> deduped = new LinkedHashSet<>(entries);
        return List.copyOf(deduped);
    }

    public static List<SeedMapperChestLootData> buildStructureChestLoot(long seed, int dimension, int mcVersion, int generatorFlags, SeedMapperFeature feature, int blockX, int blockZ) {
        if (feature == null || !feature.lootable() || feature.structureId() < 0) {
            return List.of();
        }
        if (feature.dimension() != Integer.MIN_VALUE && feature.dimension() != dimension) {
            return List.of();
        }

        int structureId = feature.structureId();
        SeedMapperNative.ensureLoaded();
        synchronized (SeedMapperNative.cubiomesLock()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment generator = Generator.allocate(arena);
                Cubiomes.setupGenerator(generator, mcVersion, generatorFlags);
                Cubiomes.applySeed(generator, dimension, seed);

                int biome = Cubiomes.getBiomeAt(generator, 4, QuartPos.fromBlock(blockX), QuartPos.fromBlock(320), QuartPos.fromBlock(blockZ));
                MemorySegment structureVariant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(structureVariant, structureId, mcVersion, seed, blockX, blockZ, biome);
                int variantBiome = StructureVariant.biome(structureVariant);
                if (variantBiome != -1) {
                    biome = variantBiome;
                }

                MemorySegment structureSaltConfig = StructureSaltConfig.allocate(arena);
                if (Cubiomes.getStructureSaltConfig(structureId, mcVersion, biome, structureSaltConfig) == 0) {
                    return List.of();
                }

                int maxPieces = 400;
                MemorySegment pieces = Piece.allocateArray(maxPieces, arena);
                int numPieces = Cubiomes.getStructurePieces(pieces, maxPieces, structureId, structureSaltConfig, structureVariant, mcVersion, seed, blockX, blockZ);
                if (numPieces <= 0) {
                    return List.of();
                }

                ArrayList<SeedMapperChestLootData> out = new ArrayList<>();
                MemorySegment contextPtrPtr = arena.allocate(Cubiomes.C_POINTER);
                for (int pieceIdx = 0; pieceIdx < numPieces; pieceIdx++) {
                    MemorySegment piece = Piece.asSlice(pieces, pieceIdx);
                    int chestCount = Math.max(0, Piece.chestCount(piece));
                    if (chestCount <= 0) {
                        continue;
                    }

                    String pieceName = Piece.name(piece) == null || Piece.name(piece).address() == 0L ? "piece" : Piece.name(piece).getString(0);
                    MemorySegment chestPoses = Piece.chestPoses(piece);
                    MemorySegment lootTables = Piece.lootTables(piece);
                    MemorySegment lootSeeds = Piece.lootSeeds(piece);
                    for (int chestIdx = 0; chestIdx < chestCount && chestIdx < 4; chestIdx++) {
                        MemorySegment lootTablePtr = lootTables.getAtIndex(Cubiomes.C_POINTER, chestIdx).reinterpret(Long.MAX_VALUE);
                        if (lootTablePtr == null || lootTablePtr.address() == 0L) {
                            continue;
                        }
                        String lootTableName = lootTablePtr.getString(0);
                        if (!isTableSupported(mcVersion, lootTableName)) {
                            continue;
                        }
                        MemorySegment lootContext = initLootTableContext(contextPtrPtr, lootTablePtr, arena, lootTableName, mcVersion);
                        if (lootContext == null || lootContext.address() == 0L) {
                            continue;
                        }

                        long lootSeed = lootSeeds.getAtIndex(Cubiomes.C_LONG_LONG, chestIdx);
                        MemorySegment chestPosInternal = Pos.asSlice(chestPoses, chestIdx);
                        BlockPos chestPos = new BlockPos(Pos.x(chestPosInternal), 0, Pos.z(chestPosInternal));
                        SimpleContainer container = new SimpleContainer(27);
                        Cubiomes.set_loot_seed(lootContext, lootSeed);
                        Cubiomes.generate_loot(lootContext);
                        int lootCount = LootTableContext.generated_item_count(lootContext);
                        for (int lootIdx = 0; lootIdx < lootCount; lootIdx++) {
                            MemorySegment stack = ItemStack.asSlice(LootTableContext.generated_items(lootContext), lootIdx);
                            int localItemId = ItemStack.item(stack);
                            int count = Math.max(1, ItemStack.count(stack));
                            MemorySegment itemNamePtr = Cubiomes.get_item_name(lootContext, localItemId);
                            String itemName = itemNamePtr == null || itemNamePtr.address() == 0L ? "" : itemNamePtr.getString(0);
                            Item item = resolveItem(itemName);
                            container.addItem(new net.minecraft.world.item.ItemStack(item, count));
                        }

                        out.add(new SeedMapperChestLootData(feature, pieceName, chestPos, lootSeed, lootTableName, container));
                    }
                }

                return out;
            } catch (Throwable ignored) {
                return List.of();
            }
        }
    }

    private static LootSample sampleTable(long seed, String tableName, int mcVersion) {
        try {
            synchronized (SeedMapperNative.cubiomesLock()) {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment context = initLootTableContext(arena.allocate(Cubiomes.C_POINTER), MemorySegment.NULL, arena, tableName, mcVersion);
                    if (context == null || context.address() == 0L) {
                        return new LootSample(List.of(), "loot context missing");
                    }

                    List<String> items = new ArrayList<>();
                    Cubiomes.set_loot_seed(context, seed ^ tableName.hashCode());
                    Cubiomes.generate_loot(context);
                    int generatedCount = LootTableContext.generated_item_count(context);
                    for (int i = 0; i < generatedCount; i++) {
                        MemorySegment stack = ItemStack.asSlice(LootTableContext.generated_items(context), i);
                        int localItemId = ItemStack.item(stack);
                        int count = ItemStack.count(stack);
                        MemorySegment itemNamePtr = Cubiomes.get_item_name(context, localItemId);
                        String itemName = itemNamePtr == null || itemNamePtr.address() == 0L ? ("item_" + localItemId) : itemNamePtr.getString(0);
                        items.add(itemName + (count > 1 ? " x" + count : ""));
                    }
                    return new LootSample(items, "");
                }
            }
        } catch (Throwable t) {
            return new LootSample(List.of(), t.getClass().getSimpleName());
        }
    }

    private static MemorySegment initLootTableContext(MemorySegment contextPtrPtr, MemorySegment lootTablePtr, Arena arena, String tableName, int mcVersion) {
        int init = 0;
        if (lootTablePtr != null && lootTablePtr.address() != 0L) {
            init = Cubiomes.init_loot_table_name(contextPtrPtr, lootTablePtr, mcVersion);
        }
        if (init == 0 && tableName.startsWith("minecraft:")) {
            MemorySegment shortCString = arena.allocateFrom(tableName.substring("minecraft:".length()), StandardCharsets.UTF_8);
            init = Cubiomes.init_loot_table_name(contextPtrPtr, shortCString, mcVersion);
        }
        if (init == 0) {
            MemorySegment tableCString = arena.allocateFrom(tableName, StandardCharsets.UTF_8);
            init = Cubiomes.init_loot_table_name(contextPtrPtr, tableCString, mcVersion);
        }
        if (init == 0) {
            return null;
        }
        return contextPtrPtr.get(Cubiomes.C_POINTER, 0);
    }

    private static Item resolveItem(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            return Items.BARRIER;
        }
        if (!name.contains(":")) {
            name = "minecraft:" + name;
        }
        try {
            Identifier id = Identifier.parse(name);
            Reference<Item> itemRef = BuiltInRegistries.ITEM.get(id).orElse(null);
            if (itemRef != null) {
                return itemRef.value();
            }
        } catch (Exception ignored) {
        }
        return Items.BARRIER;
    }

    public static Map<SeedMapperFeature, List<String>> featureTables() {
        return FEATURE_TABLES;
    }

    public static List<String> getLootSearchSuggestions(int dimension, int mcVersion, long seed) {
        TreeSet<String> suggestions = new TreeSet<>();
        SeedMapperNative.ensureLoaded();

        SeedMapperSettingsManager settings;
        try {
            settings = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions();
        } catch (Throwable t) {
            return List.of();
        }

        long resolvedSeed;
        try {
            resolvedSeed = settings.resolveSeed(VoxelConstants.getVoxelMapInstance().getWorldSeed());
        } catch (IllegalArgumentException e) {
            return List.of();
        }

        int px = GameVariableAccessShim.xCoord();
        int pz = GameVariableAccessShim.zCoord();
        int radius = 32768;
        int minX = px - radius;
        int maxX = px + radius;
        int minZ = pz - radius;
        int maxZ = pz + radius;

        synchronized (SeedMapperNative.cubiomesLock()) {
            List<SeedMapperMarker> markers = SeedMapperLocatorService.get()
                    .queryBlocking(resolvedSeed, dimension, mcVersion, 0, minX, maxX, minZ, maxZ, settings);
            if (markers.isEmpty()) {
                return List.of();
            }

            ArrayList<LootTarget> targets = new ArrayList<>();
            for (SeedMapperMarker marker : markers) {
                if (marker == null || marker.feature() == null || !marker.feature().lootable() || marker.feature().structureId() < 0) {
                    continue;
                }
                if (!LOOT_SUPPORTED_STRUCTURES.contains(marker.feature().structureId())) {
                    continue;
                }
                targets.add(new LootTarget(marker.feature().structureId(), new BlockPos(marker.blockX(), 0, marker.blockZ())));
            }
            if (targets.isEmpty()) {
                return List.of();
            }

            List<LootEntry> lootEntries = collectLootEntries(resolvedSeed, mcVersion, dimension, 0, targets);
            for (LootEntry entry : lootEntries) {
                for (LootItem item : entry.items()) {
                    String itemId = normalizeLootItemName(item.itemId());
                    if (!itemId.isBlank()) {
                        suggestions.add(itemId);
                    }
                }
            }
        }

        return List.copyOf(suggestions);
    }

    private static String normalizeLootItemName(String itemName) {
        String normalized = itemName == null ? "" : itemName.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized;
    }

    public static List<String> getEnchantSearchSuggestions(String itemQueryPrefix) {
        List<String> strict = getEnchantSearchSuggestionsStrict(itemQueryPrefix);
        if (!strict.isEmpty()) {
            return strict;
        }
        return List.copyOf(new TreeSet<>(COMMON_ENCHANT_SUGGESTIONS));
    }

    public static List<String> getEnchantSearchSuggestionsStrict(String itemQueryPrefix) {
        String item = itemQueryPrefix == null ? "" : itemQueryPrefix.toLowerCase(Locale.ROOT).trim();
        TreeSet<String> suggestions = new TreeSet<>();
        if (item.isBlank()) {
            return List.of();
        }

        if (item.startsWith("minecraft:")) {
            item = item.substring("minecraft:".length());
        }

        boolean isSword = item.endsWith("_sword");
        boolean isAxe = item.endsWith("_axe");
        boolean isPickaxe = item.endsWith("_pickaxe");
        boolean isShovel = item.endsWith("_shovel");
        boolean isHoe = item.endsWith("_hoe");
        boolean isHelmet = item.endsWith("_helmet");
        boolean isChestplate = item.endsWith("_chestplate");
        boolean isLeggings = item.endsWith("_leggings");
        boolean isBoots = item.endsWith("_boots");
        boolean isBow = item.equals("bow");
        boolean isCrossbow = item.equals("crossbow");
        boolean isTrident = item.equals("trident");
        boolean isFishingRod = item.equals("fishing_rod");
        boolean isMace = item.equals("mace");
        boolean isEnchantedBook = item.equals("enchanted_book");

        if (isSword || isAxe) {
            suggestions.addAll(Set.of("sharpness", "smite", "bane_of_arthropods", "sweeping_edge", "knockback", "fire_aspect", "looting", "unbreaking", "mending"));
        }
        if (isPickaxe || isShovel || isHoe) {
            suggestions.addAll(Set.of("efficiency", "fortune", "silk_touch", "unbreaking", "mending"));
        }
        if (isHelmet || isChestplate || isLeggings || isBoots) {
            suggestions.addAll(Set.of("protection", "fire_protection", "blast_protection", "projectile_protection", "thorns", "unbreaking", "mending"));
            if (isBoots) {
                suggestions.addAll(Set.of("feather_falling", "depth_strider", "frost_walker", "soul_speed"));
            }
            if (isHelmet) {
                suggestions.addAll(Set.of("respiration", "aqua_affinity"));
            }
            if (isLeggings) {
                suggestions.add("swift_sneak");
            }
        }
        if (isBow) {
            suggestions.addAll(Set.of("power", "punch", "flame", "infinity", "unbreaking", "mending"));
        }
        if (isCrossbow) {
            suggestions.addAll(Set.of("multishot", "piercing", "quick_charge", "unbreaking", "mending"));
        }
        if (isTrident) {
            suggestions.addAll(Set.of("impaling", "loyalty", "channeling", "riptide", "unbreaking", "mending"));
        }
        if (isFishingRod) {
            suggestions.addAll(Set.of("luck_of_the_sea", "lure", "unbreaking", "mending"));
        }
        if (isMace) {
            suggestions.addAll(Set.of("wind_burst", "density", "breach", "unbreaking", "mending"));
        }
        // Only enchanted books should show generic enchant filters.
        if (isEnchantedBook) {
            suggestions.addAll(COMMON_ENCHANT_SUGGESTIONS);
        }

        return List.copyOf(suggestions);
    }

    public static List<String> getEnchantmentLevelSuggestions() {
        return List.of("1", "2", "3", "4", "5", "i", "ii", "iii", "iv", "v");
    }

    public record LootTablePreview(SeedMapperFeature feature, String tableName, List<String> items, String error) {
    }

    public record LootTarget(int structureId, BlockPos pos) {
    }

    public record LootEntry(String id, String type, String pieceName, BlockPos pos, List<LootItem> items) {
    }

    public record LootItem(int slot, int count, String itemId, String displayName, String nbt, List<String> enchantments, List<Integer> enchantmentLevels) {
    }

    private static boolean isTableSupported(int mcVersion, String tableName) {
        if (tableName.contains("ancient_city")) {
            return mcVersion >= Cubiomes.MC_1_19();
        }
        if (tableName.contains("trail_ruins")) {
            return mcVersion >= Cubiomes.MC_1_20();
        }
        if (tableName.contains("trial_chambers")) {
            return mcVersion >= Cubiomes.MC_1_21();
        }
        return true;
    }

    private record LootSample(List<String> items, String error) {
    }
}
