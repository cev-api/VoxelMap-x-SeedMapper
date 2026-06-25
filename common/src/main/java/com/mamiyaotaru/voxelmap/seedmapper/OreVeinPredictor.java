package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class OreVeinPredictor {
    private static final int VEIN_MIN_Y = -60;
    private static final int VEIN_MAX_Y = 50;
    private static final float VEININESS_THRESHOLD = 0.4F;
    private static final float VEIN_SOLIDNESS = 0.7F;
    private static final float CHANCE_OF_RAW_ORE_BLOCK = 0.02F;
    private static final float SKIP_ORE_IF_GAP_NOISE_IS_BELOW = -0.3F;

    private static volatile HolderLookup.Provider holders;
    private static boolean unavailable;
    private static long cachedSeed = Long.MIN_VALUE;
    private static State cachedState;

    public static final class State {
        private final DensityFunction veinToggle;
        private final DensityFunction veinGap;
        private final NormalNoise noiseA;
        private final NormalNoise noiseB;
        private final PositionalRandomFactory oreRandom;
        private final int cellW;
        private final int cellH;

        private State(DensityFunction veinToggle, DensityFunction veinGap, NormalNoise noiseA, NormalNoise noiseB,
                      PositionalRandomFactory oreRandom, int cellW, int cellH) {
            this.veinToggle = veinToggle;
            this.veinGap = veinGap;
            this.noiseA = noiseA;
            this.noiseB = noiseB;
            this.oreRandom = oreRandom;
            this.cellW = cellW;
            this.cellH = cellH;
        }
    }

    private OreVeinPredictor() {
    }

    public static synchronized State prepare(long seed) {
        if (unavailable) {
            return null;
        }
        if (cachedState != null && cachedSeed == seed) {
            return cachedState;
        }
        try {
            if (holders == null) {
                holders = VanillaRegistries.createLookup();
            }
            RandomState randomState = RandomState.create(holders, NoiseGeneratorSettings.OVERWORLD, seed);
            NoiseRouter router = randomState.router();
            NoiseGeneratorSettings settings = holders.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            State state = new State(
                    router.veinToggle(),
                    router.veinGap(),
                    randomState.getOrCreateNoise(Noises.ORE_VEIN_A),
                    randomState.getOrCreateNoise(Noises.ORE_VEIN_B),
                    randomState.oreRandom(),
                    settings.noiseSettings().getCellWidth(),
                    settings.noiseSettings().getCellHeight());
            cachedState = state;
            cachedSeed = seed;
            return state;
        } catch (Throwable t) {
            unavailable = true;
            return null;
        }
    }

    public static int blockAt(State state, int x, int y, int z) {
        double veininess = interpDensity(state.veinToggle, x, y, z, state.cellW, state.cellH);
        boolean copper = veininess > 0.0;
        int minY = copper ? 0 : -60;
        int maxY = copper ? 50 : -8;
        double veininessRidged = Math.abs(veininess);
        int distanceFromTop = maxY - y;
        int distanceFromBottom = y - minY;
        if (distanceFromBottom < 0 || distanceFromTop < 0) {
            return -1;
        }
        int distanceFromEdge = Math.min(distanceFromTop, distanceFromBottom);
        double edgeRoundoff = Mth.clampedMap(distanceFromEdge, 0.0, 20.0, -0.2, 0.0);
        if (veininessRidged + edgeRoundoff < VEININESS_THRESHOLD) {
            return -1;
        }
        RandomSource random = state.oreRandom.at(x, y, z);
        if (random.nextFloat() > VEIN_SOLIDNESS) {
            return -1;
        }
        if (veinRidged(state, x, y, z) >= 0.0) {
            return -1;
        }
        double richness = Mth.clampedMap(veininessRidged, 0.4F, 0.6F, 0.1F, 0.3F);
        if (random.nextFloat() < richness && veinGap(state, x, y, z) > SKIP_ORE_IF_GAP_NOISE_IS_BELOW) {
            if (random.nextFloat() < CHANCE_OF_RAW_ORE_BLOCK) {
                return copper ? Cubiomes.RAW_COPPER_BLOCK() : Cubiomes.RAW_IRON_BLOCK();
            }
            return copper ? Cubiomes.COPPER_ORE() : Cubiomes.IRON_ORE();
        }
        return copper ? Cubiomes.GRANITE() : Cubiomes.TUFF();
    }

    public static int regionTypeAt(State state, int x, int y, int z) {
        double veininess = interpDensity(state.veinToggle, x, y, z, state.cellW, state.cellH);
        boolean copper = veininess > 0.0;
        int minY = copper ? 0 : -60;
        int maxY = copper ? 50 : -8;
        double veininessRidged = Math.abs(veininess);
        int distanceFromTop = maxY - y;
        int distanceFromBottom = y - minY;
        if (distanceFromBottom < 0 || distanceFromTop < 0) {
            return 0;
        }
        int distanceFromEdge = Math.min(distanceFromTop, distanceFromBottom);
        double edgeRoundoff = Mth.clampedMap(distanceFromEdge, 0.0, 20.0, -0.2, 0.0);
        if (veininessRidged + edgeRoundoff < VEININESS_THRESHOLD) {
            return 0;
        }
        if (veinRidged(state, x, y, z) >= 0.0) {
            return 0;
        }
        return copper ? 1 : -1;
    }

    private static double veinRidged(State state, int x, int y, int z) {
        double a = Math.abs(interpInner(state.noiseA, x, y, z, state.cellW, state.cellH));
        double b = Math.abs(interpInner(state.noiseB, x, y, z, state.cellW, state.cellH));
        return -0.08F + Math.max(a, b);
    }

    private static double veinGap(State state, int x, int y, int z) {
        return state.veinGap.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    private static double interpDensity(DensityFunction f, int x, int y, int z, int cellW, int cellH) {
        int x0 = Math.floorDiv(x, cellW) * cellW;
        int y0 = Math.floorDiv(y, cellH) * cellH;
        int z0 = Math.floorDiv(z, cellW) * cellW;
        double tx = (double) (x - x0) / cellW;
        double ty = (double) (y - y0) / cellH;
        double tz = (double) (z - z0) / cellW;
        double c00 = lerp(tx, density(f, x0, y0, z0), density(f, x0 + cellW, y0, z0));
        double c10 = lerp(tx, density(f, x0, y0 + cellH, z0), density(f, x0 + cellW, y0 + cellH, z0));
        double c01 = lerp(tx, density(f, x0, y0, z0 + cellW), density(f, x0 + cellW, y0, z0 + cellW));
        double c11 = lerp(tx, density(f, x0, y0 + cellH, z0 + cellW), density(f, x0 + cellW, y0 + cellH, z0 + cellW));
        return lerp(tz, lerp(ty, c00, c10), lerp(ty, c01, c11));
    }

    private static double interpInner(NormalNoise n, int x, int y, int z, int cellW, int cellH) {
        int x0 = Math.floorDiv(x, cellW) * cellW;
        int y0 = Math.floorDiv(y, cellH) * cellH;
        int z0 = Math.floorDiv(z, cellW) * cellW;
        double tx = (double) (x - x0) / cellW;
        double ty = (double) (y - y0) / cellH;
        double tz = (double) (z - z0) / cellW;
        double c00 = lerp(tx, inner(n, x0, y0, z0), inner(n, x0 + cellW, y0, z0));
        double c10 = lerp(tx, inner(n, x0, y0 + cellH, z0), inner(n, x0 + cellW, y0 + cellH, z0));
        double c01 = lerp(tx, inner(n, x0, y0, z0 + cellW), inner(n, x0 + cellW, y0, z0 + cellW));
        double c11 = lerp(tx, inner(n, x0, y0 + cellH, z0 + cellW), inner(n, x0 + cellW, y0 + cellH, z0 + cellW));
        return lerp(tz, lerp(ty, c00, c10), lerp(ty, c01, c11));
    }

    private static double density(DensityFunction f, int x, int y, int z) {
        return f.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    private static double inner(NormalNoise n, int x, int y, int z) {
        if (y < VEIN_MIN_Y || y > VEIN_MAX_Y) {
            return 0.0;
        }
        return n.getValue(x * 4.0, y * 4.0, z * 4.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
