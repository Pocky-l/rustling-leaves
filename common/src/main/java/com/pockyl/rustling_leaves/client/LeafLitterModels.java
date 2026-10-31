package com.pockyl.rustling_leaves.client;

import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.rustling_leaves.sim.PileBlocks;

/**
 * Vanilla leaf litter is drawn by the litter while the simulation stands in for it (see
 * {@link PileBlocks#LEAF_LITTER}), so its own flat model must not show under the leaves: the loaders wrap the models of
 * every leaf litter state ({@link #wraps}) once when the block models are baked, and the wrappers draw nothing while
 * {@link #hidden} is on. The chunk meshes keep what they were built with, so whoever changes it rebuilds them.
 */
public final class LeafLitterModels {
    private static volatile boolean hidden;

    private LeafLitterModels() {
    }

    /** Whether the model of this block state is wrapped. */
    public static boolean wraps(BlockState state) {
        return PileBlocks.LEAF_LITTER.layers(state) > 0;
    }

    /** Whether the wrapped models draw nothing right now. */
    public static boolean hidden() {
        return hidden;
    }

    static void setHidden(boolean hide) {
        hidden = hide;
    }
}
