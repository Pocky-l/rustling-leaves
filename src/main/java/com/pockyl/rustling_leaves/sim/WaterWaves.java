package com.pockyl.rustling_leaves.sim;

/**
 * The water surface as it is drawn, which can lie above or below the still water level the simulation works with:
 * waves of another mod, or a shader pack's swaying water. Floating leaves keep drifting on the still water and are only drawn riding the waves, so the
 * physics stay the same with or without them.
 */
@FunctionalInterface
public interface WaterWaves {
    WaterWaves NONE = (x, z) -> Float.NaN;

    /**
     * How far the drawn water surface at (x, z) lies above the still water level at the end of the coming tick, in
     * blocks (negative in a trough), or NaN where the water is drawn flat.
     */
    float lift(double x, double z);
}
