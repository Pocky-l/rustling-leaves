package com.pockyl.rustling_leaves.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import com.pockyl.rustling_leaves.RustlingLeaves;

import java.util.IdentityHashMap;
import java.util.Map;

/** Leaf shapes. Each shape has one or more sprites; the tree type picks the shape, the color comes from the tree. */
enum LeafKind {
    BROAD(0, 3, 1.0F, true),
    ROUND(3, 1, 0.85F, true),
    NEEDLE(4, 1, 0.8F, false),
    PETAL(5, 1, 0.7F, false);

    static final ResourceLocation[] SPRITES = {
            RustlingLeaves.id("block/leaf/broad_0"),
            RustlingLeaves.id("block/leaf/broad_1"),
            RustlingLeaves.id("block/leaf/broad_2"),
            RustlingLeaves.id("block/leaf/round"),
            RustlingLeaves.id("block/leaf/needle"),
            RustlingLeaves.id("block/leaf/petal"),
    };

    private static final Map<Block, LeafKind> BY_BLOCK = new IdentityHashMap<>();

    final int firstSprite;
    final int variants;
    final float size;
    /** Whether these leaves may turn yellow, orange or brown; needles and petals keep their color. */
    final boolean autumn;

    LeafKind(int firstSprite, int variants, float size, boolean autumn) {
        this.firstSprite = firstSprite;
        this.variants = variants;
        this.size = size;
        this.autumn = autumn;
    }

    /** Guesses the shape from the block id, which also covers modded trees with conventional names. */
    static LeafKind of(Block block) {
        return BY_BLOCK.computeIfAbsent(block, b -> {
            String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (path.contains("cherry") || path.contains("sakura") || path.contains("blossom")) {
                return PETAL;
            }
            if (path.contains("spruce") || path.contains("pine") || path.contains("fir") || path.contains("cedar")
                    || path.contains("larch") || path.contains("redwood")) {
                return NEEDLE;
            }
            if (path.contains("birch") || path.contains("azalea") || path.contains("aspen")) {
                return ROUND;
            }
            return BROAD;
        });
    }
}
