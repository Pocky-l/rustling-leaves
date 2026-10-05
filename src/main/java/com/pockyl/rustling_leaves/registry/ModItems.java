package com.pockyl.rustling_leaves.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.rustling_leaves.RustlingLeaves;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RustlingLeaves.MOD_ID);

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
