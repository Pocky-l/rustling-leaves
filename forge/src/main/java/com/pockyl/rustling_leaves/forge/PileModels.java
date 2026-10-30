package com.pockyl.rustling_leaves.forge;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;

import java.util.List;
import java.util.Map;

/** Wraps the leaf pile models of Immersive Weathering, so the litter can stand in for them. */
final class PileModels {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PileModels() {
    }

    static void wrap(Map<ResourceLocation, BakedModel> models) {
        int wrapped = 0;
        for (ResourceLocation location : ImmersiveWeathering.pileModels()) {
            if (models.computeIfPresent(location, (key, model) -> new HiddenModel(model)) != null) {
                wrapped++;
            }
        }
        LOGGER.info("Wrapped {} Immersive Weathering leaf pile models", wrapped);
    }

    /** A pile model that draws nothing while the litter stands in for the pile (breaking particles still work). */
    private static final class HiddenModel extends BakedModelWrapper<BakedModel> {
        HiddenModel(BakedModel original) {
            super(original);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return ImmersiveWeathering.hidden() ? List.of() : super.getQuads(state, side, rand);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                @Nullable RenderType renderType) {
            return ImmersiveWeathering.hidden() ? List.of() : super.getQuads(state, side, rand, data, renderType);
        }
    }
}
