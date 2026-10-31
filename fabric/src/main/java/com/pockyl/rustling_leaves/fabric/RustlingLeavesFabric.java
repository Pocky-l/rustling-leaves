package com.pockyl.rustling_leaves.fabric;

import net.fabricmc.api.ModInitializer;

/** The Fabric entry point on both sides: the optional rake channel of a server. */
public final class RustlingLeavesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricNetwork.register();
    }
}
