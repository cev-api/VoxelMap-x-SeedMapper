package com.mamiyaotaru.voxelmap.chunkanalysis;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChunkAnalysisOreTargetTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void deepslateVariantsResolveToTheSameOreFamily() {
        assertEquals(ChunkAnalysisOreTarget.DIAMOND,
                ChunkAnalysisOreTarget.fromId("deepslate_diamond_ore"));
        assertEquals(ChunkAnalysisOreTarget.DIAMOND,
                ChunkAnalysisOreTarget.fromId("minecraft:diamond_ore"));
    }

    @Test
    void infestedVariantsResolveToInfestedFamily() {
        assertEquals(ChunkAnalysisOreTarget.INFESTED,
                ChunkAnalysisOreTarget.fromId("infested_deepslate"));
    }

    @Test
    void unknownTargetsAreRejected() {
        assertNull(ChunkAnalysisOreTarget.fromId("not_an_ore"));
    }
}
