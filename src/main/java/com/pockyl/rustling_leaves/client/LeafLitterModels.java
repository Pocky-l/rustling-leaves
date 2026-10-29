package com.pockyl.rustling_leaves.client;

import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.PileBlocks;

import java.util.List;
import java.util.Map;

/**
 * Vanilla leaf litter is drawn by the litter while the simulation stands in for it (see
 * {@link PileBlocks#LEAF_LITTER}), so its own flat model must not show under the leaves: the models of every leaf
 * litter state are wrapped once when the block models are baked and draw nothing while {@link #setHidden} is on. The
 * chunk meshes keep what they were built with, so whoever changes it rebuilds them.
 */
final class LeafLitterModels {
    private static volatile boolean hidden;

    private LeafLitterModels() {
    }

    static boolean hidden() {
        return hidden;
    }

    static void setHidden(boolean hide) {
        hidden = hide;
    }

    static void wrapModels(ModelEvent.ModifyBakingResult event) {
        int wrapped = 0;
        for (Map.Entry<BlockState, BlockStateModel> entry : event.getBakingResult().blockStateModels().entrySet()) {
            if (PileBlocks.LEAF_LITTER.layers(entry.getKey()) > 0 && !(entry.getValue() instanceof Hideable)) {
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
        public void collectParts(RandomSource random, List<BlockModelPart> parts) {
            if (!hidden) {
                super.collectParts(random, parts);
            }
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
            if (!hidden) {
                super.collectParts(level, pos, state, random, parts);
            }
        }
    }
}
