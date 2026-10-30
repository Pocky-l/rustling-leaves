package com.pockyl.rustling_leaves.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A rake stroke at a point. Client to server: the local player raked there; server to clients: a player near them did,
 * so their own litter gets raked at the same spot.
 */
public record RakePayload(double x, double y, double z) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static RakePayload decode(FriendlyByteBuf buf) {
        return new RakePayload(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
