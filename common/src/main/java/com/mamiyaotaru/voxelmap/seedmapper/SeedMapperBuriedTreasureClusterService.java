package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;
import com.github.cubiomes.StructureConfig;
import com.google.common.math.BigIntegerMath;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Finds the rare buried-treasure formations shipped by SeedMapper. */
public final class SeedMapperBuriedTreasureClusterService {
    private static final String RESOURCE_PREFIX = "/store/buried_treasure_formation_";
    private static final long MASK_48 = (1L << 48) - 1L;
    private static final BigInteger TWO_POW_48 = BigInteger.ONE.shiftLeft(48);
    private static final BigInteger LATTICE_U_X = BigInteger.valueOf(-12_354_965L);
    private static final BigInteger LATTICE_U_Z = BigInteger.valueOf(-989_088L);
    private static final BigInteger LATTICE_V_X = BigInteger.valueOf(-2_831_608L);
    private static final BigInteger LATTICE_V_Z = BigInteger.valueOf(-23_009_024L);
    private static final BigInteger X0_FACTOR = BigInteger.valueOf(49_284_120_807L);
    private static final BigInteger Z0_FACTOR = BigInteger.valueOf(-126_780_825_563L);
    private static final List<Formation> FORMATIONS = loadFormations();
    private static final RenewableSoftReference<ConcurrentHashMap<ClusterKey, List<ClusterResult>>> CACHE =
            new RenewableSoftReference<>(ConcurrentHashMap::new);
    private static final int MAX_CLUSTER_RESULTS = 100_000;

    private SeedMapperBuriedTreasureClusterService() {
    }

    public static List<ClusterResult> find(long seed, int mcVersion, int generatorFlags,
                                           SeedMapperSettingsManager settings) {
        if (mcVersion < Cubiomes.MC_1_13() || FORMATIONS.isEmpty()) {
            return List.of();
        }

        MapHolder salts = new MapHolder(settings == null ? java.util.Map.of() : settings.getResolvedCustomStructureSalts());
        int saltHash = settings == null ? 0 : settings.getCustomStructureSaltHash();
        ClusterKey key = new ClusterKey(seed, mcVersion, generatorFlags, saltHash);
        return CACHE.get().computeIfAbsent(key, ignored -> {
            synchronized (SeedMapperNative.cubiomesLock()) {
                return SeedMapperNative.withStructureSalts(salts.values,
                        () -> findNative(seed, mcVersion, generatorFlags));
            }
        });
    }

    private static List<ClusterResult> findNative(long seed, int mcVersion, int generatorFlags) {
        long structureSeed = seed & MASK_48;
        BigInteger worldBorderChunks = BigInteger.valueOf(Level.MAX_LEVEL_SIZE).shiftRight(4);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment structureConfig = StructureConfig.allocate(arena);
            if (Cubiomes.getStructureConfig(Cubiomes.Treasure(), mcVersion, structureConfig) == 0) {
                return List.of();
            }
            MemorySegment defaultStructureConfig = StructureConfig.allocate(arena);
            if (Cubiomes.getStructureConfig_default(Cubiomes.Treasure(), mcVersion, defaultStructureConfig) == 0) {
                return List.of();
            }
            long saltAdjustment = (long) StructureConfig.salt(defaultStructureConfig)
                    - StructureConfig.salt(structureConfig);
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, mcVersion, generatorFlags);
            Cubiomes.applySeed(generator, Cubiomes.DIM_OVERWORLD(), seed);
            Map<ChunkPos, Integer> results = new java.util.HashMap<>();

            for (Formation formation : FORMATIONS) {
                for (long formationStructureSeed : formation.structureSeeds) {
                    // The bundled formations were generated with Cubiomes' default salt.  The
                    // structure state also includes the active salt, so shift the precomputed
                    // world-seed offset when a datapack/custom salt is in effect.
                    long offset = formationStructureSeed - structureSeed + saltAdjustment;
                    BigInteger factor = BigInteger.valueOf(offset);
                    BigInteger x0 = X0_FACTOR.multiply(factor);
                    BigInteger z0 = Z0_FACTOR.multiply(factor);

                    List<Point> corners = List.of(
                            new Point(worldBorderChunks, worldBorderChunks),
                            new Point(worldBorderChunks.negate(), worldBorderChunks),
                            new Point(worldBorderChunks.negate(), worldBorderChunks.negate()),
                            new Point(worldBorderChunks, worldBorderChunks.negate()));
                    List<Point> transformed = new ArrayList<>(corners.size());
                    for (Point corner : corners) {
                        BigInteger dx = corner.x.subtract(x0);
                        BigInteger dz = corner.z.subtract(z0);
                        BigInteger s = LATTICE_V_Z.multiply(dx).subtract(LATTICE_U_Z.multiply(dz));
                        BigInteger t = LATTICE_U_X.multiply(dz).subtract(LATTICE_V_X.multiply(dx));
                        transformed.add(new Point(s, t));
                    }

                    BigInteger sMin = transformed.stream().min(Comparator.comparing(Point::x)).orElseThrow().x;
                    BigInteger sMax = transformed.stream().max(Comparator.comparing(Point::x)).orElseThrow().x;
                    sMin = BigIntegerMath.divide(sMin, TWO_POW_48, RoundingMode.CEILING);
                    sMax = BigIntegerMath.divide(sMax, TWO_POW_48, RoundingMode.FLOOR);

                    for (BigInteger s = sMin; s.compareTo(sMax) <= 0; s = s.add(BigInteger.ONE)) {
                        BigInteger ax = x0.add(s.multiply(LATTICE_U_X));
                        BigInteger az = z0.add(s.multiply(LATTICE_V_X));
                        BigInteger txMin = BigIntegerMath.divide(worldBorderChunks.subtract(ax), LATTICE_U_Z, RoundingMode.CEILING);
                        BigInteger txMax = BigIntegerMath.divide(worldBorderChunks.negate().subtract(ax), LATTICE_U_Z, RoundingMode.FLOOR);
                        BigInteger tzMin = BigIntegerMath.divide(worldBorderChunks.subtract(az), LATTICE_V_Z, RoundingMode.CEILING);
                        BigInteger tzMax = BigIntegerMath.divide(worldBorderChunks.negate().subtract(az), LATTICE_V_Z, RoundingMode.FLOOR);
                        BigInteger tMin = txMin.max(tzMin);
                        BigInteger tMax = txMax.min(tzMax);

                        for (BigInteger t = tMin; t.compareTo(tMax) <= 0; t = t.add(BigInteger.ONE)) {
                            int x = ax.add(t.multiply(LATTICE_U_Z)).intValueExact();
                            int z = az.add(t.multiply(LATTICE_V_Z)).intValueExact();
                            if (formation.hasViableBiomes(generator, mcVersion, x, z)) {
                                results.merge(new ChunkPos(x, z), formation.treasureCount(), Math::max);
                                if (results.size() >= MAX_CLUSTER_RESULTS) {
                                    return toResults(results);
                                }
                            }
                        }
                    }
                }
            }
            return toResults(results);
        }
    }

    private static List<ClusterResult> toResults(Map<ChunkPos, Integer> results) {
        return results.entrySet().stream()
                .map(entry -> new ClusterResult(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt((ClusterResult result) -> result.origin().x())
                        .thenComparingInt(result -> result.origin().z()))
                .toList();
    }

    private static List<Formation> loadFormations() {
        ArrayList<Formation> formations = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            String resource = RESOURCE_PREFIX + index + ".bin";
            try (InputStream input = SeedMapperBuriedTreasureClusterService.class.getResourceAsStream(resource)) {
                if (input == null) continue;
                byte[] bytes = input.readAllBytes();
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                int formationLength = buffer.getInt();
                ArrayList<ChunkPos> formation = new ArrayList<>(formationLength);
                for (int i = 0; i < formationLength; i++) {
                    formation.add(new ChunkPos(buffer.getInt(), buffer.getInt()));
                }
                int seedLength = buffer.getInt();
                long[] structureSeeds = new long[seedLength];
                for (int i = 0; i < seedLength; i++) {
                    structureSeeds[i] = buffer.getLong();
                }
                formations.add(new Formation(List.copyOf(formation), structureSeeds));
            } catch (IOException | RuntimeException exception) {
                VoxelLog.warn("Unable to load buried-treasure formation " + resource, exception);
            }
        }
        return List.copyOf(formations);
    }

    private record Formation(List<ChunkPos> formation, long[] structureSeeds) {
        private int treasureCount() {
            return formation.size();
        }

        private boolean hasViableBiomes(MemorySegment generator, int mcVersion, int chunkX, int chunkZ) {
            for (ChunkPos offset : formation) {
                int biome = Cubiomes.getBiomeAt(generator, 4, ((chunkX + offset.x()) << 2) + 2,
                        319 >> 2, ((chunkZ + offset.z()) << 2) + 2);
                if (Cubiomes.isViableFeatureBiome(mcVersion, Cubiomes.Treasure(), biome) == 0) {
                    return false;
                }
            }
            return true;
        }
    }

    private record Point(BigInteger x, BigInteger z) {
    }

    private record MapHolder(java.util.Map<Integer, Integer> values) {
    }

    private record ClusterKey(long seed, int mcVersion, int generatorFlags, int saltHash) {
    }

    public record ClusterResult(ChunkPos origin, int treasureCount) {
    }

    private static final class VoxelLog {
        private static void warn(String message, Throwable throwable) {
            try {
                com.mamiyaotaru.voxelmap.VoxelConstants.getLogger().warn(message, throwable);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
