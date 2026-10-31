package com.pockyl.rustling_leaves.fabric.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.render.backend.LevelRenderHook;

/**
 * The two moments NeoForge marks with PrepareRenderBuffersEvent and RenderLevelStageEvent.AfterOpaqueBlocks: Fabric
 * API has no event that hands out the level's open render pass. The leaves are uploaded while the level prepares its
 * buffers (before the index buffers are resized for the frame) and drawn into the pass right after the opaque terrain.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Final
    LevelRenderState levelRenderState;

    @Inject(method = "prepareTranslucents", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;resizeAllAutoStorageIndexBuffers()V"))
    private void rustling_leaves$prepare(CallbackInfo ci) {
        LevelRenderHook.prepare(levelRenderState.cameraRenderState.viewRotationMatrix);
    }

    @Inject(method = "executeSolid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void rustling_leaves$draw(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame featureFrame,
            RenderPass renderPass, CallbackInfo ci) {
        LevelRenderHook.draw(renderPass);
    }
}
