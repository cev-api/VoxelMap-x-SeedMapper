package com.mamiyaotaru.voxelmap.seedmapper;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.ItemStack;
import com.github.cubiomes.LootTableContext;
import com.github.cubiomes.RandomSource;
import com.github.cubiomes.RandomState;
import com.github.cubiomes.Xoroshiro;
import net.minecraft.world.level.levelgen.RandomSupport;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/** Predicts Trial Chambers vault rewards using the vanilla 1.21+ seed stream. */
public final class SeedMapperVaultService {
    private static final int MAX_VAULT_ATTEMPTS = 1_000_000;
    private static final int MAX_GENERATED_ITEMS = 256;
    private static final long REWARD_MD5_LO = 0x102ea793e31f23ffL;
    private static final long REWARD_MD5_HI = 0xd7c231952cddadf1L;
    private static final long REWARD_OMINOUS_MD5_LO = 0x05a13d5ce5edaab3L;
    private static final long REWARD_OMINOUS_MD5_HI = 0x1a3950a30a86bc23L;

    private SeedMapperVaultService() {
    }

    public static List<VaultPrediction> predict(long seed, int mcVersion, int offset, boolean ominous, int amount) {
        if (offset < 0 || offset > MAX_VAULT_ATTEMPTS || amount < 1 || amount > 64
                || mcVersion < Cubiomes.MC_1_21_1()) {
            return List.of();
        }

        SeedMapperNative.ensureLoaded();
        synchronized (SeedMapperNative.cubiomesLock()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lootTableContext = createLootTableContext(arena, ominous, mcVersion);
                if (lootTableContext == null) {
                    return List.of();
                }

                RandomSupport.Seed128bit vaultSeed = vaultSeed(seed, ominous);
                MemorySegment state = allocateRandomState(arena, vaultSeed);
                Cubiomes.set_loot_prng_type(lootTableContext, Cubiomes.XOROSHIRO());
                Cubiomes.set_internal_loot_seed(lootTableContext, state);

                for (int i = 0; i < offset; i++) {
                    Cubiomes.generate_loot(lootTableContext);
                }

                ArrayList<VaultPrediction> predictions = new ArrayList<>(amount);
                for (int i = 0; i < amount; i++) {
                    Cubiomes.generate_loot(lootTableContext);
                    predictions.add(new VaultPrediction(
                            offset + i + 1,
                            ominous,
                            readGeneratedItems(lootTableContext, mcVersion),
                            formatState(RandomSource.xr(LootTableContext.prng_state(lootTableContext)))
                    ));
                }
                return List.copyOf(predictions);
            } catch (Throwable ignored) {
                return List.of();
            }
        }
    }

    private static RandomSupport.Seed128bit vaultSeed(long seed, boolean ominous) {
        RandomSupport.Seed128bit seed128 = RandomSupport.upgradeSeedTo128bitUnmixed(seed);
        seed128 = ominous
                ? seed128.xor(REWARD_OMINOUS_MD5_LO, REWARD_OMINOUS_MD5_HI)
                : seed128.xor(REWARD_MD5_LO, REWARD_MD5_HI);
        return seed128.mixed();
    }

    private static MemorySegment allocateRandomState(Arena arena, RandomSupport.Seed128bit seed) {
        MemorySegment state = RandomState.allocate(arena);
        MemorySegment xoroshiro = Xoroshiro.allocate(arena);
        Xoroshiro.lo(xoroshiro, seed.seedLo());
        Xoroshiro.hi(xoroshiro, seed.seedHi());
        RandomState.xr(state, xoroshiro);
        return state;
    }

    private static MemorySegment createLootTableContext(Arena arena, boolean ominous, int mcVersion) {
        MemorySegment contextPtr = arena.allocate(Cubiomes.C_POINTER);
        int initialized = ominous
                ? Cubiomes.init_reward_ominous(contextPtr, mcVersion)
                : Cubiomes.init_reward(contextPtr, mcVersion);
        if (initialized == 0) {
            return null;
        }
        return contextPtr.get(ValueLayout.ADDRESS, 0).reinterpret(LootTableContext.sizeof());
    }

    private static List<String> readGeneratedItems(MemorySegment context, int mcVersion) {
        int count = Math.min(MAX_GENERATED_ITEMS, Math.max(0, LootTableContext.generated_item_count(context)));
        ArrayList<String> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            MemorySegment stack = ItemStack.asSlice(LootTableContext.generated_items(context), index);
            int globalId = Cubiomes.get_global_item_id(context, ItemStack.item(stack));
            MemorySegment itemName = Cubiomes.global_id2item_name(globalId, mcVersion);
            String name = itemName == null || itemName.address() == 0L ? "unknown_item_" + globalId : itemName.getString(0);
            int amount = Math.max(1, ItemStack.count(stack));
            items.add(amount == 1 ? name : name + " x" + amount);
        }
        return List.copyOf(items);
    }

    private static String formatState(MemorySegment xoroshiro) {
        return "%016x%016x".formatted(Xoroshiro.hi(xoroshiro), Xoroshiro.lo(xoroshiro));
    }

    public record VaultPrediction(int offset, boolean ominous, List<String> items, String state) {
    }
}
