package com.pockyl.rustling_leaves.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.item.BagContents;
import com.pockyl.rustling_leaves.item.LeafBagItem;
import com.pockyl.rustling_leaves.registry.ModDataComponents;

/**
 * Client to server: the leaf bag of this player sucked up leaves. Leaves on the ground exist only on the client, so
 * the client reports the count; the server only accepts it while the player is actually using a bag, and at most
 * what one tick of vacuuming can collect.
 */
public record BagCollectPayload(int count, int color, int shape) implements CustomPacketPayload {
    public static final int MAX_PER_TICK = 24;
    public static final Type<BagCollectPayload> TYPE = new Type<>(RustlingLeaves.id("bag_collect"));

    public static final StreamCodec<ByteBuf, BagCollectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BagCollectPayload::count,
            ByteBufCodecs.INT, BagCollectPayload::color,
            ByteBufCodecs.VAR_INT, BagCollectPayload::shape,
            BagCollectPayload::new);

    @Override
    public Type<BagCollectPayload> type() {
        return TYPE;
    }

    public static void handle(BagCollectPayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getUseItem();
        if (!player.isUsingItem() || !(stack.getItem() instanceof LeafBagItem)) {
            return;
        }
        int count = Math.min(Math.max(0, payload.count()), MAX_PER_TICK);
        BagContents contents = LeafBagItem.contents(stack);
        stack.set(ModDataComponents.BAG_CONTENTS.get(), contents.add(count, payload.color() & 0xFFFFFF, payload.shape() & 15));
    }
}
