package com.pockyl.rustling_leaves.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Colors of falling leaves: the block's biome tint (as on the tree itself) times the average color of its texture, so
 * leaves match their tree in every biome and for modded trees, plus a share of autumn colors.
 */
final class LeafColors {
    private static final int[] AUTUMN = {0xE8B53A, 0xDB8A2C, 0xC45A26, 0xA8392A, 0x8C5C2E, 0xC9A23F};

    private final Map<Block, Integer> textureAverage = new IdentityHashMap<>();

    int pick(BlockState state, BlockAndTintGetter level, BlockPos pos, LeafKind kind, float autumnShare, RandomSource random) {
        int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
        int color = multiply(tint == -1 ? 0xFFFFFF : tint, textureAverage(state));
        if (kind.autumn && random.nextFloat() < autumnShare) {
            color = lerp(color, AUTUMN[random.nextInt(AUTUMN.length)], 0.5F + random.nextFloat() * 0.45F);
        }
        float brightness = 0.86F + random.nextFloat() * 0.22F;
        return scale(color, brightness);
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
                return scale((int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n), 1.15F);
            } catch (RuntimeException e) {
                return 0x8A8A8A;
            }
        });
    }

    private static int multiply(int a, int b) {
        int r = (a >> 16 & 0xFF) * (b >> 16 & 0xFF) / 255;
        int g = (a >> 8 & 0xFF) * (b >> 8 & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    private static int scale(int color, float factor) {
        int r = Math.min(255, (int) ((color >> 16 & 0xFF) * factor));
        int g = Math.min(255, (int) ((color >> 8 & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return r << 16 | g << 8 | b;
    }
}
