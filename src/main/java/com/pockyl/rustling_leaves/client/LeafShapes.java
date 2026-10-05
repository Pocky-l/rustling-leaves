package com.pockyl.rustling_leaves.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import com.pockyl.rustling_leaves.RustlingLeaves;
import com.pockyl.rustling_leaves.sim.LeafShape;

import java.util.IdentityHashMap;
import java.util.Map;

/** Client side of leaf shapes: the sprite of every sprite index and which shape a tree drops. */
final class LeafShapes {
    static final ResourceLocation[] SPRITES = {
            RustlingLeaves.id("block/leaf/broad_0"),
            RustlingLeaves.id("block/leaf/broad_1"),
            RustlingLeaves.id("block/leaf/broad_2"),
            RustlingLeaves.id("block/leaf/round"),
            RustlingLeaves.id("block/leaf/needle"),
            RustlingLeaves.id("block/leaf/petal"),
    };

    private static final Map<Block, LeafShape> BY_BLOCK = new IdentityHashMap<>();

    private LeafShapes() {
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
