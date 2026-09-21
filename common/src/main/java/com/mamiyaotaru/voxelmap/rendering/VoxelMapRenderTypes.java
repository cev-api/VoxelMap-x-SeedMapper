package com.mamiyaotaru.voxelmap.rendering;

import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Util;

public class VoxelMapRenderTypes {
    /**
     * Submission orders for the world-space overlay layers. Higher orders are
     * rendered later, so they end up on top of the lower ones. The ESP and
     * chunk-analysis layers deliberately stay below the waypoint labels: the
     * no-depth pipelines now draw over all terrain, and without this split
     * their translucent fills would wash out the labels.
     */
    public static final int OVERLAY_ORDER_ESP_FILL = Integer.MAX_VALUE - 4;
    public static final int OVERLAY_ORDER_ESP_LINES = Integer.MAX_VALUE - 3;
    public static final int OVERLAY_ORDER_ANALYSIS_GHOSTS = Integer.MAX_VALUE - 2;
    public static final int OVERLAY_ORDER_WAYPOINT_ICONS = Integer.MAX_VALUE - 1;
    public static final int OVERLAY_ORDER_WAYPOINT_TEXT = Integer.MAX_VALUE;

    public static final RenderType WAYPOINT_TEXT_BACKGROUND = RenderType.create(
            "voxelmap_overlay_background", RenderSetup.builder(VoxelMapPipelines.WAYPOINT_TEXT_BACKGROUND).createRenderSetup());
    public static final Function<Identifier, RenderType> GUI_TEXTURED_GEQUAL_DEPTH = Util.memoize(
            identifier -> RenderType.create(
                    "voxelmap_gui_textured_gequal_gepth",
                    RenderSetup.builder(VoxelMapPipelines.GUI_TEXTURED_GEQUAL_DEPTH)
                            .withTexture("Sampler0", identifier)
                            .createRenderSetup()
            )
    );

    public static final Function<Identifier, RenderType> GUI_TEXTURED_ANY_DEPTH = Util.memoize(
            identifier -> RenderType.create(
                    "voxelmap_gui_textured_any_depth",
                    RenderSetup.builder(VoxelMapPipelines.GUI_TEXTURED_ANY_DEPTH)
                            .withTexture("Sampler0", identifier)
                            .createRenderSetup()
            )
    );

    public static final Function<Identifier, RenderType> GUI_TEXTURED_ANY_DEPTH_MASKED = Util.memoize(
            identifier -> RenderType.create(
                    "voxelmap_gui_textured_any_depth_masked",
                    RenderSetup.builder(VoxelMapPipelines.GUI_TEXTURED_ANY_DEPTH_MASKED)
                            .withTexture("Sampler0", identifier)
                            .createRenderSetup()
            )
    );

    public static final RenderType TEXT_BACKGROUND_GEQUAL_DEPTH = RenderType.create(
            "voxelmap_text_background_gequal_depth",
            RenderSetup.builder(VoxelMapPipelines.TEXT_BACKGROUND_GEQUAL_DEPTH)
                    .createRenderSetup()
    );

    public static final RenderType TEXT_BACKGROUND_ANY_DEPTH = RenderType.create(
            "voxelmap_text_background_any_depth",
            RenderSetup.builder(VoxelMapPipelines.TEXT_BACKGROUND_ANY_DEPTH)
                    .createRenderSetup()
    );

    public static final RenderType SEEDMAPPER_LINES_NO_DEPTH = RenderType.create(
            "voxelmap_seedmapper_lines_no_depth",
            RenderSetup.builder(VoxelMapPipelines.LINES_NO_DEPTH)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    public static final RenderType SEEDMAPPER_QUADS_NO_DEPTH = RenderType.create(
            "voxelmap_seedmapper_quads_no_depth",
            RenderSetup.builder(VoxelMapPipelines.QUADS_NO_DEPTH)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    public static final RenderType SEEDMAPPER_ESP_LINES_NO_DEPTH = RenderType.create(
            "voxelmap_seedmapper_esp_lines_no_depth",
            RenderSetup.builder(VoxelMapPipelines.LINES_NO_DEPTH)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    public static final RenderType SEEDMAPPER_ESP_QUADS_NO_DEPTH = RenderType.create(
            "voxelmap_seedmapper_esp_quads_no_depth",
            RenderSetup.builder(VoxelMapPipelines.QUADS_NO_DEPTH)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    public static final RenderType CHUNK_ANALYSIS_BLOCK_GHOST = RenderType.create(
            "voxelmap_chunk_analysis_block_ghost",
            RenderSetup.builder(VoxelMapPipelines.BLOCK_GHOST_NO_DEPTH)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );
}
