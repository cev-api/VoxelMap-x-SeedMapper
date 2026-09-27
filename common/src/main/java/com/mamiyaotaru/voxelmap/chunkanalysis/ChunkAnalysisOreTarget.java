package com.mamiyaotaru.voxelmap.chunkanalysis;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ore families and replaceable block targets supported by the vanilla-worldgen ChunkAnalysis ESP.
 *
 * <p>The target IDs intentionally match the block names accepted by SeedMapper's ore ESP. A
 * deepslate variant selects the same family as its stone variant, so either spelling highlights
 * both forms when vanilla generation produces them.</p>
 */
public enum ChunkAnalysisOreTarget {
    DIAMOND("diamond_ore", "Diamond Ore", 0x3AD9D4, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
    IRON("iron_ore", "Iron Ore", 0xD8AF93, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
    GOLD("gold_ore", "Gold Ore", 0xF0B800, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
    EMERALD("emerald_ore", "Emerald Ore", 0x36CB62, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
    COPPER("copper_ore", "Copper Ore", 0xC7744A, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
    COAL("coal_ore", "Coal Ore", 0x303030, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
    LAPIS("lapis_ore", "Lapis Ore", 0x3158D9, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
    REDSTONE("redstone_ore", "Redstone Ore", 0xC42020, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
    NETHER_QUARTZ("nether_quartz_ore", "Nether Quartz Ore", 0xD8D3C8, Blocks.NETHER_QUARTZ_ORE),
    NETHER_GOLD("nether_gold_ore", "Nether Gold Ore", 0xF0B800, Blocks.NETHER_GOLD_ORE),
    ANCIENT_DEBRIS("ancient_debris", "Ancient Debris", 0x5B3E2B, Blocks.ANCIENT_DEBRIS),
    INFESTED("infested_stone", "Infested Stone", 0xA070D0,
            Blocks.INFESTED_STONE, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_STONE_BRICKS,
            Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS,
            Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.INFESTED_DEEPSLATE),
    ANDESITE("andesite", "Andesite", 0x8C8C8C, Blocks.ANDESITE),
    BLACKSTONE("blackstone", "Blackstone", 0x3E3E46, Blocks.BLACKSTONE),
    CLAY("clay", "Clay", 0xA9A0A0, Blocks.CLAY),
    DEEPSLATE("deepslate", "Deepslate", 0x4F4F58, Blocks.DEEPSLATE),
    DIORITE("diorite", "Diorite", 0xC7C7C7, Blocks.DIORITE),
    DIRT("dirt", "Dirt", 0x8B5A3C, Blocks.DIRT),
    GRANITE("granite", "Granite", 0x9B6B5A, Blocks.GRANITE),
    GRAVEL("gravel", "Gravel", 0x8A817A, Blocks.GRAVEL),
    MAGMA("magma_block", "Magma Block", 0xE05A2A, Blocks.MAGMA_BLOCK),
    RAW_COPPER("raw_copper_block", "Raw Copper Block", 0xB86E4D, Blocks.RAW_COPPER_BLOCK),
    RAW_IRON("raw_iron_block", "Raw Iron Block", 0xC59B83, Blocks.RAW_IRON_BLOCK),
    SOUL_SAND("soul_sand", "Soul Sand", 0x5C4434, Blocks.SOUL_SAND),
    STONE("stone", "Stone", 0x8A8A8A, Blocks.STONE),
    TUFF("tuff", "Tuff", 0x6F756B, Blocks.TUFF);

    private final String id;
    private final String displayName;
    private final int defaultColor;
    private final List<Block> blocks;

    ChunkAnalysisOreTarget(String id, String displayName, int defaultColor, Block... blocks) {
        this.id = id;
        this.displayName = displayName;
        this.defaultColor = defaultColor;
        this.blocks = List.of(blocks);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultColor() {
        return defaultColor;
    }

    public boolean matches(BlockState state) {
        return state != null && blocks.contains(state.getBlock());
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(ChunkAnalysisOreTarget::id).toList();
    }

    public static ChunkAnalysisOreTarget fromId(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0) normalized = normalized.substring(namespaceSeparator + 1);
        if (normalized.startsWith("deepslate_")) normalized = normalized.substring("deepslate_".length());
        for (ChunkAnalysisOreTarget target : values()) {
            if (target.id.equals(normalized)) return target;
        }
        if (normalized.startsWith("infested_")) return INFESTED;
        return null;
    }
}
