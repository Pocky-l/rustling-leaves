package com.pockyl.rustling_leaves.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import com.pockyl.rustling_leaves.network.ModNetwork;
import com.pockyl.rustling_leaves.network.RakePayload;

/** The rake channel on Fabric; optional on both sides (a side without it simply cannot send on it). */
final class FabricNetwork {
    private FabricNetwork() {
    }

    static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ModNetwork.CHANNEL, (server, player, handler, buf, sender) -> {
            RakePayload payload = RakePayload.decode(buf);
            server.execute(() -> ModNetwork.relay(player, payload));
        });
    }

    /** Client side; only called on clients. */
    static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CHANNEL, (client, handler, buf, sender) -> {
            RakePayload payload = RakePayload.decode(buf);
            client.execute(() -> ModNetwork.receive(payload));
        });
    }

    static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, ModNetwork.CHANNEL);
    }

    static void send(ServerPlayer player, RakePayload payload) {
        ServerPlayNetworking.send(player, ModNetwork.CHANNEL, encode(payload));
    }

    static boolean serverHasRake() {
        return ClientPlayNetworking.canSend(ModNetwork.CHANNEL);
    }

    static void sendToServer(RakePayload payload) {
        ClientPlayNetworking.send(ModNetwork.CHANNEL, encode(payload));
    }

    private static FriendlyByteBuf encode(RakePayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.encode(buf);
        return buf;
    }
}
