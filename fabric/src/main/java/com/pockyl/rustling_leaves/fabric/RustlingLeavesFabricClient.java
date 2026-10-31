package com.pockyl.rustling_leaves.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafCommands;
import com.pockyl.rustling_leaves.client.LeafManager;
import com.pockyl.rustling_leaves.client.RustlingLeavesClient;
import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;
import com.pockyl.rustling_leaves.client.render.backend.LevelRenderHook;

/**
 * Hooks the client part into Fabric. The use key, the start of a frame and the debug screen have no Fabric events:
 * see the mixins in {@code fabric.mixin}. The config runs on Forge Config API Port: the same file and, with Mod Menu,
 * the same config screen as on NeoForge.
 */
public final class RustlingLeavesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RustlingLeavesClient.init();
        // Forge Config API Port loads a client config right when it is registered, so the listeners come first.
        NeoForgeModConfigEvents.loading(RustlingLeaves.MOD_ID).register(RustlingLeavesFabricClient::onConfig);
        NeoForgeModConfigEvents.reloading(RustlingLeaves.MOD_ID).register(RustlingLeavesFabricClient::onConfig);
        NeoForgeConfigRegistry.INSTANCE.register(RustlingLeaves.MOD_ID, ModConfig.Type.CLIENT, Config.SPEC);
        ConfigScreenFactoryRegistry.INSTANCE.register(RustlingLeaves.MOD_ID, ConfigurationScreen::new);
        FabricNetwork.registerClient();
        if (FabricLoader.getInstance().isModLoaded(ImmersiveWeathering.MOD_ID)) {
            ModelLoadingPlugin.register(PileModels::register);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> LeafManager.onClientTick());
        // Right after the solid and cutout terrain, like NeoForge's AFTER_CUTOUT_BLOCKS.
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (context.frustum() != null) {
                LevelRenderHook.render(context.camera(), context.frustum(), context.positionMatrix(), context.projectionMatrix(),
                        context.tickCounter().getGameTimeDeltaPartialTick(false));
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LeafCommands.command(FabricClientCommandSource::sendFeedback)));
    }

    private static void onConfig(ModConfig config) {
        if (config.getSpec() == Config.SPEC) {
            LeafManager.onConfigChanged();
        }
    }
}
