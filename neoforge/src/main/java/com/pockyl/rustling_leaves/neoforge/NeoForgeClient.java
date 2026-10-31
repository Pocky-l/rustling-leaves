package com.pockyl.rustling_leaves.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafCommands;
import com.pockyl.rustling_leaves.client.LeafManager;
import com.pockyl.rustling_leaves.client.RustlingLeavesClient;
import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;
import com.pockyl.rustling_leaves.client.compat.PackWeather;
import com.pockyl.rustling_leaves.client.render.backend.LevelRenderHook;

/** Hooks the client part into NeoForge; only constructed on clients. */
@Mod(value = RustlingLeaves.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus modBus, ModContainer container) {
        RustlingLeavesClient.init();
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ModConfigEvent.Loading.class, event -> onConfig(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> onConfig(event.getConfig()));
        if (ModList.get().isLoaded(ImmersiveWeathering.MOD_ID)) {
            modBus.addListener(ModelEvent.ModifyBakingResult.class, event -> PileModels.wrap(event.getModels()));
        }

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, event -> LeafManager.onClientTick());
        bus.addListener(RenderFrameEvent.Pre.class, event ->
                PackWeather.onFrameStart(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        bus.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                LevelRenderHook.render(event.getCamera(), event.getFrustum(), event.getModelViewMatrix(),
                        event.getProjectionMatrix(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });
        bus.addListener(CustomizeGuiOverlayEvent.DebugText.class, event -> LeafManager.onDebugText(event.getRight()));
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, event -> {
            if (event.isUseItem() && LeafManager.onUseKey(event.getHand())) {
                event.setCanceled(true);
                event.setSwingHand(true);
            }
        });
        bus.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
                LeafCommands.command((source, message) -> source.sendSuccess(() -> message, false))));
    }

    private static void onConfig(ModConfig config) {
        if (config.getSpec() == Config.SPEC) {
            LeafManager.onConfigChanged();
        }
    }
}
