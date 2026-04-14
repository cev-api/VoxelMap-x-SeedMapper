package com.mamiyaotaru.voxelmap.seedmapper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;

public record SeedMapperChestLootData(
        SeedMapperFeature feature,
        String pieceName,
        BlockPos chestPos,
        long lootSeed,
        String lootTable,
        SimpleContainer container) {
}

