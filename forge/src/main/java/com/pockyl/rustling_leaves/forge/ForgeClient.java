package com.pockyl.rustling_leaves.forge;

import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.client.LeafCommands;
import com.pockyl.rustling_leaves.client.LeafManager;
import com.pockyl.rustling_leaves.client.RustlingLeavesClient;
import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;
import com.pockyl.rustling_leaves.client.compat.PackWeather;

/** Hooks the client part into Forge; called from the mod constructor on clients only. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init(IEventBus modBus) {
        RustlingLeavesClient.init();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modBus.addListener((ModConfigEvent.Loading event) -> onConfig(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onConfig(event.getConfig()));
        if (ModList.get().isLoaded(ImmersiveWeathering.MOD_ID)) {
            modBus.addListener((ModelEvent.ModifyBakingResult event) -> PileModels.wrap(event.getModels()));
        }

        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                LeafManager.onClientTick();
            }
        });
        bus.addListener((TickEvent.RenderTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) {
                PackWeather.onFrameStart(event.renderTickTime);
            }
        });
        bus.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                LeafManager.onRenderLevel(event.getCamera(), event.getFrustum(), event.getPoseStack().last().pose(),
                        event.getProjectionMatrix(), event.getPartialTick());
            }
        });
        bus.addListener((CustomizeGuiOverlayEvent.DebugText event) -> LeafManager.onDebugText(event.getRight()));
        bus.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (event.isUseItem() && LeafManager.onUseKey(event.getHand())) {
                event.setCanceled(true);
                event.setSwingHand(true);
            }
        });
        bus.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                LeafCommands.command((source, message) -> source.sendSuccess(() -> message, false))));
    }

    private static void onConfig(ModConfig config) {
        if (config.getSpec() == Config.SPEC) {
            LeafManager.onConfigChanged();
        }
    }
}
