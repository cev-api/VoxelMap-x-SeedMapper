package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.SharedConstants;

import java.util.List;
import java.util.Locale;

public final class SeedMapperCompat {
    public record MinecraftVersion(String id, String label, int cubiomesVersion) {
    }

    public static final String AUTO_VERSION = "auto";
    private static final List<MinecraftVersion> SUPPORTED_VERSIONS = List.of(
            new MinecraftVersion("1.16.1", "1.16.1", Cubiomes.MC_1_16_1()),
            new MinecraftVersion("1.16.5", "1.16.5", Cubiomes.MC_1_16_5()),
            new MinecraftVersion("1.17.1", "1.17.1", Cubiomes.MC_1_17_1()),
            new MinecraftVersion("1.18.2", "1.18.2", Cubiomes.MC_1_18_2()),
            new MinecraftVersion("1.19.2", "1.19.2", Cubiomes.MC_1_19_2()),
            new MinecraftVersion("1.19.4", "1.19.4", Cubiomes.MC_1_19_4()),
            new MinecraftVersion("1.20.6", "1.20.6", Cubiomes.MC_1_20_6()),
            new MinecraftVersion("1.21.1", "1.21.1", Cubiomes.MC_1_21_1()),
            new MinecraftVersion("1.21.3", "1.21.3", Cubiomes.MC_1_21_3()),
            new MinecraftVersion("1.21.4", "1.21.4", Cubiomes.MC_1_21_4()),
            new MinecraftVersion("1.21.5", "1.21.5", Cubiomes.MC_1_21_5()),
            new MinecraftVersion("1.21.6", "1.21.6", Cubiomes.MC_1_21_6()),
            new MinecraftVersion("1.21.9", "1.21.9", Cubiomes.MC_1_21_9()),
            new MinecraftVersion("1.21.11", "1.21.11", Cubiomes.MC_1_21_11()),
            new MinecraftVersion("26.1", "26.1", Cubiomes.MC_26_1()),
            new MinecraftVersion("26.2", "26.2", Cubiomes.MC_26_2())
    );

    private SeedMapperCompat() {
    }

    public static int getMcVersion() {
        String configured = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions().minecraftVersion;
        if (!AUTO_VERSION.equals(configured)) {
            MinecraftVersion selected = findVersion(configured);
            if (selected != null) return selected.cubiomesVersion();
        }
        return getClientMcVersion();
    }

    public static int getClientMcVersion() {
        String version = SharedConstants.getCurrentVersion().name();
        if (version.startsWith("1.16")) return Cubiomes.MC_1_16_5();
        if (version.startsWith("1.17")) return Cubiomes.MC_1_17();
        if (version.startsWith("1.18")) return Cubiomes.MC_1_18();
        if (version.startsWith("1.19")) return Cubiomes.MC_1_19();
        if (version.startsWith("1.20")) return Cubiomes.MC_1_20();
        if (version.startsWith("1.21")) return Cubiomes.MC_1_21();
        if (version.startsWith("26.1")) return Cubiomes.MC_26_1();
        if (version.startsWith("26.2")) return Cubiomes.MC_26_2();
        return Cubiomes.MC_NEWEST();
    }

    public static List<MinecraftVersion> getSupportedVersions() {
        return SUPPORTED_VERSIONS;
    }

    public static String normalizeVersionId(String value) {
        if (value == null || value.isBlank() || AUTO_VERSION.equalsIgnoreCase(value.trim())) {
            return AUTO_VERSION;
        }
        MinecraftVersion version = findVersion(value);
        return version == null ? AUTO_VERSION : version.id();
    }

    public static MinecraftVersion findVersion(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_VERSIONS.stream()
                .filter(version -> version.id().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }

    public static String getSelectedVersionLabel() {
        String configured = VoxelConstants.getVoxelMapInstance().getSeedMapperOptions().minecraftVersion;
        if (AUTO_VERSION.equals(configured)) {
            return "Auto (" + SharedConstants.getCurrentVersion().name() + ")";
        }
        MinecraftVersion version = findVersion(configured);
        return version == null ? "Auto (" + SharedConstants.getCurrentVersion().name() + ")" : version.label();
    }
}
