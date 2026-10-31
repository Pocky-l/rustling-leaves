package com.pockyl.rustling_leaves.fabric.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.rustling_leaves.client.LeafManager;

import java.util.List;

/** The leaf counts on the right side of the debug screen (F3). */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void rustling_leaves$debugText(CallbackInfoReturnable<List<String>> cir) {
        LeafManager.onDebugText(cir.getReturnValue());
    }
}
