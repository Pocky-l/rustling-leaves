package com.pockyl.rustling_leaves.client.render.backend;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.compat.ShaderPacks;
import com.pockyl.rustling_leaves.platform.ClientPlatform;

import java.util.function.Consumer;

/**
 * The render pipelines of the two passes (see {@link RenderBackend.Pass}), made from vanilla's block pipelines - the
 * ones falling blocks and pistons draw with: the block vertex format and a model offset per draw, the old chunk offset.
 * Registered with the game so they are compiled with vanilla's; with a shader pack Iris draws them with the pack's
 * terrain programs. The loaders call {@link #register} when the game takes the pipelines of mods.
 */
public final class LeafPipelines {
    /**
     * Vanilla's cutout block pipeline without face culling. Iris copies the pack's programs of the original to a
     * pipeline made with NeoForge's {@code toBuilder} (cutout terrain, and its shadow program); on Fabric it gets the
     * same program from {@link #register}.
     */
    static final RenderPipeline LEAVES = ClientPlatform.INSTANCE.cutoutBlockPipeline()
            .withLocation(Identifier.fromNamespaceAndPath(RustlingLeaves.MOD_ID, "pipeline/leaves"))
            .withCull(false)
            .build();
    /**
     * Pixel shadows: the block shader with translucent blending, keeping everything above 1% alpha like vanilla's
     * translucent terrain (the faintest shadows are 3%; vanilla's translucent block pipeline cuts out below 10%), no
     * depth writes and the decal depth bias, so they neither hide what is behind them nor flicker against the ground.
     */
    static final RenderPipeline SHADOWS = RenderPipeline.builder(ClientPlatform.INSTANCE.blockSnippet())
            .withLocation(Identifier.fromNamespaceAndPath(RustlingLeaves.MOD_ID, "pipeline/leaf_shadows"))
            .withShaderDefine("ALPHA_CUTOUT", 0.01F)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthBias(-1.0F, -10.0F)
            .withCull(false)
            .build();

    private LeafPipelines() {
    }

    /** Hands both pipelines to the game and tells Iris which of a pack's programs draws them. */
    public static void register(Consumer<RenderPipeline> game) {
        game.accept(LEAVES);
        game.accept(SHADOWS);
        ShaderPacks.assignProgram(LEAVES, "TERRAIN_CUTOUT");
        ShaderPacks.assignProgram(SHADOWS, "TRANSLUCENT");
    }
}
