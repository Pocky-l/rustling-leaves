package com.pockyl.rustling_leaves;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import com.pockyl.rustling_leaves.network.ModNetwork;

/**
 * Rustling Leaves. The leaves are client-side (see {@code client.RustlingLeavesClient}) and work on any server. The
 * common part only registers an optional rake channel: installed on a server too, the mod shows every player's rake
 * strokes to the players around them.
 */
@Mod(RustlingLeaves.MOD_ID)
public final class RustlingLeaves {
    public static final String MOD_ID = "rustling_leaves";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RustlingLeaves(IEventBus modBus) {
        modBus.addListener(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
