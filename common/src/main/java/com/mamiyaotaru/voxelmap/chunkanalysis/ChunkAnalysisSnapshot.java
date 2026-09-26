package com.mamiyaotaru.voxelmap.chunkanalysis;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;

public record ChunkAnalysisSnapshot(
        long seed,
        ResourceKey<Level> dimension,
        ChunkPos center,
        int radius,
        List<ChunkAnalysisDifference> differences,
        long missingCount,
        long unexpectedCount,
        long changedCount,
        long excavationBlockCount,
        int excavationCount,
        int comparedBlocks,
        int skippedChunks,
        int unreliableChunks,
        long generationMillis,
        ChunkAnalysisService.ScanMode mode,
        long auditContainers,
        long auditRedstone,
        long auditWorkstations
) {
    public static ChunkAnalysisSnapshot empty() {
        return new ChunkAnalysisSnapshot(0L, Level.OVERWORLD, new ChunkPos(0, 0), 0,
                List.of(), 0L, 0L, 0L, 0L, 0, 0, 0, 0, 0L,
                ChunkAnalysisService.ScanMode.FULL, 0L, 0L, 0L);
    }

    public long count(ChunkAnalysisDifference.Kind kind) {
        return switch (kind) {
            case MISSING_EXPECTED -> missingCount;
            case EXCAVATION -> excavationBlockCount;
            case UNEXPECTED -> unexpectedCount;
            case CHANGED -> changedCount;
            case UNEXPECTED_INTERESTING -> unexpectedCount;
        };
    }
}
