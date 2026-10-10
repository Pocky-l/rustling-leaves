package com.pockyl.rustling_leaves.sim;

import net.minecraft.util.Mth;

/**
 * How the year changes leaf fall and the litter on the ground, as plain numbers so the simulation never depends on a
 * season mod. The year has twelve sub-seasons (early, mid and late spring, summer, autumn, winter). Each one has a
 * value at its middle and the values in between are interpolated, so nothing changes with a jump at a sub-season
 * boundary.
 *
 * <p>Leaf fall is an autumn event: it starts in early autumn, peaks in mid and late autumn and ends with the last few
 * leaves in early winter. In spring and summer the trees keep their leaves (cherries shed their blossom in spring). The
 * litter rots away from late autumn on, fast enough that it is gone before the snow builds up in winter.
 */
public final class SeasonCurve {
    public static final int SUB_SEASONS = 12;
    /** Length of a sub-season in Serene Seasons unless configured otherwise (8 days). */
    public static final int DEFAULT_SUB_SEASON_TICKS = 8 * 24000;
    /** Leaf fall in early, mid and late autumn and early winter, relative to the autumn peak. */
    private static final float[] AUTUMN = {0.35F, 0.85F, 1.0F, 0.12F};
    /** Cherry petals through spring and early summer, relative to the cherries' own rate (blossom in mid spring). */
    private static final float[] BLOSSOM = {1.25F, 1.5F, 1.0F, 0.3F};
    /** Natural litter on newly seen ground, relative to the full carpet (leaves fallen since autumn, or not yet). */
    private static final float[] LITTER = {0.1F, 0.1F, 0.1F, 0.12F, 0.15F, 0.2F, 0.45F, 0.8F, 1.0F, 0.15F, 0.0F, 0.0F};
    /**
     * How fast lying leaves rot, in natural carpets per sub-season: a carpet from mid autumn thins out through late autumn
     * and is gone early in winter, before the snow builds up; in winter leaves are gone within days; what is left in
     * spring rots as the snow melts.
     */
    private static final float[] DECAY = {1.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3F, 0.6F, 2.0F, 3.0F, 3.0F};

    private SeasonCurve() {
    }

    /** Like {@link #apply(LeafSettings, float, int)} with sub-seasons of the default length. */
    public static void apply(LeafSettings settings, float position) {
        apply(settings, position, DEFAULT_SUB_SEASON_TICKS);
    }

    /**
     * Sets the season multipliers of the settings for a point in the year.
     *
     * @param position       sub-seasons since the start of early spring, 0 (inclusive) to {@link #SUB_SEASONS} (exclusive)
     * @param subSeasonTicks length of a sub-season in ticks, so the litter rots in step with the seasons however long they are
     */
    public static void apply(LeafSettings settings, float position, int subSeasonTicks) {
        float peak = settings.autumnFallRate;
        float off = settings.offSeasonFallRate;
        float winter = settings.winterFallRate;
        float colors = settings.autumnColorBoost;
        float[] fall = {off, off, off, off, off, off,
                AUTUMN[0] * peak, AUTUMN[1] * peak, AUTUMN[2] * peak, Math.max(winter, AUTUMN[3] * peak), winter, winter};
        float[] petals = fall.clone();
        for (int s = 0; s < BLOSSOM.length; s++) {
            petals[s] = Math.max(petals[s], BLOSSOM[s]);
        }
        settings.seasonFallRate = sample(position, fall);
        settings.seasonPetalRate = sample(position, petals);
        // The few leaves that still come down in early winter are the last brown ones.
        settings.seasonAutumnColors = sample(position, 1.0F, 1.0F, 1.0F,
                1.0F, 1.0F, 1.0F,
                Mth.lerp(0.4F, 1.0F, colors), Mth.lerp(0.75F, 1.0F, colors), colors,
                colors, Mth.lerp(0.5F, 1.0F, colors), 1.0F);
        settings.seasonLitter = sample(position, LITTER);
        settings.seasonDecay = sample(position, DECAY) * settings.seasonalDecay / Math.max(1, subSeasonTicks);
    }

    /**
     * No season: leaves fall at the configured rate all year and the litter rots at its configured pace. For worlds,
     * dimensions and biomes without seasons.
     */
    public static void clear(LeafSettings settings) {
        settings.seasonFallRate = 1.0F;
        settings.seasonPetalRate = 1.0F;
        settings.seasonAutumnColors = 1.0F;
        settings.seasonLitter = 1.0F;
        settings.seasonDecay = 0.0F;
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
