package com.pockyl.rustling_leaves.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.client.LeafManager;

/**
 * Every block change the client sees (breaking, placing, decay, pistons, explosions) with the old and the new state.
 * NeoForge has no client-side block change event.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void rustling_leaves$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        LeafManager.onBlockChanged((ClientLevel) (Object) this, pos, oldState, newState);
    }
}
