package com.mamiyaotaru.voxelmap.mixins;

import com.mamiyaotaru.voxelmap.chunkanalysis.ChunkAnalysisRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.levelgen.feature.treedecorators.PaleMossDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Mixin(PaleMossDecorator.class)
public abstract class MixinPaleMossDecoratorChunkAnalysis {
    @Shadow @Final private float leavesProbability;
    @Shadow @Final private float trunkProbability;
    @Shadow @Final private float groundProbability;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void voxelmap$placeWithoutServerLevel(TreeDecorator.Context context, CallbackInfo ci) {
        if (!ChunkAnalysisRuntime.active()) return;
        RandomSource random = context.random();
        List<BlockPos> logs = Util.shuffledCopy(context.logs(), random);
        if (!logs.isEmpty()) {
            BlockPos origin = Collections.min(logs, Comparator.comparingInt(Vec3i::getY));
            if (random.nextFloat() < this.groundProbability) {
                context.level().registryAccess().lookup(Registries.FEATURE)
                        .flatMap(registry -> registry.get(VegetationFeatures.PALE_MOSS_PATCH))
                        .ifPresent(feature -> feature.value().place(context.level(), ChunkAnalysisRuntime.generator(), random, origin.above()));
            }
            context.logs().forEach(pos -> maybeHang(pos, this.trunkProbability, context));
            context.leaves().forEach(pos -> maybeHang(pos, this.leavesProbability, context));
        }
        ci.cancel();
    }

    private static void maybeHang(BlockPos source, float probability, TreeDecorator.Context context) {
        if (context.random().nextFloat() >= probability || !context.isAir(source.below())) return;
        BlockPos pos = source.below();
        while (context.isAir(pos.below()) && context.random().nextFloat() >= 0.5F) {
            context.setBlock(pos, Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, false));
            pos = pos.below();
        }
        context.setBlock(pos, Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, true));
    }
}
