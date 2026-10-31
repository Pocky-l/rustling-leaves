package com.pockyl.rustling_leaves.client;

import com.pockyl.rustling_leaves.network.RakePayload;

/**
 * The client part, started by the loaders on clients only. They register {@link com.pockyl.rustling_leaves.Config}
 * and call {@link LeafManager#onConfigChanged} when it loads or changes, hook the {@code LeafManager.on...} methods
 * into the game and hand the rake strokes that arrive from the server to {@link #onRake}.
 */
public final class RustlingLeavesClient {
    private RustlingLeavesClient() {
    }

    /** A rake stroke relayed by the server arrived (main thread). */
    public static void onRake(RakePayload payload) {
        if (payload.isFinite()) {
            LeafManager.onRemoteRake(payload);
        }
    }
}
