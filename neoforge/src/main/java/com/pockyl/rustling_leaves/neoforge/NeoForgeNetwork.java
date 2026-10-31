package com.pockyl.rustling_leaves.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.pockyl.rustling_leaves.network.ModNetwork;
import com.pockyl.rustling_leaves.network.RakePayload;

/** The rake channel on NeoForge; optional on both sides. */
final class NeoForgeNetwork {
    private NeoForgeNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModNetwork.PROTOCOL_VERSION).optional();
        registrar.playBidirectional(RakePayload.TYPE, RakePayload.STREAM_CODEC, NeoForgeNetwork::handle);
    }

    private static void handle(RakePayload payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            ModNetwork.receive(payload);
        } else if (context.player() instanceof ServerPlayer player) {
            ModNetwork.relay(player, payload);
        }
    }

    static boolean canSend(ServerPlayer player) {
        return player.connection.hasChannel(RakePayload.TYPE);
    }

    static void send(ServerPlayer player, RakePayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Client side; only called on clients. */
    static boolean serverHasRake() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(RakePayload.TYPE);
    }

    /** Client side; only called on clients. */
    static void sendToServer(RakePayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
