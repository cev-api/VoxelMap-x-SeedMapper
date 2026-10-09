package com.mamiyaotaru.voxelmap.persistent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapUploadBudgetTest {
    @Test void displaySizeImagesCanUploadWithoutStarvingOrBypassingTheFrameBudget() {
        WorldMapUploadBudget.beginFrame();
        assertTrue(WorldMapUploadBudget.available(8 << 20));
        WorldMapUploadBudget.begin(8 << 20);
        assertFalse(WorldMapUploadBudget.available(1));
        WorldMapUploadBudget.beginFrame();
        WorldMapUploadBudget.begin(2 << 20);
        assertFalse(WorldMapUploadBudget.available(8 << 20));
        WorldMapUploadBudget.beginFrame();
        assertTrue(WorldMapUploadBudget.available(8 << 20));
    }
}
