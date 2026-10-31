package com.pockyl.rustling_leaves.fabric.mixin;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.rustling_leaves.fabric.LeafDebugEntry;

import java.util.Map;

/** The leaf counts on the debug screen (F3): an entry of their own, in the default profile; see {@link LeafDebugEntry}. */
@Mixin(DebugScreenEntries.class)
abstract class DebugScreenEntriesMixin {
    @Shadow
    @Final
    @Mutable
    public static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> PROFILES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void rustling_leaves$leafCounts(CallbackInfo ci) {
        PROFILES = LeafDebugEntry.register(PROFILES);
    }
}
