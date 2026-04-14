package com.mamiyaotaru.voxelmap.seedmapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SeedMapperNative {
    private static volatile boolean loaded;
    private static final Object CUBIOMES_LOCK = new Object();

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
}
