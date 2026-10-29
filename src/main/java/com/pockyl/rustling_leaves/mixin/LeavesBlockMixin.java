package com.pockyl.rustling_leaves.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Since 1.21.5 every leaves block drops vanilla falling leaf particles (cherry petals, pale oak, poplar and tinted
 * leaves) that ignore everything. Each cherry petal becomes a simulated petal at the same spot instead, so cherry trees
 * shed as often as in vanilla (times the fall rate and the season); the other leaves are left out, the simulation drops
 * those itself. Wrapped where all leaves blocks with falling particles decide to drop one, so modded leaves that extend
 * {@link FallingParticlesLeavesBlock} follow too. Only applied on the client, where animateTick runs. There is no event
 * for block particles, hence the mixin.
 */
@Mixin(FallingParticlesLeavesBlock.class)
abstract class LeavesBlockMixin {
    @WrapOperation(method = "makeFallingLeavesParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/FallingParticlesLeavesBlock;"
            + "spawnFallingLeavesParticle(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void rustling_leaves$simulatedLeaf(FallingParticlesLeavesBlock block, Level level, BlockPos pos, RandomSource random, Operation<Void> original) {
        if (!LeafManager.replaceLeafParticle(level, pos)) {
            original.call(block, level, pos, random);
        }
    }
}
