package com.pockyl.rustling_leaves.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.pockyl.rustling_leaves.RustlingLeaves;

/** The NeoForge entry point on both sides: the optional rake channel. The client part is {@link NeoForgeClient}. */
@Mod(RustlingLeaves.MOD_ID)
public final class RustlingLeavesNeoForge {
    public RustlingLeavesNeoForge(IEventBus modBus) {
        modBus.addListener(NeoForgeNetwork::register);
    }
}
