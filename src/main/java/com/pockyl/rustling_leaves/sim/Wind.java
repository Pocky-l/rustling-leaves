package com.pockyl.rustling_leaves.sim;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * A cheap global wind field: a slowly wandering direction, a base speed that grows with rain and thunder, and gust
 * fronts that travel across the world along the wind direction (so a gust visibly sweeps through a forest instead of
 * every leaf twitching at once). All speeds are in blocks per tick.
 */
public final class Wind {
    private static final float CALM = 0.028F;
    private static final float RAIN = 0.035F;
    private static final float THUNDER = 0.06F;

    private float dirX = 1.0F;
    private float dirZ;
    private float base;
    private float time;
    private float rain;

    void update(Level level, int tick, float strength) {
        time = tick;
        float angle = 0.9F * Mth.sin(tick * 0.00071F) + 0.5F * Mth.sin(tick * 0.0023F + 1.3F) + 0.7F;
        dirX = Mth.cos(angle);
        dirZ = Mth.sin(angle);
        boolean open = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
        rain = open ? level.getRainLevel(1.0F) : 0.0F;
        float thunder = open ? level.getThunderLevel(1.0F) : 0.0F;
        base = open ? (CALM + RAIN * rain + THUNDER * thunder) * strength : 0.0F;
    }

    /** Gust factor in [0, 1] at a horizontal position. */
    public float gust(double x, double z) {
        float s = time * 0.025F - (float) ((x * dirX + z * dirZ) * 0.05);
        float g = 0.5F + 0.32F * Mth.sin(s) + 0.18F * Mth.sin(s * 2.7F + 1.1F);
        return g * g;
    }

    /** Wind speed at a position; stronger in gusts and higher up. */
    public float speed(double x, double y, double z) {
        if (base == 0.0F) {
            return 0.0F;
        }
        float altitude = 1.0F + Mth.clamp((float) (y - 62.0) / 80.0F, 0.0F, 0.8F);
        return base * (0.4F + 1.6F * gust(x, z)) * altitude;
    }

    public float dirX() {
        return dirX;
    }

    public float dirZ() {
        return dirZ;
    }

    /** Current rain level in [0, 1] where the sky is open. */
    public float rain() {
        return rain;
    }
}
