package com.pockyl.rustling_leaves.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.LeafManager;

/** The use key: a rake stroke takes the place of using the item, as Forge's InteractionKeyMappingTriggered allows. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    private int rightClickDelay;
    @Shadow
    @Nullable
    public MultiPlayerGameMode gameMode;
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void rustling_leaves$rake(CallbackInfo ci) {
        if (gameMode == null || player == null || gameMode.isDestroying() || player.isHandsBusy()) {
            return;
        }
        if (LeafManager.onUseKey(InteractionHand.MAIN_HAND)) {
            rightClickDelay = 4;
            player.swing(InteractionHand.MAIN_HAND);
            ci.cancel();
        }
    }
}
