package com.pockyl.rustling_leaves.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.pockyl.rustling_leaves.RustlingLeaves;

/**
 * A rake stroke at a point. Client to server: the local player raked there; server to clients: a player near them did,
 * so their own litter gets raked at the same spot.
 */
public record RakePayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<RakePayload> TYPE = new Type<>(RustlingLeaves.id("rake"));

    public static final StreamCodec<ByteBuf, RakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RakePayload::x,
            ByteBufCodecs.DOUBLE, RakePayload::y,
            ByteBufCodecs.DOUBLE, RakePayload::z,
            RakePayload::new);

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    @Override
    public Type<RakePayload> type() {
        return TYPE;
    }
}
