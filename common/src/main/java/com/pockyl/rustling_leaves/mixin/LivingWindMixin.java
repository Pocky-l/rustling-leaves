package com.pockyl.rustling_leaves.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * <a href="https://www.curseforge.com/minecraft/mc-mods/living-wind">Living Wind</a> (mod id {@code livingwind}) blows
 * its own leaf particles out of the tree crowns, so with both mods every tree sheds twice. Its canopy spawner asks this
 * method whether to drop them (its own switch for handing the leaves to another mod), which says no while the
 * simulation drops leaves itself; its grass, plant and dust particles stay. The mod has no API for it, hence the mixin;
 * it is applied only when the mod is installed and does nothing if the method is gone in another version.
 */
@Pseudo
@Mixin(targets = "com.macatmovie.livingwind.client.ParticleWindFilter", remap = false)
abstract class LivingWindMixin {
    @Inject(method = "shouldSpawnNativeLeaves", at = @At("HEAD"), cancellable = true, require = 0)
    private static void rustling_leaves$simulatedLeavesOnly(CallbackInfoReturnable<Boolean> cir) {
        if (LeafManager.replacesFallingLeaves()) {
            cir.setReturnValue(false);
        }
    }
}
