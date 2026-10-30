package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.compat.ShaderPacks;

/**
 * The render pipelines of the two passes (see {@link RenderBackend.Pass}), made from vanilla's block pipelines - the
 * ones falling blocks and pistons draw with: the block vertex format and a model offset per draw, the old chunk offset.
 * Registered with the game so they are compiled with vanilla's; with a shader pack Iris draws them with the pack's
 * terrain programs. Depth is reversed since 26.2 (near is 1): nearer passes with "greater or equal" and the decal
 * bias is positive. They are never drawn into a pack's shadow map, so they get no shadow programs of their own:
 * Iris' public API would pick its Sodium chunk programs for them, which read another vertex layout.
 */
@EventBusSubscriber(modid = RustlingLeaves.MOD_ID, value = Dist.CLIENT)
final class LeafPipelines {
    /**
     * Vanilla's cutout block pipeline without face culling. Iris copies the pack's programs of the original to a
     * pipeline made with {@code toBuilder} (cutout terrain, and its shadow program).
     */
    static final RenderPipeline LEAVES = RenderPipelines.CUTOUT_BLOCK.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(RustlingLeaves.MOD_ID, "pipeline/leaves"))
            .withCull(false)
            .build();
    /**
     * Pixel shadows: the block shader with translucent blending and no alpha cutout (the faintest shadows are 3%;
     * vanilla's translucent block pipeline cuts out below 10%, and with blending and no depth writes a cutout changes
     * nothing; without one Iris also picks the pack's translucent terrain program, not its Sodium chunk program), no
     * depth writes and vanilla's decal depth bias (the block breaking overlay's), so they neither hide what is behind
     * them nor flicker against the ground.
     */
    static final RenderPipeline SHADOWS = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(RustlingLeaves.MOD_ID, "pipeline/leaf_shadows"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, 1.0F, 10.0F))
            .withCull(false)
            .build();

    private LeafPipelines() {
    }

    @SubscribeEvent
    static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LEAVES);
        event.registerPipeline(SHADOWS);
        ShaderPacks.assignProgram(LEAVES, "TERRAIN_CUTOUT");
        ShaderPacks.assignProgram(SHADOWS, "TRANSLUCENT");
    }
}
