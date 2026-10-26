package com.pockyl.rustling_leaves.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * <a href="https://www.curseforge.com/minecraft/mc-mods/falling-leaves-forge">Falling Leaves</a> (NeoForge port, mod id
 * {@code fallingleaves}) drops leaf particles from every leaves block, so with both mods every tree sheds twice, half
 * of it as flat particles. All of its leaves (the steady fall, decaying blocks and hitting a leaves block) go through
 * this one method, which is skipped while the simulation drops leaves itself; its snow particles stay. The mod has no
 * API or event for it, hence the mixin; it is applied only when the mod is installed and does nothing if the method
 * is gone in another version.
 */
@Pseudo
@Mixin(targets = "de.cheaterpaul.fallingleaves.util.LeafUtil", remap = false)
abstract class FallingLeavesMixin {
    @Inject(method = "trySpawnLeafParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private static void rustling_leaves$simulatedLeavesOnly(CallbackInfo ci) {
        if (LeafManager.replacesFallingLeaves()) {
            ci.cancel();
        }
    }
}
