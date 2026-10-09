package com.pockyl.rustling_leaves.sim;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Leaf pile blocks of other mods (Immersive Weathering's leaf piles) that the litter can stand in for. The client
 * answers from the installed mods; game tests use a vanilla stand-in.
 */
public interface PileBlocks {
    PileBlocks NONE = state -> 0;

    /** Height of the pile in eighths of a block (1..8) if the state is a leaf pile block on the ground, else 0. */
    int layers(BlockState state);
}
