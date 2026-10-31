package com.pockyl.rustling_leaves.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.rustling_leaves.client.RustlingLeavesClient;
import com.pockyl.rustling_leaves.network.ModNetwork;
import com.pockyl.rustling_leaves.network.RakePayload;

/**
 * The rake channel on Fabric; optional on both sides (a side without it simply cannot send on it). Fabric API runs the
 * handlers on the main thread.
 */
final class FabricNetwork {
    private FabricNetwork() {
    }

    /** Both sides: the payload in both directions and the server's receiver. */
    static void register() {
        PayloadTypeRegistry.playC2S().register(RakePayload.TYPE, RakePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RakePayload.TYPE, RakePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RakePayload.TYPE, (payload, context) -> ModNetwork.relay(context.player(), payload));
    }

    /** Client side; only called on clients. */
    static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(RakePayload.TYPE, (payload, context) -> RustlingLeavesClient.onRake(payload));
    }

    static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, RakePayload.TYPE);
    }

    static void send(ServerPlayer player, RakePayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    /** Client side; only called on clients. */
    static boolean serverHasRake() {
        return ClientPlayNetworking.canSend(RakePayload.TYPE);
    }

    /** Client side; only called on clients. */
    static void sendToServer(RakePayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
