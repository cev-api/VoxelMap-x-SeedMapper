package com.mamiyaotaru.voxelmap.rendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMapGeometryBatchTest {
    @Test void repeatedWideZoomsRetainEveryQuadBelowTheNativeVertexLimit() {
        // An exact wide view exceeds the 16,777,215 vertex limit even without shared layers.
        int wide = 3_111_160 + 2_054_525;
        assertTrue((long) wide * 4 > 16_777_215);
        for (int count : new int[]{wide, 176_109, wide, 90_620, wide, Integer.MAX_VALUE}) {
            int[] next = {0};
            WorldMapGeometryBatch.forEach(count, (first, length) -> {
                assertEquals(next[0], first); // Contiguous ranges: no skipped or duplicated detail.
                assertTrue(length > 0 && length <= WorldMapGeometryBatch.MAX_QUADS);
                assertTrue((long) length * 4 < 16_777_215);
                next[0] += length;
            });
            assertEquals(count, next[0]);
        }
    }
    @Test void exactBatchBoundariesAndEmptyLayersWork() {
        int max = WorldMapGeometryBatch.MAX_QUADS;
        List<Integer> sizes = new ArrayList<>();
        WorldMapGeometryBatch.forEach(0, (first, length) -> fail("Empty layer has no draws"));
        WorldMapGeometryBatch.forEach(max * 2 + 1, (first, length) -> sizes.add(length));
        assertEquals(List.of(max, max, 1), sizes);
        assertThrows(IllegalArgumentException.class, () -> WorldMapGeometryBatch.forEach(-1, (first, length) -> { }));
    }
    @Test void rangeBuildUsesOriginalSnapshotWithoutCopyingOrWritingOtherQuads() {
        float[] vertices = new float[24];
        for (int i = 0; i < vertices.length; i++) vertices[i] = i;
        var state = new WorldMapQuadRenderState(null, null, new Matrix3x2f(), vertices, 1, 1, 0xff123456, null, null);
        List<Float> written = new ArrayList<>();
        int[] colors = {0};
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            if (method.getName().equals("addVertex")) { written.add((Float) args[0]); written.add((Float) args[1]); }
            if (method.getName().equals("setColor")) { assertEquals(0xff123456, args[0]); colors[0]++; }
            return proxy;
        });
        state.buildVertices(consumer);
        assertSame(vertices, state.vertices());
        assertEquals(List.of(8F, 9F, 10F, 11F, 12F, 13F, 14F, 15F), written);
        assertEquals(4, colors[0]);
    }
    @Test void renderStateRejectsRangesThatCouldOverflowOrReadOutsideSnapshot() {
        float[] vertices = new float[8];
        assertThrows(IllegalArgumentException.class, () -> new WorldMapQuadRenderState(null, null, new Matrix3x2f(), vertices, 1, 1, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new WorldMapQuadRenderState(null, null, new Matrix3x2f(), vertices, -1, 1, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new WorldMapQuadRenderState(null, null, new Matrix3x2f(), vertices, 0, WorldMapGeometryBatch.MAX_QUADS + 1, 0, null, null));
    }
}
