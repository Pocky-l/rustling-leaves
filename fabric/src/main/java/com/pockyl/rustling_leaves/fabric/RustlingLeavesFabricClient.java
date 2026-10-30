package com.pockyl.rustling_leaves.fabric;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import fuzs.forgeconfigapiport.api.config.v2.ModConfigEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.fml.config.ModConfig;

import com.pockyl.rustling_leaves.Config;
import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.client.LeafCommands;
import com.pockyl.rustling_leaves.client.LeafManager;
import com.pockyl.rustling_leaves.client.RustlingLeavesClient;
import com.pockyl.rustling_leaves.client.compat.ImmersiveWeathering;
import com.pockyl.rustling_leaves.client.compat.PackWeather;

/**
 * Hooks the client part into Fabric. The use key and the debug screen have no Fabric events: see the mixins in
 * {@code fabric.mixin}.
 */
public final class RustlingLeavesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RustlingLeavesClient.init();
        ForgeConfigRegistry.INSTANCE.register(RustlingLeaves.MOD_ID, ModConfig.Type.CLIENT, Config.SPEC);
        ModConfigEvents.loading(RustlingLeaves.MOD_ID).register(RustlingLeavesFabricClient::onConfig);
        ModConfigEvents.reloading(RustlingLeaves.MOD_ID).register(RustlingLeavesFabricClient::onConfig);
        FabricNetwork.registerClient();
        if (FabricLoader.getInstance().isModLoaded(ImmersiveWeathering.MOD_ID)) {
            ModelLoadingPlugin.register(PileModels::register);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> LeafManager.onClientTick());
        WorldRenderEvents.START.register(context -> PackWeather.onFrameStart(context.tickDelta()));
        // Right after the solid and cutout terrain, like Forge's AFTER_CUTOUT_BLOCKS.
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (context.frustum() != null) {
                LeafManager.onRenderLevel(context.camera(), context.frustum(), context.matrixStack().last().pose(),
                        context.projectionMatrix(), context.tickDelta());
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
