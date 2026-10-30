package com.pockyl.rustling_leaves.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.pockyl.rustling_leaves.RustlingLeaves;

/** The Forge entry point. */
@Mod(RustlingLeaves.MOD_ID)
public final class RustlingLeavesForge {
    public RustlingLeavesForge() {
        ForgeNetwork.register();
        // The client class is only loaded on a client; a dedicated server never resolves it.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus()));
    }
}
