package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.util.VoxelMapRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SeedMapperEspManager {
    private static final byte AXIS_X = 0;
    private static final byte AXIS_Y = 1;
    private static final byte AXIS_Z = 2;
    private static final float LINE_WIDTH = 2.0F;
    private static final Set<HighlightBox> HIGHLIGHTS = ConcurrentHashMap.newKeySet();
    private static final long DEFAULT_TIMEOUT_MS = 5L * 60L * 1000L;
    private static volatile long timeoutMs = DEFAULT_TIMEOUT_MS;

    private SeedMapperEspManager() {
    }

    public static void setHighlightTimeoutMinutes(double minutes) {
        timeoutMs = Math.max(0L, (long) Math.round(minutes * 60_000.0D));
    }

    public static void clear() {
        HIGHLIGHTS.clear();
    }

    public static void drawBoxes(SeedMapperEspTarget target, Iterable<BlockPos> blocks, int fallbackColor) {
        long now = System.currentTimeMillis();
        for (BlockPos pos : blocks) {
            HIGHLIGHTS.add(new HighlightBox(pos.immutable(), fallbackColor & 0x00FFFFFF, target, now));
        }
    }

    public static void drawBoxes(Iterable<BlockPos> blocks, int fallbackColor) {
        drawBoxes(SeedMapperEspTarget.BLOCK_HIGHLIGHT, blocks, fallbackColor);
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera, SeedMapperSettingsManager settings) {
        if (!settings.espEnabled) {
            return;
        }
        setHighlightTimeoutMinutes(settings.espTimeoutMinutes);
        long now = System.currentTimeMillis();
        if (timeoutMs == 0L) {
            HIGHLIGHTS.clear();
            return;
        }
        HIGHLIGHTS.removeIf(box -> now - box.createdMs() > timeoutMs);
        if (HIGHLIGHTS.isEmpty()) {
            return;
        }

        Set<StyledBlock> styledBlocks = new HashSet<>();
        for (HighlightBox box : HIGHLIGHTS) {
            SeedMapperEspStyleSnapshot snapshot = settings.getEspStyleSnapshot(box.target(), box.fallbackColor());
            int outline = resolveColor(snapshot.outlineColor(), snapshot.rainbow(), snapshot.rainbowSpeed(), box.pos(), now);
            int fill = resolveColor(snapshot.fillColor(), snapshot.rainbow(), snapshot.rainbowSpeed(), box.pos().offset(3, 1, 5), now);
            styledBlocks.add(new StyledBlock(box.pos(), outline, snapshot.outlineAlpha(), snapshot.fillEnabled(), fill, snapshot.fillAlpha()));
        }

        renderStyledBoxes(poseStack, bufferSource, camera, styledBlocks);
    }

    private static int resolveColor(int baseColor, boolean rainbow, float rainbowSpeed, BlockPos pos, long now) {
        if (!rainbow) {
            return baseColor;
        }
        float hue = ((now / 1000.0F) * rainbowSpeed * 0.2F + ((pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13) & 255) / 255.0F) % 1.0F;
        return Color.HSBtoRGB(hue, 0.9F, 1.0F);
    }

    private static void renderStyledBoxes(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera, Set<StyledBlock> styledBlocks) {
        if (styledBlocks.isEmpty()) {
            return;
        }

        Map<EdgeStyle, Map<EdgeKey, EdgeAccumulator>> styleEdgeMaps = new HashMap<>();
        List<FillFace> fillFaces = new ArrayList<>();
        Vec3 cameraPos = camera.position();

        for (StyledBlock block : styledBlocks) {
            EdgeStyle edgeStyle = new EdgeStyle(block.outlineColor(), block.outlineAlpha());
            Map<EdgeKey, EdgeAccumulator> edgeMap = styleEdgeMaps.computeIfAbsent(edgeStyle, ignored -> new HashMap<>());
            addEdgesForBlock(edgeMap, block.pos(), edgeStyle);

            if (block.fillEnabled() && block.fillAlpha() > 0.0F) {
                Vec3 min = Vec3.atLowerCornerOf(block.pos()).subtract(cameraPos);
                Vec3 max = min.add(1.0, 1.0, 1.0);
                Vec3[] p = new Vec3[] {
                        new Vec3(min.x, min.y, min.z),
                        new Vec3(max.x, min.y, min.z),
                        new Vec3(max.x, min.y, max.z),
                        new Vec3(min.x, min.y, max.z),
                        new Vec3(min.x, max.y, min.z),
                        new Vec3(max.x, max.y, min.z),
                        new Vec3(max.x, max.y, max.z),
                        new Vec3(min.x, max.y, max.z)
                };
                fillFaces.add(new FillFace(p[0], p[1], p[2], p[3], block.fillColor(), block.fillAlpha()));
                fillFaces.add(new FillFace(p[4], p[5], p[6], p[7], block.fillColor(), block.fillAlpha()));
                fillFaces.add(new FillFace(p[0], p[4], p[7], p[3], block.fillColor(), block.fillAlpha()));
                fillFaces.add(new FillFace(p[1], p[5], p[6], p[2], block.fillColor(), block.fillAlpha()));
                fillFaces.add(new FillFace(p[0], p[1], p[5], p[4], block.fillColor(), block.fillAlpha()));
                fillFaces.add(new FillFace(p[3], p[2], p[6], p[7], block.fillColor(), block.fillAlpha()));
            }
        }

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<EdgeStyle, Map<EdgeKey, EdgeAccumulator>> styleEntry : styleEdgeMaps.entrySet()) {
            EdgeStyle style = styleEntry.getKey();
            styleEntry.getValue().forEach((key, accumulator) -> {
                if (accumulator.styles().isEmpty()) {
                    return;
                }
                Vec3 worldStart = new Vec3(key.x, key.y, key.z);
                Vec3 worldEnd = switch (key.axis) {
                    case AXIS_X -> worldStart.add(1.0D, 0.0D, 0.0D);
                    case AXIS_Y -> worldStart.add(0.0D, 1.0D, 0.0D);
                    case AXIS_Z -> worldStart.add(0.0D, 0.0D, 1.0D);
                    default -> worldStart;
                };
                Vec3 start = worldStart.subtract(cameraPos);
                Vec3 end = worldEnd.subtract(cameraPos);
                lines.add(new Line(start, end, style.color(), style.alpha()));
            });
        }

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer lineBuffer = bufferSource.getBuffer(VoxelMapRenderTypes.SEEDMAPPER_LINES_NO_DEPTH);
        for (Line line : lines) {
            drawLine(lineBuffer, pose, line);
        }
        bufferSource.endBatch(VoxelMapRenderTypes.SEEDMAPPER_LINES_NO_DEPTH);

        if (!fillFaces.isEmpty()) {
            VertexConsumer fillBuffer = bufferSource.getBuffer(VoxelMapRenderTypes.SEEDMAPPER_QUADS_NO_DEPTH);
            for (FillFace face : fillFaces) {
                drawQuadFill(fillBuffer, pose, face);
            }
            bufferSource.endBatch(VoxelMapRenderTypes.SEEDMAPPER_QUADS_NO_DEPTH);
        }
    }

    private static void drawLine(VertexConsumer buffer, PoseStack.Pose pose, Line line) {
        Vec3 start = line.start();
        Vec3 end = line.end();
        Vec3 normal = end.subtract(start).normalize();
        float red = ARGB.redFloat(line.color());
        float green = ARGB.greenFloat(line.color());
        float blue = ARGB.blueFloat(line.color());
        buffer.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, line.alpha())
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                .setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, line.alpha())
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                .setLineWidth(LINE_WIDTH);
    }

    private static void drawQuadFill(VertexConsumer buffer, PoseStack.Pose pose, FillFace face) {
        int rgb = face.color & 0x00FFFFFF;
        int a = Math.max(0, Math.min(255, Math.round(face.alpha * 255.0f)));
        int packed = (a << 24) | rgb;
        Vec3 u = face.b.subtract(face.a);
        Vec3 v = face.c.subtract(face.a);
        Vec3 normal = u.cross(v).normalize();
        buffer.addVertex(pose, (float) face.a.x, (float) face.a.y, (float) face.a.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) face.b.x, (float) face.b.y, (float) face.b.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) face.c.x, (float) face.c.y, (float) face.c.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) face.d.x, (float) face.d.y, (float) face.d.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) face.d.x, (float) face.d.y, (float) face.d.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) face.c.x, (float) face.c.y, (float) face.c.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) face.b.x, (float) face.b.y, (float) face.b.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) face.a.x, (float) face.a.y, (float) face.a.z).setColor(packed).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void addEdgesForBlock(Map<EdgeKey, EdgeAccumulator> edges, BlockPos pos, EdgeStyle style) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_X), style);
        toggleEdge(edges, new EdgeKey(x, y, z + 1, AXIS_X), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z, AXIS_X), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z + 1, AXIS_X), style);
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_Y), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z, AXIS_Y), style);
        toggleEdge(edges, new EdgeKey(x, y, z + 1, AXIS_Y), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z + 1, AXIS_Y), style);
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_Z), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z, AXIS_Z), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z, AXIS_Z), style);
        toggleEdge(edges, new EdgeKey(x + 1, y + 1, z, AXIS_Z), style);
    }

    private static void toggleEdge(Map<EdgeKey, EdgeAccumulator> edges, EdgeKey key, EdgeStyle style) {
        EdgeAccumulator acc = edges.computeIfAbsent(key, ignored -> new EdgeAccumulator());
        acc.toggle(style);
        if (acc.isEmpty()) {
            edges.remove(key);
        }
    }

    private record Line(Vec3 start, Vec3 end, int color, float alpha) {
    }

    private record FillFace(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
    }

    private record EdgeKey(int x, int y, int z, byte axis) {
    }

    private record EdgeStyle(int color, float alpha) {
    }

    private static final class EdgeAccumulator {
        private final Set<EdgeStyle> styles = new HashSet<>();

        void toggle(EdgeStyle style) {
            if (!this.styles.add(style)) {
                this.styles.remove(style);
            }
        }

        boolean isEmpty() {
            return this.styles.isEmpty();
        }

        Set<EdgeStyle> styles() {
            return this.styles;
        }
    }

    private record HighlightBox(BlockPos pos, int fallbackColor, SeedMapperEspTarget target, long createdMs) {
    }

    private record StyledBlock(BlockPos pos, int outlineColor, float outlineAlpha, boolean fillEnabled, int fillColor, float fillAlpha) {
    }
}
