package com.pockyl.rustling_leaves.client;

import com.pockyl.rustling_leaves.network.ModNetwork;

/**
 * The client part, started by the loaders on clients only. They also register {@link com.pockyl.rustling_leaves.Config}
 * and call {@link LeafManager#onConfigChanged} when it loads or changes, and hook the {@code LeafManager.on...} methods
 * into the game.
 */
public final class RustlingLeavesClient {
    private RustlingLeavesClient() {
    }

    public static void init() {
        ModNetwork.setClientRake(LeafManager::onRemoteRake);
    }
}
