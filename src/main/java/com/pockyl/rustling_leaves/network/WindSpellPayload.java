package com.pockyl.rustling_leaves.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server to clients: a staff of winds raised a whirlwind at a spot or sent a squall from a spot along a direction. */
public record WindSpellPayload(int kind, double x, double y, double z, float dirX, float dirZ) {
    public static final int WHIRLWIND = 0;
    public static final int SQUALL = 1;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(kind);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(dirX);
        buffer.writeFloat(dirZ);
    }

    public static WindSpellPayload decode(FriendlyByteBuf buffer) {
        return new WindSpellPayload(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                buffer.readFloat());
    }
}
