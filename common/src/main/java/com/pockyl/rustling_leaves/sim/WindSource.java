package com.pockyl.rustling_leaves.sim;

/**
 * The wind of another mod that blows across the whole world: while it has one, the simulation's ambient wind takes its
 * heading and strength instead of wandering on its own, so the leaves fly the way that mod's wind vanes point. Gust
 * waves, squalls and whirlwinds stay the simulation's own.
 */
@FunctionalInterface
public interface WindSource {
    WindSource NONE = out -> false;

    /**
     * Writes the wind of the coming tick into {@code out}: the heading it blows towards as a unit vector
     * ({@code out[0]} x, {@code out[1]} z) and its mean speed in blocks per tick ({@code out[2]}), before the player's
     * wind strength setting, or NaN to keep the simulation's own speed. Returns false when there is no such wind right now; the simulation's own wind blows then.
     */
    boolean read(float[] out);
}
