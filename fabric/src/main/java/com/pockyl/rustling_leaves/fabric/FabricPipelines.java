package com.pockyl.rustling_leaves.fabric;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import com.pockyl.rustling_leaves.client.render.backend.LeafPipelines;

import java.util.ArrayList;
import java.util.List;

/**
 * The leaves' render pipelines, compiled with vanilla's whenever the shaders are loaded (see
 * {@code mixin.ShaderManagerMixin}), as NeoForge does with the pipelines registered to it.
 */
public final class FabricPipelines {
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();

    private FabricPipelines() {
    }

    static void register() {
        LeafPipelines.register(PIPELINES::add);
    }

    /** Vanilla's pipelines and the mod's. */
    public static List<RenderPipeline> withVanilla(List<RenderPipeline> vanilla) {
        List<RenderPipeline> all = new ArrayList<>(vanilla);
        all.addAll(PIPELINES);
        return all;
    }
}
