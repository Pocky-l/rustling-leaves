package com.pockyl.rustling_leaves.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafShape;

import java.util.IdentityHashMap;
import java.util.Map;

/** Client side of leaf shapes: the sprite of every sprite index and which shape a tree drops. */
final class LeafShapes {
    /** The sprite indices of {@link LeafShape}: leaves and petals of newer Minecraft versions, the old sprites, the needle sprig. */
    static final ResourceLocation[] SPRITES = {
            RustlingLeaves.id("block/leaf/leaf_light"),
            RustlingLeaves.id("block/leaf/leaf_mid"),
            RustlingLeaves.id("block/leaf/leaf_dark"),
            RustlingLeaves.id("block/leaf/petal_light"),
            RustlingLeaves.id("block/leaf/petal_mid"),
            RustlingLeaves.id("block/leaf/petal_dark"),
            RustlingLeaves.id("block/leaf/broad_0"),
            RustlingLeaves.id("block/leaf/broad_1"),
            RustlingLeaves.id("block/leaf/broad_2"),
            RustlingLeaves.id("block/leaf/round"),
            RustlingLeaves.id("block/leaf/needle"),
            RustlingLeaves.id("block/leaf/petal"),
            RustlingLeaves.id("block/leaf/needle_light"),
            RustlingLeaves.id("block/leaf/needle_mid"),
            RustlingLeaves.id("block/leaf/needle_dark"),
    };
    /**
     * Shadow sprites: white silhouettes with hard pixel edges, drawn translucent black by the renderer - leaf and
     * petal for falling leaves, a square for the ground around piles.
     */
    static final ResourceLocation[] SHADOW_SPRITES = {
            RustlingLeaves.id("block/leaf/shadow/leaf"),
            RustlingLeaves.id("block/leaf/shadow/petal"),
            RustlingLeaves.id("block/leaf/shadow/cell"),
            RustlingLeaves.id("block/leaf/shadow/needle"),
    };
    static final int SHADOW_LEAF = 0;
    static final int SHADOW_PETAL = 1;
    static final int SHADOW_CELL = 2;
    static final int SHADOW_NEEDLE = 3;

    /** First old sprite and number of old sprites of every shape (option {@code oldLeafTextures}). */
    private static final int[] OLD_FIRST = {6, 9, 10, 11};
    private static final int[] OLD_VARIANTS = {3, 1, 1, 1};

    /** Seamless texture of densely packed leaves for the body of piles. */
    static final ResourceLocation LITTER = RustlingLeaves.id("block/leaf/litter");
    /** Loose leaves with gaps, over a thin carpet: the ground shows through. */
    static final ResourceLocation LITTER_SPARSE = RustlingLeaves.id("block/leaf/litter_sparse");
    /** Denser loose leaves with gaps, over thicker carpets and on top of pile bodies. */
    static final ResourceLocation LITTER_LAYER = RustlingLeaves.id("block/leaf/litter_layer");

    private static final Map<Block, LeafShape> BY_BLOCK = new IdentityHashMap<>();

    private LeafShapes() {
    }

    /** The shadow sprite of a leaf of this shape. */
    static int shadow(int shape) {
        return shape == LeafShape.PETAL.ordinal() ? SHADOW_PETAL : shape == LeafShape.NEEDLE.ordinal() ? SHADOW_NEEDLE : SHADOW_LEAF;
    }

    /** Index into {@link #SPRITES} of a leaf drawn with sprite index {@code sprite} of its shape. */
    static int slot(int shape, int sprite, boolean old) {
        if (!old) {
            return sprite;
        }
        LeafShape leafShape = LeafShape.byId(shape);
        int s = leafShape.ordinal();
        return OLD_FIRST[s] + Mth.clamp(sprite - leafShape.firstSprite, 0, OLD_VARIANTS[s] - 1);
    }

    /** Guesses the shape from the block id, which also covers modded trees with conventional names. */
    static LeafShape of(Block block) {
        return BY_BLOCK.computeIfAbsent(block, b -> {
            String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (path.contains("cherry") || path.contains("sakura") || path.contains("blossom")) {
                return LeafShape.PETAL;
            }
            if (path.contains("spruce") || path.contains("pine") || path.contains("fir") || path.contains("cedar")
                    || path.contains("larch") || path.contains("redwood")) {
                return LeafShape.NEEDLE;
            }
            if (path.contains("birch") || path.contains("azalea") || path.contains("aspen")) {
                return LeafShape.ROUND;
            }
            return LeafShape.BROAD;
        });
    }
}
