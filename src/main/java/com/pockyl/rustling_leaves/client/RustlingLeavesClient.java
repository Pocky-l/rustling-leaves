package com.pockyl.rustling_leaves.client;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.LeafEffects;
import com.pockyl.rustling_leaves.registry.ModEntities;

/** Client entry point, called from the mod constructor on clients only. */
public final class RustlingLeavesClient {
    private RustlingLeavesClient() {
    }

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modBus.addListener((ModConfigEvent.Loading event) -> onConfig(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onConfig(event.getConfig()));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(ModEntities.AUTUMN_BOMB.get(), ThrownItemRenderer::new));
        LeafEffects.set(new ClientLeafEffects());
    }

    private static void onConfig(ModConfig config) {
        if (config.getSpec() == Config.SPEC) {
            LeafManager.onConfigChanged();
        }
    }
}
