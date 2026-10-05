package com.pockyl.rustling_leaves.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.pockyl.rustling_leaves.sim.LeafPalette;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Colors of leaves: the block's biome tint (as on the tree itself) times the average color of its texture, so leaves
 * match their tree in every biome and for modded trees. Per-leaf variation comes from {@link LeafPalette#vary}.
 */
final class LeafColors {
    private final Map<Block, Integer> textureAverage = new IdentityHashMap<>();

    /** The tree color of a leaves block at a position. */
    int base(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
        return LeafPalette.multiply(tint == -1 ? 0xFFFFFF : tint, textureAverage(state));
    }

    void clear() {
        textureAverage.clear();
    }

    private int textureAverage(BlockState state) {
        return textureAverage.computeIfAbsent(state.getBlock(), block -> {
            try {
                TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY);
                int width = sprite.contents().width();
                int height = sprite.contents().height();
                long r = 0;
                long g = 0;
                long b = 0;
                int n = 0;
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        int abgr = sprite.getPixelRGBA(0, x, y);
                        if ((abgr >>> 24) > 127) {
                            r += abgr & 0xFF;
                            g += abgr >> 8 & 0xFF;
                            b += abgr >> 16 & 0xFF;
                            n++;
                        }
                    }
                }
                if (n == 0) {
                    return 0x8A8A8A;
                }
                // The leaf sprites are light, so the texture average is brightened a bit to land near the tree's look.
                return LeafPalette.scale((int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n), 1.15F);
            } catch (RuntimeException e) {
                return 0x8A8A8A;
            }
        });
    }
}
