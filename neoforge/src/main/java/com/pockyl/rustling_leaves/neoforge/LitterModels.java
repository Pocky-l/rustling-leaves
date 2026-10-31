package com.pockyl.rustling_leaves.neoforge;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafLitterModels;

import java.util.List;
import java.util.Map;

/** Wraps the models of vanilla leaf litter, so the litter can stand in for them (see {@link LeafLitterModels}). */
final class LitterModels {
    private LitterModels() {
    }

    static void wrap(ModelEvent.ModifyBakingResult event) {
        int wrapped = 0;
        for (Map.Entry<BlockState, BlockStateModel> entry : event.getBakingResult().blockStateModels().entrySet()) {
            if (LeafLitterModels.wraps(entry.getKey()) && !(entry.getValue() instanceof Hideable)) {
                entry.setValue(new Hideable(entry.getValue()));
                wrapped++;
            }
        }
        RustlingLeaves.LOGGER.info("Wrapped {} leaf litter models", wrapped);
    }

    /** A leaf litter model that has no parts while the litter draws the leaf litter. */
    private static final class Hideable extends DelegateBlockStateModel {
        Hideable(BlockStateModel delegate) {
            super(delegate);
        }

        @Override
        @Deprecated
        public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            if (!LeafLitterModels.hidden()) {
                super.collectParts(random, parts);
            }
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            if (!LeafLitterModels.hidden()) {
                super.collectParts(level, pos, state, random, parts);
            }
        }
    }
}
