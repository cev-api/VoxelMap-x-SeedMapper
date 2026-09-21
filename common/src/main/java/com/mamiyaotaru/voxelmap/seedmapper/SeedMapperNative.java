package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.StructureConfig;
import com.github.cubiomes.StructureConfigProvider;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.util.MessageUtils;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class SeedMapperNative {
    private static final String LIBRARY_BASE_NAME = "cubiomes";
    private static volatile boolean loaded;
    private static volatile String loadFailureDetail;
    private static volatile boolean failureReported;
    private static final Object CUBIOMES_LOCK = new Object();
    private static final ThreadLocal<Map<Integer, Integer>> ACTIVE_STRUCTURE_SALTS = new ThreadLocal<>();

    private SeedMapperNative() {
    }

    /**
     * Loads the cubiomes native library.
     *
     * <p>The bundled copy is tried first because its ABI matches the generated bindings. It is
     * extracted next to the game directory before the system temp directory: many Linux systems
     * mount /tmp with noexec, and a library loaded from such a mount fails with an
     * UnsatisfiedLinkError. An already extracted copy is reused so the file is not rewritten on
     * every launch.</p>
     *
     * <p>If no extracted copy can be executed, the launcher's native directory
     * (java.library.path) is used as a last resort, so a manually supplied libcubiomes.so keeps
     * working instead of being ignored.</p>
     */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (SeedMapperNative.class) {
            if (loaded) {
                return;
            }

            String libraryName = System.mapLibraryName(LIBRARY_BASE_NAME);
            List<String> failures = new ArrayList<>();

            byte[] bundled = readBundledLibrary(libraryName, failures);
            if (bundled != null) {
                for (Path target : extractionTargets(libraryName)) {
                    try {
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        if (!isCurrent(target, bundled)) {
                            Files.write(target, bundled);
                        }
                        System.load(target.toAbsolutePath().toString());
                        loaded = true;
                        return;
                    } catch (IOException | UnsatisfiedLinkError | RuntimeException e) {
                        failures.add(target + " -> " + describe(e));
                    }
                }
            }

            // A launcher-managed natives directory is the usual fallback when the bundled copy
            // cannot be executed (read-only home directory, noexec temp mount, sandbox, ...).
            try {
                System.loadLibrary(LIBRARY_BASE_NAME);
                loaded = true;
                return;
            } catch (UnsatisfiedLinkError e) {
                failures.add("System.loadLibrary(" + LIBRARY_BASE_NAME + ") -> " + describe(e));
            }

            String detail = "Failed to load the cubiomes native library " + libraryName
                    + ". Attempts: " + String.join(" | ", failures);
            loadFailureDetail = detail;
            VoxelConstants.getLogger().error(detail);
            throw new RuntimeException(detail);
        }
    }

    /** True once the native library has been loaded successfully. */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Tells the player once that the native library is unavailable.
     *
     * <p>Without this the only symptom is an empty seed map, because the preview falls back to a
     * transparent pixel buffer when world generation throws.</p>
     */
    public static void reportFailureOnce() {
        if (loadFailureDetail == null || failureReported) {
            return;
        }
        failureReported = true;
        try {
            MessageUtils.chatInfo("SeedMapper: the cubiomes native library could not be loaded, so seed "
                    + "map features are disabled. See the game log for the attempted paths.");
        } catch (RuntimeException ignored) {
            // Never let this reporting path mask the original failure; the log entry already exists.
        }
    }

    private static byte[] readBundledLibrary(String libraryName, List<String> failures) {
        ClassLoader loader = SeedMapperNative.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(libraryName)) {
            if (input == null) {
                failures.add("bundled resource " + libraryName + " not found on the classpath");
                return null;
            }
            return input.readAllBytes();
        } catch (IOException e) {
            failures.add("reading bundled " + libraryName + " -> " + describe(e));
            return null;
        }
    }

    private static List<Path> extractionTargets(String libraryName) {
        List<Path> targets = new ArrayList<>(3);
        try {
            targets.add(VoxelConstants.getMinecraft().gameDirectory.toPath()
                    .resolve("voxelmap-natives").resolve(libraryName));
        } catch (RuntimeException ignored) {
            // The game directory is preferred but not required; the temp directory still works.
        }
        String tempDirectory = System.getProperty("java.io.tmpdir", ".");
        targets.add(Path.of(tempDirectory, "voxelmap-natives", libraryName));
        targets.add(Path.of(tempDirectory, libraryName));
        return targets;
    }

    /**
     * Compares content, not just length: a rebuilt native can keep its previous size, and
     * loading a stale library would mismatch the generated bindings.
     */
    private static boolean isCurrent(Path target, byte[] expected) {
        try {
            return Files.size(target) == expected.length && Arrays.equals(Files.readAllBytes(target), expected);
        } catch (IOException e) {
            return false;
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
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
