package com.pockyl.rustling_leaves.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.pockyl.rustling_leaves.item.BagContents;
import com.pockyl.rustling_leaves.item.LeafBagItem;

import java.util.function.Supplier;

/**
 * Client to server: the leaf bag of this player sucked up leaves. Leaves on the ground exist only on the client, so
 * the client reports the count; the server only accepts it while the player is actually using a bag, and at most
 * what one tick of vacuuming can collect.
 */
public record BagCollectPayload(int count, int color, int shape) {
    public static final int MAX_PER_TICK = 24;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(count);
        buffer.writeInt(color);
        buffer.writeVarInt(shape);
    }

    public static BagCollectPayload decode(FriendlyByteBuf buffer) {
        return new BagCollectPayload(buffer.readVarInt(), buffer.readInt(), buffer.readVarInt());
    }

    public static void handle(BagCollectPayload payload, Supplier<NetworkEvent.Context> context) {
        Player player = context.get().getSender();
        if (player == null) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (!player.isUsingItem() || !(stack.getItem() instanceof LeafBagItem)) {
            return;
        }
        int count = Math.min(Math.max(0, payload.count()), MAX_PER_TICK);
        BagContents contents = LeafBagItem.contents(stack);
        contents.add(count, payload.color() & 0xFFFFFF, payload.shape() & 15).save(stack);
    }
}
