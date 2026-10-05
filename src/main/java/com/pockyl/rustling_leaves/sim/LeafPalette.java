package com.pockyl.rustling_leaves.sim;

import net.minecraft.util.Mth;

/** Color helpers shared by falling leaves and the leaf litter. Colors are 0xRRGGBB. */
public final class LeafPalette {
    private static final int[] AUTUMN = {0xE8B53A, 0xDB8A2C, 0xCF6A2E, 0xBC4E34, 0xA87842, 0xD4AE4A, 0x9C7448};

    private LeafPalette() {
    }

    /**
     * Gives one leaf its own color from the tree color: a share of leaves turns to an autumn color, every leaf gets a
     * slightly different brightness. Deterministic in {@code hash}.
     */
    public static int vary(int base, long hash, float autumnShare, LeafShape shape) {
        float pick = (hash & 0xFFFF) / 65536.0F;
        int color = base;
        if (shape.autumn && pick < autumnShare) {
            int autumn = AUTUMN[(int) ((hash >>> 16) & 0xFF) % AUTUMN.length];
            color = lerp(color, autumn, 0.5F + ((hash >>> 24) & 0xFF) / 255.0F * 0.45F);
        }
        float brightness = 0.93F + ((hash >>> 32) & 0xFF) / 255.0F * 0.17F;
        return scale(color, brightness);
    }

    public static int multiply(int a, int b) {
        int r = (a >> 16 & 0xFF) * (b >> 16 & 0xFF) / 255;
        int g = (a >> 8 & 0xFF) * (b >> 8 & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    public static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    public static int scale(int color, float factor) {
        int r = Math.min(255, (int) ((color >> 16 & 0xFF) * factor));
        int g = Math.min(255, (int) ((color >> 8 & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return r << 16 | g << 8 | b;
    }

    /** A well mixed 64-bit hash of a few ints. */
    public static long hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        h *= 0x81DADEF4BC2DD44DL;
        return h ^ h >>> 33;
    }
}
