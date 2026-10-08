package com.pockyl.rustling_leaves.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CherryLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Cherry leaves drop vanilla petal particles that ignore everything; each one becomes a simulated petal at the same
 * spot instead, so cherry trees shed exactly as often as in vanilla. Only applied on the client, where animateTick runs.
 * There is no event for block particles, hence the mixin; a plain redirect, since Forge 47 does not ship MixinExtras.
 */
@Mixin(CherryLeavesBlock.class)
abstract class CherryLeavesBlockMixin {
    @Redirect(method = "animateTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ParticleUtils;spawnParticleBelow("
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;"
            + "Lnet/minecraft/core/particles/ParticleOptions;)V"))
    private void rustling_leaves$simulatedPetal(Level level, BlockPos pos, RandomSource random, ParticleOptions particle) {
        if (!LeafManager.replaceCherryPetal(level, pos, level.getBlockState(pos))) {
            ParticleUtils.spawnParticleBelow(level, pos, random, particle);
        }
    }
}
