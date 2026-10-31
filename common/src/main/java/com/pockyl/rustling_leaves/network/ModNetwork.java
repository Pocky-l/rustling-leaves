package com.pockyl.rustling_leaves.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.pockyl.rustling_leaves.platform.Platform;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The only network traffic of the mod: rake strokes, relayed by a server that has the mod to the players around the
 * raker. Everything is optional - without the mod on the server (or on a client) nothing is sent and the leaves stay
 * client-side as before. The loaders carry {@link RakePayload} on an optional channel and call {@link #relay} with the
 * strokes a server receives. Strokes arriving on a client are handled by the client part of the mod, which registers
 * its own handler (NeoForge 21.11 takes client handlers only from the client side).
 */
public final class ModNetwork {
    public static final String PROTOCOL_VERSION = "1";
    /** Rake strokes are relayed to players this close to the raker (they see the litter there). */
    private static final double RELAY_DISTANCE = 96.0;
    /** How far beyond the block reach a rake point may lie (the rake radius plus some slack for lag). */
    private static final double REACH_SLACK = 3.0;
    /** At most one stroke per player this often; holding the use key strokes every 4 ticks. */
    private static final long MIN_INTERVAL = 2L;

    private static final Map<ServerPlayer, Long> LAST_RAKE = new WeakHashMap<>();

    private ModNetwork() {
    }

    /** Passes a stroke on to every player around the raker whose client has the mod; checks it is plausible first. */
    public static void relay(ServerPlayer raker, RakePayload payload) {
        if (!payload.isFinite()) {
            return;
        }
        long time = raker.level().getGameTime();
        Long last = LAST_RAKE.get(raker);
        if (last != null && time - last < MIN_INTERVAL && time >= last) {
            return;
        }
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        if (!canRake(raker.getEyePosition(), at, raker.blockInteractionRange(), raker.isShiftKeyDown(), raker.getMainHandItem())) {
            return;
        }
        LAST_RAKE.put(raker, time);
        double maxSq = RELAY_DISTANCE * RELAY_DISTANCE;
        for (ServerPlayer player : raker.level().players()) {
            if (player != raker && player.distanceToSqr(at) < maxSq && Platform.INSTANCE.canSendRake(player)) {
                Platform.INSTANCE.sendRake(player, payload);
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
