package com.pockyl.rustling_leaves.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the leaves take their light from: packed light coordinates at a block (block light in the low 16 bits, sky
 * light in the high 16 bits, 16 steps per light level). The client reads them the way the game lights the blocks
 * there, so light that other mods add to the blocks (the dynamic light of a torch in hand) lights the leaves as well.
 */
@FunctionalInterface
public interface LightReader {
    /** The light stored in the level. */
    LightReader LEVEL = (level, state, pos) ->
            level.getBrightness(LightLayer.SKY, pos) << 20 | level.getBrightness(LightLayer.BLOCK, pos) << 4;

    int light(BlockAndTintGetter level, BlockState state, BlockPos pos);
}
