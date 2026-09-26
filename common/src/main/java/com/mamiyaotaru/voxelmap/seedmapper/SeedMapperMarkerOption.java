package com.mamiyaotaru.voxelmap.seedmapper;

import net.minecraft.world.level.block.Blocks;

import java.util.List;

public enum SeedMapperMarkerOption {
    SINGLE_CHEST("single_chest", "Single Chests", Category.CONTAINERS), DOUBLE_CHEST("double_chest", "Double Chests", Category.CONTAINERS), TRAPPED_CHEST("trapped_chest", "Trapped Chest", Category.CONTAINERS), ENDER_CHEST("ender_chest", "Ender Chest", Category.CONTAINERS), BARREL("barrel", "Barrel", Category.CONTAINERS), SHULKER_BOX("shulker_box", "Shulker Boxes", Category.CONTAINERS),
    CRAFTING_TABLE("crafting_table", "Crafting Table", Category.WORKSTATIONS), FURNACE("furnace", "Furnace", Category.WORKSTATIONS), BLAST_FURNACE("blast_furnace", "Blast Furnace", Category.WORKSTATIONS), SMOKER("smoker", "Smoker", Category.WORKSTATIONS), STONECUTTER("stonecutter", "Stonecutter", Category.WORKSTATIONS), LOOM("loom", "Loom", Category.WORKSTATIONS), CARTOGRAPHY_TABLE("cartography_table", "Cartography Table", Category.WORKSTATIONS), FLETCHING_TABLE("fletching_table", "Fletching Table", Category.WORKSTATIONS), SMITHING_TABLE("smithing_table", "Smithing Table", Category.WORKSTATIONS), GRINDSTONE("grindstone", "Grindstone", Category.WORKSTATIONS), ANVIL("anvil", "Anvil", Category.WORKSTATIONS), ENCHANTING_TABLE("enchanting_table", "Enchanting Table", Category.WORKSTATIONS), BREWING_STAND("brewing_stand", "Brewing Stand", Category.WORKSTATIONS), LECTERN("lectern", "Lectern", Category.WORKSTATIONS),
    DISPENSER("dispenser", "Dispenser", Category.REDSTONE), DROPPER("dropper", "Dropper", Category.REDSTONE), HOPPER("hopper", "Hopper", Category.REDSTONE), CRAFTER("crafter", "Crafter", Category.REDSTONE), REDSTONE_BLOCK("redstone_block", "Redstone Block", Category.REDSTONE), REDSTONE_TORCH("redstone_torch", "Redstone Torch", Category.REDSTONE), REPEATER("repeater", "Repeater", Category.REDSTONE), COMPARATOR("comparator", "Comparator", Category.REDSTONE), OBSERVER("observer", "Observer", Category.REDSTONE), PISTON("piston", "Piston", Category.REDSTONE), STICKY_PISTON("sticky_piston", "Sticky Piston", Category.REDSTONE), NOTE_BLOCK("note_block", "Note Block", Category.REDSTONE),
    SPAWNER("spawner", "Spawner", Category.SPAWNERS), TRIAL_SPAWNER("trial_spawner", "Trial Spawner", Category.SPAWNERS);

    public enum Category { CONTAINERS, WORKSTATIONS, REDSTONE, SPAWNERS }
    private final String id; private final String label; private final Category category;
    SeedMapperMarkerOption(String id, String label, Category category) { this.id = id; this.label = label; this.category = category; }
    public String id() { return id; } public String label() { return label; } public Category category() { return category; }
    public static List<SeedMapperMarkerOption> forCategory(Category category) { return java.util.Arrays.stream(values()).filter(x -> x.category == category).toList(); }
    public static SeedMapperMarkerOption fromBlock(net.minecraft.world.level.block.Block block) {
        if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return SHULKER_BOX;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        for (SeedMapperMarkerOption option : values()) if (option.id.equals(id)) return option;
        return null;
    }

    /** Shared category lookup used by both marker scanning and targeted audits. */
    public static Category categoryFor(net.minecraft.world.level.block.state.BlockState state) {
        Category category = null;
        SeedMapperMarkerOption option = fromBlock(state.getBlock());
        if (option != null) category = option.category;
        if (category != null) return category;

        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.equals("chest") || path.equals("trapped_chest") || path.equals("ender_chest")
                || path.equals("barrel") || state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
            return Category.CONTAINERS;
        }
        if (path.equals("lever") || path.equals("redstone_wire") || path.equals("target")
                || path.equals("tripwire") || path.equals("tripwire_hook")
                || path.endsWith("_button") || path.endsWith("_pressure_plate")) {
            return Category.REDSTONE;
        }
        return null;
    }
}
