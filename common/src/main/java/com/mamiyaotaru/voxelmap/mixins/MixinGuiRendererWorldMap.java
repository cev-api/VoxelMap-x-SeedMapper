package com.mamiyaotaru.voxelmap.mixins;

import com.mamiyaotaru.voxelmap.rendering.WorldMapQuadRenderState;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Range splitting alone is insufficient: the GUI otherwise merges matching states again. */
@Mixin(GuiRenderer.class)
public abstract class MixinGuiRendererWorldMap {
    @Shadow private StagedVertexBuffer.Draw previousDraw;

    @Inject(method = "addElementToMesh", at = {@At("HEAD"), @At("RETURN")})
    private void voxelmap$isolateGeometryDraw(GuiElementRenderState element, CallbackInfo ci) {
        // appendDraw/getVertexBuilder finish the preceding builder and start a fresh one.
        // Clearing after this state also prevents subsequent ordinary GUI elements joining it.
        if (element instanceof WorldMapQuadRenderState) previousDraw = null;
    }
}
