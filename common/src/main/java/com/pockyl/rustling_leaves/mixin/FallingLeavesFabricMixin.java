package com.pockyl.rustling_leaves.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * <a href="https://modrinth.com/mod/fallingleaves">Falling Leaves</a> on Fabric (the original mod, mod id
 * {@code fallingleaves}, whose NeoForge port {@link FallingLeavesMixin} handles): the same double shedding, other code.
 * Its steady fall, decaying blocks and hitting a leaves block all spawn their leaves through this one method, which is
 * skipped while the simulation drops leaves itself; its snow particles stay. Applied only when the mod is installed
 * and does nothing if the method is gone in another version.
 */
@Pseudo
@Mixin(targets = "randommcsomethin.fallingleaves.util.LeafUtil", remap = false)
abstract class FallingLeavesFabricMixin {
    @Inject(method = "spawnLeafParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private static void rustling_leaves$simulatedLeavesOnly(CallbackInfo ci) {
        if (LeafManager.replacesFallingLeaves()) {
            ci.cancel();
        }
    }
}
