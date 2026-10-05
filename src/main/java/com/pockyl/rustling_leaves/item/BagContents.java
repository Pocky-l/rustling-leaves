package com.pockyl.rustling_leaves.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.pockyl.rustling_leaves.sim.LeafPalette;

/**
 * What a leaf bag holds: how many leaves, their average tree color (0xRRGGBB) and their shape (see
 * {@code LeafShape}), enough to pour back leaves that look like the ones collected.
 */
public record BagContents(int count, int color, int shape) {
    public static final int CAPACITY = 1000;
    public static final BagContents EMPTY = new BagContents(0, 0x8A6A2E, 0);

    public static final Codec<BagContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, CAPACITY).fieldOf("count").forGetter(BagContents::count),
            Codec.INT.fieldOf("color").forGetter(BagContents::color),
            Codec.intRange(0, 15).fieldOf("shape").forGetter(BagContents::shape)
    ).apply(instance, BagContents::new));

    public static final StreamCodec<ByteBuf, BagContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BagContents::count,
            ByteBufCodecs.INT, BagContents::color,
            ByteBufCodecs.VAR_INT, BagContents::shape,
            BagContents::new);

    public int room() {
        return CAPACITY - count;
    }

    /** Adds leaves of a color; the bag's color moves towards it in proportion to how many were added. */
    public BagContents add(int leaves, int leafColor, int leafShape) {
        int added = Math.min(leaves, room());
        if (added <= 0) {
            return this;
        }
        int total = count + added;
        int mixed = count == 0 ? leafColor : LeafPalette.lerp(color, leafColor, added / (float) total);
        return new BagContents(total, mixed, count == 0 ? leafShape : shape);
    }

    public BagContents remove(int leaves) {
        return new BagContents(Math.max(0, count - leaves), color, shape);
    }
}
