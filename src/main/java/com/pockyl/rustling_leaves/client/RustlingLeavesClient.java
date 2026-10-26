package com.pockyl.rustling_leaves.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;
import com.pockyl.rustling_leaves.network.ModNetwork;

/** Client entry point, called from the mod constructor on clients only. */
public final class RustlingLeavesClient {
    private RustlingLeavesClient() {
    }

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modBus.addListener((ModConfigEvent.Loading event) -> onConfig(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onConfig(event.getConfig()));
        ModNetwork.setClientRake(LeafManager::onRemoteRake);
        if (ModList.get().isLoaded(ImmersiveWeathering.MOD_ID)) {
            modBus.addListener((ModelEvent.ModifyBakingResult event) -> ImmersiveWeathering.wrapModels(event));
        }
    }

    private static void onConfig(ModConfig config) {
        if (config.getSpec() == Config.SPEC) {
            LeafManager.onConfigChanged();
        }
    }
}
