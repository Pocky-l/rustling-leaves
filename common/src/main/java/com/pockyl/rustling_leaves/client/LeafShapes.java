package com.pockyl.rustling_leaves.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafShape;
import com.pockyl.rustling_leaves.sim.TreeKinds;
import com.pockyl.rustling_leaves.sim.TreeProfile;

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

    /**
     * Seamless texture of densely packed leaves for the body of piles, strewn from the falling leaf sprites (pixel for
     * pixel the size of a loose leaf).
     */
    static final ResourceLocation LITTER = RustlingLeaves.id("block/leaf/litter");
    /** Loose leaves with gaps, over a thin carpet: the ground shows through. */
    static final ResourceLocation LITTER_SPARSE = RustlingLeaves.id("block/leaf/litter_sparse");
    /** Denser loose leaves with gaps, over thicker carpets and on top of pile bodies. */
    static final ResourceLocation LITTER_LAYER = RustlingLeaves.id("block/leaf/litter_layer");
    /** The same three of needle sprigs: the rusty needle mat under conifers. */
    static final ResourceLocation NEEDLE_LITTER = RustlingLeaves.id("block/leaf/needle_litter");
    static final ResourceLocation NEEDLE_LITTER_SPARSE = RustlingLeaves.id("block/leaf/needle_litter_sparse");
    static final ResourceLocation NEEDLE_LITTER_LAYER = RustlingLeaves.id("block/leaf/needle_litter_layer");
    /** The pile textures before 2.8.0, with the option {@code oldLeafTextures}. */
    static final ResourceLocation OLD_LITTER = RustlingLeaves.id("block/leaf/old_litter");
    static final ResourceLocation OLD_LITTER_SPARSE = RustlingLeaves.id("block/leaf/old_litter_sparse");
    static final ResourceLocation OLD_LITTER_LAYER = RustlingLeaves.id("block/leaf/old_litter_layer");

    private static final Map<Block, Integer> KINDS = new IdentityHashMap<>();

    private LeafShapes() {
    }

    /** The shadow sprite of a leaf of this kind of tree. */
    static int shadow(int kind) {
        return TreeKinds.profile(kind).shadow;
    }

    /** Index into the sprites ({@link LeafStyles#sprites}) of a leaf of a kind drawn with sprite index {@code sprite}. */
    static int slot(int kind, int sprite, boolean old) {
        sprite &= 0xFF;
        if (!old) {
            return sprite;
        }
        TreeProfile profile = TreeKinds.profile(kind);
        LeafShape leafShape = profile.shape;
        if (!profile.defaultTexture || sprite < leafShape.firstSprite || sprite >= leafShape.firstSprite + leafShape.variants) {
            // A texture the player chose: the option of the old textures is only about the default ones.
            return sprite;
        }
        int s = leafShape.ordinal();
        return OLD_FIRST[s] + Mth.clamp(sprite - leafShape.firstSprite, 0, OLD_VARIANTS[s] - 1);
    }

    /** The kind of tree of a leaves block ({@link TreeKinds}), keyed by its block id. */
    static int kind(Block block) {
        synchronized (KINDS) {
            return KINDS.computeIfAbsent(block, b -> TreeKinds.id(BuiltInRegistries.BLOCK.getKey(b).toString()));
        }
    }

    /** The settings of a leaves block's tree. */
    static TreeProfile profile(Block block) {
        return TreeKinds.profile(kind(block));
    }

    /** Whether new ground under this tree gets a natural carpet. */
    static boolean carpets(Block block) {
        TreeProfile profile = profile(block);
        return profile.enabled && profile.carpet;
    }

    /** Lets every tree pick up its settings again. */
    static void clearProfiles() {
        TreeKinds.refresh();
    }

    /** Guesses the shape from the block id, which also covers modded trees with conventional names. */
    static LeafShape guess(String key) {
        String path = key.substring(key.indexOf(':') + 1);
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
    }

    /** The shape a leaves block drops, by its settings. */
    static LeafShape of(Block block) {
        return profile(block).shape;
    }
}
