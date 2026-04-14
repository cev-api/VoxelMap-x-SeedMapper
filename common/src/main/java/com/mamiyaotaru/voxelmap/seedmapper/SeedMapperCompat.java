package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import net.minecraft.SharedConstants;

public final class SeedMapperCompat {
    private SeedMapperCompat() {
    }

    public static int getMcVersion() {
        String version = SharedConstants.getCurrentVersion().name();
        if (version.startsWith("1.16")) return Cubiomes.MC_1_16_5();
        if (version.startsWith("1.17")) return Cubiomes.MC_1_17();
        if (version.startsWith("1.18")) return Cubiomes.MC_1_18();
        if (version.startsWith("1.19")) return Cubiomes.MC_1_19();
        if (version.startsWith("1.20")) return Cubiomes.MC_1_20();
        if (version.startsWith("1.21")) return Cubiomes.MC_1_21();
        return Cubiomes.MC_NEWEST();
    }
}
