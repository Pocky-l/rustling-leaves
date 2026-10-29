package com.pockyl.rustling_leaves.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's own falling leaf particles of a leaves block, for when another leaf mod took the call over
 * ({@link FallingLeavesMixin} hands it back, so {@link LeavesBlockMixin} still sees every vanilla leaf).
 */
@Mixin(LeavesBlock.class)
public interface LeavesBlockInvoker {
    @Invoker("makeFallingLeavesParticles")
    void rustling_leaves$makeFallingLeavesParticles(Level level, BlockPos pos, RandomSource random, BlockState belowState, BlockPos below);
}
