package com.mamiyaotaru.voxelmap.rendering;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class VoxelMapPipelines {

    public static final RenderPipeline GUI_TEXTURED_GEQUAL_DEPTH = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/gui_textured_gequal_depth"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .build();

    public static final RenderPipeline GUI_TEXTURED_ANY_DEPTH = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/gui_textured_any_depth"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .build();

    public static final RenderPipeline GUI_TEXTURED_ANY_DEPTH_MASKED = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/gui_textured_any_depth_masked"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
            .build();

    public static final RenderPipeline WAYPOINT_TEXT_BACKGROUND = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/waypoint_text_background"))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    public static final RenderPipeline LINES_NO_DEPTH = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/seedmapper_lines_no_depth"))
            // LINES_SNIPPET is only a snippet and does not provide the attachment
            // state used by the vanilla LINES pipeline, so declare it here.
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            // ESP outlines must remain visible through world geometry. Together
            // with the lack of depth writes this keeps them unoccluded, which is
            // why these render types must not opt into the OIT pipeline set.
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    public static final RenderPipeline QUADS_NO_DEPTH = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/seedmapper_quads_no_depth"))
            // DEBUG_FILLED_SNIPPET already declares the single translucent color
            // target used by the standalone world-overlay pass. Adding another
            // target here makes the pipeline expect two attachments and crashes
            // when ESP fill is rendered.
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    public static final RenderPipeline BLOCK_GHOST_NO_DEPTH = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/chunk_analysis_block_ghost"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    public static final RenderPipeline TEXT_BACKGROUND_GEQUAL_DEPTH = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/text_background_gequal_depth"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    public static final RenderPipeline TEXT_BACKGROUND_ANY_DEPTH = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/text_background_any_depth"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    public static final RenderPipeline ENTITY_ICON = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/entity_icon"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final RenderPipeline ENTITY_ICON_CULLED = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(VoxelConstants.MOD_ID, "pipeline/entity_icon_culled"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(true)
            .build();
}
