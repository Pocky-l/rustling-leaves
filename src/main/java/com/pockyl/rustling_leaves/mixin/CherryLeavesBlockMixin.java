package com.pockyl.rustling_leaves.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CherryLeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Cherry leaves drop vanilla petal particles that ignore everything; each one becomes a simulated petal at the same
 * spot instead, so cherry trees shed exactly as often as in vanilla. Only applied on the client, where animateTick runs.
 * There is no event for block particles, hence the mixin.
 */
@Mixin(CherryLeavesBlock.class)
abstract class CherryLeavesBlockMixin {
    @WrapOperation(method = "animateTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ParticleUtils;spawnParticleBelow("
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;"
            + "Lnet/minecraft/core/particles/ParticleOptions;)V"))
    private void rustling_leaves$simulatedPetal(Level level, BlockPos pos, RandomSource random, ParticleOptions particle,
            Operation<Void> original, BlockState state) {
        if (!LeafManager.replaceCherryPetal(level, pos, state)) {
            original.call(level, pos, random, particle);
        }
    }
}
