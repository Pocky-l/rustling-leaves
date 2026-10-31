package com.pockyl.rustling_leaves.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.rustling_leaves.sim.LeafPalette;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Colors of leaves: the block's biome tint (as on the tree itself) times the average color of its texture, so leaves
 * match their tree in every biome and for modded trees. Per-leaf variation comes from {@link LeafPalette#vary}.
 */
final class LeafColors {
    private final Map<Block, Integer> textureAverage = new IdentityHashMap<>();

    /** The tree color of a leaves block at a position (the client level tints by biome). */
    int base(BlockState state, Level level, BlockPos pos) {
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        int tint = source == null ? -1
                : level instanceof BlockAndTintGetter tinted ? source.colorInWorld(state, tinted, pos) : source.color(state);
        return LeafPalette.multiply(tint == -1 ? 0xFFFFFF : tint, textureAverage(state));
    }

    void clear() {
        textureAverage.clear();
    }

    private int textureAverage(BlockState state) {
        return textureAverage.computeIfAbsent(state.getBlock(), block -> {
            try {
                TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
                int width = sprite.contents().width();
                int height = sprite.contents().height();
                long r = 0;
                long g = 0;
                long b = 0;
                int n = 0;
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        // Despite its name, NeoForge's getPixelRGBA hands out ARGB here (vanilla NativeImage.getPixel).
                        int argb = sprite.getPixelRGBA(0, x, y);
                        if ((argb >>> 24) > 127) {
                            r += argb >> 16 & 0xFF;
                            g += argb >> 8 & 0xFF;
                            b += argb & 0xFF;
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
