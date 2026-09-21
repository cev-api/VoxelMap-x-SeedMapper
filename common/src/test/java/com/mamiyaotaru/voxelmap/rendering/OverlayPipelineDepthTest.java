package com.mamiyaotaru.voxelmap.rendering;

import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ESP and world-overlay pipelines must ignore and not write depth.
 *
 * <p>These pipelines are built from vanilla debug snippets that are themselves
 * depth tested. If the snippet's depth state ever wins over the explicit state
 * the overlay geometry is drawn normally and gets hidden by terrain, which is
 * exactly the "only visible when standing under the ground" symptom. This test
 * pins the behaviour so that regression cannot silently come back.</p>
 */
class OverlayPipelineDepthTest {
    @Test
    void noDepthPipelinesIgnoreAndDoNotWriteDepth() {
        assertNoDepth("LINES_NO_DEPTH", VoxelMapPipelines.LINES_NO_DEPTH);
        assertNoDepth("QUADS_NO_DEPTH", VoxelMapPipelines.QUADS_NO_DEPTH);
        assertNoDepth("BLOCK_GHOST_NO_DEPTH", VoxelMapPipelines.BLOCK_GHOST_NO_DEPTH);
    }

    private static void assertNoDepth(String name, RenderPipeline pipeline) {
        DepthStencilState state = pipeline.getDepthStencilState();
        assertEquals(CompareOp.ALWAYS_PASS, state.depthTest(),
                name + " must always pass the depth test or terrain hides the overlay");
        assertFalse(state.writeDepth(),
                name + " must not write depth or it corrupts later overlay passes");
    }

    @Test
    void espRenderTypesUseTheNoDepthPipelines() {
        assertEquals(CompareOp.ALWAYS_PASS,
                VoxelMapRenderTypes.SEEDMAPPER_ESP_QUADS_NO_DEPTH.pipeline().getDepthStencilState().depthTest());
        assertEquals(CompareOp.ALWAYS_PASS,
                VoxelMapRenderTypes.SEEDMAPPER_ESP_LINES_NO_DEPTH.pipeline().getDepthStencilState().depthTest());
        assertEquals(CompareOp.ALWAYS_PASS,
                VoxelMapRenderTypes.CHUNK_ANALYSIS_BLOCK_GHOST.pipeline().getDepthStencilState().depthTest());
    }

    /**
     * SubmitNodeCollection routes custom geometry by simple flags:
     * outline -> outline phase, blending -> translucentCustomGeometry phase,
     * otherwise -> the SOLID phase, which is drawn together with opaque terrain.
     * A non-blending overlay can therefore be overdrawn by terrain even though
     * its pipeline ignores depth.
     */
    @Test
    void overlayRenderTypesAreNotRoutedIntoTheSolidPhase() {
        assertNotSolid("SEEDMAPPER_ESP_QUADS_NO_DEPTH", VoxelMapRenderTypes.SEEDMAPPER_ESP_QUADS_NO_DEPTH);
        assertNotSolid("SEEDMAPPER_ESP_LINES_NO_DEPTH", VoxelMapRenderTypes.SEEDMAPPER_ESP_LINES_NO_DEPTH);
        assertNotSolid("SEEDMAPPER_QUADS_NO_DEPTH", VoxelMapRenderTypes.SEEDMAPPER_QUADS_NO_DEPTH);
        assertNotSolid("SEEDMAPPER_LINES_NO_DEPTH", VoxelMapRenderTypes.SEEDMAPPER_LINES_NO_DEPTH);
        assertNotSolid("CHUNK_ANALYSIS_BLOCK_GHOST", VoxelMapRenderTypes.CHUNK_ANALYSIS_BLOCK_GHOST);
    }

    private static void assertNotSolid(String name, net.minecraft.client.renderer.rendertype.RenderType renderType) {
        System.out.println(name + ": outline=" + renderType.isOutline() + " blending=" + renderType.hasBlending()
                + " pipelineColorTargets=" + renderType.pipeline().getColorTargetStates().size());
        assertFalse(renderType.isOutline(), name + " must not be treated as an entity outline");
        assertTrue(renderType.hasBlending(),
                name + " must declare a blending color target, otherwise it is drawn in the solid phase with terrain");
    }
}
