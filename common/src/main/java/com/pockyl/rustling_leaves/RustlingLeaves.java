package com.pockyl.rustling_leaves;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * Rustling Leaves. The leaves are client-side (see {@code client.RustlingLeavesClient}) and work on any server. The
 * common part only registers an optional rake channel: installed on a server too, the mod shows every player's rake
 * strokes to the players around them. The entry points are in the loader projects (neoforge, fabric).
 */
public final class RustlingLeaves {
    public static final String MOD_ID = "rustling_leaves";
    public static final Logger LOGGER = LogUtils.getLogger();

    private RustlingLeaves() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
