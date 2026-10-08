package com.pockyl.rustling_leaves.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import com.pockyl.rustling_leaves.sim.LeafPalette;

/**
 * What a leaf bag holds: how many leaves, their average tree color (0xRRGGBB) and their shape (see
 * {@code LeafShape}), enough to pour back leaves that look like the ones collected. Stored in the stack's NBT under
 * {@value #TAG}; a bag without it is empty.
 */
public record BagContents(int count, int color, int shape) {
    public static final int CAPACITY = 1000;
    public static final BagContents EMPTY = new BagContents(0, 0x8A6A2E, 0);
    private static final String TAG = "BagContents";

    /** The contents saved in a stack, or {@link #EMPTY}; out-of-range values are clamped. */
    public static BagContents of(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG, Tag.TAG_COMPOUND)) {
            return EMPTY;
        }
        CompoundTag tag = root.getCompound(TAG);
        return new BagContents(Mth.clamp(tag.getInt("count"), 0, CAPACITY), tag.getInt("color"), Mth.clamp(tag.getInt("shape"), 0, 15));
    }

    public void save(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", count);
        tag.putInt("color", color);
        tag.putInt("shape", shape);
        stack.getOrCreateTag().put(TAG, tag);
    }

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
