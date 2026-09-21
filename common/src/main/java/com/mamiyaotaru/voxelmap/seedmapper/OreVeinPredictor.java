package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.lang.ref.SoftReference;

/** Samples the registered functions and random sequence used by vanilla OreVeinRule. */
public final class OreVeinPredictor {
    private static HolderLookup.Provider holders;
    private static long cachedSeed = Long.MIN_VALUE;
    private static SoftReference<State> cachedState = new SoftReference<>(null);

    public record State(DensitySampler.Bound copperDensity, DensitySampler.Bound ironDensity,
                        DensitySampler.Bound richness, DensitySampler.Bound gap,
                        PositionalRandomFactory oreRandom) {}

    private OreVeinPredictor() {}

    public static synchronized State prepare(long seed) {
        State cached = cachedState.get();
        if (cached != null && cachedSeed == seed) return cached;
        try {
            if (holders == null) holders = VanillaRegistries.createWorldLookup();
            NoiseGeneratorSettings settings = holders.lookupOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            RandomState randomState = RandomState.create(holders.lookupOrThrow(Registries.NOISE), seed, settings);
            State state = new State(sampler(randomState, "copper_density"), sampler(randomState, "iron_density"),
                    sampler(randomState, "richness"), sampler(randomState, "gap"),
                    randomState.getOrCreateRandomFactory(Identifier.withDefaultNamespace("ore")));
            cachedState = new SoftReference<>(state);
            cachedSeed = seed;
            return state;
        } catch (RuntimeException exception) {
            VoxelConstants.getLogger().error("Could not initialize vanilla ore vein prediction", exception);
            return null;
        }
    }

    private static DensitySampler.Bound sampler(RandomState randomState, String name) {
        ResourceKey<DensityFunction> key = ResourceKey.create(Registries.DENSITY_FUNCTION,
                Identifier.withDefaultNamespace("overworld/ore_vein/" + name));
        // Registered functions include vanilla interpolation; do not interpolate twice.
        return randomState.getSampler(holders.lookupOrThrow(Registries.DENSITY_FUNCTION).getOrThrow(key).value())
                .bind(SamplerContext.EMPTY_UNCACHED);
    }

    public static int regionTypeAt(State state, int x, int y, int z) {
        if (state.copperDensity.sampleValue(x, y, z) > 0.0F) return 1;
        if (state.ironDensity.sampleValue(x, y, z) > 0.0F) return -1;
        return 0;
    }

    public static int blockAt(State state, int x, int y, int z) {
        int type = regionTypeAt(state, x, y, z);
        if (type == 0) return -1;
        boolean copper = type > 0;
        float density = (copper ? state.copperDensity : state.ironDensity).sampleValue(x, y, z);
        RandomSource random = state.oreRandom.at(x, y, z);
        if (random.nextFloat() > density) return -1;
        if (random.nextFloat() < state.richness.sampleValue(x, y, z) && state.gap.sampleValue(x, y, z) < 0.0F) {
            if (random.nextFloat() < 0.02F) return copper ? Cubiomes.RAW_COPPER_BLOCK() : Cubiomes.RAW_IRON_BLOCK();
            return copper ? Cubiomes.COPPER_ORE() : Cubiomes.IRON_ORE();
        }
        return copper ? Cubiomes.GRANITE() : Cubiomes.TUFF();
    }
}
