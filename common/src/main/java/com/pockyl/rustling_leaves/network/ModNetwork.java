package com.pockyl.rustling_leaves.network;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.pockyl.rustling_leaves.RustlingLeaves;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The only network traffic of the mod: rake strokes, relayed by a server that has the mod to the players around the
 * raker. Everything is optional - without the mod on the server (or on a client) nothing is sent and the leaves stay
 * client-side as before.
 */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    /** Rake strokes are relayed to players this close to the raker (they see the litter there). */
    private static final double RELAY_DISTANCE = 96.0;
    /** How far beyond the block reach a rake point may lie (the rake radius plus some slack for lag). */
    private static final double REACH_SLACK = 3.0;
    /** At most one stroke per player this often; holding the use key strokes every 4 ticks. */
    private static final long MIN_INTERVAL = 2L;

    // Optional on both sides. A new channel name: the old "main" channel of the leaf tools had other messages.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(RustlingLeaves.id("rake"), () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION), NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

    /** Set by the client part of the mod; a dedicated server never loads client classes through this. */
    private static volatile Consumer<RakePayload> clientRake = payload -> {
    };
    private static final Map<ServerPlayer, Long> LAST_RAKE = new WeakHashMap<>();

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(RakePayload.class, 0)
                .encoder(RakePayload::encode)
                .decoder(RakePayload::decode)
                .consumerMainThread(ModNetwork::handle)
                .add();
    }

    public static void setClientRake(Consumer<RakePayload> handler) {
        clientRake = handler;
    }

    /** Whether the other side of a connection has the rake channel. */
    public static boolean present(Connection connection) {
        return connection != null && CHANNEL.isRemotePresent(connection);
    }

    public static void sendToServer(RakePayload payload) {
        CHANNEL.sendToServer(payload);
    }

    private static void handle(RakePayload payload, Supplier<NetworkEvent.Context> context) {
        if (!payload.isFinite()) {
            return;
        }
        NetworkEvent.Context ctx = context.get();
        if (ctx.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
            clientRake.accept(payload);
        } else if (ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER && ctx.getSender() != null) {
            relay(ctx.getSender(), payload);
        }
    }

    /** Passes a stroke on to every player around the raker whose client has the mod; checks it is plausible first. */
    private static void relay(ServerPlayer raker, RakePayload payload) {
        long time = raker.serverLevel().getGameTime();
        Long last = LAST_RAKE.get(raker);
        if (last != null && time - last < MIN_INTERVAL && time >= last) {
            return;
        }
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        if (!canRake(raker.getEyePosition(), at, raker.getBlockReach(), raker.isShiftKeyDown(), raker.getMainHandItem())) {
            return;
        }
        LAST_RAKE.put(raker, time);
        double maxSq = RELAY_DISTANCE * RELAY_DISTANCE;
        for (ServerPlayer player : raker.serverLevel().players()) {
            if (player != raker && player.distanceToSqr(at) < maxSq && present(player.connection.connection)) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
            }
        }
    }

    /** Whether a player at {@code eye} could rake at {@code at}: sneaking with a hoe or shovel, within reach. */
    public static boolean canRake(Vec3 eye, Vec3 at, double reach, boolean sneaking, ItemStack mainHand) {
        if (!sneaking || !mainHand.is(ItemTags.HOES) && !mainHand.is(ItemTags.SHOVELS)) {
            return false;
        }
        double max = reach + REACH_SLACK;
        return eye.distanceToSqr(at) <= max * max;
    }
}
