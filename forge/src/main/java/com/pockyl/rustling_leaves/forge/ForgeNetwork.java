package com.pockyl.rustling_leaves.forge;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.rustling_leaves.network.ModNetwork;
import com.pockyl.rustling_leaves.network.RakePayload;

import java.util.function.Supplier;

/** The rake channel on Forge; optional on both sides. */
final class ForgeNetwork {
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ModNetwork.CHANNEL, () -> ModNetwork.PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(ModNetwork.PROTOCOL_VERSION), NetworkRegistry.acceptMissingOr(ModNetwork.PROTOCOL_VERSION));

    private ForgeNetwork() {
    }

    static void register() {
        CHANNEL.messageBuilder(RakePayload.class, 0)
                .encoder(RakePayload::encode)
                .decoder(RakePayload::decode)
                .consumerMainThread(ForgeNetwork::handle)
                .add();
    }

    /** Whether the other side of a connection has the rake channel. */
    static boolean present(Connection connection) {
        return connection != null && CHANNEL.isRemotePresent(connection);
    }

    static void send(ServerPlayer player, RakePayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static void handle(RakePayload payload, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        if (ctx.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
            ModNetwork.receive(payload);
        } else if (ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER && ctx.getSender() != null) {
            ModNetwork.relay(ctx.getSender(), payload);
        }
    }
}
