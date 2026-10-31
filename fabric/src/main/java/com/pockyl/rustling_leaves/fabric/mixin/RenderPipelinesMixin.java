package com.pockyl.rustling_leaves.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.pockyl.rustling_leaves.fabric.FabricPipelines;

import java.util.List;

/**
 * The leaves' render pipelines are compiled with vanilla's required ones whenever the shaders are (re)loaded, like the
 * pipelines registered to NeoForge, instead of on their first frame.
 */
@Mixin(RenderPipelines.class)
abstract class RenderPipelinesMixin {
    @ModifyReturnValue(method = "requiredPipelines", at = @At("RETURN"))
    private static List<RenderPipeline> rustling_leaves$leafPipelines(List<RenderPipeline> vanilla) {
        return FabricPipelines.withVanilla(vanilla);
    }
}
