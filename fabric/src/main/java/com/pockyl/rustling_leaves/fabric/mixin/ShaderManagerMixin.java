package com.pockyl.rustling_leaves.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.pockyl.rustling_leaves.fabric.FabricPipelines;

import java.util.List;

/**
 * The leaves' render pipelines are compiled with vanilla's whenever the shaders are (re)loaded, like the pipelines
 * registered to NeoForge, instead of on their first frame.
 */
@Mixin(ShaderManager.class)
abstract class ShaderManagerMixin {
    @ModifyExpressionValue(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderPipelines;getStaticPipelines()Ljava/util/List;"))
    private List<RenderPipeline> rustling_leaves$leafPipelines(List<RenderPipeline> vanilla) {
        return FabricPipelines.withVanilla(vanilla);
    }
}
