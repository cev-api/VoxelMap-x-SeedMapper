package com.mamiyaotaru.voxelmap.mixins;

import com.mamiyaotaru.voxelmap.chunkanalysis.ChunkAnalysisRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FossilFeature;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FossilFeature.class)
public abstract class MixinFossilFeatureChunkAnalysis {
    @Shadow
    private static int countEmptyCorners(WorldGenLevel level, BoundingBox box) {
        throw new AssertionError();
    }

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void voxelmap$placeWithoutServerLevel(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!ChunkAnalysisRuntime.active()) return;
        FossilFeature config = (FossilFeature) (Object) this;
        Rotation rotation = Rotation.getRandom(random);
        int index = random.nextInt(config.fossilStructures().size());
        StructureTemplate base = ChunkAnalysisRuntime.templates().getOrCreate(config.fossilStructures().get(index));
        StructureTemplate overlay = ChunkAnalysisRuntime.templates().getOrCreate(config.overlayStructures().get(index));
        ChunkPos chunkPos = ChunkPos.containing(origin);
        BoundingBox bounds = new BoundingBox(chunkPos.getMinBlockX() - 16, level.getMinY(), chunkPos.getMinBlockZ() - 16,
                chunkPos.getMaxBlockX() + 16, level.getMaxY(), chunkPos.getMaxBlockZ() + 16);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setBoundingBox(bounds).setRandom(random);
        Vec3i size = base.getSize(rotation);
        BlockPos lowCorner = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        int lowest = origin.getY();
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                lowest = Math.min(lowest, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lowCorner.getX() + x, lowCorner.getZ() + z));
            }
        }
        int targetY = Math.max(lowest - 15 - random.nextInt(10), level.getMinY() + 10);
        BlockPos target = base.getZeroPositionWithTransform(lowCorner.atY(targetY), Mirror.NONE, rotation);
        if (countEmptyCorners(level, base.getBoundingBox(settings, target)) > config.maxEmptyCornersAllowed()) {
            cir.setReturnValue(false);
            return;
        }
        settings.clearProcessors();
        config.fossilProcessors().value().list().forEach(settings::addProcessor);
        base.placeInWorld(level, target, target, settings, random, 260);
        settings.clearProcessors();
        config.overlayProcessors().value().list().forEach(settings::addProcessor);
        overlay.placeInWorld(level, target, target, settings, random, 260);
        cir.setReturnValue(true);
    }
}
