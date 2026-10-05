package com.pockyl.rustling_leaves.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.pockyl.rustling_leaves.RustlingLeaves;

/** Server to clients: a staff of winds raised a whirlwind at a spot or sent a squall from a spot along a direction. */
public record WindSpellPayload(int kind, double x, double y, double z, float dirX, float dirZ) implements CustomPacketPayload {
    public static final int WHIRLWIND = 0;
    public static final int SQUALL = 1;
    public static final Type<WindSpellPayload> TYPE = new Type<>(RustlingLeaves.id("wind_spell"));

    public static final StreamCodec<ByteBuf, WindSpellPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WindSpellPayload::kind,
            ByteBufCodecs.DOUBLE, WindSpellPayload::x,
            ByteBufCodecs.DOUBLE, WindSpellPayload::y,
            ByteBufCodecs.DOUBLE, WindSpellPayload::z,
            ByteBufCodecs.FLOAT, WindSpellPayload::dirX,
            ByteBufCodecs.FLOAT, WindSpellPayload::dirZ,
            WindSpellPayload::new);

    @Override
    public Type<WindSpellPayload> type() {
        return TYPE;
    }
}
