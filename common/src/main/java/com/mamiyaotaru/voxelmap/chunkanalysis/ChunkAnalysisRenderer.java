package com.mamiyaotaru.voxelmap.chunkanalysis;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import com.mamiyaotaru.voxelmap.rendering.VoxelMapRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ChunkAnalysisRenderer {
    private static final Direction[] QUAD_DIRECTIONS = { null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST };

    private ChunkAnalysisRenderer() { }

    public static void render(PoseStack poseStack, SubmitNodeCollector collector, Camera camera) {
        ChunkAnalysisSettingsManager settings = VoxelConstants.getVoxelMapInstance().getChunkAnalysisOptions();
        if (settings == null || (!settings.ghostBlocks && !settings.espFill)) return;

        ChunkAnalysisSnapshot snapshot = ChunkAnalysisService.get().snapshot();
        List<ChunkAnalysisDifference> source = snapshot.differences();
        if (source.isEmpty()) return;
        boolean allUnexpected = snapshot.mode() == ChunkAnalysisService.ScanMode.ALL_UNEXPECTED;

        Vec3 cameraPos = camera.position();
        double maxDistanceSquared = allUnexpected ? Double.POSITIVE_INFINITY
                : settings.renderDistance * (double) settings.renderDistance;
        if (settings.ghostBlocks) {
            OrderedSubmitNodeCollector ghostCollector = collector.order(VoxelMapRenderTypes.OVERLAY_ORDER_ANALYSIS_GHOSTS);
            List<ChunkAnalysisDifference> expectedCandidates = source.stream()
                    .filter(difference -> difference.displayState() != null)
                    .toList();
            List<ChunkAnalysisDifference> visible = fairVisibleSample(expectedCandidates, cameraPos,
                    maxDistanceSquared, allUnexpected ? expectedCandidates.size() : settings.renderLimit);
            float opacity = (float) settings.ghostOpacity * flashMultiplier(settings.flashDetections);
            if (!visible.isEmpty()) {
                ghostCollector.submitCustomGeometry(poseStack, VoxelMapRenderTypes.CHUNK_ANALYSIS_BLOCK_GHOST,
                        (pose, buffer) -> drawBlockGhosts(buffer, pose, visible, cameraPos, opacity, settings));
            }
        }
        if (settings.espFill) {
            OrderedSubmitNodeCollector fillCollector = collector.order(VoxelMapRenderTypes.OVERLAY_ORDER_ESP_FILL);
            // When ghosts are active, keep solid fill off expected-state differences so it
            // cannot cover their textures. Blue/unexpected positions still need ESP fill
            // because the expected state there is air and therefore has no ghost model.
            List<ChunkAnalysisDifference> fillCandidates = settings.ghostBlocks
                    ? (allUnexpected ? source : source.stream().filter(difference -> difference.displayState() == null
                            || difference.kind() == ChunkAnalysisDifference.Kind.EXCAVATION).toList())
                    : source;
            List<ChunkAnalysisDifference> visible = fairVisibleSample(fillCandidates, cameraPos,
                    maxDistanceSquared, allUnexpected ? fillCandidates.size() : settings.renderLimit);
            float opacity = (float) settings.fillOpacity * flashMultiplier(settings.flashDetections);
            if (!visible.isEmpty()) {
                fillCollector.submitCustomGeometry(poseStack, VoxelMapRenderTypes.SEEDMAPPER_ESP_QUADS_NO_DEPTH,
                        (pose, buffer) -> drawEspFills(buffer, pose, visible, cameraPos, opacity, settings));
            }
        }
    }

    private static float flashMultiplier(boolean enabled) {
        if (!enabled) return 1.0F;
        return 0.35F + 0.65F * (0.5F + 0.5F * (float) Math.sin(System.nanoTime() * 0.000000012D));
    }

    /** Samples evenly through scan order, instead of dropping all chunks after an arbitrary prefix. */
    private static List<ChunkAnalysisDifference> fairVisibleSample(List<ChunkAnalysisDifference> source, Vec3 camera,
                                                                   double maxDistanceSquared, int limit) {
        int wanted = Math.max(1, Math.min(limit, source.size()));
        double stride = source.size() / (double) wanted;
        ArrayList<ChunkAnalysisDifference> result = new ArrayList<>(wanted);
        for (int sample = 0; sample < wanted; sample++) {
            ChunkAnalysisDifference difference = source.get(Math.min(source.size() - 1, (int) (sample * stride)));
            BlockPos pos = difference.pos();
            if (pos.distToCenterSqr(camera.x, camera.y, camera.z) <= maxDistanceSquared) result.add(difference);
        }
        return result;
    }

    private static void drawBlockGhosts(VertexConsumer buffer, PoseStack.Pose basePose,
                                        List<ChunkAnalysisDifference> differences, Vec3 camera, float opacity,
                                        ChunkAnalysisSettingsManager settings) {
        Minecraft minecraft = Minecraft.getInstance();
        ArrayList<BlockStateModelPart> parts = new ArrayList<>();
        QuadInstance quadData = new QuadInstance();
        quadData.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        quadData.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        for (ChunkAnalysisDifference difference : differences) {
            if (difference.displayState() == null || difference.displayState().isAir()) continue;
            parts.clear();
            minecraft.getModelManager().getBlockStateModelSet().get(difference.displayState())
                    .collectParts(RandomSource.create(difference.pos().asLong()), parts);

            PoseStack.Pose translated = basePose.copy();
            translated.pose().translate((float) (difference.pos().getX() - camera.x),
                    (float) (difference.pos().getY() - camera.y), (float) (difference.pos().getZ() - camera.z));
            for (BlockStateModelPart part : parts) {
                for (Direction direction : QUAD_DIRECTIONS) {
                    for (BakedQuad quad : part.getQuads(direction)) {
                        int nativeTint = 0xFFFFFF;
                        if (quad.materialInfo().isTinted()) {
                            BlockTintSource tintSource = minecraft.getBlockColors().getTintSource(
                                    difference.displayState(), quad.materialInfo().tintIndex());
                            if (tintSource != null) nativeTint = tintSource.color(difference.displayState());
                        }
                        quadData.setColor(softTint(nativeTint, colorFor(difference, settings), opacity));
                        buffer.putBakedQuad(translated, quad, quadData);
                    }
                }
            }
        }
    }

    private static int softTint(int nativeColor, int categoryColor, float alpha) {
        int red = (ARGB.red(nativeColor) * 3 + ARGB.red(categoryColor)) / 4;
        int green = (ARGB.green(nativeColor) * 3 + ARGB.green(categoryColor)) / 4;
        int blue = (ARGB.blue(nativeColor) * 3 + ARGB.blue(categoryColor)) / 4;
        return ARGB.colorFromFloat(alpha, red / 255.0F, green / 255.0F, blue / 255.0F);
    }

    private static void drawEspFills(VertexConsumer buffer, PoseStack.Pose pose,
                                     List<ChunkAnalysisDifference> differences, Vec3 camera, float opacity,
                                     ChunkAnalysisSettingsManager settings) {
        for (ChunkAnalysisDifference difference : differences) {
            drawBoxFaces(buffer, pose, difference.pos(), camera, colorFor(difference, settings), opacity);
        }
    }

    private static int colorFor(ChunkAnalysisDifference difference, ChunkAnalysisSettingsManager settings) {
        ChunkAnalysisDifference.Kind kind = difference.kind();
        String configured = switch (kind) {
            case MISSING_EXPECTED -> settings.missingExpectedColor;
            case EXCAVATION -> settings.excavationColor;
            case UNEXPECTED -> settings.unexpectedColor;
            case CHANGED -> settings.changedColor;
            case UNEXPECTED_INTERESTING -> difference.interestingCategory() == ChunkAnalysisDifference.InterestingCategory.REDSTONE
                    ? settings.redstoneColor
                    : difference.interestingCategory() == ChunkAnalysisDifference.InterestingCategory.WORKSTATIONS
                    ? settings.workstationsColor
                    : settings.inventoriesColor;
        };
        try {
            return Integer.parseInt(configured.substring(configured.charAt(0) == '#' ? 1 : 0), 16);
        } catch (RuntimeException ignored) {
            return kind.color();
        }
    }

    private static void drawBoxFaces(VertexConsumer buffer, PoseStack.Pose pose, BlockPos block, Vec3 camera, int color, float alpha) {
        float x0=(float)(block.getX()-camera.x), y0=(float)(block.getY()-camera.y), z0=(float)(block.getZ()-camera.z);
        float x1=x0+1, y1=y0+1, z1=z0+1;
        quad(buffer,pose,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,color,alpha);
        quad(buffer,pose,x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0,color,alpha);
        quad(buffer,pose,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,color,alpha);
        quad(buffer,pose,x1,y0,z0,x1,y1,z0,x1,y1,z1,x1,y0,z1,color,alpha);
        quad(buffer,pose,x0,y0,z0,x0,y1,z0,x1,y1,z0,x1,y0,z0,color,alpha);
        quad(buffer,pose,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,color,alpha);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose p, float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,int color,float alpha) {
        int packed=((int)(alpha*255)<<24)|(color&0xFFFFFF);
        vertex(b,p,ax,ay,az,packed); vertex(b,p,bx,by,bz,packed); vertex(b,p,cx,cy,cz,packed); vertex(b,p,dx,dy,dz,packed);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose p,float x,float y,float z,int color) {
        b.addVertex(p,x,y,z).setColor(color).setNormal(p,0,1,0);
    }
}
