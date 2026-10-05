package com.pockyl.rustling_leaves.client;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.LeafEffects;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.registry.ModEntities;

@Mod(value = RustlingLeaves.MOD_ID, dist = Dist.CLIENT)
public final class RustlingLeavesClient {
    public RustlingLeavesClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ModConfigEvent.Loading.class, event -> onConfig(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> onConfig(event.getConfig()));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(ModEntities.AUTUMN_BOMB.get(), ThrownItemRenderer::new));
        LeafEffects.set(new ClientLeafEffects());
    }

    private static void onConfig(ModConfig config) {
        if (config.getSpec() == Config.SPEC) {
            LeafManager.onConfigChanged();
        }
    }
}
