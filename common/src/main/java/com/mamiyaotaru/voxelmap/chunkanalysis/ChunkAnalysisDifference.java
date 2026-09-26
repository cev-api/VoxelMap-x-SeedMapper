package com.mamiyaotaru.voxelmap.chunkanalysis;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record ChunkAnalysisDifference(BlockPos pos, Kind kind, BlockState displayState, InterestingCategory interestingCategory) {
    public ChunkAnalysisDifference(BlockPos pos, Kind kind, BlockState displayState) {
        this(pos, kind, displayState, null);
    }

    public enum InterestingCategory {
        INVENTORIES,
        REDSTONE,
        WORKSTATIONS
    }

    public enum Kind {
        MISSING_EXPECTED(0xFF3333),
        EXCAVATION(0xFF174D),
        UNEXPECTED(0x3388FF),
        CHANGED(0xFFD43B),
        UNEXPECTED_INTERESTING(0xFF8800);

        private final int color;

        Kind(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }
}
