package com.mamiyaotaru.voxelmap.mixins;

import com.mamiyaotaru.voxelmap.chunkanalysis.ChunkAnalysisRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.TemplateFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TemplateFeature.class)
public abstract class MixinTemplateFeatureChunkAnalysis {
    @Shadow
    private Vec3i getRotatedOffset(Rotation rotation, Direction.Axis axis, StructureTemplate template) {
        throw new AssertionError();
    }

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void voxelmap$placeWithoutServerLevel(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!ChunkAnalysisRuntime.active()) return;
        TemplateFeature feature = (TemplateFeature) (Object) this;
        TemplateFeature.TemplateEntry entry = feature.templates().getRandomOrThrow(random);
        Rotation rotation = Util.getRandom(entry.rotations(), random);
        StructureTemplate template = ChunkAnalysisRuntime.templates().getOrCreate(entry.template());
        BlockPos pos = origin
                .offset(this.getRotatedOffset(rotation, Direction.Axis.X, template))
                .offset(this.getRotatedOffset(rotation, Direction.Axis.Z, template));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRandom(random);
        feature.processors().ifPresent(processors -> processors.value().list().forEach(settings::addProcessor));
        cir.setReturnValue(template.placeInWorld(level, pos, pos, settings, random, 3));
    }
}
