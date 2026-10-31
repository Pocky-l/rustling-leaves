package com.pockyl.rustling_leaves.neoforge;

import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafCommands;
import com.pockyl.rustling_leaves.client.LeafManager;
import com.pockyl.rustling_leaves.client.compat.PackWeather;
import com.pockyl.rustling_leaves.client.render.backend.LeafPipelines;
import com.pockyl.rustling_leaves.client.render.backend.LevelRenderHook;

/** Hooks the client part into NeoForge; only constructed on clients. */
@Mod(value = RustlingLeaves.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ModConfigEvent.Loading.class, event -> onConfig(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> onConfig(event.getConfig()));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, NeoForgeNetwork::registerClient);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, LitterModels::wrap);
        modBus.addListener(RegisterRenderPipelinesEvent.class, event -> LeafPipelines.register(event::registerPipeline));
        modBus.addListener(RegisterDebugEntriesEvent.class, event -> {
            event.register(LeafManager.DEBUG_ENTRY, (displayer, level, clientChunk, serverChunk) -> LeafManager.debugText(displayer));
            event.includeInProfile(LeafManager.DEBUG_ENTRY, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
        });

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, event -> LeafManager.onClientTick());
        bus.addListener(RenderFrameEvent.Pre.class, event ->
                PackWeather.onFrameStart(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        bus.addListener(ExtractLevelRenderStateEvent.class, event -> LevelRenderHook.extract(event.getCamera(), event.getFrustum(),
                event.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
        bus.addListener(RenderLevelStageEvent.AfterOpaqueBlocks.class, event -> LevelRenderHook.render(event.getModelViewMatrix()));
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
