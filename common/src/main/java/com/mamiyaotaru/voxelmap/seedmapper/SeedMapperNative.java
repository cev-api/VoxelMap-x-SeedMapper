package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.StructureConfig;
import com.github.cubiomes.StructureConfigProvider;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Supplier;

public final class SeedMapperNative {
    private static volatile boolean loaded;
    private static final Object CUBIOMES_LOCK = new Object();
    private static final ThreadLocal<Map<Integer, Integer>> ACTIVE_STRUCTURE_SALTS = new ThreadLocal<>();

    private SeedMapperNative() {
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (SeedMapperNative.class) {
            if (loaded) {
                return;
            }

            String libraryName = System.mapLibraryName("cubiomes");
            try {
                Path tempFile = Files.createTempFile("cubiomes", libraryName);
                try (InputStream input = SeedMapperNative.class.getClassLoader().getResourceAsStream(libraryName)) {
                    if (input == null) {
                        throw new IOException("Missing native library resource: " + libraryName);
                    }
                    Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                System.load(tempFile.toAbsolutePath().toString());
                tempFile.toFile().deleteOnExit();
                loaded = true;
            } catch (IOException | UnsatisfiedLinkError e) {
                throw new RuntimeException("Failed to load cubiomes native library: " + libraryName, e);
            }
        }
    }

    public static Object cubiomesLock() {
        return CUBIOMES_LOCK;
    }

    public static <T> T withStructureSalts(Map<Integer, Integer> salts, Supplier<T> action) {
        synchronized (CUBIOMES_LOCK) {
            ensureLoaded();
            // Cubiomes_1 initializes its native symbol lookup by calling back into
            // ensureLoaded().  Installing the callback from ensureLoaded itself
            // would therefore run while SYMBOL_LOOKUP is still null.  Touching a
            // harmless generated constant first completes that initialization.
            Cubiomes.DIM_OVERWORLD();
            installStructureConfigProvider();
            Map<Integer, Integer> previous = ACTIVE_STRUCTURE_SALTS.get();
            if (salts == null || salts.isEmpty()) {
                ACTIVE_STRUCTURE_SALTS.remove();
            } else {
                ACTIVE_STRUCTURE_SALTS.set(Map.copyOf(salts));
            }
            try {
                return action.get();
            } finally {
                if (previous == null || previous.isEmpty()) {
                    ACTIVE_STRUCTURE_SALTS.remove();
                } else {
                    ACTIVE_STRUCTURE_SALTS.set(previous);
                }
            }
        }
    }

    public static void withStructureSalts(Map<Integer, Integer> salts, Runnable action) {
        withStructureSalts(salts, () -> {
            action.run();
            return null;
        });
    }

    private static void installStructureConfigProvider() {
        if (structureConfigProviderInstalled) {
            return;
        }
        synchronized (SeedMapperNative.class) {
            if (structureConfigProviderInstalled) {
                return;
            }
        Cubiomes.setStructureConfigProvider(StructureConfigProvider.allocate((structureType, mc, structureConfig) -> {
            int result = Cubiomes.getStructureConfig_default(structureType, mc, structureConfig);
            if (result == 0) {
                return 0;
            }
            Map<Integer, Integer> salts = ACTIVE_STRUCTURE_SALTS.get();
            if (salts != null) {
                Integer salt = salts.get(structureType);
                if (salt != null) {
                    StructureConfig.salt(structureConfig, salt);
                }
            }
            return 1;
        }, Arena.global()));
            structureConfigProviderInstalled = true;
        }
    }

    private static volatile boolean structureConfigProviderInstalled;
}
