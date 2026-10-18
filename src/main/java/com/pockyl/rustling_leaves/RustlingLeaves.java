package com.pockyl.rustling_leaves;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.client.RustlingLeavesClient;

/**
 * Rustling Leaves. The mod is purely client-side (see {@code client.RustlingLeavesClient}): it registers nothing, so it
 * works on any server, with or without the mod.
 */
@Mod(RustlingLeaves.MOD_ID)
public final class RustlingLeaves {
    public static final String MOD_ID = "rustling_leaves";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RustlingLeaves() {
        // The client class is only loaded on a client; a dedicated server never resolves it.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RustlingLeavesClient.init(FMLJavaModLoadingContext.get().getModEventBus()));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
