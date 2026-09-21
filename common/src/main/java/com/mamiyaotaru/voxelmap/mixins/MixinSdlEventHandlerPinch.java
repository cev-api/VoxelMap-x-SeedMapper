package com.mamiyaotaru.voxelmap.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.mamiyaotaru.voxelmap.gui.GuiSeedMapperMap;
import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;
import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards SDL trackpad pinch gestures to the world map zoom controller. */
@Mixin(SDLEventHandler.class)
public class MixinSdlEventHandlerPinch {
    @Unique
    private static final int VOXELMAP_SDL_EVENT_PINCH_UPDATE = 0x711;

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "pollEvents", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;handleEvent(Lorg/lwjgl/sdl/SDL_Event;)V"), cancellable = true)
    private void voxelmap$handlePinch(CallbackInfo callbackInfo, @Local(name = "event") SDL_Event event) {
        if (event.type() == VOXELMAP_SDL_EVENT_PINCH_UPDATE
                && this.minecraft.gui.screen() instanceof GuiPersistentMap worldMap) {
            this.minecraft.execute(() -> worldMap.pinchUpdated(event.pinch().scale()));
            callbackInfo.cancel();
        } else if (event.type() == VOXELMAP_SDL_EVENT_PINCH_UPDATE
                && this.minecraft.gui.screen() instanceof GuiSeedMapperMap seedMap) {
            this.minecraft.execute(() -> seedMap.pinchUpdated(event.pinch().scale()));
            callbackInfo.cancel();
        }
    }
}
