package com.pockyl.rustling_leaves.fabric;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Wraps the leaf pile models of Immersive Weathering, so the litter can stand in for them. */
final class PileModels {
    private PileModels() {
    }

    static void register(ModelLoadingPlugin.Context context) {
        Set<ResourceLocation> piles = ImmersiveWeathering.pileModels();
        context.modifyModelAfterBake().register((model, modelContext) ->
                model != null && piles.contains(modelContext.id()) ? new HiddenModel(model) : model);
    }

    /** A pile model that draws nothing while the litter stands in for the pile (breaking particles still work). */
    private static final class HiddenModel extends ForwardingBakedModel {
        HiddenModel(BakedModel original) {
            wrapped = original;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return ImmersiveWeathering.hidden() ? List.of() : super.getQuads(state, side, rand);
        }

        @Override
        public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> rand,
                RenderContext context) {
            if (!ImmersiveWeathering.hidden()) {
                super.emitBlockQuads(level, state, pos, rand, context);
            }
        }
    }
}
