package com.pockyl.rustling_leaves.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * <a href="https://www.curseforge.com/minecraft/mc-mods/falling-leaves-forge">Falling Leaves</a> (NeoForge port, mod id
 * {@code fallingleaves}) drops leaf particles from every leaves block, so with both mods every tree sheds twice, half
 * of it as flat particles. Since its 3.x versions all of its leaves go through its per-level {@code Leaves} object: the
 * steady fall (it takes over vanilla's falling leaf particles of every leaves block), decaying blocks and hitting a
 * leaves block. While the simulation drops leaves itself they are skipped; the steady fall goes back to vanilla, where
 * {@link LeavesBlockMixin} turns cherry petals into simulated ones as without the mod. Its snow particles stay. The mod
 * has no API or event for it, hence the mixin; it is applied only when the mod is installed and does nothing if the
 * methods are gone in another version.
 */
@Pseudo
@Mixin(targets = "de.cheaterpaul.fallingleaves.leaves.Leaves", remap = false)
abstract class FallingLeavesMixin {
    @Inject(method = "makeLeavesParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void rustling_leaves$vanillaFall(ClientLevel level, BlockPos pos, RandomSource random, BlockState belowState, BlockPos below,
            CallbackInfo ci) {
        if (LeafManager.replacesFallingLeaves()) {
            ci.cancel();
            if (level.getBlockState(pos).getBlock() instanceof LeavesBlockInvoker leaves) {
                leaves.rustling_leaves$makeFallingLeavesParticles(level, pos, random);
            }
        }
    }

    @Inject(method = {"makeDecayingLeavesParticles", "makeAttackLeavesParticles"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void rustling_leaves$simulatedLeavesOnly(CallbackInfo ci) {
        if (LeafManager.replacesFallingLeaves()) {
            ci.cancel();
        }
    }
}
