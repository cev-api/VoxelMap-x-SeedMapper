package com.mamiyaotaru.voxelmap.seedmapper;

import com.mamiyaotaru.voxelmap.util.VoxelMapRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static volatile boolean geometryDirty = true;
    private static Map<StyleKey, SeedMapperEspStyleSnapshot> cachedStyles = Map.of();
    private static RenderGeometry cachedGeometry = RenderGeometry.EMPTY;

    private SeedMapperEspManager() {
    }

    public static void setHighlightTimeoutMinutes(double minutes) {
        timeoutMs = Math.max(0L, (long) Math.round(minutes * 60_000.0D));
    }

    public static void clear() {
        HIGHLIGHTS.clear();
        geometryDirty = true;
    }

    public static void drawBoxes(SeedMapperEspTarget target, Iterable<BlockPos> blocks, int fallbackColor) {
        long now = System.currentTimeMillis();
        for (BlockPos pos : blocks) {
            HIGHLIGHTS.add(new HighlightBox(pos.immutable(), fallbackColor & 0x00FFFFFF, target, now));
        }
        geometryDirty = true;
    }

    public static void drawBoxes(Iterable<BlockPos> blocks, int fallbackColor) {
        drawBoxes(SeedMapperEspTarget.BLOCK_HIGHLIGHT, blocks, fallbackColor);
    }

    public static void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Camera camera, SeedMapperSettingsManager settings) {
        if (!settings.espEnabled) {
            return;
        }
        setHighlightTimeoutMinutes(settings.espTimeoutMinutes);
        long now = System.currentTimeMillis();
        if (timeoutMs == 0L) {
            HIGHLIGHTS.clear();
            return;
        }
        if (HIGHLIGHTS.removeIf(box -> now - box.createdMs() > timeoutMs)) {
            geometryDirty = true;
        }
        if (HIGHLIGHTS.isEmpty()) {
            return;
        }

        Map<StyleKey, SeedMapperEspStyleSnapshot> styles = new HashMap<>();
        boolean hasRainbow = false;
        for (HighlightBox box : HIGHLIGHTS) {
            StyleKey key = new StyleKey(box.target(), box.fallbackColor());
            SeedMapperEspStyleSnapshot snapshot = styles.computeIfAbsent(key, ignored -> settings.getEspStyleSnapshot(box.target(), box.fallbackColor()));
            hasRainbow |= snapshot.rainbow();
        }

        if (geometryDirty || hasRainbow || !styles.equals(cachedStyles)) {
            Set<StyledBlock> styledBlocks = new HashSet<>();
            for (HighlightBox box : HIGHLIGHTS) {
                SeedMapperEspStyleSnapshot snapshot = styles.get(new StyleKey(box.target(), box.fallbackColor()));
                int outline = resolveColor(snapshot.outlineColor(), snapshot.rainbow(), snapshot.rainbowSpeed(), box.pos(), now);
                int fill = resolveColor(snapshot.fillColor(), snapshot.rainbow(), snapshot.rainbowSpeed(), box.pos().offset(3, 1, 5), now);
                styledBlocks.add(new StyledBlock(box.pos(), outline, snapshot.outlineAlpha(), snapshot.fillEnabled(), fill, snapshot.fillAlpha()));
            }
            cachedGeometry = buildGeometry(styledBlocks);
            cachedStyles = hasRainbow ? Map.of() : Map.copyOf(styles);
            geometryDirty = false;
        }

        renderGeometry(poseStack, submitNodeCollector, camera, cachedGeometry);
    }

    private static RenderGeometry buildGeometry(Set<StyledBlock> styledBlocks) {
        if (styledBlocks.isEmpty()) {
            return RenderGeometry.EMPTY;
        }
        Map<EdgeStyle, Map<EdgeKey, EdgeAccumulator>> styleEdgeMaps = new HashMap<>();
        List<FillFace> fillFaces = new ArrayList<>();
        Set<BlockPos> filledBlocks = new HashSet<>();

        for (StyledBlock block : styledBlocks) {
            if (block.fillEnabled() && block.fillAlpha() > 0.0F) {
                filledBlocks.add(block.pos());
            }
        }

        for (StyledBlock block : styledBlocks) {
            EdgeStyle edgeStyle = new EdgeStyle(block.outlineColor(), block.outlineAlpha());
            Map<EdgeKey, EdgeAccumulator> edgeMap = styleEdgeMaps.computeIfAbsent(edgeStyle, ignored -> new HashMap<>());
            addEdgesForBlock(edgeMap, block.pos(), edgeStyle);

            if (block.fillEnabled() && block.fillAlpha() > 0.0F) {
                Vec3 min = Vec3.atLowerCornerOf(block.pos());
                Vec3 max = min.add(1.0, 1.0, 1.0);
                Vec3[] p = new Vec3[] {
                        new Vec3(min.x, min.y, min.z), new Vec3(max.x, min.y, min.z), new Vec3(max.x, min.y, max.z), new Vec3(min.x, min.y, max.z),
                        new Vec3(min.x, max.y, min.z), new Vec3(max.x, max.y, min.z), new Vec3(max.x, max.y, max.z), new Vec3(min.x, max.y, max.z)
                };
                addVisibleFillFaces(fillFaces, filledBlocks, block, p);
            }
        }

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<EdgeStyle, Map<EdgeKey, EdgeAccumulator>> styleEntry : styleEdgeMaps.entrySet()) {
            EdgeStyle style = styleEntry.getKey();
            styleEntry.getValue().forEach((key, accumulator) -> {
                if (accumulator.styles().isEmpty()) return;
                Vec3 start = new Vec3(key.x, key.y, key.z);
                Vec3 end = switch (key.axis) {
                    case AXIS_X -> start.add(1.0D, 0.0D, 0.0D);
                    case AXIS_Y -> start.add(0.0D, 1.0D, 0.0D);
                    case AXIS_Z -> start.add(0.0D, 0.0D, 1.0D);
                    default -> start;
                };
                lines.add(new Line(start, end, style.color(), style.alpha()));
            });
        }
        return new RenderGeometry(lines, fillFaces);
    }

    private static void renderGeometry(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Camera camera, RenderGeometry geometry) {
        Vec3 cameraPos = camera.position();
        if (!geometry.fillFaces().isEmpty()) {
            List<FillFace> sortedFillFaces = new ArrayList<>(geometry.fillFaces());
            // With an always-pass depth writer, render far faces first so the nearest fill surface
            // remains in the overlay depth buffer for the outline pass.
            sortedFillFaces.sort(Comparator.comparingDouble(face -> -face.distanceToSqr(cameraPos)));
            submitNodeCollector.submitCustomGeometry(poseStack, VoxelMapRenderTypes.SEEDMAPPER_QUADS_NO_DEPTH, (pose, fillBuffer) -> {
                for (FillFace face : sortedFillFaces) drawQuadFill(fillBuffer, pose, face, cameraPos);
            });
        }
        submitNodeCollector.submitCustomGeometry(poseStack, VoxelMapRenderTypes.SEEDMAPPER_LINES_NO_DEPTH, (pose, lineBuffer) -> {
            for (Line line : geometry.lines()) drawLine(lineBuffer, pose, line, cameraPos);
        });
    }

    private static int resolveColor(int baseColor, boolean rainbow, float rainbowSpeed, BlockPos pos, long now) {
        if (!rainbow) {
            return baseColor;
        }
        float hue = ((now / 1000.0F) * rainbowSpeed * 0.2F + ((pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13) & 255) / 255.0F) % 1.0F;
        return Color.HSBtoRGB(hue, 0.9F, 1.0F);
    }

    private static void drawLine(VertexConsumer buffer, PoseStack.Pose pose, Line line, Vec3 cameraPos) {
        Vec3 start = line.start();
        Vec3 end = line.end();
        Vec3 normal = end.subtract(start).normalize();
        float red = ARGB.redFloat(line.color());
        float green = ARGB.greenFloat(line.color());
        float blue = ARGB.blueFloat(line.color());
        buffer.addVertex(pose, (float) (start.x - cameraPos.x), (float) (start.y - cameraPos.y), (float) (start.z - cameraPos.z))
                .setColor(red, green, blue, line.alpha())
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                .setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, (float) (end.x - cameraPos.x), (float) (end.y - cameraPos.y), (float) (end.z - cameraPos.z))
                .setColor(red, green, blue, line.alpha())
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
                .setLineWidth(LINE_WIDTH);
    }

    private static void drawQuadFill(VertexConsumer buffer, PoseStack.Pose pose, FillFace face, Vec3 cameraPos) {
        int rgb = face.color & 0x00FFFFFF;
        int a = Math.max(0, Math.min(255, Math.round(face.alpha * 255.0f)));
        int packed = (a << 24) | rgb;
        Vec3 u = face.b.subtract(face.a);
        Vec3 v = face.c.subtract(face.a);
        Vec3 normal = u.cross(v).normalize();
        addFillVertex(buffer, pose, face.a, cameraPos, packed, normal); addFillVertex(buffer, pose, face.b, cameraPos, packed, normal);
        addFillVertex(buffer, pose, face.c, cameraPos, packed, normal); addFillVertex(buffer, pose, face.d, cameraPos, packed, normal);
        addFillVertex(buffer, pose, face.d, cameraPos, packed, normal); addFillVertex(buffer, pose, face.c, cameraPos, packed, normal);
        addFillVertex(buffer, pose, face.b, cameraPos, packed, normal); addFillVertex(buffer, pose, face.a, cameraPos, packed, normal);
    }

    private static void addFillVertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 vertex, Vec3 cameraPos, int color, Vec3 normal) {
        buffer.addVertex(pose, (float) (vertex.x - cameraPos.x), (float) (vertex.y - cameraPos.y), (float) (vertex.z - cameraPos.z))
                .setColor(color).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /**
     * Adjacent highlighted blocks form one solid volume. Rendering their shared faces
     * stacks transparent fill and needlessly multiplies the vertex load.
     */
    private static void addVisibleFillFaces(List<FillFace> fillFaces, Set<BlockPos> filledBlocks, StyledBlock block, Vec3[] p) {
        BlockPos pos = block.pos();
        int color = block.fillColor();
        float alpha = block.fillAlpha();

        if (!filledBlocks.contains(pos.below())) fillFaces.add(new FillFace(p[0], p[1], p[2], p[3], color, alpha));
        if (!filledBlocks.contains(pos.above())) fillFaces.add(new FillFace(p[4], p[5], p[6], p[7], color, alpha));
        if (!filledBlocks.contains(pos.west())) fillFaces.add(new FillFace(p[0], p[4], p[7], p[3], color, alpha));
        if (!filledBlocks.contains(pos.east())) fillFaces.add(new FillFace(p[1], p[5], p[6], p[2], color, alpha));
        if (!filledBlocks.contains(pos.north())) fillFaces.add(new FillFace(p[0], p[1], p[5], p[4], color, alpha));
        if (!filledBlocks.contains(pos.south())) fillFaces.add(new FillFace(p[3], p[2], p[6], p[7], color, alpha));
    }

    private static void addEdgesForBlock(Map<EdgeKey, EdgeAccumulator> edges, BlockPos pos, EdgeStyle style) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_X, pos), style);
        toggleEdge(edges, new EdgeKey(x, y, z + 1, AXIS_X, pos), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z, AXIS_X, pos), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z + 1, AXIS_X, pos), style);
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_Y, pos), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z, AXIS_Y, pos), style);
        toggleEdge(edges, new EdgeKey(x, y, z + 1, AXIS_Y, pos), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z + 1, AXIS_Y, pos), style);
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_Z, pos), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z, AXIS_Z, pos), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z, AXIS_Z, pos), style);
        toggleEdge(edges, new EdgeKey(x + 1, y + 1, z, AXIS_Z, pos), style);
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
        double distanceToSqr(Vec3 point) {
            double x = (a.x + b.x + c.x + d.x) * 0.25D - point.x;
            double y = (a.y + b.y + c.y + d.y) * 0.25D - point.y;
            double z = (a.z + b.z + c.z + d.z) * 0.25D - point.z;
            return x * x + y * y + z * z;
        }
    }

    /** Owner prevents neighbouring highlighted blocks from merging into one outline. */
    private record EdgeKey(int x, int y, int z, byte axis, BlockPos owner) {
    }

    private record EdgeStyle(int color, float alpha) {
    }

    private record StyleKey(SeedMapperEspTarget target, int fallbackColor) {
    }

    private record RenderGeometry(List<Line> lines, List<FillFace> fillFaces) {
        private static final RenderGeometry EMPTY = new RenderGeometry(List.of(), List.of());
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
