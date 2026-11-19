package com.pockyl.rustling_leaves.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Every chunk section the game re-renders for new light, so the litter there is lit anew as well: light changes of
 * the level and light data from the server, and the sections that dynamic light mods (LambDynamicLights and its
 * ports) re-render around a moving light. Sodium replaces both methods with its own bodies; they are still called.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void rustling_leaves$sectionDirty(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread,
            CallbackInfo ci) {
        LeafManager.onSectionDirty(sectionX, sectionY, sectionZ);
    }

    @Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"))
    private void rustling_leaves$sectionsDirty(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    LeafManager.onSectionDirty(sectionX + dx, sectionY + dy, sectionZ + dz);
                }
            }
        }
    }
}
