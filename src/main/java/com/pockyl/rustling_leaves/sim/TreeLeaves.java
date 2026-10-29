package com.pockyl.rustling_leaves.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** What the leaves of a tree look like; the client answers from block colors and textures. */
public interface TreeLeaves {
    /** The tree color (0xRRGGBB) of a leaves block at a position. */
    int color(BlockState state, Level level, BlockPos pos);

    /** Which leaves a leaves block drops. */
    LeafShape shape(BlockState state);

    /**
     * Whether the block drops vanilla falling leaves by itself (every leaves block since 1.21.5), which the client turns
     * into simulated leaves one by one ({@link LeafSpawner#dropBelow}); the canopy spawner then only adds the leaves the
     * wind shakes off.
     */
    default boolean dropsVanillaLeaves(BlockState state) {
        return false;
    }
}
