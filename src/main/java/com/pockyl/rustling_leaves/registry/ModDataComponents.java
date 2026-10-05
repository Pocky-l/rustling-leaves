package com.pockyl.rustling_leaves.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.item.BagContents;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, RustlingLeaves.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BagContents>> BAG_CONTENTS =
            COMPONENTS.registerComponentType("bag_contents",
                    builder -> builder.persistent(BagContents.CODEC).networkSynchronized(BagContents.STREAM_CODEC).cacheEncoding());

    private ModDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
