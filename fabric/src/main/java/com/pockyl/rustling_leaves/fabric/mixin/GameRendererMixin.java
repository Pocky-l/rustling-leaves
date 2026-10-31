package com.pockyl.rustling_leaves.fabric.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.compat.PackWeather;

/**
 * The start of every frame, where NeoForge fires RenderFrameEvent.Pre: the wind for shader packs is handed over before
 * Iris sets up the level render, and its texture exists before the first frame of a pack.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void rustling_leaves$frameStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        PackWeather.onFrameStart(deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
