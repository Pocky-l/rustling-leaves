package com.pockyl.rustling_leaves.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import fuzs.forgeconfigapiport.fabric.api.v5.client.ConfigScreenFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.joml.Matrix4f;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafCommands;
import com.pockyl.rustling_leaves.client.LeafManager;
import com.pockyl.rustling_leaves.client.render.backend.LevelRenderHook;

/**
 * Hooks the client part into Fabric. The use key, the start of a frame, the debug screen entry and the compiling of
 * the render pipelines have no Fabric events: see the mixins in {@code fabric.mixin}. The config runs on Forge Config API Port: the same
 * file and, with Mod Menu, the same config screen as on NeoForge.
 */
public final class RustlingLeavesFabricClient implements ClientModInitializer {
    /** The camera's model-view matrix of the frame, kept from its extraction for the drawing. */
    private static final Matrix4f MODEL_VIEW = new Matrix4f();

    @Override
    public void onInitializeClient() {
        ConfigRegistry.INSTANCE.register(RustlingLeaves.MOD_ID, ModConfig.Type.CLIENT, Config.SPEC);
        ConfigScreenFactoryRegistry.INSTANCE.register(RustlingLeaves.MOD_ID, ConfigurationScreen::new);
        ModConfigEvents.loading(RustlingLeaves.MOD_ID).register(RustlingLeavesFabricClient::onConfig);
        ModConfigEvents.reloading(RustlingLeaves.MOD_ID).register(RustlingLeavesFabricClient::onConfig);
        FabricNetwork.registerClient();
        ModelLoadingPlugin.register(LitterModels::register);
        FabricPipelines.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> LeafManager.onClientTick());
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            LevelRenderHook.extract(context.camera(), context.frustum(), context.tickCounter().getGameTimeDeltaPartialTick(false));
            MODEL_VIEW.set(context.viewMatrix());
        });
        // Right after the opaque and cutout terrain, like NeoForge's AfterOpaqueBlocks.
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> LevelRenderHook.render(MODEL_VIEW));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LeafCommands.command(FabricClientCommandSource::sendFeedback)));
    }

    private static void onConfig(ModConfig config) {
        if (config.getSpec() == Config.SPEC) {
            LeafManager.onConfigChanged();
        }
    }
}
