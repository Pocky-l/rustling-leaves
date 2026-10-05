package com.pockyl.rustling_leaves;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Rustling Leaves is purely visual and lives on the client (see {@code client.RustlingLeavesClient}); on a dedicated
 * server it loads and does nothing, so it is safe in server modpacks too.
 */
@Mod(RustlingLeaves.MOD_ID)
public final class RustlingLeaves {
    public static final String MOD_ID = "rustling_leaves";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RustlingLeaves() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
