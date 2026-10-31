package com.pockyl.rustling_leaves.sim;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Leaf pile blocks that the litter can stand in for: vanilla leaf litter (1.21.5) and the leaf piles of other mods.
 * The client chooses them from its options; game tests use these and a stand-in.
 */
public interface PileBlocks {
    PileBlocks NONE = state -> 0;
    /** All 16 cells of a block, see {@link #cells}. */
    int ALL_CELLS = 0xFFFF;

    /**
     * Vanilla leaf litter (and modded blocks built on it): a thin layer of leaves on the quarters of the block its
     * segments cover. The first segment lies in the quarter to the left of its facing, each further one a quarter turn
     * counter-clockwise, as the block's model and shape place them.
     */
    PileBlocks LEAF_LITTER = new PileBlocks() {
        @Override
        public int layers(BlockState state) {
            return state.getBlock() instanceof LeafLitterBlock ? 1 : 0;
        }

        @Override
        public int cells(BlockState state) {
            Direction quarter = state.getValue(LeafLitterBlock.FACING);
            int cells = 0;
            int segments = state.getValue(((LeafLitterBlock) state.getBlock()).getSegmentAmountProperty());
            for (int segment = segments; segment > 0; segment--) {
                cells |= switch (quarter) {
                    case EAST -> 0x00CC;
                    case SOUTH -> 0xCC00;
                    case WEST -> 0x3300;
                    default -> 0x0033;
                };
                quarter = quarter.getCounterClockWise();
            }
            return cells;
        }
    };

    /** Height of the pile in eighths of a block (1..8) if the state is a leaf pile block on the ground, else 0. */
    int layers(BlockState state);

    /**
     * The cells of the block the pile lies on: bit {@code a + 4 * b} for the cell {@code a} quarter blocks east and
     * {@code b} south of the block's north-west corner. Most piles fill their block.
     */
    default int cells(BlockState state) {
        return ALL_CELLS;
    }
}
