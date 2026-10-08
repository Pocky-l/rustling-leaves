package com.pockyl.rustling_leaves.sim;

import net.minecraft.util.Mth;

/**
 * How the year changes leaf fall, as plain numbers so the simulation never depends on a season mod. The year has
 * twelve sub-seasons (early, mid and late spring, summer, autumn, winter). Each one has a value at its middle and the
 * values in between are interpolated, so leaf fall changes smoothly instead of jumping at a sub-season boundary.
 */
public final class SeasonCurve {
    public static final int SUB_SEASONS = 12;
    /** Leaf fall in spring, relative to summer: young leaves hold on. */
    private static final float SPRING_FALL = 0.4F;
    /** Cherry petals at the height of the blossom in mid spring, relative to summer. */
    private static final float BLOSSOM = 1.5F;

    private SeasonCurve() {
    }

    /**
     * Sets the season multipliers of the settings for a point in the year.
     *
     * @param position sub-seasons since the start of early spring, 0 (inclusive) to {@link #SUB_SEASONS} (exclusive)
     */
    public static void apply(LeafSettings settings, float position) {
        float autumn = settings.autumnFallRate;
        float winter = settings.winterFallRate;
        float colors = settings.autumnColorBoost;
        settings.seasonFallRate = sample(position, SPRING_FALL, SPRING_FALL, Mth.lerp(0.5F, SPRING_FALL, 1.0F),
                1.0F, 1.0F, 1.0F,
                Mth.lerp(0.4F, 1.0F, autumn), Mth.lerp(0.8F, 1.0F, autumn), autumn,
                winter, winter, winter);
        settings.seasonPetalRate = sample(position, Mth.lerp(0.5F, 1.0F, BLOSSOM), BLOSSOM, Mth.lerp(0.5F, 1.0F, BLOSSOM),
                1.0F, 1.0F, 1.0F,
                1.0F, 1.0F, 1.0F,
                winter, winter, winter);
        // The few leaves that still come down in early winter are the last brown ones.
        settings.seasonAutumnColors = sample(position, 1.0F, 1.0F, 1.0F,
                1.0F, 1.0F, 1.0F,
                Mth.lerp(0.4F, 1.0F, colors), Mth.lerp(0.75F, 1.0F, colors), colors,
                colors, Mth.lerp(0.5F, 1.0F, colors), 1.0F);
    }

    /** No season (summer all year): for worlds, dimensions and biomes without seasons. */
    public static void clear(LeafSettings settings) {
        settings.seasonFallRate = 1.0F;
        settings.seasonPetalRate = 1.0F;
        settings.seasonAutumnColors = 1.0F;
    }

    /** Linear interpolation between the values at the middles of the two sub-seasons around {@code position}. */
    private static float sample(float position, float... middles) {
        float shifted = position - 0.5F;
        int floor = Mth.floor(shifted);
        int from = Math.floorMod(floor, SUB_SEASONS);
        int to = (from + 1) % SUB_SEASONS;
        return Mth.lerp(shifted - floor, middles[from], middles[to]);
    }
}
